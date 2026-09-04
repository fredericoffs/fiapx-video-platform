#!/usr/bin/env bash
# Desprovisiona tudo da Oracle (VCN + OKE + node pool + Load Balancer + volumes)
# apos uma sessao de testes. Espelha o padrao das fases anteriores
# (techChallenge-bkp/TechChallenge/scripts/destroy-zero-cost-aws.sh, AWS):
#   [1] limpeza Kubernetes (LB do ingress-nginx + namespaces, best-effort)
#   [2] terraform destroy (best-effort — so funciona se houver state local valido)
#   [3] varredura via oci CLI por nome/VCN, independente do state do Terraform —
#       e a garantia real, ja que k8s/terraform/oracle/main.tf nao tem backend remoto
#   [4] scripts/oracle-validate.sh --strict, pra confirmar que nao sobrou nada
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/oracle-destroy.sh --compartment-id <ocid> [opcoes]

Opcoes:
  --compartment-id <ocid>   OCID do compartment (obrigatorio; ou defina COMPARTMENT_OCID).
  --cluster-name <nome>     Nome do cluster/prefixo dos recursos (padrao: fiapx).
  --auto-approve            Executa sem confirmacao interativa.
  --skip-k8s-cleanup        Nao executa limpeza previa via kubectl/helm.
  --skip-terraform-destroy  Nao tenta terraform destroy.
  --skip-cli-cleanup        Nao executa a varredura via oci CLI (fase [3]).
  --skip-validate           Nao executa scripts/oracle-validate.sh ao final.
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
  echo "Isso vai tentar remover TODOS os recursos Oracle do projeto '$CLUSTER_NAME' no compartment $COMPARTMENT_OCID."
  read -r -p "Continuar? (yes/no): " answer
  [[ "$answer" == "yes" ]] || { echo "Abortado."; exit 0; }
}

run_best_effort() {
  local label="$1"; shift
  if "$@" >/tmp/oracle_destroy_step.log 2>&1; then
    print_status "[OK]" "$label" "executado"
    return 0
  fi
  print_status "[ALERTA]" "$label" "falhou (continuando)"
  tail -n 5 /tmp/oracle_destroy_step.log | sed 's/^/  > /' >&2
  return 1
}

# ── Fase 1: limpeza Kubernetes ──────────────────────────────────────────────

k8s_cleanup() {
  if ! command -v kubectl >/dev/null 2>&1 || ! kubectl cluster-info >/dev/null 2>&1; then
    print_status "[OK]" "kubectl" "cluster inalcancavel, pulando limpeza k8s"
    return
  fi

  if command -v helm >/dev/null 2>&1 && helm status ingress-nginx -n ingress-nginx >/dev/null 2>&1; then
    run_best_effort "helm uninstall ingress-nginx (derruba o LB da OCI)" \
      helm uninstall ingress-nginx -n ingress-nginx || true
  else
    print_status "[OK]" "ingress-nginx" "nao instalado"
  fi

  run_best_effort "kubectl delete namespace $NAMESPACE" \
    kubectl delete namespace "$NAMESPACE" --ignore-not-found --wait=true --timeout=120s || true
  run_best_effort "kubectl delete namespace monitoring/keda" \
    kubectl delete namespace monitoring keda --ignore-not-found --wait=true --timeout=120s || true
}

# ── Fase 2: terraform destroy (best-effort) ─────────────────────────────────

terraform_destroy() {
  if ! command -v terraform >/dev/null 2>&1; then
    print_status "[OK]" "terraform" "nao instalado, pulando (varredura oci CLI cobre isso)"
    return
  fi

  if [[ ! -f "$TF_DIR/terraform.tfvars" ]] && [[ -z "${TF_VAR_tenancy_ocid:-}" ]]; then
    print_status "[OK]" "terraform destroy" "sem tfvars/TF_VAR_*, pulando (varredura oci CLI cobre isso)"
    return
  fi

  if [[ -z "${OCI_OS_NAMESPACE:-}" || -z "${OCI_S3_ACCESS_KEY_ID:-}" || -z "${OCI_S3_SECRET_ACCESS_KEY:-}" ]]; then
    print_status "[OK]" "terraform destroy" "sem OCI_OS_NAMESPACE/OCI_S3_*, pulando (varredura oci CLI cobre isso)"
    return
  fi

  run_best_effort "terraform init" "$SCRIPT_DIR/oracle-tf-init.sh" "$TF_DIR" || return
  run_best_effort "terraform destroy" terraform -chdir="$TF_DIR" destroy -input=false -auto-approve || true
}

