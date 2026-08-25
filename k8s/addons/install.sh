#!/usr/bin/env bash
# metrics-server, KEDA, kube-prometheus-stack. --kubelet-insecure-tls é necessário em
# kind (certificado self-signed do kubelet), inofensivo num cluster gerenciado.
set -euo pipefail

ADDONS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null
helm repo add kedacore https://kedacore.github.io/charts >/dev/null
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update >/dev/null

echo "==> metrics-server"
helm upgrade --install metrics-server metrics-server/metrics-server \
  --namespace kube-system \
  --set args='{--kubelet-insecure-tls}' \
  --wait --timeout 3m

echo "==> KEDA"
kubectl get namespace keda >/dev/null 2>&1 || kubectl create namespace keda
helm upgrade --install keda kedacore/keda \
  --namespace keda \
  --wait --timeout 3m

echo "==> kube-prometheus-stack"
kubectl get namespace monitoring >/dev/null 2>&1 || kubectl create namespace monitoring
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --values "${ADDONS_DIR}/values/kube-prometheus-stack.yaml" \
  --wait --timeout 5m

echo "==> dashboards Grafana + alerta de profundidade de fila"
kubectl apply -k "${ADDONS_DIR}"

echo "==> Add-ons prontos:"
kubectl get deployment -n kube-system metrics-server
kubectl get deployment -n keda
kubectl get pods -n monitoring
