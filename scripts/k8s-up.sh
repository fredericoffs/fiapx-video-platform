#!/usr/bin/env bash
# Idempotente — pode rodar de novo sobre um cluster já existente.
# Migration Job precisa terminar ANTES do Deployment do video-api (Hibernate
# ddl-auto: validate derruba o container se o schema não existir ainda).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER_NAME="fiapx"
NAMESPACE="fiapx"
OVERLAY="${1:-local}"
RENDER_DIR="$(mktemp -d)"
trap 'rm -rf "$RENDER_DIR"' EXIT

command -v kind >/dev/null || { echo "kind não encontrado — 'brew install kind'"; exit 1; }
command -v helm >/dev/null || { echo "helm não encontrado — 'brew install helm'"; exit 1; }

echo "==> [1/6] cluster kind"
if ! kind get clusters | grep -qx "$CLUSTER_NAME"; then
  kind create cluster --config "$ROOT_DIR/k8s/kind/cluster-config.yaml"
else
  echo "cluster '$CLUSTER_NAME' já existe, reaproveitando"
fi
kubectl config use-context "kind-${CLUSTER_NAME}" >/dev/null

echo "==> [2/6] build + load das imagens locais"
for svc in video-gateway video-api video-worker notification-worker web; do
  docker build -t "fiapx/${svc}:local" "${ROOT_DIR}/${svc}"
  kind load docker-image "fiapx/${svc}:local" --name "$CLUSTER_NAME"
done

echo "==> [3/6] add-ons de cluster (metrics-server, KEDA)"
"$ROOT_DIR/k8s/addons/install.sh"

echo "==> [4/6] infra (Postgres, RabbitMQ, Redis, MinIO)"
"$ROOT_DIR/k8s/infra/install.sh"

echo "==> [5/6] migração de schema (video-api) antes do deploy da aplicação"
kubectl kustomize --load-restrictor LoadRestrictionsNone \
  "$ROOT_DIR/k8s/apps/overlays/${OVERLAY}" > "${RENDER_DIR}/rendered.yaml"

awk -v outdir="$RENDER_DIR" '
  BEGIN { n = 0; file = sprintf("%s/doc-%03d.yaml", outdir, n) }
  /^---$/ { close(file); n++; file = sprintf("%s/doc-%03d.yaml", outdir, n); next }
  { print > file }
' "${RENDER_DIR}/rendered.yaml"

JOB_FILE="$(grep -l '^kind: Job$' "${RENDER_DIR}"/doc-*.yaml | head -1)"
if [ -z "$JOB_FILE" ]; then
  echo "não encontrei o manifest do Job de migração no overlay '${OVERLAY}'"; exit 1
fi
# Aplico tudo exceto o Deployment do video-api — é o único recurso que precisa
# esperar a migração terminar.
VIDEO_API_DEPLOY_FILE=""
for f in "${RENDER_DIR}"/doc-*.yaml; do
  if grep -q '^kind: Deployment$' "$f" && grep -q 'name: video-api$' "$f"; then
    VIDEO_API_DEPLOY_FILE="$f"
    break
  fi
done
if [ -z "$VIDEO_API_DEPLOY_FILE" ]; then
  echo "não encontrei o Deployment do video-api no overlay '${OVERLAY}'"; exit 1
fi

kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
for f in "${RENDER_DIR}"/doc-*.yaml; do
  [ "$f" = "$VIDEO_API_DEPLOY_FILE" ] && continue
  kubectl apply -f "$f"
done
kubectl -n "$NAMESPACE" delete job video-api-migrate --ignore-not-found
kubectl apply -f "$JOB_FILE"
kubectl -n "$NAMESPACE" wait --for=condition=complete --timeout=180s job/video-api-migrate

echo "==> [6/6] deploy da aplicação (overlay: ${OVERLAY})"
kubectl apply -f "$VIDEO_API_DEPLOY_FILE"
kubectl -n "$NAMESPACE" rollout status deployment/video-gateway --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-api --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/video-worker --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/notification-worker --timeout=300s

echo "==> pronto. video-gateway em http://localhost:8080"
kubectl -n "$NAMESPACE" get pods
