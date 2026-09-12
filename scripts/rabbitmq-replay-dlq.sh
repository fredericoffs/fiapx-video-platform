#!/usr/bin/env bash
# Replay controlado de uma DLQ do RabbitMQ (perfil local/kind) via API de management:
# lê as mensagens da DLQ em lotes (ack, sem requeue) e republica cada uma na fila de
# origem, preservando corpo, correlation_id e headers. Uso:
#   scripts/rabbitmq-replay-dlq.sh video.status-updates.dlq video.status-updates [--limit N]
# Variáveis: RABBITMQ_MGMT_URL (padrão http://localhost:15672), RABBITMQ_USER/PASSWORD
# (padrão guest/guest), RABBITMQ_VHOST (padrão /).
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/rabbitmq-replay-dlq.sh <dlq> <fila-destino> [--limit N] [--dry-run]

Opcoes:
  --limit N    Reprocessa no maximo N mensagens (padrao: 50).
  --dry-run    So lista as mensagens (peek), nao consome nem republica.
EOF
}

command -v curl >/dev/null || { echo "Erro: curl nao encontrado" >&2; exit 1; }
command -v jq >/dev/null || { echo "Erro: jq nao encontrado" >&2; exit 1; }

DLQ="${1:-}"; TARGET="${2:-}"; shift 2 2>/dev/null || { usage; exit 1; }
LIMIT=50; DRY_RUN="false"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --limit) LIMIT="${2:-50}"; shift 2 ;;
    --dry-run) DRY_RUN="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 1 ;;
  esac
done
[[ -n "$DLQ" && -n "$TARGET" ]] || { usage; exit 1; }

MGMT="${RABBITMQ_MGMT_URL:-http://localhost:15672}"
AUTH="${RABBITMQ_USER:-guest}:${RABBITMQ_PASSWORD:-guest}"
VHOST="$(printf '%s' "${RABBITMQ_VHOST:-/}" | jq -sRr @uri)"

ACKMODE="ack_requeue_false"
[[ "$DRY_RUN" == "true" ]] && ACKMODE="ack_requeue_true"

messages="$(curl -sf -u "$AUTH" -H 'Content-Type: application/json' \
  -X POST "${MGMT}/api/queues/${VHOST}/${DLQ}/get" \
  -d "{\"count\":${LIMIT},\"ackmode\":\"${ACKMODE}\",\"encoding\":\"auto\"}")"

total="$(echo "$messages" | jq 'length')"
echo "DLQ ${DLQ}: ${total} mensagem(ns) lida(s) (limite ${LIMIT}, dry-run=${DRY_RUN})"
[[ "$total" -gt 0 ]] || exit 0

echo "$messages" | jq -c '.[]' | while read -r msg; do
  body="$(echo "$msg" | jq -r '.payload')"
  props="$(echo "$msg" | jq -c '.properties | del(.headers["x-death"], .headers["x-first-death-exchange"], .headers["x-first-death-queue"], .headers["x-first-death-reason"]) // {}')"
  corr="$(echo "$msg" | jq -r '.properties.correlation_id // empty')"
  if [[ "$DRY_RUN" == "true" ]]; then
    echo "  [peek] correlation_id=${corr:-<sem>} body=$(echo "$body" | cut -c1-120)"
    continue
  fi
  payload="$(jq -cn --arg rk "$TARGET" --arg body "$body" --argjson props "$props" \
    '{routing_key:$rk, payload:$body, payload_encoding:"string", properties:$props}')"
  result="$(curl -sf -u "$AUTH" -H 'Content-Type: application/json' \
    -X POST "${MGMT}/api/exchanges/${VHOST}/amq.default/publish" -d "$payload" | jq -r '.routed')"
  echo "  [replay] correlation_id=${corr:-<sem>} -> ${TARGET} routed=${result}"
  [[ "$result" == "true" ]] || { echo "Erro: mensagem nao roteada para ${TARGET}; interrompendo" >&2; exit 1; }
done
