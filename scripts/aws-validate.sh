#!/usr/bin/env bash
# shellcheck disable=SC2016  # crases sao sintaxe JMESPath do --query, nao expansao de shell
# Valida custo zero na AWS apos um destroy — mesmo padrao de
# validate-zero-cost-aws.sh das fases anteriores. Conta residuos por recurso; nao
# apaga nada, so relata. Pode rodar isolado a qualquer momento (so leitura).
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/aws-validate.sh [opcoes]

Opcoes:
  --region <regiao>      Regiao AWS (padrao: us-east-1).
  --project <prefixo>    Prefixo/tag Project dos recursos (padrao: fiapx).
  --namespace <ns>       Namespace Kubernetes para checagem opcional (padrao: fiapx).
  --strict               Falha (exit 1) se existir qualquer residuo, mesmo nao-critico.
  --skip-k8s             Nao executa verificacoes via kubectl.
  -h, --help             Exibe esta ajuda.

Codigos de saida:
  0  Sem residuos criticos (ou sem residuos nenhum no modo --strict).
  1  Residuos encontrados conforme a politica do modo atual.
  2  Erro operacional (ex.: aws CLI indisponivel ou sem credenciais).
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 2; }
}

print_status() {
  printf "%-9s %-42s %s\n" "$1" "$2" "$3"
}

to_int() {
  local raw="${1:-0}"
  [[ "$raw" =~ ^[0-9]+$ ]] && echo "$raw" || echo "0"
}

# Executa uma consulta aws CLI que retorna um numero (length(...)) e classifica.
run_count_check() {
  local label="$1" critical="$2"
  shift 2
  local output
  if ! output="$("$@" 2>/dev/null)"; then
    print_status "[ERRO]" "$label" "falha na consulta"
    errors_total=$((errors_total + 1))
    return
  fi
  local count
  count="$(to_int "$output")"
  if (( count > 0 )); then
    print_status "[RESIDUO]" "$label" "$count"
    residues_total=$((residues_total + count))
    [[ "$critical" == "true" ]] && critical_residues=$((critical_residues + count))
  else
    print_status "[OK]" "$label" "0"
  fi
}

AWS_REGION="${AWS_REGION:-us-east-1}"
PROJECT="${PROJECT:-fiapx}"
NAMESPACE="fiapx"
STRICT_MODE="false"
SKIP_K8S="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --region) AWS_REGION="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --namespace) NAMESPACE="${2:-}"; shift 2 ;;
    --strict) STRICT_MODE="true"; shift ;;
    --skip-k8s) SKIP_K8S="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 2 ;;
  esac
done

require_cmd aws

residues_total=0
critical_residues=0
errors_total=0

echo "Validando custo zero na AWS (regiao $AWS_REGION, projeto $PROJECT)..."
if ! aws sts get-caller-identity --region "$AWS_REGION" >/dev/null 2>&1; then
  echo "Erro: nao foi possivel validar credenciais AWS (sessao do Learner Lab expirou?)." >&2
  exit 2
fi

echo
echo "AWS"

run_count_check "Clusters EKS ($PROJECT)" "true" \
  aws eks list-clusters --region "$AWS_REGION" \
    --query "length(clusters[?contains(@, '${PROJECT}')])" --output text

run_count_check "Instancias EC2 (tag Project=$PROJECT, nao terminadas)" "true" \
  aws ec2 describe-instances --region "$AWS_REGION" \
    --filters "Name=tag:Project,Values=${PROJECT}" \
              "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'length(Reservations[].Instances[])' --output text

run_count_check "Instancias EC2 (cluster eks:$PROJECT, nao terminadas)" "true" \
  aws ec2 describe-instances --region "$AWS_REGION" \
    --filters "Name=tag:eks:cluster-name,Values=${PROJECT}" \
              "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'length(Reservations[].Instances[])' --output text

run_count_check "Classic ELBs (todos na regiao)" "true" \
  aws elb describe-load-balancers --region "$AWS_REGION" \
    --query 'length(LoadBalancerDescriptions)' --output text

