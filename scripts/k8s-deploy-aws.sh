#!/usr/bin/env bash
# Deploy no EKS, chamado por .github/workflows/cd-aws.yml depois que o cluster e os serviços
# gerenciados já existem (Terraform, ver k8s/terraform/aws) e o kubeconfig já está
# configurado (aws eks update-kubeconfig). Migration Job antes do Deployment do video-api,
# com imagens do ECR e sem infra self-hosted: os hosts de RDS/ElastiCache, os buckets S3 e a
# URL da fila SQS (KEDA) são descobertos por nome via aws CLI e injetados no ConfigMap em
# runtime, sem depender do state do Terraform. Os segredos da aplicação (DB_USER,
# DB_PASSWORD, JWT_SECRET, NOTIFICATION_WEBHOOK_URL) vêm do SSM Parameter Store
# (/fiapx/..., criados pelo Terraform); variáveis de ambiente com o mesmo nome, se
# definidas, têm precedência.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="fiapx"
BASE_DIR="${ROOT_DIR}/k8s/apps/base"
RENDER_DIR="$(mktemp -d)"
trap 'rm -rf "$RENDER_DIR"' EXIT

AWS_REGION="${AWS_REGION:-us-east-1}"
CLUSTER_NAME="${CLUSTER_NAME:-fiapx}"
PROJECT="${PROJECT:-fiapx}"

: "${ECR_REGISTRY:?defina ECR_REGISTRY (ex.: 123456789012.dkr.ecr.us-east-1.amazonaws.com)}"
: "${IMAGE_TAG:?defina IMAGE_TAG}"

command -v aws >/dev/null || { echo "aws CLI não encontrado"; exit 1; }
command -v kustomize >/dev/null || { echo "kustomize não encontrado"; exit 1; }
command -v kubectl >/dev/null || { echo "kubectl não encontrado"; exit 1; }
command -v helm >/dev/null || { echo "helm não encontrado"; exit 1; }

# Sem o driver EBS CSI ativo, PVCs (só o kube-prometheus-stack usa) ficam Pending pra sempre.
wait_for_ebs_csi() {
  local timeout=600 elapsed=0 status=""
  while [ "$elapsed" -lt "$timeout" ]; do
    status="$(aws eks describe-addon --region "$AWS_REGION" --cluster-name "$CLUSTER_NAME" \
      --addon-name aws-ebs-csi-driver --query 'addon.status' --output text 2>/dev/null || true)"
    [ "$status" = "ACTIVE" ] && return 0
    echo "add-on aws-ebs-csi-driver: ${status:-desconhecido}, aguardando..."
    sleep 15
    elapsed=$((elapsed + 15))
  done
  echo "timeout esperando o add-on aws-ebs-csi-driver ficar ACTIVE" >&2
  return 1
}

# Na AWS o Service LoadBalancer expõe hostname (NLB), não IP.
wait_for_lb_hostname() {
  local timeout=300 elapsed=0 host=""
  while [ "$elapsed" -lt "$timeout" ]; do
    host="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
      -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
    [ -n "$host" ] && { echo "$host"; return 0; }
    sleep 10
    elapsed=$((elapsed + 10))
  done
  echo "timeout esperando hostname do LoadBalancer do ingress-nginx" >&2
  return 1
}

# O DNS do ELB demora ~1–2 min pra propagar depois de criado.
wait_for_dns() {
  local host="$1" timeout=300 elapsed=0
  while [ "$elapsed" -lt "$timeout" ]; do
    getent hosts "$host" >/dev/null 2>&1 && return 0
    sleep 10
    elapsed=$((elapsed + 10))
  done
  echo "aviso: DNS de ${host} ainda não resolve após ${timeout}s — seguindo mesmo assim" >&2
  return 0
}

# Parâmetro SSM por nome; "" se não existir (só o webhook é opcional).
ssm_param() {
  aws ssm get-parameter --region "$AWS_REGION" --name "$1" --with-decryption \
    --query 'Parameter.Value' --output text 2>/dev/null || true
}

