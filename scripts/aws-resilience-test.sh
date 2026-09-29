#!/usr/bin/env bash
# Teste de resiliencia contra o ambiente implantado. Tres cenarios, pela URL publica:
#   burst  — N uploads simultaneos: separa aceitos (201) de rejeitados pelo rate limit do
#            gateway (429), confere que so os aceitos foram persistidos e que todos terminam.
#   worker — mata a forca os pods do video-worker com um video em processamento e exige
#            que ele termine COMPLETED depois da reentrega do SQS (heartbeat de 120s,
#            lease de 90s no S3).
#   sqs    — nega SendMessage/ReceiveMessage na fila de processamento por uma queue policy
#            temporaria (sem reiniciar nada), faz uploads durante a falha, confere que
#            ficam QUEUED (persistidos pela outbox) e que terminam depois de restaurar.
# Criterio: nenhum upload aceito some ou fica pendente; todo FAILED tem errorMessage.
# Altera o ambiente de proposito (mata pods, muda a policy da fila) — um trap restaura a
# policy original mesmo se o script abortar. Rodar fora do horario de uso/demo.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

usage() {
  cat <<'EOF'
Uso:
  scripts/aws-resilience-test.sh [opcoes]

Opcoes:
  --host <host>            Host publico do ingress (padrao: descoberto via kubectl).
  --namespace <ns>         Namespace da aplicacao (padrao: fiapx).
  --region <regiao>        Regiao AWS (padrao: us-east-1).
  --scenarios <lista>      Cenarios separados por virgula (padrao: burst,worker,sqs).
  --burst <n>              Uploads simultaneos no cenario burst (padrao: 25; a cota do
                           gateway e 20 POST /videos por minuto por IP).
  --long-video <arquivo>   Video longo do cenario worker (padrao: gerado com ffmpeg,
                           600s 854x480).
  --timeout <seg>          Espera maxima por cenario (padrao: 900).
  --log <arquivo>          Onde gravar a evidencia (padrao: resilience-<data>.log).
  -h, --help               Exibe esta ajuda.

Codigos de saida:
  0  Todos os cenarios passaram.
  1  Algum cenario falhou (o resumo diz qual e por que).
  2  Erro operacional (dependencia ausente, host nao encontrado, login falhou).
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 2; }
}

HOST=""
NAMESPACE="fiapx"
AWS_REGION="${AWS_REGION:-us-east-1}"
SCENARIOS="burst,worker,sqs"
BURST=25
LONG_VIDEO=""
TIMEOUT=900
LOG_FILE="resilience-$(date +%Y%m%d-%H%M%S).log"
SAMPLE="$ROOT_DIR/web/e2e/fixtures/sample.mp4"
PROCESSING_QUEUE="fiapx-video-processing"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="${2:-}"; shift 2 ;;
    --namespace) NAMESPACE="${2:-}"; shift 2 ;;
    --region) AWS_REGION="${2:-}"; shift 2 ;;
    --scenarios) SCENARIOS="${2:-}"; shift 2 ;;
    --burst) BURST="${2:-}"; shift 2 ;;
    --long-video) LONG_VIDEO="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT="${2:-}"; shift 2 ;;
    --log) LOG_FILE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao desconhecida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

has_scenario() { [[ ",$SCENARIOS," == *",$1,"* ]]; }

require_cmd curl
require_cmd jq
require_cmd kubectl
has_scenario sqs && require_cmd aws
has_scenario worker && [[ -z "$LONG_VIDEO" ]] && require_cmd ffmpeg
[[ -f "$SAMPLE" ]] || { echo "Erro: fixture nao encontrada: $SAMPLE" >&2; exit 2; }

if [[ -z "$HOST" ]]; then
  HOST="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
  # Sem load balancer (INGRESS_EXPOSE=nodeport): a URL pública, com porta, está no CORS.
  [[ -n "$HOST" ]] || HOST="$(kubectl -n "$NAMESPACE" get configmap fiapx-config \
    -o jsonpath='{.data.GATEWAY_CORS_ALLOWED_ORIGINS}' 2>/dev/null | sed 's#^https://##')"
  [[ -n "$HOST" ]] || { echo "Erro: nao achei o host do ingress; passe --host" >&2; exit 2; }
fi
# Certificado autoassinado (k8s/apps/base/certificate.yaml): -k e esperado aqui.
BASE_URL="https://$HOST"
CURL=(curl -sS -k)

tmp_dir="$(mktemp -d)"
: >"$LOG_FILE"
log() { echo "$*" | tee -a "$LOG_FILE"; }
now() { date +%s; }