# ── Fase 3: varredura via oci CLI, independente do state do Terraform ──────

wait_until_absent() {
  local label="$1" retries="$2" sleep_s="$3"; shift 3
  local i
  for ((i = 1; i <= retries; i++)); do
    local count
    count="$("$@" 2>/dev/null | jq 'length' 2>/dev/null || echo 0)"
    [[ "$count" == "0" || -z "$count" ]] && { print_status "[OK]" "$label" "confirmado ausente"; return 0; }
    print_status "[INFO]" "$label" "ainda existe (tentativa $i/$retries), aguardando ${sleep_s}s..."
    sleep "$sleep_s"
  done
  print_status "[ALERTA]" "$label" "ainda presente apos $retries tentativas"
  return 1
}

# --lifecycle-state do oci CLI nao aceita lista separada por virgula de forma confiavel
# (diferente do AWS CLI) — filtra estados terminais via JMESPath no --query em vez disso.
NOT_TERMINAL_JMESPATH='"lifecycle-state"!='"'"'DELETED'"'"' && "lifecycle-state"!='"'"'FAILED'"'"''

delete_oke_by_name() {
  local cluster_id
  cluster_id="$(oci ce cluster list --compartment-id "$COMPARTMENT_OCID" --all --name "$CLUSTER_NAME" \
    --query "data[?$NOT_TERMINAL_JMESPATH] | [0].id" --raw-output 2>/dev/null || true)"

  if [[ -z "$cluster_id" || "$cluster_id" == "null" ]]; then
    print_status "[OK]" "Cluster OKE ($CLUSTER_NAME)" "nao encontrado"
    return
  fi

  local node_pool_ids
  node_pool_ids="$(oci ce node-pool list --compartment-id "$COMPARTMENT_OCID" --cluster-id "$cluster_id" --all \
    --query 'data[].id' --output json 2>/dev/null | jq -r '.[]' || true)"

  local np
  for np in $node_pool_ids; do
    oci ce node-pool delete --node-pool-id "$np" --force >/dev/null 2>&1 || true
  done
  if [[ -n "$node_pool_ids" ]]; then
    wait_until_absent "Node pools" 20 15 \
      oci ce node-pool list --compartment-id "$COMPARTMENT_OCID" --cluster-id "$cluster_id" --all \
        --query "data[?$NOT_TERMINAL_JMESPATH]" --output json || true
  fi

  oci ce cluster delete --cluster-id "$cluster_id" --force >/dev/null 2>&1 || true
  wait_until_absent "Cluster OKE ($CLUSTER_NAME)" 20 15 \
    oci ce cluster list --compartment-id "$COMPARTMENT_OCID" --all --name "$CLUSTER_NAME" \
      --query "data[?$NOT_TERMINAL_JMESPATH]" --output json || true
}

