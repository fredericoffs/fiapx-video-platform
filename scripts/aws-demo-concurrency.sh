#!/usr/bin/env bash
# Evidencia de processamento simultaneo + autoscaling do video-worker pelo KEDA.
# Faz upload de 2+ videos ao mesmo tempo pela URL publica e acompanha, a cada N segundos,
# as replicas do video-worker e o status de cada video — registra o pico de replicas, o
# pico de videos em PROCESSING ao mesmo tempo e a volta para minReplicaCount.
# Nao altera nada no cluster (so kubectl get); o unico efeito e o proprio upload.
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/aws-demo-concurrency.sh --email <e-mail> --password <senha> <video1> <video2> [video3...]

Opcoes:
  --host <host>          Host publico do ingress (padrao: descoberto via kubectl no
                         Service ingress-nginx-controller).
  --email <e-mail>       Usuario da demo. Se o login falhar com 401, cadastra antes.
  --password <senha>     Senha do usuario (min. 8 caracteres).
  --namespace <ns>       Namespace da aplicacao (padrao: fiapx).
  --interval <seg>       Intervalo entre amostras (padrao: 5).
  --timeout <seg>        Tempo maximo de acompanhamento (padrao: 1800).
  --log <arquivo>        Onde gravar a evidencia (padrao: demo-concurrency-<data>.log).
  -h, --help             Exibe esta ajuda.

Use videos de 1 min ou mais: o KEDA consulta a fila a cada 15s e o pod novo ainda
precisa subir, entao o segundo video comeca uns 30-60s depois do primeiro.

Codigos de saida:
  0  Houve pelo menos 2 videos em PROCESSING ao mesmo tempo.
  1  Nao houve sobreposicao (ou timeout antes dos videos terminarem).
  2  Erro operacional (dependencia ausente, login/upload falhou, etc.).
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 2; }
}

HOST=""
EMAIL=""
PASSWORD=""
NAMESPACE="fiapx"
INTERVAL=5
TIMEOUT=1800
LOG_FILE="demo-concurrency-$(date +%Y%m%d-%H%M%S).log"
FILES=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="${2:-}"; shift 2 ;;
    --email) EMAIL="${2:-}"; shift 2 ;;
    --password) PASSWORD="${2:-}"; shift 2 ;;
    --namespace) NAMESPACE="${2:-}"; shift 2 ;;
    --interval) INTERVAL="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT="${2:-}"; shift 2 ;;
    --log) LOG_FILE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "Opcao desconhecida: $1" >&2; usage >&2; exit 2 ;;
    *) FILES+=("$1"); shift ;;
  esac
done

require_cmd curl
require_cmd jq
require_cmd kubectl

