#!/usr/bin/env bash
# shellcheck disable=SC2016  # crases sao sintaxe JMESPath do --query, nao expansao de shell
# Desprovisiona tudo da AWS (EKS + node group + ELB + volumes + ECR + RDS + ElastiCache +
# SQS + S3 + VPC) apos uma
# sessao de testes. Mesmo padrao de destroy-zero-cost-aws.sh das fases anteriores:
#   [1] limpeza Kubernetes (ELB do ingress-nginx + namespaces, best-effort) — libera
#       o Load Balancer e os volumes EBS antes do Terraform tentar apagar a VPC
#   [2] terraform destroy (best-effort, state remoto no S3)
#   [3] varredura via aws CLI por nome/tag Project, independente do state do Terraform
#   [4] scripts/aws-validate.sh --strict, pra confirmar que nao sobrou nada
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/aws-destroy.sh [opcoes]

Opcoes:
  --region <regiao>         Regiao AWS (padrao: us-east-1).
  --project <prefixo>       Prefixo/tag Project dos recursos (padrao: fiapx).
  --auto-approve            Executa sem confirmacao interativa.
  --skip-k8s-cleanup        Nao executa limpeza previa via kubectl/helm.
  --skip-terraform-destroy  Nao tenta terraform destroy.
  --skip-cli-cleanup        Nao executa a varredura via aws CLI (fase [3]).
  --skip-validate           Nao executa scripts/aws-validate.sh ao final.
  --destroy-tf-state        Remove tambem o bucket S3 do state do Terraform.
  -h, --help                Exibe esta ajuda.
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 2; }
}

print_status() {
  printf "%-9s %-42s %s\n" "$1" "$2" "$3"
}

confirm_execution() {
  [[ "$AUTO_APPROVE" == "true" ]] && return
  echo
  echo "Isso vai tentar remover TODOS os recursos AWS do projeto '$PROJECT' em $AWS_REGION."
  read -r -p "Continuar? (yes/no): " answer
  [[ "$answer" == "yes" ]] || { echo "Abortado."; exit 0; }
}

STEP_LOG="$(mktemp)"
trap 'rm -f "$STEP_LOG"' EXIT

run_best_effort() {
  local label="$1"; shift
  if "$@" >"$STEP_LOG" 2>&1; then
    print_status "[OK]" "$label" "executado"
    return 0
  fi
  print_status "[ALERTA]" "$label" "falhou (continuando)"
  tail -n 5 "$STEP_LOG" | sed 's/^/  > /' >&2
  return 1
}

aws_text() {
  aws --region "$AWS_REGION" --output text "$@" 2>/dev/null || true
}

is_empty() {
  [[ -z "${1:-}" || "$1" == "None" ]]
}

# ── Fase 1: limpeza Kubernetes ──────────────────────────────────────────────

k8s_cleanup() {
  if ! command -v kubectl >/dev/null 2>&1 || ! kubectl cluster-info >/dev/null 2>&1; then
    print_status "[OK]" "kubectl" "cluster inalcancavel, pulando limpeza k8s"
    return
  fi

  if command -v helm >/dev/null 2>&1 && helm status ingress-nginx -n ingress-nginx >/dev/null 2>&1; then
    run_best_effort "helm uninstall ingress-nginx (derruba o ELB)" \
      helm uninstall ingress-nginx -n ingress-nginx --wait --timeout 3m || true
  else
    print_status "[OK]" "ingress-nginx" "nao instalado"
  fi

  # Apagar os namespaces remove os PVCs → o driver EBS CSI apaga os volumes.
  run_best_effort "kubectl delete namespace $NAMESPACE" \
    kubectl delete namespace "$NAMESPACE" --ignore-not-found --wait=true --timeout=180s || true
  run_best_effort "kubectl delete namespace monitoring/keda/ingress-nginx" \
    kubectl delete namespace monitoring keda ingress-nginx --ignore-not-found --wait=true --timeout=180s || true
}

# ── Fase 2: terraform destroy (best-effort) ─────────────────────────────────

terraform_destroy() {
  if ! command -v terraform >/dev/null 2>&1; then
    print_status "[OK]" "terraform" "nao instalado, pulando (varredura aws CLI cobre isso)"
    return
  fi

  export TF_VAR_aws_region="$AWS_REGION"
  run_best_effort "terraform init" "$SCRIPT_DIR/aws-tf-init.sh" "$TF_DIR" || return
  run_best_effort "terraform destroy" terraform -chdir="$TF_DIR" destroy -input=false -auto-approve || true
}