# --- restauracao da policy da fila (cenario sqs) ----------------------------------------
QUEUE_URL=""
ORIGINAL_POLICY=""
POLICY_CHANGED="false"
restore_policy() {
  [[ "$POLICY_CHANGED" == "true" ]] || return 0
  aws sqs set-queue-attributes --region "$AWS_REGION" --queue-url "$QUEUE_URL" \
    --attributes "$(jq -n --arg p "$ORIGINAL_POLICY" '{Policy:$p}')" \
    && POLICY_CHANGED="false" \
    && log "   policy original da fila restaurada"
}
cleanup() {
  restore_policy || echo "ATENCAO: nao consegui restaurar a policy de $QUEUE_URL — restaure a mao" >&2
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

# --- sessao -------------------------------------------------------------------------------
stamp="$(date +%s)-$RANDOM"
if [[ "${NOTIFICATION_FROM:-}" == *@* ]]; then
  EMAIL="${NOTIFICATION_FROM%%@*}+resilience-${stamp}@${NOTIFICATION_FROM#*@}"
else
  EMAIL="resilience-${stamp}@example.com"
fi
PASSWORD="res-$(openssl rand -hex 12)"
credentials="$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')"

log "== Teste de resiliencia — $(date -u +%Y-%m-%dT%H:%M:%SZ) — $BASE_URL"
log "== Cenarios: $SCENARIOS — usuario $EMAIL"
status="$("${CURL[@]}" -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/register" \
  -H 'Content-Type: application/json' -d "$credentials")"
[[ "$status" == "201" ]] || { echo "Erro: cadastro retornou HTTP $status" >&2; exit 2; }
TOKEN="$("${CURL[@]}" -X POST "$BASE_URL/auth/login" -H 'Content-Type: application/json' \
  -d "$credentials" | jq -r '.accessToken // empty')"
[[ -n "$TOKEN" ]] || { echo "Erro: login falhou" >&2; exit 2; }
AUTH=(-H "Authorization: Bearer $TOKEN")

# Um upload; imprime "<http_code> <id|->". Nao trata 429 — o cenario burst precisa ve-lo.
upload_once() {
  local file="$1" out code
  out="$(mktemp "$tmp_dir/upload.XXXXXX")"
  code="$("${CURL[@]}" -o "$out" -w '%{http_code}' -X POST "$BASE_URL/videos" "${AUTH[@]}" -F "file=@$file" || echo 000)"
  echo "$code $(jq -r '.id // "-"' "$out" 2>/dev/null || echo -)"
}

# Upload que respeita o rate limit (usado fora do burst): espera o Retry-After e tenta de novo.
upload_retrying() {
  local file="$1" result code
  for _ in 1 2 3 4 5; do
    result="$(upload_once "$file")"
    code="${result%% *}"
    if [[ "$code" == "201" ]]; then
      echo "${result#* }"
      return 0
    fi
    # Log no stderr: esta funcao e chamada dentro de $(...) e o stdout e o id.
    [[ "$code" == "429" ]] || { log "   upload falhou com HTTP $code" >&2; return 1; }
    log "   429 do gateway (cota do minuto usada pelo burst) — aguardando 60s" >&2
    sleep 60
  done
  return 1
}

# Status atual de todos os videos do usuario: linhas "<id> <status> <temErro:0|1>".
# Uma chamada so (GET /videos) — o polling fica bem abaixo da cota de 20 GET/min.
snapshot() {
  "${CURL[@]}" "$BASE_URL/videos?size=100" "${AUTH[@]}" \
    | jq -r '.items[] | "\(.id) \(.status) \(if (.errorMessage // "") == "" then 0 else 1 end)"'
}

# Espera todos os ids (arquivo, um por linha) sairem de QUEUED/PROCESSING.
# Devolve 0 se todos terminaram; grava o ultimo snapshot em $tmp_dir/last.
wait_terminal() {
  local ids_file="$1" label="$2" deadline pending
  deadline=$(( $(now) + TIMEOUT ))
  while :; do
    snapshot >"$tmp_dir/last" || true
    pending="$(grep -Ff "$ids_file" "$tmp_dir/last" | grep -cE ' (QUEUED|PROCESSING) ' || true)"
    log "   [$label] pendentes: $pending/$(wc -l <"$ids_file" | tr -d ' ')"
    (( pending == 0 )) && return 0
    (( $(now) >= deadline )) && return 1
    sleep 10
  done
}