delete_lbs_in_project_vcn() {
  local vcn_id="$1"
  local subnet_ids_json
  subnet_ids_json="$(oci network subnet list --compartment-id "$COMPARTMENT_OCID" --vcn-id "$vcn_id" --all \
    --query 'data[].id' --output json 2>/dev/null || echo '[]')"

  local lb_ids
  lb_ids="$(oci lb load-balancer list --compartment-id "$COMPARTMENT_OCID" --all \
    --query 'data[].{id:id,"subnet-ids":"subnet-ids"}' --output json 2>/dev/null | \
    jq -r --argjson subnets "$subnet_ids_json" \
      '.[] | select((."subnet-ids" // []) | any(. as $s | $subnets | index($s) != null)) | .id' || true)"

  if [[ -z "$lb_ids" ]]; then
    print_status "[OK]" "Load Balancers (VCN do projeto)" "nenhum"
    return
  fi

  local lb
  for lb in $lb_ids; do
    oci lb load-balancer delete --load-balancer-id "$lb" --force >/dev/null 2>&1 || true
    print_status "[INFO]" "Load Balancer delete" "$lb (solicitado)"
  done
  wait_until_absent "Load Balancers (VCN do projeto)" 20 15 \
    oci lb load-balancer list --compartment-id "$COMPARTMENT_OCID" --all \
      --query "data[?\"lifecycle-state\" != 'DELETED']" --output json || true
}

delete_available_volumes() {
  local vol_ids
  vol_ids="$(oci bv volume list --compartment-id "$COMPARTMENT_OCID" --all \
    --query "data[?\"lifecycle-state\"=='AVAILABLE'].id" --output json 2>/dev/null | jq -r '.[]' || true)"
  local v
  for v in $vol_ids; do
    oci bv volume delete --volume-id "$v" --force >/dev/null 2>&1 || true
    print_status "[INFO]" "Block Volume delete" "$v (solicitado)"
  done
  [[ -z "$vol_ids" ]] && print_status "[OK]" "Block Volumes disponiveis" "nenhum"

  local bv_ids
  bv_ids="$(oci bv boot-volume list --compartment-id "$COMPARTMENT_OCID" --all \
    --query "data[?\"lifecycle-state\"=='AVAILABLE'].id" --output json 2>/dev/null | jq -r '.[]' || true)"
  local b
  for b in $bv_ids; do
    oci bv boot-volume delete --boot-volume-id "$b" --force >/dev/null 2>&1 || true
    print_status "[INFO]" "Boot Volume delete" "$b (solicitado)"
  done
  [[ -z "$bv_ids" ]] && print_status "[OK]" "Boot Volumes disponiveis" "nenhum"
}

delete_vcn_and_deps() {
  local vcn_id
  vcn_id="$(oci network vcn list --compartment-id "$COMPARTMENT_OCID" --all \
    --query "data[?\"display-name\"=='${CLUSTER_NAME}-vcn'].id | [0]" --raw-output 2>/dev/null || true)"

  if [[ -z "$vcn_id" || "$vcn_id" == "null" ]]; then
    print_status "[OK]" "VCN ($CLUSTER_NAME-vcn)" "nao encontrada"
    return
  fi

  print_status "[INFO]" "VCN cleanup" "processando $vcn_id"

  delete_lbs_in_project_vcn "$vcn_id"

  local subnet_ids
  subnet_ids="$(oci network subnet list --compartment-id "$COMPARTMENT_OCID" --vcn-id "$vcn_id" --all \
    --query 'data[].id' --output json 2>/dev/null | jq -r '.[]' || true)"
  local sid
  for sid in $subnet_ids; do
    oci network subnet delete --subnet-id "$sid" --force >/dev/null 2>&1 || true
  done

  local igw_id
  igw_id="$(oci network internet-gateway list --compartment-id "$COMPARTMENT_OCID" --vcn-id "$vcn_id" --all \
    --query 'data[0].id' --raw-output 2>/dev/null || true)"
  [[ -n "$igw_id" && "$igw_id" != "null" ]] && oci network internet-gateway delete --ig-id "$igw_id" --force >/dev/null 2>&1 || true

  local rt_ids
  rt_ids="$(oci network route-table list --compartment-id "$COMPARTMENT_OCID" --vcn-id "$vcn_id" --all \
    --query "data[?\"display-name\"!='Default Route Table for ${CLUSTER_NAME}-vcn'].id" --output json 2>/dev/null | jq -r '.[]' || true)"
  local rt
  for rt in $rt_ids; do
    oci network route-table delete --rt-id "$rt" --force >/dev/null 2>&1 || true
  done

  local sl_ids
  sl_ids="$(oci network security-list list --compartment-id "$COMPARTMENT_OCID" --vcn-id "$vcn_id" --all \
    --query "data[?\"display-name\"!='Default Security List for ${CLUSTER_NAME}-vcn'].id" --output json 2>/dev/null | jq -r '.[]' || true)"
  local sl
  for sl in $sl_ids; do
    oci network security-list delete --security-list-id "$sl" --force >/dev/null 2>&1 || true
  done

  local vcn_deleted=false
  local retry
  for ((retry = 1; retry <= 10; retry++)); do
    if oci network vcn delete --vcn-id "$vcn_id" --force >/dev/null 2>&1; then
      vcn_deleted=true
      break
    fi
    print_status "[INFO]" "VCN delete" "$vcn_id ainda tem dependencias (tentativa $retry/10), aguardando..."
    sleep 15
  done
  if [[ "$vcn_deleted" == "true" ]]; then
    print_status "[OK]" "VCN delete" "$vcn_id removida"
  else
    print_status "[ALERTA]" "VCN delete" "nao foi possivel remover $vcn_id apos 10 tentativas — verifique dependencias manualmente"
  fi
}