# ── Fase 3: varredura via aws CLI, independente do state do Terraform ──────

delete_eks_by_name() {
  local clusters
  clusters="$(aws_text eks list-clusters --query "clusters[?contains(@, '${PROJECT}')]")"
  if is_empty "$clusters"; then
    print_status "[OK]" "Clusters EKS" "nenhum cluster com prefixo '$PROJECT'"
    return
  fi

  local cluster ng nodegroups
  for cluster in $clusters; do
    nodegroups="$(aws_text eks list-nodegroups --cluster-name "$cluster" --query 'nodegroups')"
    for ng in $nodegroups; do
      is_empty "$ng" && continue
      aws eks delete-nodegroup --region "$AWS_REGION" --cluster-name "$cluster" --nodegroup-name "$ng" >/dev/null 2>&1 || true
      print_status "[INFO]" "EKS node group" "$cluster/$ng (delete solicitado)"
    done
    for ng in $nodegroups; do
      is_empty "$ng" && continue
      aws eks wait nodegroup-deleted --region "$AWS_REGION" --cluster-name "$cluster" --nodegroup-name "$ng" >/dev/null 2>&1 || true
    done

    local addons addon
    addons="$(aws_text eks list-addons --cluster-name "$cluster" --query 'addons')"
    for addon in $addons; do
      is_empty "$addon" && continue
      aws eks delete-addon --region "$AWS_REGION" --cluster-name "$cluster" --addon-name "$addon" >/dev/null 2>&1 || true
    done

    aws eks delete-cluster --region "$AWS_REGION" --name "$cluster" >/dev/null 2>&1 || true
    aws eks wait cluster-deleted --region "$AWS_REGION" --name "$cluster" >/dev/null 2>&1 || true
    print_status "[OK]" "Cluster EKS" "$cluster removido"
  done
}

delete_ecr_repositories() {
  local repos repo
  repos="$(aws_text ecr describe-repositories \
    --query "repositories[?starts_with(repositoryName, '${PROJECT}/')].repositoryName")"
  if is_empty "$repos"; then
    print_status "[OK]" "Repositorios ECR" "nenhum com prefixo '$PROJECT/'"
    return
  fi
  for repo in $repos; do
    aws ecr delete-repository --region "$AWS_REGION" --repository-name "$repo" --force >/dev/null 2>&1 || true
    print_status "[INFO]" "ECR delete" "$repo"
  done
}

delete_classic_elb_by_vpc() {
  local vpc_id="$1" elb_names elb
  elb_names="$(aws_text elb describe-load-balancers \
    --query "LoadBalancerDescriptions[?VPCId=='${vpc_id}'].LoadBalancerName")"
  is_empty "$elb_names" && return
  for elb in $elb_names; do
    aws elb delete-load-balancer --region "$AWS_REGION" --load-balancer-name "$elb" >/dev/null 2>&1 || true
    print_status "[INFO]" "Classic ELB delete" "$elb (solicitado)"
  done
}

delete_elbv2_by_vpc() {
  local vpc_id="$1" lb_arns lb_arn
  lb_arns="$(aws_text elbv2 describe-load-balancers \
    --query "LoadBalancers[?VpcId=='${vpc_id}'].LoadBalancerArn")"
  is_empty "$lb_arns" && return
  for lb_arn in $lb_arns; do
    aws elbv2 delete-load-balancer --region "$AWS_REGION" --load-balancer-arn "$lb_arn" >/dev/null 2>&1 || true
    print_status "[INFO]" "ELBv2 delete" "$lb_arn (solicitado)"
  done
  local tg_arns tg_arn
  tg_arns="$(aws_text elbv2 describe-target-groups --query "TargetGroups[?VpcId=='${vpc_id}'].TargetGroupArn")"
  for tg_arn in $tg_arns; do
    is_empty "$tg_arn" && continue
    aws elbv2 delete-target-group --region "$AWS_REGION" --target-group-arn "$tg_arn" >/dev/null 2>&1 || true
  done
}