[[ -n "$EMAIL" && -n "$PASSWORD" ]] || { echo "Erro: --email e --password sao obrigatorios" >&2; exit 2; }
(( ${#FILES[@]} >= 2 )) || { echo "Erro: informe pelo menos 2 videos" >&2; exit 2; }
for f in "${FILES[@]}"; do
  [[ -f "$f" ]] || { echo "Erro: arquivo nao encontrado: $f" >&2; exit 2; }
done

if [[ -z "$HOST" ]]; then
  HOST="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
  [[ -n "$HOST" ]] || { echo "Erro: nao achei o host do ingress; passe --host" >&2; exit 2; }
fi
BASE_URL="http://$HOST"

: >"$LOG_FILE"
log() { echo "$*" | tee -a "$LOG_FILE"; }

# --- login (cadastra o usuario se ainda nao existir) -------------------------------------
login() {
  curl -sS -X POST "$BASE_URL/auth/login" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" \
    -w '\n%{http_code}'
}

login_response="$(login)"
login_status="${login_response##*$'\n'}"
if [[ "$login_status" == "401" ]]; then
  log "Usuario $EMAIL nao existe (ou senha errada) — tentando cadastrar"
  register_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/register" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')")"
  [[ "$register_status" == "201" ]] || { echo "Erro: cadastro falhou (HTTP $register_status)" >&2; exit 2; }
  login_response="$(login)"
  login_status="${login_response##*$'\n'}"
fi
[[ "$login_status" == "200" ]] || { echo "Erro: login falhou (HTTP $login_status)" >&2; exit 2; }
TOKEN="$(jq -r '.accessToken' <<<"${login_response%$'\n'*}")"

# --- uploads em paralelo -----------------------------------------------------------------
log "== Demo de processamento simultaneo — $(date -u +%Y-%m-%dT%H:%M:%SZ) — $BASE_URL"
log "== ScaledObject: $(kubectl -n "$NAMESPACE" get scaledobject video-worker \
  -o jsonpath='min={.spec.minReplicaCount} max={.spec.maxReplicaCount} queueLength={.spec.triggers[0].metadata.queueLength}')"
log "== Replicas antes do upload: $(kubectl -n "$NAMESPACE" get deploy video-worker -o jsonpath='{.status.readyReplicas}')"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
pids=()
for i in "${!FILES[@]}"; do
  curl -sS -X POST "$BASE_URL/videos" -H "Authorization: Bearer $TOKEN" \
    -F "file=@${FILES[$i]}" -o "$tmp_dir/upload-$i.json" -w '%{http_code}' >"$tmp_dir/status-$i" &
  pids+=("$!")
done
for pid in "${pids[@]}"; do wait "$pid" || true; done

VIDEO_IDS=()
for i in "${!FILES[@]}"; do
  status="$(cat "$tmp_dir/status-$i")"
  [[ "$status" == "201" ]] || { echo "Erro: upload de ${FILES[$i]} falhou (HTTP $status)" >&2; exit 2; }
  id="$(jq -r '.id' "$tmp_dir/upload-$i.json")"
  VIDEO_IDS+=("$id")
  log "== Upload ok: ${FILES[$i]} -> $id"
done
ids_json="$(printf '%s\n' "${VIDEO_IDS[@]}" | jq -R . | jq -s .)"

# --- acompanhamento ----------------------------------------------------------------------
log ""
log "$(printf '%-8s %-9s %-7s %-10s %s' 'tempo' 'replicas' 'prontas' 'PROCESSING' 'status por video')"
start=$(date +%s)
peak_replicas=0
peak_processing=0
overlap_at=""
all_done_at=""
back_to_min_at=""
min_replicas="$(kubectl -n "$NAMESPACE" get scaledobject video-worker -o jsonpath='{.spec.minReplicaCount}')"

while :; do
  elapsed=$(( $(date +%s) - start ))
  replicas="$(kubectl -n "$NAMESPACE" get deploy video-worker -o jsonpath='{.spec.replicas}' 2>/dev/null || echo '?')"
  ready="$(kubectl -n "$NAMESPACE" get deploy video-worker -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)"
  ready="${ready:-0}"
  [[ "$replicas" =~ ^[0-9]+$ ]] || replicas=0
  statuses="$(curl -sS "$BASE_URL/videos?size=100" -H "Authorization: Bearer $TOKEN" \
    | jq -r --argjson ids "$ids_json" \
      '[.items[] | select(.id as $i | $ids | index($i))] | map(.status) | join(",")' 2>/dev/null || echo '?')"
  processing="$(tr ',' '\n' <<<"$statuses" | grep -c '^PROCESSING$' || true)"
  terminal="$(tr ',' '\n' <<<"$statuses" | grep -cE '^(COMPLETED|FAILED)$' || true)"

  (( replicas > peak_replicas )) && peak_replicas=$replicas
  (( processing > peak_processing )) && peak_processing=$processing
  [[ -z "$overlap_at" ]] && (( processing >= 2 )) && overlap_at="${elapsed}s"
  [[ -z "$all_done_at" ]] && (( terminal == ${#VIDEO_IDS[@]} )) && all_done_at="${elapsed}s"
  [[ -n "$all_done_at" && -z "$back_to_min_at" && "$replicas" == "$min_replicas" ]] && back_to_min_at="${elapsed}s"

  log "$(printf '%-8s %-9s %-7s %-10s %s' "${elapsed}s" "$replicas" "$ready" "$processing" "$statuses")"

  [[ -n "$back_to_min_at" ]] && break
  if (( elapsed >= TIMEOUT )); then
    log "== Timeout de ${TIMEOUT}s atingido"
    break
  fi
  sleep "$INTERVAL"
done

log ""
log "== Pods do video-worker no fim:"
kubectl -n "$NAMESPACE" get pods -l app.kubernetes.io/name=video-worker -o wide 2>&1 | tee -a "$LOG_FILE"
log ""
log "== Resumo"
log "   pico de replicas do video-worker:        $peak_replicas"
log "   pico de videos em PROCESSING juntos:     $peak_processing (primeira sobreposicao em ${overlap_at:-nunca})"
log "   todos os videos terminaram em:           ${all_done_at:-nao terminaram}"
log "   voltou para $min_replicas replica(s) em:            ${back_to_min_at:-nao observado}"
log "   evidencia gravada em: $LOG_FILE"

(( peak_processing >= 2 )) || exit 1
