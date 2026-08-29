#!/usr/bin/env bash
# Deploy na OKE, chamado por .github/workflows/cd-oracle.yml depois que o cluster já
# existe (Terraform, ver k8s/terraform/oracle) e o kubeconfig já está configurado.
# Mesma ordenação do scripts/k8s-up.sh (migration Job antes do Deployment do
# video-api), adaptada pra imagens do GHCR em vez de build/load local. IP público
# do ingress e imagePullSecrets são resolvidos aqui, sem passo manual.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="fiapx"
OVERLAY_DIR="${ROOT_DIR}/k8s/apps/overlays/oracle"
RENDER_DIR="$(mktemp -d)"
trap 'rm -rf "$RENDER_DIR"' EXIT

: "${GHCR_OWNER:?defina GHCR_OWNER}"
: "${GHCR_PULL_TOKEN:?defina GHCR_PULL_TOKEN}"
: "${IMAGE_TAG:?defina IMAGE_TAG}"
: "${DB_PASSWORD:?defina DB_PASSWORD}"
: "${RABBITMQ_PASSWORD:?defina RABBITMQ_PASSWORD}"
: "${STORAGE_ACCESS_KEY:?defina STORAGE_ACCESS_KEY}"
: "${STORAGE_SECRET_KEY:?defina STORAGE_SECRET_KEY}"
: "${JWT_SECRET:?defina JWT_SECRET}"

command -v kustomize >/dev/null || { echo "kustomize não encontrado"; exit 1; }
command -v kubectl >/dev/null || { echo "kubectl não encontrado"; exit 1; }
command -v helm >/dev/null || { echo "helm não encontrado"; exit 1; }

wait_for_lb_ip() {
  local timeout=300 elapsed=0 ip=""
  while [ "$elapsed" -lt "$timeout" ]; do
    ip="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
      -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
    [ -n "$ip" ] && { echo "$ip"; return 0; }
    sleep 10
    elapsed=$((elapsed + 10))
  done
  echo "timeout esperando IP do LoadBalancer do ingress-nginx" >&2
  return 1
}

echo "==> [1/8] ingress-nginx"
"$ROOT_DIR/k8s/addons/install-ingress-nginx.sh"

echo "==> [1b/8] aguardando IP público do LoadBalancer"
LB_IP="$(wait_for_lb_ip)"
echo "IP público: ${LB_IP}"

echo "==> [2/8] add-ons de cluster (metrics-server, KEDA, kube-prometheus-stack)"
"$ROOT_DIR/k8s/addons/install.sh"

echo "==> [3/8] infra (Postgres, RabbitMQ, Redis, MinIO)"
"$ROOT_DIR/k8s/infra/install.sh"

echo "==> [4/8] Secret fiapx-secrets"
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

echo "==> [4b/8] imagePullSecrets pro GHCR (pacotes ficam privados)"
kubectl -n "$NAMESPACE" create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username="${GHCR_OWNER}" \
  --docker-password="${GHCR_PULL_TOKEN}" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" patch serviceaccount default \
  -p '{"imagePullSecrets": [{"name": "ghcr-pull-secret"}]}'

echo "==> [5/8] apontando as imagens do overlay pro GHCR (tag ${IMAGE_TAG})"
(
  cd "$OVERLAY_DIR"
  for svc in video-gateway video-api video-worker notification-worker web; do
    kustomize edit set image "fiapx/${svc}:local=ghcr.io/${GHCR_OWNER}/fiapx-${svc}:${IMAGE_TAG}"
  done
)

echo "==> [6/8] aplicando o overlay"
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

echo "==> [7/8] propagando URL pública (CORS do gateway + API_BASE_URL do web)"
kubectl -n "$NAMESPACE" patch configmap fiapx-config --type merge \
  -p "{\"data\":{\"GATEWAY_CORS_ALLOWED_ORIGINS\":\"http://${LB_IP}\"}}"
kubectl -n "$NAMESPACE" rollout restart deployment/video-gateway
kubectl -n "$NAMESPACE" set env deployment/web API_BASE_URL="http://${LB_IP}"

echo "==> [8/8] aguardando rollout"
kubectl -n "$NAMESPACE" rollout status deployment/video-gateway --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-api --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/notification-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/web --timeout=300s

echo "==> pronto. Endereço público: http://${LB_IP}"