terminate_instances_in_vpc() {
  local vpc_id="$1" ids
  ids="$(aws_text ec2 describe-instances \
    --filters "Name=vpc-id,Values=${vpc_id}" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[].Instances[].InstanceId')"
  is_empty "$ids" && return
  # shellcheck disable=SC2086
  aws ec2 terminate-instances --region "$AWS_REGION" --instance-ids $ids >/dev/null 2>&1 || true
  # shellcheck disable=SC2086
  aws ec2 wait instance-terminated --region "$AWS_REGION" --instance-ids $ids >/dev/null 2>&1 || true
  print_status "[INFO]" "EC2 terminate" "$ids"
}

delete_rds() {
  local db="${PROJECT}-postgres"
  if aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "$db" >/dev/null 2>&1; then
    aws rds modify-db-instance --region "$AWS_REGION" --db-instance-identifier "$db" \
      --no-deletion-protection --apply-immediately >/dev/null 2>&1 || true
    aws rds delete-db-instance --region "$AWS_REGION" --db-instance-identifier "$db" \
      --skip-final-snapshot --delete-automated-backups >/dev/null 2>&1 || true
    print_status "[INFO]" "RDS" "$db (delete solicitado, aguardando...)"
    aws rds wait db-instance-deleted --region "$AWS_REGION" --db-instance-identifier "$db" >/dev/null 2>&1 || true
  else
    print_status "[OK]" "RDS" "nenhuma instancia $db"
  fi
  aws rds delete-db-subnet-group --region "$AWS_REGION" --db-subnet-group-name "${PROJECT}-rds" >/dev/null 2>&1 || true
  local snaps snap
  snaps="$(aws_text rds describe-db-snapshots --db-instance-identifier "$db" --query 'DBSnapshots[].DBSnapshotIdentifier')"
  for snap in $snaps; do
    is_empty "$snap" && continue
    aws rds delete-db-snapshot --region "$AWS_REGION" --db-snapshot-identifier "$snap" >/dev/null 2>&1 || true
  done
}

delete_elasticache() {
  local cluster="${PROJECT}-redis"
  if aws elasticache describe-cache-clusters --region "$AWS_REGION" --cache-cluster-id "$cluster" >/dev/null 2>&1; then
    aws elasticache delete-cache-cluster --region "$AWS_REGION" --cache-cluster-id "$cluster" >/dev/null 2>&1 || true
    print_status "[INFO]" "ElastiCache" "$cluster (delete solicitado, aguardando...)"
    aws elasticache wait cache-cluster-deleted --region "$AWS_REGION" --cache-cluster-id "$cluster" >/dev/null 2>&1 || true
  else
    print_status "[OK]" "ElastiCache" "nenhum cluster $cluster"
  fi
  aws elasticache delete-cache-subnet-group --region "$AWS_REGION" --cache-subnet-group-name "${PROJECT}-redis" >/dev/null 2>&1 || true
}

delete_sqs_queues() {
  local urls url
  urls="$(aws_text sqs list-queues --queue-name-prefix "${PROJECT}-" --query 'QueueUrls')"
  if is_empty "$urls"; then
    print_status "[OK]" "Filas SQS" "nenhuma com prefixo '${PROJECT}-'"
    return
  fi
  for url in $urls; do
    aws sqs delete-queue --region "$AWS_REGION" --queue-url "$url" >/dev/null 2>&1 || true
    print_status "[INFO]" "SQS delete" "$url"
  done
}

delete_ssm_parameters() {
  local names name
  names="$(aws_text ssm get-parameters-by-path --path "/${PROJECT}" --recursive --query 'Parameters[].Name')"
  if is_empty "$names"; then
    print_status "[OK]" "Parametros SSM" "nenhum sob /${PROJECT}"
    return
  fi
  for name in $names; do
    aws ssm delete-parameter --region "$AWS_REGION" --name "$name" >/dev/null 2>&1 || true
    print_status "[INFO]" "SSM delete" "$name"
  done
}

delete_iam_roles() {
  # Roles do cluster e dos nos (iam.tf): so saem depois do node group/cluster. Precisam ter as
  # policies desanexadas e as inline apagadas antes do delete-role.
  local role policy
  for role in "${PROJECT}-eks-cluster" "${PROJECT}-eks-node"; do
    if ! aws iam get-role --role-name "$role" >/dev/null 2>&1; then
      print_status "[OK]" "IAM role" "${role} nao existe"
      continue
    fi
    for policy in $(aws iam list-attached-role-policies --role-name "$role" --query 'AttachedPolicies[].PolicyArn' --output text 2>/dev/null); do
      aws iam detach-role-policy --role-name "$role" --policy-arn "$policy" >/dev/null 2>&1 || true
    done
    for policy in $(aws iam list-role-policies --role-name "$role" --query 'PolicyNames' --output text 2>/dev/null); do
      aws iam delete-role-policy --role-name "$role" --policy-name "$policy" >/dev/null 2>&1 || true
    done
    aws iam delete-role --role-name "$role" >/dev/null 2>&1 || true
    print_status "[INFO]" "IAM role delete" "$role"
  done
}

