#!/usr/bin/env bash
# Postgres/Redis via Helm/Bitnami; RabbitMQ/MinIO via manifests próprios (charts
# Bitnami sem tag pública gratuita). Services reais: postgres-postgresql,
# redis-master, rabbitmq, minio — batem com k8s/apps/base/configmap.yaml.
set -euo pipefail

NAMESPACE="${NAMESPACE:-fiapx}"
INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY="oci://registry-1.docker.io/bitnamicharts"

kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"

install_helm() {
  local release="$1" chart="$2" values="$3"
  echo "==> helm upgrade --install ${release} ${REGISTRY}/${chart}"
  helm upgrade --install "$release" "${REGISTRY}/${chart}" \
    --namespace "$NAMESPACE" \
    --values "${INFRA_DIR}/values/${values}" \
    --wait --timeout 5m
}

install_helm postgres postgresql postgres.yaml
install_helm redis redis redis.yaml

echo "==> rabbitmq (manifest próprio)"
kubectl apply -f "${INFRA_DIR}/rabbitmq/"
kubectl -n "$NAMESPACE" rollout status deployment/rabbitmq --timeout=180s

echo "==> minio (manifest próprio)"
kubectl apply -f "${INFRA_DIR}/minio/secret.yaml" -f "${INFRA_DIR}/minio/pvc.yaml" \
  -f "${INFRA_DIR}/minio/deployment.yaml" -f "${INFRA_DIR}/minio/service.yaml"
kubectl -n "$NAMESPACE" rollout status deployment/minio --timeout=180s

echo "==> minio: criando buckets (videos-raw, videos-processed)"
kubectl -n "$NAMESPACE" delete job minio-init --ignore-not-found
kubectl apply -f "${INFRA_DIR}/minio/init-job.yaml"
kubectl -n "$NAMESPACE" wait --for=condition=complete --timeout=60s job/minio-init

echo "==> Infra pronta no namespace ${NAMESPACE}:"
kubectl -n "$NAMESPACE" get pods -l 'app.kubernetes.io/name in (rabbitmq,minio)'
kubectl -n "$NAMESPACE" get pods -l 'app.kubernetes.io/instance in (postgres,redis)'
