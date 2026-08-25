#!/usr/bin/env bash
# Deploy na OKE, chamado por .github/workflows/cd-oracle.yml depois que o cluster já
# existe (Terraform, ver k8s/terraform/oracle) e o kubeconfig já está configurado.
# Mesma ordenação do scripts/k8s-up.sh (migration Job antes do Deployment do
# video-api), adaptada pra imagens do GHCR em vez de build/load local.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="fiapx"
OVERLAY_DIR="${ROOT_DIR}/k8s/apps/overlays/oracle"
RENDER_DIR="$(mktemp -d)"
trap 'rm -rf "$RENDER_DIR"' EXIT

: "${GHCR_OWNER:?defina GHCR_OWNER}"
: "${IMAGE_TAG:?defina IMAGE_TAG}"
: "${DB_PASSWORD:?defina DB_PASSWORD}"
: "${RABBITMQ_PASSWORD:?defina RABBITMQ_PASSWORD}"
: "${STORAGE_ACCESS_KEY:?defina STORAGE_ACCESS_KEY}"
: "${STORAGE_SECRET_KEY:?defina STORAGE_SECRET_KEY}"
: "${JWT_SECRET:?defina JWT_SECRET}"

command -v kustomize >/dev/null || { echo "kustomize não encontrado"; exit 1; }
command -v kubectl >/dev/null || { echo "kubectl não encontrado"; exit 1; }
command -v helm >/dev/null || { echo "helm não encontrado"; exit 1; }

echo "==> [1/6] ingress-nginx"
"$ROOT_DIR/k8s/addons/install-ingress-nginx.sh"

echo "==> [2/6] add-ons de cluster (metrics-server, KEDA, kube-prometheus-stack)"
"$ROOT_DIR/k8s/addons/install.sh"

echo "==> [3/6] infra (Postgres, RabbitMQ, Redis, MinIO)"
"$ROOT_DIR/k8s/infra/install.sh"

echo "==> [4/6] Secret fiapx-secrets"
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

echo "==> [5/6] apontando as imagens do overlay pro GHCR (tag ${IMAGE_TAG})"
(
  cd "$OVERLAY_DIR"
  for svc in video-gateway video-api video-worker notification-worker web; do
    kustomize edit set image "fiapx/${svc}:local=ghcr.io/${GHCR_OWNER}/fiapx-${svc}:${IMAGE_TAG}"
  done
)

echo "==> [6/6] aplicando o overlay"
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
kubectl -n "$NAMESPACE" rollout status deployment/video-gateway --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-api --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/notification-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/web --timeout=300s

echo "==> pronto. Endereço público do Ingress:"
kubectl -n ingress-nginx get svc ingress-nginx-controller -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
echo
echo "GATEWAY_CORS_ALLOWED_ORIGINS ainda está com REPLACE_WITH_PUBLIC_URL — troque pelo IP acima e reaplique."