oci_cli_cleanup() {
  require_cmd oci
  require_cmd jq

  if ! oci iam compartment get --compartment-id "$COMPARTMENT_OCID" >/dev/null 2>&1; then
    echo "Erro: nao foi possivel validar credenciais/compartment OCI." >&2
    exit 2
  fi

  delete_oke_by_name
  delete_available_volumes
  delete_vcn_and_deps
}

# ── main ──────────────────────────────────────────────────────────────────

COMPARTMENT_OCID="${COMPARTMENT_OCID:-}"
CLUSTER_NAME="${CLUSTER_NAME:-fiapx}"
NAMESPACE="fiapx"
AUTO_APPROVE="false"
SKIP_K8S_CLEANUP="false"
SKIP_TERRAFORM_DESTROY="false"
SKIP_CLI_CLEANUP="false"
SKIP_VALIDATE="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compartment-id) COMPARTMENT_OCID="${2:-}"; shift 2 ;;
    --cluster-name) CLUSTER_NAME="${2:-}"; shift 2 ;;
    --auto-approve) AUTO_APPROVE="true"; shift ;;
    --skip-k8s-cleanup) SKIP_K8S_CLEANUP="true"; shift ;;
    --skip-terraform-destroy) SKIP_TERRAFORM_DESTROY="true"; shift ;;
    --skip-cli-cleanup) SKIP_CLI_CLEANUP="true"; shift ;;
    --skip-validate) SKIP_VALIDATE="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$COMPARTMENT_OCID" ]]; then
  echo "Erro: --compartment-id e obrigatorio (ou defina COMPARTMENT_OCID)." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TF_DIR="$ROOT_DIR/k8s/terraform/oracle"

confirm_execution

echo
print_status "[INFO]" "Compartment" "$COMPARTMENT_OCID"
print_status "[INFO]" "Cluster/prefixo" "$CLUSTER_NAME"

if [[ "$SKIP_K8S_CLEANUP" == "false" ]]; then
  echo; echo "[1/4] Limpeza Kubernetes (LB do ingress-nginx + namespaces)..."
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
  echo; echo "[3/4] Varredura via oci CLI (garantia real, independente do state)..."
  oci_cli_cleanup
else
  print_status "[INFO]" "Varredura oci CLI" "ignorada por flag"
fi

if [[ "$SKIP_VALIDATE" == "false" ]]; then
  echo; echo "[4/4] Validando custo zero..."
  "$SCRIPT_DIR/oracle-validate.sh" --compartment-id "$COMPARTMENT_OCID" --cluster-name "$CLUSTER_NAME" \
    --namespace "$NAMESPACE" --strict
else
  print_status "[INFO]" "Validacao final" "ignorada por flag"
fi

echo
print_status "[OK]" "Rotina oracle-destroy" "concluida"