# Conta, entre os ids, quantos estao em cada status. Imprime "completed failed failed_sem_msg ausentes".
tally() {
  local ids_file="$1" completed failed failed_no_msg missing
  completed="$(grep -Ff "$ids_file" "$tmp_dir/last" | grep -c ' COMPLETED ' || true)"
  failed="$(grep -Ff "$ids_file" "$tmp_dir/last" | grep -c ' FAILED ' || true)"
  failed_no_msg="$(grep -Ff "$ids_file" "$tmp_dir/last" | grep -c ' FAILED 0$' || true)"
  missing=$(( $(wc -l <"$ids_file") - $(grep -cFf "$ids_file" "$tmp_dir/last" || true) ))
  echo "$completed $failed $failed_no_msg $missing"
}

RESULTS=()
record() { RESULTS+=("$1"); log "$1"; }

# --- cenario 1: pico de uploads simultaneos ------------------------------------------------
scenario_burst() {
  log ""
  local i pids=() accepted=0 rejected=0 others=0 persisted completed failed failed_no_msg missing capacity
  capacity="$(kubectl -n "$NAMESPACE" get configmap fiapx-config -o jsonpath='{.data.GATEWAY_RATE_LIMIT_CAPACITY}' 2>/dev/null || true)"
  [[ "$capacity" =~ ^[0-9]+$ ]] || capacity=20
  log "== [burst] $BURST uploads simultaneos (cota do gateway: $capacity POST /videos por minuto por IP)"
  for (( i = 0; i < BURST; i++ )); do
    upload_once "$SAMPLE" >"$tmp_dir/burst-$i" &
    pids+=("$!")
  done
  for pid in "${pids[@]}"; do wait "$pid" || true; done
  : >"$tmp_dir/burst-ids"
  for (( i = 0; i < BURST; i++ )); do
    read -r code id <"$tmp_dir/burst-$i"
    case "$code" in
      201) accepted=$((accepted + 1)); echo "$id" >>"$tmp_dir/burst-ids" ;;
      429) rejected=$((rejected + 1)) ;;
      *) others=$((others + 1)); log "   resposta inesperada: HTTP $code" ;;
    esac
  done
  log "   aceitos (201): $accepted · rejeitados pelo rate limit (429): $rejected · outros: $others"

  persisted="$("${CURL[@]}" "$BASE_URL/videos?size=100" "${AUTH[@]}" | jq -r '.totalElements')"
  log "   videos persistidos para o usuario: $persisted"

  if (( accepted == 0 )); then
    record "FALHOU burst: nenhum upload aceito"; return 1
  fi
  if (( others > 0 )); then
    record "FALHOU burst: $others resposta(s) fora de 201/429"; return 1
  fi
  # Todos os POST saem do mesmo IP no mesmo segundo: acima da cota, o gateway tem que recusar.
  # Nenhum 429 aqui significa que o rate limit nao ve o IP do cliente (ex.: conta por no).
  if (( BURST > capacity && accepted > capacity )); then
    record "FALHOU burst: $accepted aceitos com cota de $capacity por IP — o rate limit nao ve o IP do cliente?"; return 1
  fi
  if [[ "$persisted" != "$accepted" ]]; then
    record "FALHOU burst: $persisted persistidos, mas $accepted aceitos (um 429 virou video ou um 201 sumiu)"; return 1
  fi
  if ! wait_terminal "$tmp_dir/burst-ids" burst; then
    record "FALHOU burst: aceitos ainda pendentes apos ${TIMEOUT}s"; return 1
  fi
  read -r completed failed failed_no_msg missing <<<"$(tally "$tmp_dir/burst-ids")"
  if (( missing > 0 || failed_no_msg > 0 )); then
    record "FALHOU burst: $missing sumiram, $failed_no_msg FAILED sem errorMessage"; return 1
  fi
  record "OK burst: $accepted aceitos -> $completed COMPLETED, $failed FAILED rastreaveis; $rejected rejeitados (429) sem persistir"
}

# Pods do video-worker como "nome pronto(true|false)", um por linha.
worker_pods() {
  kubectl -n "$NAMESPACE" get pods -l app.kubernetes.io/name=video-worker \
    -o jsonpath='{range .items[*]}{.metadata.name} {.status.containerStatuses[0].ready}{"\n"}{end}'
}

