#!/usr/bin/env bash
# ingress-nginx — só necessário em clusters reais expostos via Ingress
# (overlays/aws — em EKS o Service LoadBalancer vira um load balancer da AWS).
# O overlay local usa NodePort direto.
#
# NLB, não Classic ELB: um Classic ELB recém-criado precisa de "pré-aquecimento"
# gradual pra sustentar throughput alto (limitação documentada da AWS) — o
# download de um zip grande (pode passar de 1GB) acelera até algumas centenas
# de Mbps em segundos, mais rápido do que o Classic ELB escala capacidade, e a
# conexão é derrubada no meio (confirmado: o mesmo download completa inteiro
# indo direto no video-api ou no nginx-ingress, só falha atravessando o
# Classic ELB). NLB escala automaticamente, sem esse aquecimento — resolve de
# vez. O idle timeout do NLB é fixo em 350s (não configurável, mas já cobre o
# proxy-read/send-timeout de 300s do nginx — ver k8s/apps/overlays/aws/ingress.yaml)
# então a annotation de idle timeout do Classic ELB não se aplica mais aqui.
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
  --set-string controller.service.annotations."service\.beta\.kubernetes\.io/aws-load-balancer-type"=nlb \
  --wait --timeout 5m
