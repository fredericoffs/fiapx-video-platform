#!/usr/bin/env bash
# Instala os add-ons de cluster necessários para autoscaling real (RT2):
#   - metrics-server: pré-requisito do HPA (video-api). Em kind, o kubelet usa
#     certificado self-signed -> precisa de --kubelet-insecure-tls (gotcha conhecido,
#     não é algo que aconteça num cluster gerenciado real).
#   - KEDA: ScaledObject do video-worker (fila RabbitMQ + CPU).
set -euo pipefail

helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null
helm repo add kedacore https://kedacore.github.io/charts >/dev/null
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

echo "==> Add-ons prontos:"
kubectl get deployment -n kube-system metrics-server
kubectl get deployment -n keda