delete_video_buckets() {
  local buckets bucket
  buckets="$(aws_text s3api list-buckets --query "Buckets[?starts_with(Name, '${PROJECT}-videos-')].Name")"
  if is_empty "$buckets"; then
    print_status "[OK]" "Buckets S3 de video" "nenhum com prefixo '${PROJECT}-videos-'"
    return
  fi
  for bucket in $buckets; do
    aws s3 rm "s3://${bucket}" --recursive --region "$AWS_REGION" >/dev/null 2>&1 || true
    aws s3api delete-bucket --bucket "$bucket" --region "$AWS_REGION" >/dev/null 2>&1 || true
    print_status "[INFO]" "S3 delete" "$bucket"
  done
}

delete_available_volumes() {
  local ids id
  ids="$(aws_text ec2 describe-volumes \
    --filters "Name=status,Values=available" "Name=tag-key,Values=kubernetes.io/cluster/${PROJECT}" \
    --query 'Volumes[].VolumeId')"
  if is_empty "$ids"; then
    print_status "[OK]" "Volumes EBS do cluster (available)" "nenhum"
    return
  fi
  for id in $ids; do
    aws ec2 delete-volume --region "$AWS_REGION" --volume-id "$id" >/dev/null 2>&1 || true
    print_status "[INFO]" "EBS delete" "$id"
  done
}

delete_security_groups_by_vpc() {
  local vpc_id="$1" sg_ids sg rules
  sg_ids="$(aws_text ec2 describe-security-groups \
    --filters "Name=vpc-id,Values=${vpc_id}" \
    --query 'SecurityGroups[?GroupName!=`default`].GroupId')"
  is_empty "$sg_ids" && return

  # Regras que referenciam outros SGs criam dependencia circular — revoga antes.
  for sg in $sg_ids; do
    rules="$(aws ec2 describe-security-groups --region "$AWS_REGION" --group-ids "$sg" \
      --query 'SecurityGroups[0].IpPermissions[?UserIdGroupPairs!=`[]`]' --output json 2>/dev/null || true)"
    if [[ -n "$rules" && "$rules" != "null" && "$rules" != "[]" ]]; then
      aws ec2 revoke-security-group-ingress --region "$AWS_REGION" --group-id "$sg" --ip-permissions "$rules" >/dev/null 2>&1 || true
    fi
    rules="$(aws ec2 describe-security-groups --region "$AWS_REGION" --group-ids "$sg" \
      --query 'SecurityGroups[0].IpPermissionsEgress[?UserIdGroupPairs!=`[]`]' --output json 2>/dev/null || true)"
    if [[ -n "$rules" && "$rules" != "null" && "$rules" != "[]" ]]; then
      aws ec2 revoke-security-group-egress --region "$AWS_REGION" --group-id "$sg" --ip-permissions "$rules" >/dev/null 2>&1 || true
    fi
  done
  for sg in $sg_ids; do
    aws ec2 delete-security-group --region "$AWS_REGION" --group-id "$sg" >/dev/null 2>&1 || true
  done
}

