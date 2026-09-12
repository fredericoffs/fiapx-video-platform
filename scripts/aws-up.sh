#!/usr/bin/env bash
# Provisiona VPC + EKS + ECR + RDS + ElastiCache + SQS + S3 via Terraform (k8s/terraform/aws).
# Mesmo padrao de scripts/aws-up.sh das fases anteriores (mechanicshop-infra-k8s).
# So cuida da infra (Terraform) — deploy da app e scripts/k8s-deploy-aws.sh.
# --apply-if-changed e o modo usado pelo cd-aws.yml: plan com -detailed-exitcode e
# apply so quando ha diferenca entre o state e o codigo (infra no ar = no-op rapido).
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/aws-up.sh [opcoes]

Opcoes:
  --region <regiao>   Regiao AWS (padrao: us-east-1).
  --plan-only         Para depois do terraform plan (nao aplica).
  --auto-approve      Executa terraform apply sem confirmacao interativa.
  --apply-if-changed  Plan com -detailed-exitcode; aplica (sem confirmacao) apenas se
                      houver mudancas. Sem mudancas, termina com sucesso sem aplicar.
  -h, --help          Exibe esta ajuda.

Credenciais: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY e AWS_SESSION_TOKEN no ambiente
(Learner Lab > AWS Details). Variaveis do Terraform via TF_VAR_* (opcional — os
defaults de k8s/terraform/aws/variables.tf ja servem pro Learner Lab).
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 1; }
}

AWS_REGION="${AWS_REGION:-us-east-1}"
AUTO_APPROVE="false"
PLAN_ONLY="false"
APPLY_IF_CHANGED="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --region) AWS_REGION="${2:-}"; shift 2 ;;
    --plan-only) PLAN_ONLY="true"; shift ;;
    --auto-approve) AUTO_APPROVE="true"; shift ;;
    --apply-if-changed) APPLY_IF_CHANGED="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 1 ;;
  esac
done

require_cmd terraform
require_cmd aws

for var in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
  if [[ -z "${!var:-}" ]]; then
    echo "Erro: $var nao definida (copie de Learner Lab > AWS Details)." >&2
    exit 1
  fi
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="$ROOT_DIR/k8s/terraform/aws"
export AWS_REGION
export TF_VAR_aws_region="$AWS_REGION"

terraform -chdir="$TF_DIR" fmt -check
"$ROOT_DIR/scripts/aws-tf-init.sh" "$TF_DIR"
terraform -chdir="$TF_DIR" validate

if [[ "$APPLY_IF_CHANGED" == "true" ]]; then
  # exit 0 = sem mudancas, 2 = ha mudancas, qualquer outro = erro
  set +e
  terraform -chdir="$TF_DIR" plan -input=false -detailed-exitcode -out=tfplan
  PLAN_EXIT=$?
  set -e
  case "$PLAN_EXIT" in
    0)
      echo "infra ja provisionada e igual ao codigo — nada a aplicar."
      [[ -n "${GITHUB_STEP_SUMMARY:-}" ]] && echo "- Terraform: infra já no ar, sem mudanças (apply pulado)" >> "$GITHUB_STEP_SUMMARY"
      exit 0
      ;;
    2)
      echo "plan com mudancas — aplicando."
      terraform -chdir="$TF_DIR" apply -input=false -auto-approve tfplan
      [[ -n "${GITHUB_STEP_SUMMARY:-}" ]] && echo "- Terraform: mudanças aplicadas (ver log do job \`provision\`)" >> "$GITHUB_STEP_SUMMARY"
      ;;
    *)
      echo "terraform plan falhou (exit ${PLAN_EXIT})." >&2
      exit "$PLAN_EXIT"
      ;;
  esac
else
  terraform -chdir="$TF_DIR" plan -input=false -out=tfplan
fi

if [[ "$PLAN_ONLY" == "true" ]]; then
  echo "plan concluido (--plan-only), nada aplicado."
  exit 0
fi

if [[ "$APPLY_IF_CHANGED" == "true" ]]; then
  : # ja aplicado acima
elif [[ "$AUTO_APPROVE" == "true" ]]; then
  terraform -chdir="$TF_DIR" apply -input=false -auto-approve tfplan
else
  terraform -chdir="$TF_DIR" apply -input=false tfplan
fi

echo
echo "Infra AWS provisionada. State no bucket S3 remoto — nao depende deste disco/runner."
echo
echo "Kubeconfig:"
terraform -chdir="$TF_DIR" output -raw kubeconfig_command
echo
echo
echo "Proximo passo (deploy da app): .github/workflows/cd-aws.yml ou scripts/k8s-deploy-aws.sh"
