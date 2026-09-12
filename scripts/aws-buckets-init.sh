#!/usr/bin/env bash
# Cria os buckets de video (fiapx-videos-{raw,processed}-<account>) com aws CLI, de forma
# idempotente, antes do terraform plan/apply — mesmo padrao do bucket de state em
# aws-tf-init.sh. Nao usa aws_s3_bucket no Terraform: a SCP do Learner Lab nega
# s3:GetBucketObjectLockConfiguration, que o provider AWS chama ao ler qualquer bucket, e
# o apply falha logo apos criar o recurso. Buckets privados (public access block),
# SSE-S3, tag Project (varredura do destroy). Removidos por scripts/aws-destroy.sh.
set -euo pipefail

AWS_REGION="${AWS_REGION:-us-east-1}"
PROJECT="${PROJECT:-fiapx}"

command -v aws >/dev/null 2>&1 || { echo "Erro: aws CLI nao encontrado" >&2; exit 1; }

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

for kind in raw processed; do
  BUCKET="${PROJECT}-videos-${kind}-${ACCOUNT_ID}"
  if aws s3api head-bucket --bucket "$BUCKET" --region "$AWS_REGION" >/dev/null 2>&1; then
    echo "bucket ${BUCKET} ja existe"
    continue
  fi
  echo "criando bucket ${BUCKET}"
  if [[ "$AWS_REGION" == "us-east-1" ]]; then
    aws s3api create-bucket --bucket "$BUCKET" --region "$AWS_REGION" >/dev/null
  else
    aws s3api create-bucket --bucket "$BUCKET" --region "$AWS_REGION" \
      --create-bucket-configuration LocationConstraint="$AWS_REGION" >/dev/null
  fi
  aws s3api put-public-access-block --bucket "$BUCKET" --region "$AWS_REGION" \
    --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  aws s3api put-bucket-encryption --bucket "$BUCKET" --region "$AWS_REGION" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-bucket-tagging --bucket "$BUCKET" --region "$AWS_REGION" \
    --tagging "TagSet=[{Key=Project,Value=${PROJECT}},{Key=Name,Value=${PROJECT}-videos-${kind}}]"
done