# --- cenario 2: worker derrubado no meio do processamento ----------------------------------
scenario_worker() {
  log ""
  log "== [worker] crash do video-worker durante o processamento"
  local video="$LONG_VIDEO" id deadline killed_at old_pods pod still_old new_ready finisher completed _failed _failed_no_msg _missing
  if [[ -z "$video" ]]; then
    video="$tmp_dir/longo.mp4"
    log "   gerando video de teste (600s, 854x480)"
    ffmpeg -v error -f lavfi -i testsrc2=duration=600:size=854x480:rate=24 -pix_fmt yuv420p "$video"
  fi
  id="$(upload_retrying "$video")" || { record "FALHOU worker: upload nao aceito"; return 1; }
  echo "$id" >"$tmp_dir/worker-ids"
  log "   upload aceito: $id — aguardando ESTE video entrar em PROCESSING"

  # PROCESSING vem do evento "started" do proprio worker: prova que o video do teste (nao
  # qualquer mensagem da fila) esta sendo processado no momento do crash.
  deadline=$(( $(now) + 300 ))
  until snapshot >"$tmp_dir/last" 2>/dev/null && grep -q "^$id PROCESSING " "$tmp_dir/last"; do
    if grep -qE "^$id (COMPLETED|FAILED) " "$tmp_dir/last" 2>/dev/null; then
      record "INCONCLUSIVO worker: o video terminou antes do crash — use um --long-video maior"; return 1
    fi
    (( $(now) >= deadline )) && { record "FALHOU worker: video $id nao entrou em PROCESSING em 300s"; return 1; }
    sleep 5
  done

  old_pods="$(worker_pods | cut -d' ' -f1)"
  [[ -n "$old_pods" ]] || { record "FALHOU worker: nenhum pod do video-worker encontrado"; return 1; }
  log "   video em PROCESSING; matando a forca: $(echo "$old_pods" | tr '\n' ' ')"
  # shellcheck disable=SC2086  # um nome de pod por palavra, de proposito
  if ! kubectl -n "$NAMESPACE" delete pod $old_pods --grace-period=0 --force >>"$LOG_FILE" 2>&1; then
    record "FALHOU worker: kubectl delete pod falhou (permissao/conexao?) — ver o log"; return 1
  fi
  killed_at="$(now)"
  # Checado logo apos o delete, antes de qualquer substituto subir: COMPLETED aqui so pode
  # ter vindo do worker antigo, entao o crash nao interrompeu nada.
  snapshot >"$tmp_dir/last" || true
  if grep -q "^$id COMPLETED " "$tmp_dir/last"; then
    record "INCONCLUSIVO worker: o video concluiu antes da interrupcao — use um --long-video maior"; return 1
  fi

  # Substituicao confirmada: nenhum pod antigo sobrou e ha pelo menos um pod novo pronto.
  deadline=$(( $(now) + 300 ))
  while :; do
    still_old=0
    new_ready=0
    while read -r pod ready; do
      [[ -n "$pod" ]] || continue
      if grep -qx "$pod" <<<"$old_pods"; then
        still_old=$((still_old + 1))
      elif [[ "$ready" == "true" ]]; then
        new_ready=$((new_ready + 1))
      fi
    done <<<"$(worker_pods)"
    (( still_old == 0 && new_ready >= 1 )) && break
    (( $(now) >= deadline )) && { record "FALHOU worker: pods nao foram substituidos em 300s (antigos: $still_old, novos prontos: $new_ready)"; return 1; }
    sleep 5
  done
  log "   pods substituidos em $(( $(now) - killed_at ))s: $(worker_pods | cut -d' ' -f1 | tr '\n' ' ')"

  log "   aguardando a reentrega (visibilidade <=120s, lease 90s) reprocessar o video"

  if ! wait_terminal "$tmp_dir/worker-ids" worker; then
    record "FALHOU worker: video $id pendente ${TIMEOUT}s apos o crash"; return 1
  fi
  read -r completed _failed _failed_no_msg _missing <<<"$(tally "$tmp_dir/worker-ids")"
  if (( completed != 1 )); then
    record "FALHOU worker: video terminou FAILED em vez de ser reprocessado (ver errorMessage em /videos/$id)"; return 1
  fi
  # Quem concluiu: os logs dos pods mortos somem com eles, entao uma linha de conclusao deste
  # video num pod que nao estava na lista antiga prova que foi o substituto.
  finisher="$(kubectl -n "$NAMESPACE" logs -l app.kubernetes.io/name=video-worker --prefix --tail=-1 \
    --max-log-requests=10 2>/dev/null | grep "$id" | grep -E 'processado(: zip em| \(zip existente)' \
    | sed -E 's|^\[pod/([^/]+)/.*|\1|' | while read -r pod; do
        grep -qx "$pod" <<<"$old_pods" || echo "$pod"
      done | head -1)"
  if [[ -z "$finisher" ]]; then
    record "INCONCLUSIVO worker: video COMPLETED, mas nenhum pod novo registrou a conclusao de $id"; return 1
  fi
  record "OK worker: $(echo "$old_pods" | wc -l | tr -d ' ') pod(s) derrubado(s) com o video em PROCESSING; o substituto $finisher concluiu $(( $(now) - killed_at ))s apos o crash"
}