run_count_check "ELBv2 (ALB/NLB, todos na regiao)" "true" \
  aws elbv2 describe-load-balancers --region "$AWS_REGION" \
    --query 'length(LoadBalancers)' --output text

run_count_check "NAT Gateways (nao deletados)" "true" \
  aws ec2 describe-nat-gateways --region "$AWS_REGION" \
    --query 'length(NatGateways[?State!=`deleted`])' --output text

run_count_check "Elastic IPs alocados" "true" \
  aws ec2 describe-addresses --region "$AWS_REGION" \
    --query 'length(Addresses)' --output text

run_count_check "VPCs do projeto ($PROJECT)" "true" \
  aws ec2 describe-vpcs --region "$AWS_REGION" \
    --filters "Name=tag:Project,Values=${PROJECT}" \
    --query 'length(Vpcs)' --output text

run_count_check "Volumes EBS disponiveis (nao anexados)" "false" \
  aws ec2 describe-volumes --region "$AWS_REGION" \
    --filters "Name=status,Values=available" \
    --query 'length(Volumes)' --output text

run_count_check "Repositorios ECR ($PROJECT/*)" "false" \
  aws ecr describe-repositories --region "$AWS_REGION" \
    --query "length(repositories[?starts_with(repositoryName, '${PROJECT}/')])" --output text

if [[ "$SKIP_K8S" == "false" ]] && command -v kubectl >/dev/null 2>&1; then
  echo
  echo "Kubernetes"
  if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
    svc_total="$(to_int "$(kubectl get svc -n "$NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')")"
    deploy_total="$(to_int "$(kubectl get deploy -n "$NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')")"
    lb_svc_total="$(to_int "$(kubectl get svc -A -o custom-columns=TYPE:.spec.type --no-headers 2>/dev/null | grep -c '^LoadBalancer$' || true)")"

    if (( svc_total > 0 )); then
      print_status "[RESIDUO]" "K8s services ($NAMESPACE)" "$svc_total"
      residues_total=$((residues_total + svc_total))
    else
      print_status "[OK]" "K8s services ($NAMESPACE)" "0"
    fi

    if (( deploy_total > 0 )); then
      print_status "[RESIDUO]" "K8s deployments ($NAMESPACE)" "$deploy_total"
      residues_total=$((residues_total + deploy_total))
    else
      print_status "[OK]" "K8s deployments ($NAMESPACE)" "0"
    fi

    if (( lb_svc_total > 0 )); then
      print_status "[RESIDUO]" "K8s services LoadBalancer (todos os ns)" "$lb_svc_total"
      residues_total=$((residues_total + lb_svc_total))
      critical_residues=$((critical_residues + lb_svc_total))
    else
      print_status "[OK]" "K8s services LoadBalancer (todos os ns)" "0"
    fi
  else
    print_status "[OK]" "Namespace $NAMESPACE" "nao encontrado (cluster provavelmente ja destruido)"
  fi
elif [[ "$SKIP_K8S" == "false" ]]; then
  print_status "[INFO]" "Kubernetes" "kubectl indisponivel ou cluster inalcancavel, checagem ignorada"
fi

echo
echo "Resumo"
echo "- residuos totais: $residues_total"
echo "- residuos criticos: $critical_residues"
echo "- erros de consulta: $errors_total"

if (( errors_total > 0 )); then
  echo "Resultado: FALHA (erros operacionais durante a validacao)."
  exit 2
fi

if [[ "$STRICT_MODE" == "true" ]]; then
  if (( residues_total > 0 )); then
    echo "Resultado: FALHA (modo --strict exige zero residuos absolutos)."
    exit 1
  fi
  echo "Resultado: OK (zero residuos absolutos)."
  exit 0
fi

if (( critical_residues > 0 )); then
  echo "Resultado: FALHA (residuos criticos encontrados — provavelmente ainda cobrando)."
  exit 1
fi

if (( residues_total > 0 )); then
  echo "Resultado: ALERTA (sem residuos criticos, mas ainda ha itens residuais)."
  exit 0
fi

echo "Resultado: OK (sem residuos relevantes)."