delete_vpc_and_deps() {
  local vpc_ids vpc_id
  vpc_ids="$(aws_text ec2 describe-vpcs \
    --filters "Name=tag:Project,Values=${PROJECT}" "Name=is-default,Values=false" \
    --query 'Vpcs[].VpcId')"
  if is_empty "$vpc_ids"; then
    print_status "[OK]" "VPCs do projeto" "nenhuma VPC com tag Project=$PROJECT"
    return
  fi

  for vpc_id in $vpc_ids; do
    print_status "[INFO]" "VPC cleanup" "processando $vpc_id"

    delete_classic_elb_by_vpc "$vpc_id"
    delete_elbv2_by_vpc "$vpc_id"
    terminate_instances_in_vpc "$vpc_id"

    local nat_ids nat_id
    nat_ids="$(aws_text ec2 describe-nat-gateways --filter "Name=vpc-id,Values=${vpc_id}" \
      --query 'NatGateways[?State!=`deleted`].NatGatewayId')"
    for nat_id in $nat_ids; do
      is_empty "$nat_id" && continue
      aws ec2 delete-nat-gateway --region "$AWS_REGION" --nat-gateway-id "$nat_id" >/dev/null 2>&1 || true
      aws ec2 wait nat-gateway-deleted --region "$AWS_REGION" --nat-gateway-ids "$nat_id" >/dev/null 2>&1 || true
    done

    # ENIs do ELB/EKS demoram alguns minutos pra sumir depois do delete.
    local eni_ids eni_id attempt
    for attempt in $(seq 1 12); do
      eni_ids="$(aws_text ec2 describe-network-interfaces --filters "Name=vpc-id,Values=${vpc_id}" \
        --query 'NetworkInterfaces[].NetworkInterfaceId')"
      is_empty "$eni_ids" && break
      for eni_id in $eni_ids; do
        aws ec2 delete-network-interface --region "$AWS_REGION" --network-interface-id "$eni_id" >/dev/null 2>&1 || true
      done
      print_status "[INFO]" "ENIs na VPC" "ainda existem (tentativa $attempt/12), aguardando 15s..."
      sleep 15
    done

    local igw_ids igw_id
    igw_ids="$(aws_text ec2 describe-internet-gateways --filters "Name=attachment.vpc-id,Values=${vpc_id}" \
      --query 'InternetGateways[].InternetGatewayId')"
    for igw_id in $igw_ids; do
      is_empty "$igw_id" && continue
      aws ec2 detach-internet-gateway --region "$AWS_REGION" --internet-gateway-id "$igw_id" --vpc-id "$vpc_id" >/dev/null 2>&1 || true
      aws ec2 delete-internet-gateway --region "$AWS_REGION" --internet-gateway-id "$igw_id" >/dev/null 2>&1 || true
    done

    local assoc_ids assoc_id
    assoc_ids="$(aws_text ec2 describe-route-tables --filters "Name=vpc-id,Values=${vpc_id}" \
      --query 'RouteTables[].Associations[?Main!=`true`].RouteTableAssociationId')"
    for assoc_id in $assoc_ids; do
      is_empty "$assoc_id" && continue
      aws ec2 disassociate-route-table --region "$AWS_REGION" --association-id "$assoc_id" >/dev/null 2>&1 || true
    done

    local rt_ids rt_id
    rt_ids="$(aws_text ec2 describe-route-tables --filters "Name=vpc-id,Values=${vpc_id}" \
      --query 'RouteTables[?Associations[?Main==`true`]|length(@)==`0`].RouteTableId')"
    for rt_id in $rt_ids; do
      is_empty "$rt_id" && continue
      aws ec2 delete-route-table --region "$AWS_REGION" --route-table-id "$rt_id" >/dev/null 2>&1 || true
    done

    local subnet_ids subnet_id
    subnet_ids="$(aws_text ec2 describe-subnets --filters "Name=vpc-id,Values=${vpc_id}" --query 'Subnets[].SubnetId')"
    for subnet_id in $subnet_ids; do
      is_empty "$subnet_id" && continue
      aws ec2 delete-subnet --region "$AWS_REGION" --subnet-id "$subnet_id" >/dev/null 2>&1 || true
    done

    delete_security_groups_by_vpc "$vpc_id"

    local vpc_deleted="false" retry
    for retry in $(seq 1 8); do
      if aws ec2 delete-vpc --region "$AWS_REGION" --vpc-id "$vpc_id" >/dev/null 2>&1; then
        vpc_deleted="true"
        break
      fi
      print_status "[INFO]" "Delete VPC" "$vpc_id ainda tem dependencias (tentativa $retry/8), aguardando 15s..."
      sleep 15
    done
    if [[ "$vpc_deleted" == "true" ]]; then
      print_status "[OK]" "Delete VPC" "$vpc_id removida"
    else
      print_status "[ALERTA]" "Delete VPC" "nao foi possivel remover $vpc_id apos 8 tentativas"
    fi
  done
}

