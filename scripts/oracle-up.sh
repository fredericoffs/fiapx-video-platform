#!/usr/bin/env bash
# Provisiona VCN + cluster OKE + node pool via Terraform (k8s/terraform/oracle).
# Espelha scripts/aws-up.sh do padrão usado nas fases anteriores (mechanicshop-infra-k8s).
# Só cuida da infra (Terraform) — deploy da app é scripts/k8s-deploy-oracle.sh.
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/oracle-up.sh [opcoes]

Opcoes:
  --auto-approve   Executa terraform apply sem confirmacao interativa.
  -h, --help       Exibe esta ajuda.

Variaveis (terraform.tfvars em k8s/terraform/oracle/ tem prioridade se existir;
senao usa as TF_VAR_* abaixo, mesmo nome usado por .github/workflows/terraform-oracle.yml):
  TF_VAR_tenancy_ocid, TF_VAR_user_ocid, TF_VAR_fingerprint, TF_VAR_private_key_pem,
  TF_VAR_region, TF_VAR_compartment_ocid, TF_VAR_ssh_public_key (opcional)

Backend remoto (obrigatorio — saida de bootstrap-oracle-backend.yml, rodado uma unica vez):
  OCI_REGION, OCI_OS_NAMESPACE, OCI_S3_ACCESS_KEY_ID, OCI_S3_SECRET_ACCESS_KEY
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 1; }
}

AUTO_APPROVE="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --auto-approve) AUTO_APPROVE="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 1 ;;
  esac
done

require_cmd terraform

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="$ROOT_DIR/k8s/terraform/oracle"

if [[ ! -f "$TF_DIR/terraform.tfvars" ]]; then
  for var in TF_VAR_tenancy_ocid TF_VAR_user_ocid TF_VAR_fingerprint TF_VAR_private_key_pem TF_VAR_region TF_VAR_compartment_ocid; do
    if [[ -z "${!var:-}" ]]; then
      echo "Erro: nem k8s/terraform/oracle/terraform.tfvars existe nem $var esta definida." >&2
      echo "Copie terraform.tfvars.example para terraform.tfvars e preencha, ou exporte as TF_VAR_*." >&2
      exit 1
    fi
  done
fi

terraform -chdir="$TF_DIR" fmt -check
"$ROOT_DIR/scripts/oracle-tf-init.sh" "$TF_DIR"
terraform -chdir="$TF_DIR" validate
terraform -chdir="$TF_DIR" plan -input=false -out=tfplan

if [[ "$AUTO_APPROVE" == "true" ]]; then
  terraform -chdir="$TF_DIR" apply -input=false -auto-approve tfplan
else
  terraform -chdir="$TF_DIR" apply -input=false tfplan
fi

echo
echo "Infra Oracle provisionada. State fica no bucket remoto (Object Storage) — nao"
echo "depende mais deste disco/runner."
echo
echo "Kubeconfig:"
terraform -chdir="$TF_DIR" output -raw kubeconfig_command
echo
echo
echo "Proximo passo (deploy da app): ver scripts/k8s-deploy-oracle.sh"
