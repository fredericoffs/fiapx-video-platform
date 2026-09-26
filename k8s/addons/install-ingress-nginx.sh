#!/usr/bin/env bash
# ingress-nginx expõe o cluster via Ingress (k8s/apps/base/ingress.yaml) — em EKS o
# Service LoadBalancer do controller vira um load balancer da AWS (NLB).
#
# NLB, não Classic ELB: um Classic ELB recém-criado precisa de "pré-aquecimento"
# gradual pra sustentar throughput alto (limitação documentada da AWS) — o
# download de um zip grande (pode passar de 1GB) acelera até algumas centenas
# de Mbps em segundos, mais rápido do que o Classic ELB escala capacidade, e a
# conexão é derrubada no meio (confirmado: o mesmo download completa inteiro
# indo direto no video-api ou no nginx-ingress, só falha atravessando o
# Classic ELB). NLB escala automaticamente, sem esse aquecimento — resolve de
# vez. O idle timeout do NLB é fixo em 350s (não configurável, mas já cobre o
# proxy-read/send-timeout de 300s do nginx — ver k8s/apps/base/ingress.yaml)
# então a annotation de idle timeout do Classic ELB não se aplica mais aqui.
set -euo pipefail

helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null
helm repo update >/dev/null

kubectl get namespace ingress-nginx >/dev/null 2>&1 || kubectl create namespace ingress-nginx

echo "==> ingress-nginx"
# Versão fixada (item 20 da revisão crítica): sem --version, cada execução puxa o chart mais
# novo do momento — como este script roda de novo a cada sessão do Learner Lab (o lab reseta
# entre sessões), a versão podia mudar de uma sessão pra outra sem nenhuma mudança de código.
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --version 4.15.1 \
  --set controller.resources.requests.cpu=100m \
  --set controller.resources.requests.memory=128Mi \
  --set controller.resources.limits.cpu=250m \
  --set controller.resources.limits.memory=256Mi \
  --set-string controller.service.annotations."service\.beta\.kubernetes\.io/aws-load-balancer-type"=nlb \
  --wait --timeout 5m