delete_tf_state_bucket() {
  local account_id bucket
  account_id="$(aws_text sts get-caller-identity --query Account)"
  bucket="${TF_STATE_BUCKET:-${PROJECT}-terraform-state-${account_id}}"
  if ! aws s3api head-bucket --bucket "$bucket" --region "$AWS_REGION" >/dev/null 2>&1; then
    print_status "[OK]" "Bucket de state" "$bucket nao existe"
    return
  fi
  # Bucket versionado: precisa apagar todas as versoes antes do delete-bucket.
  local versions
  versions="$(aws s3api list-object-versions --bucket "$bucket" --region "$AWS_REGION" --output json 2>/dev/null \
    | jq '{Objects: (((.Versions // []) + (.DeleteMarkers // [])) | map({Key, VersionId})), Quiet: true}')"
  if [[ "$(echo "$versions" | jq '.Objects | length')" -gt 0 ]]; then
    aws s3api delete-objects --bucket "$bucket" --region "$AWS_REGION" --delete "$versions" >/dev/null 2>&1 || true
  fi
  aws s3api delete-bucket --bucket "$bucket" --region "$AWS_REGION" >/dev/null 2>&1 || true
  print_status "[OK]" "Bucket de state" "$bucket removido"
}

aws_cli_cleanup() {
  require_cmd aws
  if ! aws sts get-caller-identity --region "$AWS_REGION" >/dev/null 2>&1; then
    echo "Erro: nao foi possivel validar credenciais AWS (credenciais invalidas ou expiradas?)." >&2
    exit 2
  fi

  delete_eks_by_name
  delete_ecr_repositories
  delete_available_volumes
  # Gerenciados ficam nas subnets privadas: precisam sumir antes da VPC (ENIs/SGs deles).
  delete_rds
  delete_elasticache
  delete_sqs_queues
  delete_video_buckets
  delete_ssm_parameters
  delete_iam_roles
  delete_vpc_and_deps
  if [[ "$DESTROY_TF_STATE" == "true" ]]; then
    require_cmd jq
    delete_tf_state_bucket
  fi
}

# ── main ──────────────────────────────────────────────────────────────────

AWS_REGION="${AWS_REGION:-us-east-1}"
PROJECT="${PROJECT:-fiapx}"
NAMESPACE="fiapx"
AUTO_APPROVE="false"
SKIP_K8S_CLEANUP="false"
SKIP_TERRAFORM_DESTROY="false"
SKIP_CLI_CLEANUP="false"
SKIP_VALIDATE="false"
DESTROY_TF_STATE="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --region) AWS_REGION="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --auto-approve) AUTO_APPROVE="true"; shift ;;
    --skip-k8s-cleanup) SKIP_K8S_CLEANUP="true"; shift ;;
    --skip-terraform-destroy) SKIP_TERRAFORM_DESTROY="true"; shift ;;
    --skip-cli-cleanup) SKIP_CLI_CLEANUP="true"; shift ;;
    --skip-validate) SKIP_VALIDATE="true"; shift ;;
    --destroy-tf-state) DESTROY_TF_STATE="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TF_DIR="$ROOT_DIR/k8s/terraform/aws"
export AWS_REGION PROJECT

confirm_execution

echo
print_status "[INFO]" "Regiao" "$AWS_REGION"
print_status "[INFO]" "Projeto/prefixo" "$PROJECT"

if [[ "$SKIP_K8S_CLEANUP" == "false" ]]; then
  echo; echo "[1/4] Limpeza Kubernetes (ELB do ingress-nginx + namespaces)..."
  k8s_cleanup
else
  print_status "[INFO]" "Limpeza k8s" "ignorada por flag"
fi

if [[ "$SKIP_TERRAFORM_DESTROY" == "false" ]]; then
  echo; echo "[2/4] terraform destroy (best-effort)..."
  terraform_destroy
else
  print_status "[INFO]" "terraform destroy" "ignorado por flag"
fi

if [[ "$SKIP_CLI_CLEANUP" == "false" ]]; then
  echo; echo "[3/4] Varredura via aws CLI (garantia real, independente do state)..."
  aws_cli_cleanup
else
  print_status "[INFO]" "Varredura aws CLI" "ignorada por flag"
fi

if [[ "$SKIP_VALIDATE" == "false" ]]; then
  echo; echo "[4/4] Validando custo zero..."
  "$SCRIPT_DIR/aws-validate.sh" --region "$AWS_REGION" --project "$PROJECT" \
    --namespace "$NAMESPACE" --strict --skip-k8s
else
  print_status "[INFO]" "Validacao final" "ignorada por flag"
fi

echo
print_status "[OK]" "Rotina aws-destroy" "concluida"
