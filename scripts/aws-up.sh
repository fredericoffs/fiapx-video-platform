#!/usr/bin/env bash
# Provisiona VPC + cluster EKS + node group + ECR via Terraform (k8s/terraform/aws).
# Mesmo padrao de scripts/aws-up.sh das fases anteriores (mechanicshop-infra-k8s).
# So cuida da infra (Terraform) — deploy da app e scripts/k8s-deploy-aws.sh.
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/aws-up.sh [opcoes]

Opcoes:
  --region <regiao>   Regiao AWS (padrao: us-east-1).
  --plan-only         Para depois do terraform plan (nao aplica).
  --auto-approve      Executa terraform apply sem confirmacao interativa.
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

while [[ $# -gt 0 ]]; do
  case "$1" in
    --region) AWS_REGION="${2:-}"; shift 2 ;;
    --plan-only) PLAN_ONLY="true"; shift ;;
    --auto-approve) AUTO_APPROVE="true"; shift ;;
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
terraform -chdir="$TF_DIR" plan -input=false -out=tfplan

if [[ "$PLAN_ONLY" == "true" ]]; then
  echo "plan concluido (--plan-only), nada aplicado."
  exit 0
fi

if [[ "$AUTO_APPROVE" == "true" ]]; then
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
