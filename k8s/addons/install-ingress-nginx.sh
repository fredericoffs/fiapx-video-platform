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
# externalTrafficPolicy=Local preserva o IP do cliente até o nginx, que o repassa no
# X-Forwarded-For. Com o padrão (Cluster) o kube-proxy troca a origem pelo IP do nó, e o rate
# limit do video-gateway (por IP) contava por nó: todos os clientes dividiam duas cotas —
# observado ao vivo, 25 uploads de um mesmo IP e nenhum 429. O NLB passa a checar a saúde
# pelo healthCheckNodePort e só encaminha para nós com pod do controller.
# controller.metrics.enabled abre a porta 10254 (requisições, latência e status na borda,
# incluindo os 429 do rate limit); o ServiceMonitor fica em k8s/addons/servicemonitors.yaml,
# aplicado depois do kube-prometheus-stack, que é quem cria o CRD.
# Versão fixada (item 20 da revisão crítica): sem --version, cada execução puxa o chart mais
# novo do momento — como este script roda de novo a cada sessão do Learner Lab (o lab reseta
# entre sessões), a versão podia mudar de uma sessão pra outra sem nenhuma mudança de código.
# INGRESS_EXPOSE=nodeport: contorno para conta AWS que não pode criar load balancer
# ("OperationNotPermitted: This AWS account currently does not support creating load
# balancers" — trava de conta nova, só o AWS Support libera). O controller vira NodePort
# fixo (HTTPS em 30443) e é acessado pelo IP público do nó; k8s-deploy-aws.sh abre a porta
# no security group do cluster. Só para demonstração: sem NLB, um nó é o ponto de entrada.
expose_args=(
  --set-string 'controller.service.annotations.service\.beta\.kubernetes\.io/aws-load-balancer-type=nlb'
)
if [ "${INGRESS_EXPOSE:-nlb}" = "nodeport" ]; then
  expose_args=(
    --set controller.service.type=NodePort
    --set controller.service.nodePorts.http=30080
    --set controller.service.nodePorts.https=30443
  )
fi

helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --version 4.15.1 \
  --set controller.resources.requests.cpu=100m \
  --set controller.resources.requests.memory=128Mi \
  --set controller.resources.limits.cpu=250m \
  --set controller.resources.limits.memory=256Mi \
  "${expose_args[@]}" \
  --set controller.service.externalTrafficPolicy=Local \
  --set controller.metrics.enabled=true \
  --wait --timeout 5m