echo "==> [1/9] serviços gerenciados (descoberta por nome via aws CLI) e segredos (SSM)"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
DB_USER="${DB_USER:-$(ssm_param "/${PROJECT}/db/username")}"
DB_PASSWORD="${DB_PASSWORD:-$(ssm_param "/${PROJECT}/db/password")}"
JWT_SECRET="${JWT_SECRET:-$(ssm_param "/${PROJECT}/jwt/secret")}"
ADMIN_SEED_PASSWORD="${ADMIN_SEED_PASSWORD:-$(ssm_param "/${PROJECT}/admin/password")}"
NOTIFICATION_WEBHOOK_URL="${NOTIFICATION_WEBHOOK_URL:-$(ssm_param "/${PROJECT}/notification/webhook-url")}"
[ -n "$DB_USER" ] || { echo "parâmetro SSM /${PROJECT}/db/username não encontrado (terraform apply rodou?)" >&2; exit 1; }
[ -n "$DB_PASSWORD" ] || { echo "parâmetro SSM /${PROJECT}/db/password não encontrado (terraform apply rodou?)" >&2; exit 1; }
[ -n "$JWT_SECRET" ] || { echo "parâmetro SSM /${PROJECT}/jwt/secret não encontrado (terraform apply rodou?)" >&2; exit 1; }
[ -n "$ADMIN_SEED_PASSWORD" ] || { echo "parâmetro SSM /${PROJECT}/admin/password não encontrado (terraform apply rodou?)" >&2; exit 1; }
echo "segredos: DB_USER=${DB_USER}, DB_PASSWORD/JWT_SECRET/ADMIN_SEED_PASSWORD lidos do SSM, webhook=$([ -n "$NOTIFICATION_WEBHOOK_URL" ] && echo configurado || echo ausente)"
DB_HOST="$(aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "${PROJECT}-postgres" \
  --query 'DBInstances[0].Endpoint.Address' --output text)"
DB_STATUS="$(aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "${PROJECT}-postgres" \
  --query 'DBInstances[0].DBInstanceStatus' --output text)"
[ "$DB_STATUS" = "available" ] || { echo "RDS ${PROJECT}-postgres em estado '${DB_STATUS}', esperado 'available'" >&2; exit 1; }
REDIS_HOST="$(aws elasticache describe-cache-clusters --region "$AWS_REGION" --cache-cluster-id "${PROJECT}-redis" \
  --show-cache-node-info --query 'CacheClusters[0].CacheNodes[0].Endpoint.Address' --output text)"
PROCESSING_QUEUE_URL="$(aws sqs get-queue-url --region "$AWS_REGION" --queue-name "${PROJECT}-video-processing" \
  --query QueueUrl --output text)"
BUCKET_RAW="${PROJECT}-videos-raw-${ACCOUNT_ID}"
BUCKET_PROCESSED="${PROJECT}-videos-processed-${ACCOUNT_ID}"
aws s3api head-bucket --bucket "$BUCKET_RAW" --region "$AWS_REGION" >/dev/null
aws s3api head-bucket --bucket "$BUCKET_PROCESSED" --region "$AWS_REGION" >/dev/null
echo "RDS=${DB_HOST} Redis=${REDIS_HOST} SQS=${PROCESSING_QUEUE_URL} S3=${BUCKET_RAW},${BUCKET_PROCESSED}"

echo "==> [2/9] add-on aws-ebs-csi-driver + StorageClass padrão (gp3)"
wait_for_ebs_csi
kubectl apply -f "$ROOT_DIR/k8s/addons/aws/storageclass-gp3.yaml"

echo "==> [3/9] ingress-nginx"
"$ROOT_DIR/k8s/addons/install-ingress-nginx.sh"

echo "==> [3b/9] aguardando hostname público do LoadBalancer"
LB_HOST="$(wait_for_lb_hostname)"
echo "Hostname público: ${LB_HOST}"
wait_for_dns "$LB_HOST"

echo "==> [4/9] add-ons de cluster (metrics-server, KEDA, kube-prometheus-stack)"
"$ROOT_DIR/k8s/addons/install.sh"

echo "==> [5/9] Secret fiapx-secrets"
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
kubectl -n "$NAMESPACE" create secret generic fiapx-secrets \
  --from-literal=JWT_SECRET="${JWT_SECRET}" \
  --from-literal=DB_USER="${DB_USER}" \
  --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
  --from-literal=ADMIN_SEED_PASSWORD="${ADMIN_SEED_PASSWORD}" \
  --from-literal=NOTIFICATION_WEBHOOK_URL="${NOTIFICATION_WEBHOOK_URL}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> [6/9] apontando as imagens (k8s/apps/base) pro ECR (tag ${IMAGE_TAG})"
(
  cd "$BASE_DIR"
  for svc in video-gateway video-api video-worker notification-worker web; do
    kustomize edit set image "fiapx/${svc}:local=${ECR_REGISTRY}/fiapx/${svc}:${IMAGE_TAG}"
  done
)

