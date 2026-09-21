#!/usr/bin/env bash
# metrics-server, KEDA, kube-prometheus-stack.
set -euo pipefail

ADDONS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null
helm repo add kedacore https://kedacore.github.io/charts >/dev/null
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add jetstack https://charts.jetstack.io >/dev/null
helm repo update >/dev/null

# Versões fixadas (item 20 da revisão crítica): sem --version, cada execução puxa o chart
# mais novo do momento — como este script roda de novo a cada sessão do Learner Lab (o lab
# reseta entre sessões), a versão podia mudar de uma sessão pra outra sem nenhuma mudança de
# código. Atualizar aqui é uma decisão deliberada, não um efeito colateral de "helm repo update".
echo "==> cert-manager (TLS no Ingress — ver cert-manager-cluster-issuer.yaml)"
kubectl get namespace cert-manager >/dev/null 2>&1 || kubectl create namespace cert-manager
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --version v1.21.2 \
  --set crds.enabled=true \
  --wait --timeout 3m
kubectl apply -f "${ADDONS_DIR}/cert-manager-cluster-issuer.yaml"

echo "==> metrics-server"
helm upgrade --install metrics-server metrics-server/metrics-server \
  --namespace kube-system \
  --version 3.14.0 \
  --set args='{--kubelet-insecure-tls}' \
  --wait --timeout 3m

echo "==> KEDA"
kubectl get namespace keda >/dev/null 2>&1 || kubectl create namespace keda
helm upgrade --install keda kedacore/keda \
  --namespace keda \
  --version 2.20.2 \
  --wait --timeout 3m

echo "==> kube-prometheus-stack"
kubectl get namespace monitoring >/dev/null 2>&1 || kubectl create namespace monitoring
# ALERTMANAGER_WEBHOOK_URL (opcional): sem ela, o receiver "default" fica sem
# webhook_configs — os alertas existem (visíveis na UI do Alertmanager) mas não chegam a
# lugar nenhum, o gap do item 19 da revisão crítica. Defina a variável antes de rodar este
# script (ex.: um webhook de entrada do Slack) pra fechar essa lacuna. Comando duplicado (em
# vez de um array de flags condicional) pra não depender de expansão de array vazio sob
# "set -u", que quebra no bash 3.2 (o padrão no macOS).
if [ -n "${ALERTMANAGER_WEBHOOK_URL:-}" ]; then
  helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
    --namespace monitoring \
    --version 91.4.1 \
    --values "${ADDONS_DIR}/values/kube-prometheus-stack.yaml" \
    --set-string "alertmanager.config.receivers[0].name=default" \
    --set-string "alertmanager.config.receivers[0].webhook_configs[0].url=${ALERTMANAGER_WEBHOOK_URL}" \
    --wait --timeout 5m
else
  helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
    --namespace monitoring \
    --version 91.4.1 \
    --values "${ADDONS_DIR}/values/kube-prometheus-stack.yaml" \
    --wait --timeout 5m
fi

echo "==> dashboards Grafana + alerta de profundidade de fila"
kubectl apply -k "${ADDONS_DIR}"

echo "==> Add-ons prontos:"
kubectl get deployment -n kube-system metrics-server
kubectl get deployment -n keda
kubectl get pods -n monitoring
