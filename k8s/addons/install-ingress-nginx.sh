#!/usr/bin/env bash
# ingress-nginx — só necessário em clusters reais expostos via Ingress
# (overlays/oracle, overlays/aws). O overlay local usa NodePort direto.
set -euo pipefail

helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null
helm repo update >/dev/null

kubectl get namespace ingress-nginx >/dev/null 2>&1 || kubectl create namespace ingress-nginx

echo "==> ingress-nginx"
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --set controller.resources.requests.cpu=100m \
  --set controller.resources.requests.memory=128Mi \
  --set controller.resources.limits.cpu=250m \
  --set controller.resources.limits.memory=256Mi \
  --wait --timeout 5m

echo "==> aguardando IP público do Load Balancer (pode levar alguns minutos)"
kubectl -n ingress-nginx get svc ingress-nginx-controller
