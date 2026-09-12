#!/usr/bin/env bash
# ingress-nginx — só necessário em clusters reais expostos via Ingress
# (overlays/aws — em EKS o Service LoadBalancer vira um Classic ELB). O overlay
# local usa NodePort direto.
#
# O Classic ELB tem idle timeout padrão de 60s (AWS) e o nginx tem
# proxy-read/send-timeout padrão de 60s — os dois cortam a conexão de um
# download grande (zip de frames, pode passar de 1GB) assim que ficar 60s sem
# tráfego, e o navegador acusa "Failed to fetch". A annotation abaixo sobe o
# idle timeout do ELB pra 300s; os timeouts do nginx sobem via annotation no
# Ingress (ver k8s/apps/overlays/aws/ingress.yaml).
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
  --set-string controller.service.annotations."service\.beta\.kubernetes\.io/aws-load-balancer-connection-idle-timeout"=300 \
  --wait --timeout 5m