# --- cenario 3: SQS indisponivel --------------------------------------------------------------
scenario_sqs() {
  log ""
  log "== [sqs] fila de processamento indisponivel (queue policy com Deny temporario)"
  local arn deny ids_file="$tmp_dir/sqs-ids" id still_queued completed failed failed_no_msg missing
  arn="$(aws sqs get-queue-attributes --region "$AWS_REGION" --queue-url "$QUEUE_URL" \
    --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)"
  ORIGINAL_POLICY="$(aws sqs get-queue-attributes --region "$AWS_REGION" --queue-url "$QUEUE_URL" \
    --attribute-names Policy --query 'Attributes.Policy' --output text)"
  [[ "$ORIGINAL_POLICY" == "None" ]] && ORIGINAL_POLICY=""
  # So SendMessage/ReceiveMessage: SetQueueAttributes continua liberado pra restaurar.
  deny="$(jq -nc --arg arn "$arn" '{Version:"2012-10-17",Statement:[{Sid:"ResilienceTestOutage",
    Effect:"Deny",Principal:"*",Action:["sqs:SendMessage","sqs:ReceiveMessage"],Resource:$arn}]}')"
  aws sqs set-queue-attributes --region "$AWS_REGION" --queue-url "$QUEUE_URL" \
    --attributes "$(jq -n --arg p "$deny" '{Policy:$p}')"
  POLICY_CHANGED="true"
  log "   Deny aplicado; aguardando 60s de propagacao da policy no SQS"
  sleep 60

  : >"$ids_file"
  for _ in 1 2 3; do
    id="$(upload_retrying "$SAMPLE")" || { record "FALHOU sqs: upload nao aceito durante a falha"; return 1; }
    echo "$id" >>"$ids_file"
  done
  log "   3 uploads aceitos durante a falha; aguardando 60s pra conferir que nao andaram"
  sleep 60
  snapshot >"$tmp_dir/last" || true
  still_queued="$(grep -Ff "$ids_file" "$tmp_dir/last" | grep -c ' QUEUED ' || true)"
  log "   em QUEUED durante a falha: $still_queued/3"
  log "   erros de publicacao da outbox no video-api: $(kubectl -n "$NAMESPACE" logs -l app.kubernetes.io/name=video-api \
    --since=3m --tail=-1 2>/dev/null | grep -c 'Falha ao publicar outbox event' || true)"
  if (( still_queued != 3 )); then
    record "FALHOU sqs: esperava 3 QUEUED durante a falha, havia $still_queued (a policy nao bloqueou?)"; return 1
  fi

  restore_policy
  log "   SQS restaurado; outbox deve publicar sozinha (propagacao de ate 60s)"
  if ! wait_terminal "$ids_file" sqs; then
    record "FALHOU sqs: uploads feitos durante a falha ainda pendentes ${TIMEOUT}s apos restaurar"; return 1
  fi
  read -r completed failed failed_no_msg missing <<<"$(tally "$ids_file")"
  if (( completed != 3 )); then
    record "FALHOU sqs: $completed/3 COMPLETED apos restaurar ($failed FAILED, $missing sumiram)"; return 1
  fi
  record "OK sqs: 3 uploads persistidos como QUEUED durante a falha e COMPLETED apos restaurar"
}

if has_scenario sqs; then
  QUEUE_URL="$(aws sqs get-queue-url --region "$AWS_REGION" --queue-name "$PROCESSING_QUEUE" \
    --query QueueUrl --output text)"
fi

failures=0
has_scenario burst && { scenario_burst || failures=$((failures + 1)); }
has_scenario worker && { scenario_worker || failures=$((failures + 1)); }
has_scenario sqs && { scenario_sqs || failures=$((failures + 1)); }

log ""
log "== Resumo"
for line in ${RESULTS[@]+"${RESULTS[@]}"}; do log "   $line"; done
log "   evidencia gravada em: $LOG_FILE"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## Teste de resiliência — $([[ $failures -eq 0 ]] && echo OK || echo "$failures cenário(s) falharam")"
    echo
    for line in ${RESULTS[@]+"${RESULTS[@]}"}; do echo "- $line"; done
  } >>"$GITHUB_STEP_SUMMARY"
fi
(( failures == 0 ))
