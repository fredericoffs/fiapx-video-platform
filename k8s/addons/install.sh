#!/usr/bin/env bash
# metrics-server, KEDA, kube-prometheus-stack, Loki + Alloy (logs no Grafana).
set -euo pipefail

ADDONS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null
helm repo add kedacore https://kedacore.github.io/charts >/dev/null
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add jetstack https://charts.jetstack.io >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
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
# prometheus.operator.enabled só abre a porta de métricas do operator (valor da métrica de
# cada ScaledObject); o ServiceMonitor fica em servicemonitors.yaml, aplicado no fim deste
# script — o CRD ServiceMonitor só existe depois do kube-prometheus-stack.
helm upgrade --install keda kedacore/keda \
  --namespace keda \
  --version 2.20.2 \
  --set prometheus.operator.enabled=true \
  --wait --timeout 3m

echo "==> kube-prometheus-stack"
kubectl get namespace monitoring >/dev/null 2>&1 || kubectl create namespace monitoring
# Destinos dos alertas, montados num values temporário (gerado com jq para escapar senhas e
# URLs): webhook em ALERTMANAGER_WEBHOOK_URL e, com SMTP_HOST/SMTP_USER/SMTP_PASSWORD, e-mail
# para ALERTMANAGER_EMAIL_TO (padrão: plus-address "+alertas" de NOTIFICATION_FROM ou de
# SMTP_USER) pelo mesmo SMTP da aplicação. A lista de receivers é redefinida inteira aqui
# (o Helm substitui listas) e por isso repete o receiver "null" usado pela rota do Watchdog.
ALERTS_VALUES="$(mktemp)"
trap 'rm -f "$ALERTS_VALUES"' EXIT
SMTP_FROM="${NOTIFICATION_FROM:-${SMTP_USER:-}}"
EMAIL_TO="${ALERTMANAGER_EMAIL_TO:-}"
if [ -z "$EMAIL_TO" ] && [ -n "$SMTP_FROM" ]; then
  EMAIL_TO="${SMTP_FROM%%@*}+alertas@${SMTP_FROM#*@}"
fi
SEND_EMAIL="false"
if [ -n "${SMTP_HOST:-}" ] && [ "${SMTP_HOST}" != "smtp.invalid" ] && [ -n "${SMTP_USER:-}" ] \
  && [ -n "${SMTP_PASSWORD:-}" ] && [ -n "$EMAIL_TO" ]; then
  SEND_EMAIL="true"
fi
jq -n \
  --arg webhook "${ALERTMANAGER_WEBHOOK_URL:-}" \
  --arg sendEmail "$SEND_EMAIL" \
  --arg to "$EMAIL_TO" \
  --arg from "$SMTP_FROM" \
  --arg host "${SMTP_HOST:-}:${SMTP_PORT:-587}" \
  --arg user "${SMTP_USER:-}" \
  --arg pass "${SMTP_PASSWORD:-}" \
  '{alertmanager: {config: {
      global: (if $sendEmail == "true" then {resolve_timeout: "5m", smtp_smarthost: $host, smtp_from: $from,
               smtp_auth_username: $user, smtp_auth_password: $pass, smtp_require_tls: true}
               else {resolve_timeout: "5m"} end),
      receivers: [
        ({name: "default"}
          + (if $webhook != "" then {webhook_configs: [{url: $webhook, send_resolved: true}]} else {} end)
          + (if $sendEmail == "true" then {email_configs: [{to: $to, send_resolved: true}]} else {} end)),
        {name: "null"}
      ]}}}' > "$ALERTS_VALUES"
echo "alertas: webhook=$([ -n "${ALERTMANAGER_WEBHOOK_URL:-}" ] && echo sim || echo não), e-mail=$([ "$SEND_EMAIL" = true ] && echo "$EMAIL_TO" || echo não)"
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --version 91.4.1 \
  --values "${ADDONS_DIR}/values/kube-prometheus-stack.yaml" \
  --values "$ALERTS_VALUES" \
  --wait --timeout 5m

# Logs: o Grafana do kube-prometheus-stack já tem o datasource Loki (values/kube-prometheus-stack.yaml);
# o Loki guarda 72h num PVC gp3 e o Alloy coleta o stdout dos pods de fiapx e ingress-nginx.
echo "==> Loki (logs)"
helm upgrade --install loki grafana/loki \
  --namespace monitoring \
  --version 7.3.0 \
  --values "${ADDONS_DIR}/values/loki.yaml" \
  --wait --timeout 5m

echo "==> Alloy (coleta de logs dos pods)"
helm upgrade --install alloy grafana/alloy \
  --namespace monitoring \
  --version 1.13.0 \
  --values "${ADDONS_DIR}/values/alloy.yaml" \
  --wait --timeout 3m

echo "==> dashboards Grafana, ServiceMonitors do ingress/KEDA e alerta de profundidade de fila"
kubectl apply -k "${ADDONS_DIR}"

echo "==> Add-ons prontos:"
kubectl get deployment -n kube-system metrics-server
kubectl get deployment -n keda
kubectl get pods -n monitoring
