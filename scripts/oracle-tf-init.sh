#!/usr/bin/env bash
# terraform init com o backend remoto (Object Storage/S3-compatible da Oracle) —
# monta o -backend-config na hora a partir de env vars (nunca commitado, contém
# credenciais). Usado por oracle-up.sh, oracle-destroy.sh e terraform-oracle.yml,
# pra nao duplicar essa logica em 3 lugares. Backend precisa ter sido criado antes
# via .github/workflows/bootstrap-oracle-backend.yml (roda uma unica vez).
set -euo pipefail

TF_DIR="${1:?uso: oracle-tf-init.sh <diretorio-terraform>}"

: "${OCI_REGION:?defina OCI_REGION (mesma regiao do TF_VAR_region)}"
: "${OCI_OS_NAMESPACE:?defina OCI_OS_NAMESPACE (saida do bootstrap-oracle-backend.yml)}"
: "${OCI_S3_ACCESS_KEY_ID:?defina OCI_S3_ACCESS_KEY_ID (saida do bootstrap-oracle-backend.yml)}"
: "${OCI_S3_SECRET_ACCESS_KEY:?defina OCI_S3_SECRET_ACCESS_KEY (saida do bootstrap-oracle-backend.yml)}"

BACKEND_FILE="$(mktemp)"
trap 'rm -f "$BACKEND_FILE"' EXIT

cat > "$BACKEND_FILE" <<HCL
bucket     = "fiapx-terraform-state"
key        = "oracle/terraform.tfstate"
region     = "${OCI_REGION}"
endpoints  = { s3 = "https://${OCI_OS_NAMESPACE}.compat.objectstorage.${OCI_REGION}.oraclecloud.com" }
access_key = "${OCI_S3_ACCESS_KEY_ID}"
secret_key = "${OCI_S3_SECRET_ACCESS_KEY}"
skip_region_validation      = true
skip_credentials_validation = true
skip_metadata_api_check     = true
skip_requesting_account_id  = true
force_path_style            = true
HCL

terraform -chdir="$TF_DIR" init -input=false -backend-config="$BACKEND_FILE"
