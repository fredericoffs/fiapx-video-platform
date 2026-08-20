#!/usr/bin/env bash
# Instala as 4 dependências de infra no namespace "fiapx".
#
# Postgres e Redis: Helm/Bitnami (image.tag ainda disponível gratuitamente no Docker
# Hub, verificado via API em 2026-08-19). RabbitMQ e MinIO: manifests próprios em
# k8s/infra/{rabbitmq,minio}/ — descoberto durante a Sprint 4 que docker.io/bitnami/
# rabbitmq e docker.io/bitnami/minio não têm mais NENHUMA tag pública (Broadcom moveu
# para o catálogo pago "Bitnami Secure Images"; count:0 na API do Docker Hub para os
# dois). Usam as MESMAS imagens oficiais já validadas no docker-compose.yml.
#
# Hostnames reais dos Services (confirmados via `kubectl get svc`, não assumidos):
# chart postgresql -> Service "postgres-postgresql"; chart redis -> Service
# "redis-master" (o helper `common.names.fullname` do Bitnami só colapsa Release+Chart
# num nome só em alguns charts, não em todos — não dá pra assumir). rabbitmq/minio são
# manifests próprios, Service literalmente "rabbitmq"/"minio". Todos batem com
# k8s/apps/base/configmap.yaml (DB_HOST/REDIS_HOST/RABBITMQ_HOST/STORAGE_ENDPOINT).
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
