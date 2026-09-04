#!/usr/bin/env bash
# Valida custo zero na Oracle apos um destroy — espelha
# techChallenge-bkp/TechChallenge/scripts/validate-zero-cost-aws.sh (fases anteriores,
# AWS). Conta residuos por recurso; nao apaga nada, so relata.
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/oracle-validate.sh --compartment-id <ocid> [opcoes]

Opcoes:
  --compartment-id <ocid>   OCID do compartment a validar (obrigatorio).
  --cluster-name <nome>     Nome do cluster/prefixo dos recursos (padrao: fiapx).
  --namespace <ns>          Namespace Kubernetes para checagem opcional (padrao: fiapx).
  --strict                  Falha (exit 1) se existir qualquer residuo, mesmo nao-critico.
  --skip-k8s                Nao executa verificacoes via kubectl.
  -h, --help                Exibe esta ajuda.

Codigos de saida:
  0  Sem residuos criticos (ou sem residuos nenhum no modo --strict).
  1  Residuos encontrados conforme a politica do modo atual.
  2  Erro operacional (ex.: oci CLI indisponivel ou sem credenciais).
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 2; }
}

print_status() {
  printf "%-9s %-38s %s\n" "$1" "$2" "$3"
}

to_int() {
  local raw="${1:-0}"
  [[ "$raw" =~ ^[0-9]+$ ]] && echo "$raw" || echo "0"
}

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
  count="$(to_int "$(echo "$output" | jq 'length' 2>/dev/null || echo 0)")"
  if (( count > 0 )); then
    print_status "[RESIDUO]" "$label" "$count"
    residues_total=$((residues_total + count))
    [[ "$critical" == "true" ]] && critical_residues=$((critical_residues + count))
  else
    print_status "[OK]" "$label" "0"
  fi
}

COMPARTMENT_OCID="${COMPARTMENT_OCID:-}"
CLUSTER_NAME="${CLUSTER_NAME:-fiapx}"
NAMESPACE="fiapx"
STRICT_MODE="false"
SKIP_K8S="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compartment-id) COMPARTMENT_OCID="${2:-}"; shift 2 ;;
    --cluster-name) CLUSTER_NAME="${2:-}"; shift 2 ;;
    --namespace) NAMESPACE="${2:-}"; shift 2 ;;
    --strict) STRICT_MODE="true"; shift ;;
    --skip-k8s) SKIP_K8S="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 2 ;;
  esac
done

require_cmd oci
require_cmd jq

if [[ -z "$COMPARTMENT_OCID" ]]; then
  echo "Erro: --compartment-id e obrigatorio (ou defina COMPARTMENT_OCID)." >&2
  exit 2
fi

residues_total=0
critical_residues=0
errors_total=0

echo "Validando custo zero na Oracle (compartment $COMPARTMENT_OCID)..."
if ! oci iam compartment get --compartment-id "$COMPARTMENT_OCID" >/dev/null 2>&1; then
  echo "Erro: nao foi possivel validar credenciais/compartment OCI." >&2
  exit 2
fi

# VCN do projeto (usada para escopar LB/subnet abaixo) — pode nao existir mais, tudo bem.
VCN_ID="$(oci network vcn list --compartment-id "$COMPARTMENT_OCID" --all \
  --query "data[?\"display-name\"=='${CLUSTER_NAME}-vcn'].id | [0]" --raw-output 2>/dev/null || true)"

echo
echo "Oracle Cloud Infrastructure"

# --lifecycle-state do oci CLI nao aceita lista separada por virgula de forma confiavel
# (diferente do AWS CLI) — filtra estados terminais via JMESPath no --query em vez disso.
run_count_check "Clusters OKE ($CLUSTER_NAME)" "true" \
  oci ce cluster list --compartment-id "$COMPARTMENT_OCID" --all --name "$CLUSTER_NAME" \
    --query "data[?\"lifecycle-state\"!='DELETED' && \"lifecycle-state\"!='FAILED']" --output json

run_count_check "VCNs do projeto ($CLUSTER_NAME)" "true" \
  oci network vcn list --compartment-id "$COMPARTMENT_OCID" --all \
    --query "data[?\"display-name\"=='${CLUSTER_NAME}-vcn']" --output json

if [[ -n "$VCN_ID" && "$VCN_ID" != "null" ]]; then
  SUBNET_IDS_JSON="$(oci network subnet list --compartment-id "$COMPARTMENT_OCID" --vcn-id "$VCN_ID" --all \
    --query 'data[].id' --output json 2>/dev/null || echo '[]')"

  lb_json="$(oci lb load-balancer list --compartment-id "$COMPARTMENT_OCID" --all \
    --query 'data[].{id:id,"subnet-ids":"subnet-ids"}' --output json 2>/dev/null || echo '[]')"
  lb_count="$(echo "$lb_json" | jq --argjson subnets "$SUBNET_IDS_JSON" \
    '[.[] | select((."subnet-ids" // []) | any(. as $s | $subnets | index($s) != null))] | length' 2>/dev/null || echo 0)"
  lb_count="$(to_int "$lb_count")"
  if (( lb_count > 0 )); then
    print_status "[RESIDUO]" "Load Balancers (na VCN do projeto)" "$lb_count"
    residues_total=$((residues_total + lb_count))
    critical_residues=$((critical_residues + lb_count))
  else
    print_status "[OK]" "Load Balancers (na VCN do projeto)" "0"
  fi
else
  print_status "[OK]" "Load Balancers (na VCN do projeto)" "0 (VCN nao existe)"
fi

run_count_check "Block Volumes disponiveis (nao anexados)" "false" \
  oci bv volume list --compartment-id "$COMPARTMENT_OCID" --all \
    --query "data[?\"lifecycle-state\"=='AVAILABLE']" --output json

run_count_check "Boot Volumes disponiveis (nao anexados)" "false" \
  oci bv boot-volume list --compartment-id "$COMPARTMENT_OCID" --all \
    --query "data[?\"lifecycle-state\"=='AVAILABLE']" --output json

run_count_check "Compute instances (nao terminadas)" "true" \
  oci compute instance list --compartment-id "$COMPARTMENT_OCID" --all \
    --query "data[?\"lifecycle-state\" != 'TERMINATED' && \"lifecycle-state\" != 'TERMINATING']" --output json

if [[ "$SKIP_K8S" == "false" ]] && command -v kubectl >/dev/null 2>&1; then
  echo
  echo "Kubernetes"
  if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
    svc_total="$(to_int "$(kubectl get svc -n "$NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')")"
    deploy_total="$(to_int "$(kubectl get deploy -n "$NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')")"
    lb_svc_total="$(to_int "$(kubectl get svc -n "$NAMESPACE" -o custom-columns=TYPE:.spec.type --no-headers 2>/dev/null | grep -c '^LoadBalancer$' || true)")"

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
      print_status "[RESIDUO]" "K8s services LoadBalancer" "$lb_svc_total"
      residues_total=$((residues_total + lb_svc_total))
      critical_residues=$((critical_residues + lb_svc_total))
    else
      print_status "[OK]" "K8s services LoadBalancer" "0"
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