echo "==> [7/9] aplicando os manifests (ConfigMap primeiro, com os hosts gerenciados)"
kubectl kustomize --load-restrictor LoadRestrictionsNone "$BASE_DIR" > "${RENDER_DIR}/rendered.yaml"

awk -v outdir="$RENDER_DIR" '
  BEGIN { n = 0; file = sprintf("%s/doc-%03d.yaml", outdir, n) }
  /^---$/ { close(file); n++; file = sprintf("%s/doc-%03d.yaml", outdir, n); next }
  { print > file }
' "${RENDER_DIR}/rendered.yaml"

JOB_FILE="$(grep -l '^kind: Job$' "${RENDER_DIR}"/doc-*.yaml | head -1)"
[ -n "$JOB_FILE" ] || { echo "não encontrei o manifest do Job de migração"; exit 1; }

VIDEO_API_DEPLOY_FILE=""
CONFIGMAP_FILE=""
for f in "${RENDER_DIR}"/doc-*.yaml; do
  if grep -q '^kind: Deployment$' "$f" && grep -q 'name: video-api$' "$f"; then
    VIDEO_API_DEPLOY_FILE="$f"
  elif grep -q '^kind: ConfigMap$' "$f" && grep -q 'name: fiapx-config$' "$f"; then
    CONFIGMAP_FILE="$f"
  fi
done
[ -n "$VIDEO_API_DEPLOY_FILE" ] || { echo "não encontrei o Deployment do video-api"; exit 1; }
[ -n "$CONFIGMAP_FILE" ] || { echo "não encontrei o ConfigMap fiapx-config"; exit 1; }

# ConfigMap antes de tudo, já com os valores descobertos — assim o Job de migração e os
# Deployments sobem apontando pro RDS/ElastiCache/S3 desde o primeiro pod.
kubectl apply -f "$CONFIGMAP_FILE"
kubectl -n "$NAMESPACE" patch configmap fiapx-config --type merge -p "{\"data\":{
  \"DB_HOST\":\"${DB_HOST}\",
  \"REDIS_HOST\":\"${REDIS_HOST}\",
  \"STORAGE_BUCKET_RAW\":\"${BUCKET_RAW}\",
  \"STORAGE_BUCKET_PROCESSED\":\"${BUCKET_PROCESSED}\",
  \"GATEWAY_CORS_ALLOWED_ORIGINS\":\"http://${LB_HOST}\"
}}"

for f in "${RENDER_DIR}"/doc-*.yaml; do
  [ "$f" = "$VIDEO_API_DEPLOY_FILE" ] && continue
  [ "$f" = "$CONFIGMAP_FILE" ] && continue
  [ "$f" = "$JOB_FILE" ] && continue
  kubectl apply -f "$f"
done
kubectl -n "$NAMESPACE" delete job video-api-migrate --ignore-not-found
kubectl apply -f "$JOB_FILE"
kubectl -n "$NAMESPACE" wait --for=condition=complete --timeout=300s job/video-api-migrate

kubectl apply -f "$VIDEO_API_DEPLOY_FILE"

echo "==> [8/9] KEDA: URL real da fila SQS + URL pública no web"
kubectl -n "$NAMESPACE" patch scaledobject video-worker --type json \
  -p "[{\"op\":\"replace\",\"path\":\"/spec/triggers/0/metadata/queueURL\",\"value\":\"${PROCESSING_QUEUE_URL}\"}]"
kubectl -n "$NAMESPACE" set env deployment/web API_BASE_URL="http://${LB_HOST}"

echo "==> [9/9] aguardando rollout"
kubectl -n "$NAMESPACE" rollout status deployment/video-gateway --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-api --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/notification-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/web --timeout=300s

echo "==> pronto. Endereço público: http://${LB_HOST}"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "## Deploy no EKS concluído (perfil aws: RDS + ElastiCache + S3 + SQS)"
    echo
    echo "- Aplicação: http://${LB_HOST}"
    echo "- Imagens: \`${ECR_REGISTRY}/fiapx/<serviço>:${IMAGE_TAG}\`"
    echo "- RDS: \`${DB_HOST}\` · Redis: \`${REDIS_HOST}\`"
    echo "- Fila de processamento: \`${PROCESSING_QUEUE_URL}\`"
    echo "- Buckets: \`${BUCKET_RAW}\`, \`${BUCKET_PROCESSED}\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi
