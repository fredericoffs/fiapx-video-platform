#!/usr/bin/env bash
# Deploy no EKS, chamado por .github/workflows/cd-aws.yml depois que o cluster já
# existe (Terraform, ver k8s/terraform/aws) e o kubeconfig já está configurado
# (aws eks update-kubeconfig). Mesma ordenação do scripts/k8s-up.sh (migration Job
# antes do Deployment do video-api), adaptada pra imagens do ECR em vez de
# build/load local. Hostname público do ELB é resolvido aqui, sem passo manual.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="fiapx"
OVERLAY_DIR="${ROOT_DIR}/k8s/apps/overlays/aws"
RENDER_DIR="$(mktemp -d)"
trap 'rm -rf "$RENDER_DIR"' EXIT

AWS_REGION="${AWS_REGION:-us-east-1}"
CLUSTER_NAME="${CLUSTER_NAME:-fiapx}"

: "${ECR_REGISTRY:?defina ECR_REGISTRY (ex.: 123456789012.dkr.ecr.us-east-1.amazonaws.com)}"
: "${IMAGE_TAG:?defina IMAGE_TAG}"
: "${DB_PASSWORD:?defina DB_PASSWORD}"
: "${RABBITMQ_PASSWORD:?defina RABBITMQ_PASSWORD}"
: "${STORAGE_ACCESS_KEY:?defina STORAGE_ACCESS_KEY}"
: "${STORAGE_SECRET_KEY:?defina STORAGE_SECRET_KEY}"
: "${JWT_SECRET:?defina JWT_SECRET}"

command -v aws >/dev/null || { echo "aws CLI não encontrado"; exit 1; }
command -v kustomize >/dev/null || { echo "kustomize não encontrado"; exit 1; }
command -v kubectl >/dev/null || { echo "kubectl não encontrado"; exit 1; }
command -v helm >/dev/null || { echo "helm não encontrado"; exit 1; }

# Sem o driver EBS CSI ativo, todo PVC de k8s/infra fica Pending pra sempre.
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

# Na AWS o Service LoadBalancer expõe hostname (Classic ELB), não IP.
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

echo "==> [1/8] add-on aws-ebs-csi-driver + StorageClass padrão (gp3)"
wait_for_ebs_csi
kubectl apply -f "$ROOT_DIR/k8s/addons/aws/storageclass-gp3.yaml"

echo "==> [2/8] ingress-nginx"
"$ROOT_DIR/k8s/addons/install-ingress-nginx.sh"

echo "==> [2b/8] aguardando hostname público do LoadBalancer"
LB_HOST="$(wait_for_lb_hostname)"
echo "Hostname público: ${LB_HOST}"
wait_for_dns "$LB_HOST"

echo "==> [3/8] add-ons de cluster (metrics-server, KEDA, kube-prometheus-stack)"
"$ROOT_DIR/k8s/addons/install.sh"

echo "==> [4/8] infra (Postgres, RabbitMQ, Redis, MinIO)"
"$ROOT_DIR/k8s/infra/install.sh"

echo "==> [5/8] Secret fiapx-secrets"
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
kubectl -n "$NAMESPACE" create secret generic fiapx-secrets \
  --from-literal=JWT_SECRET="${JWT_SECRET}" \
  --from-literal=DB_USER="${DB_USER:-fiapx}" \
  --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
  --from-literal=RABBITMQ_USER="${RABBITMQ_USER:-guest}" \
  --from-literal=RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD}" \
  --from-literal=STORAGE_ACCESS_KEY="${STORAGE_ACCESS_KEY}" \
  --from-literal=STORAGE_SECRET_KEY="${STORAGE_SECRET_KEY}" \
  --from-literal=RABBITMQ_MANAGEMENT_URL="http://${RABBITMQ_USER:-guest}:${RABBITMQ_PASSWORD}@rabbitmq.${NAMESPACE}.svc.cluster.local:15672/%2F" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> [6/8] apontando as imagens do overlay pro ECR (tag ${IMAGE_TAG})"
(
  cd "$OVERLAY_DIR"
  for svc in video-gateway video-api video-worker notification-worker web; do
    kustomize edit set image "fiapx/${svc}:local=${ECR_REGISTRY}/fiapx/${svc}:${IMAGE_TAG}"
  done
)

echo "==> [7/8] aplicando o overlay"
kubectl kustomize --load-restrictor LoadRestrictionsNone "$OVERLAY_DIR" > "${RENDER_DIR}/rendered.yaml"

awk -v outdir="$RENDER_DIR" '
  BEGIN { n = 0; file = sprintf("%s/doc-%03d.yaml", outdir, n) }
  /^---$/ { close(file); n++; file = sprintf("%s/doc-%03d.yaml", outdir, n); next }
  { print > file }
' "${RENDER_DIR}/rendered.yaml"

JOB_FILE="$(grep -l '^kind: Job$' "${RENDER_DIR}"/doc-*.yaml | head -1)"
[ -n "$JOB_FILE" ] || { echo "não encontrei o manifest do Job de migração"; exit 1; }

VIDEO_API_DEPLOY_FILE=""
for f in "${RENDER_DIR}"/doc-*.yaml; do
  if grep -q '^kind: Deployment$' "$f" && grep -q 'name: video-api$' "$f"; then
    VIDEO_API_DEPLOY_FILE="$f"
    break
  fi
done
[ -n "$VIDEO_API_DEPLOY_FILE" ] || { echo "não encontrei o Deployment do video-api"; exit 1; }

for f in "${RENDER_DIR}"/doc-*.yaml; do
  [ "$f" = "$VIDEO_API_DEPLOY_FILE" ] && continue
  kubectl apply -f "$f"
done
kubectl -n "$NAMESPACE" delete job video-api-migrate --ignore-not-found
kubectl apply -f "$JOB_FILE"
kubectl -n "$NAMESPACE" wait --for=condition=complete --timeout=180s job/video-api-migrate

kubectl apply -f "$VIDEO_API_DEPLOY_FILE"

echo "==> [7b/8] propagando URL pública (CORS do gateway + API_BASE_URL do web)"
kubectl -n "$NAMESPACE" patch configmap fiapx-config --type merge \
  -p "{\"data\":{\"GATEWAY_CORS_ALLOWED_ORIGINS\":\"http://${LB_HOST}\"}}"
kubectl -n "$NAMESPACE" rollout restart deployment/video-gateway
kubectl -n "$NAMESPACE" set env deployment/web API_BASE_URL="http://${LB_HOST}"

echo "==> [8/8] aguardando rollout"
kubectl -n "$NAMESPACE" rollout status deployment/video-gateway --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-api --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/notification-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/web --timeout=300s

echo "==> pronto. Endereço público: http://${LB_HOST}"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "## Deploy no EKS concluído"
    echo
    echo "- Aplicação: http://${LB_HOST}"
    echo "- Imagens: \`${ECR_REGISTRY}/fiapx/<serviço>:${IMAGE_TAG}\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi
