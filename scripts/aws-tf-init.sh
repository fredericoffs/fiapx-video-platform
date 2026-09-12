#!/usr/bin/env bash
# terraform init com backend remoto no S3 — cria o bucket de state se ainda nao
# existir (idempotente) e monta o -backend-config em runtime. Usado por aws-up.sh,
# aws-destroy.sh e terraform-aws.yml, pra nao duplicar essa logica em 3 lugares.
# Lock nativo do S3 (use_lockfile, Terraform >= 1.10): dispensa tabela DynamoDB.
set -euo pipefail

TF_DIR="${1:?uso: aws-tf-init.sh <diretorio-terraform>}"
AWS_REGION="${AWS_REGION:-us-east-1}"
PROJECT="${PROJECT:-fiapx}"

command -v aws >/dev/null 2>&1 || { echo "Erro: aws CLI nao encontrado" >&2; exit 1; }
command -v terraform >/dev/null 2>&1 || { echo "Erro: terraform nao encontrado" >&2; exit 1; }

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
BUCKET="${TF_STATE_BUCKET:-${PROJECT}-terraform-state-${ACCOUNT_ID}}"

if aws s3api head-bucket --bucket "$BUCKET" --region "$AWS_REGION" >/dev/null 2>&1; then
  echo "bucket de state ${BUCKET} ja existe"
else
  echo "criando bucket de state ${BUCKET}"
  if [[ "$AWS_REGION" == "us-east-1" ]]; then
    aws s3api create-bucket --bucket "$BUCKET" --region "$AWS_REGION" >/dev/null
  else
    aws s3api create-bucket --bucket "$BUCKET" --region "$AWS_REGION" \
      --create-bucket-configuration LocationConstraint="$AWS_REGION" >/dev/null
  fi
  aws s3api put-bucket-versioning --bucket "$BUCKET" \
    --versioning-configuration Status=Enabled
  aws s3api put-public-access-block --bucket "$BUCKET" \
    --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  aws s3api put-bucket-encryption --bucket "$BUCKET" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
fi

terraform -chdir="$TF_DIR" init -input=false -reconfigure \
  -backend-config="bucket=${BUCKET}" \
  -backend-config="key=eks/terraform.tfstate" \
  -backend-config="region=${AWS_REGION}" \
  -backend-config="use_lockfile=true" \
  -backend-config="encrypt=true"
