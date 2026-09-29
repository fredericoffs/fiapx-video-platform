#!/usr/bin/env bash
# Teste de ponta a ponta contra o ambiente implantado (URL publica do ingress), sem nada
# simulado: cadastro -> upload real -> ffmpeg no video-worker -> COMPLETED -> download ->
# abre o zip e confere os frames; depois um arquivo que nao e video -> FAILED -> notificacao
# de falha confirmada no log do notification-worker e, via IMAP, na caixa de entrada do
# destinatario. Roda no fim do job deploy do cd-aws.yml e tambem localmente (kubectl
# apontando pro cluster).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

usage() {
  cat <<'EOF'
Uso:
  scripts/aws-e2e-smoke.sh [opcoes]

Opcoes:
  --host <host>          Host publico do ingress (padrao: descoberto via kubectl no
                         Service ingress-nginx-controller).
  --namespace <ns>       Namespace da aplicacao (padrao: fiapx).
  --video <arquivo>      Video valido do teste (padrao: web/e2e/fixtures/sample.mp4).
  --timeout <seg>        Espera maxima por cada etapa assincrona (padrao: 300).
  -h, --help             Exibe esta ajuda.

O usuario do teste e criado a cada execucao. O e-mail dele (destinatario da notificacao de
falha) e um plus-address de NOTIFICATION_FROM (ex.: conta+e2e-<ts>@gmail.com), para a
mensagem cair na propria caixa da conta remetente; sem NOTIFICATION_FROM, usa @example.com.

Com SMTP_USER e SMTP_PASSWORD no ambiente (a mesma App Password do Gmail), a etapa final
entra na INBOX dessa conta por IMAP (IMAP_HOST; padrao: SMTP_HOST com smtp. -> imap.) e
exige a mensagem com o videoId — prova de chegada, nao so de envio. Sem elas, essa etapa e
pulada com aviso.

Codigos de saida:
  0  Todas as etapas passaram.
  1  Alguma etapa falhou (a mensagem diz qual).
  2  Erro operacional (dependencia ausente, host nao encontrado).
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Erro: comando obrigatorio nao encontrado: $1" >&2; exit 2; }
}

HOST=""
NAMESPACE="fiapx"
VIDEO="$ROOT_DIR/web/e2e/fixtures/sample.mp4"
TIMEOUT=300

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="${2:-}"; shift 2 ;;
    --namespace) NAMESPACE="${2:-}"; shift 2 ;;
    --video) VIDEO="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao desconhecida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_cmd curl
require_cmd jq
require_cmd kubectl
require_cmd unzip
[[ -f "$VIDEO" ]] || { echo "Erro: video nao encontrado: $VIDEO" >&2; exit 2; }

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
trap 'rm -rf "$tmp_dir"' EXIT

step() { echo "==> $*"; }
fail() { echo "FALHOU: $*" >&2; exit 1; }

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
stamp="$(date +%s)-$RANDOM"
if [[ "${NOTIFICATION_FROM:-}" == *@* ]]; then
  EMAIL="${NOTIFICATION_FROM%%@*}+e2e-${stamp}@${NOTIFICATION_FROM#*@}"
else
  EMAIL="e2e-${stamp}@example.com"
fi
PASSWORD="e2e-$(openssl rand -hex 12)"
credentials="$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')"

step "[1/7] cadastro e login de $EMAIL em $BASE_URL"
status="$("${CURL[@]}" -o "$tmp_dir/register.json" -w '%{http_code}' -X POST "$BASE_URL/auth/register" \
  -H 'Content-Type: application/json' -d "$credentials")"
[[ "$status" == "201" ]] || fail "cadastro retornou HTTP $status: $(cat "$tmp_dir/register.json")"
status="$("${CURL[@]}" -o "$tmp_dir/login.json" -w '%{http_code}' -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' -d "$credentials")"
[[ "$status" == "200" ]] || fail "login retornou HTTP $status"
TOKEN="$(jq -r '.accessToken' "$tmp_dir/login.json")"
AUTH=(-H "Authorization: Bearer $TOKEN")

upload() {
  local file="$1" out="$2" code
  code="$("${CURL[@]}" -o "$out" -w '%{http_code}' -X POST "$BASE_URL/videos" "${AUTH[@]}" -F "file=@$file")"
  [[ "$code" == "201" ]] || fail "upload de $(basename "$file") retornou HTTP $code: $(cat "$out")"
  jq -r '.id' "$out"
}

# Espera o video sair de QUEUED/PROCESSING; imprime o JSON final.
wait_terminal() {
  local id="$1" deadline=$(( $(date +%s) + TIMEOUT )) body st
  while (( $(date +%s) < deadline )); do
    body="$("${CURL[@]}" "$BASE_URL/videos/$id" "${AUTH[@]}")"
    st="$(jq -r '.status' <<<"$body")"
    if [[ "$st" == "COMPLETED" || "$st" == "FAILED" ]]; then
      echo "$body"
      return 0
    fi
    sleep 5
  done
  fail "video $id nao terminou em ${TIMEOUT}s (ultimo status: ${st:-desconhecido})"
}

step "[2/7] upload do video valido ($(basename "$VIDEO"))"
VALID_ID="$(upload "$VIDEO" "$tmp_dir/upload-valid.json")"
echo "    id=$VALID_ID"

step "[3/7] aguardando o processamento real (ffmpeg no video-worker)"
final="$(wait_terminal "$VALID_ID")"
[[ "$(jq -r '.status' <<<"$final")" == "COMPLETED" ]] \
  || fail "video valido terminou como $(jq -r '.status' <<<"$final"): $(jq -r '.errorMessage' <<<"$final")"
echo "    COMPLETED"

step "[4/7] download e conferencia do zip"
zip_file="$tmp_dir/frames.zip"
status="$("${CURL[@]}" -o "$zip_file" -w '%{http_code}' "$BASE_URL/videos/$VALID_ID/download" "${AUTH[@]}")"
[[ "$status" == "200" ]] || fail "download retornou HTTP $status"
unzip -tq "$zip_file" >/dev/null || fail "zip corrompido"
frames="$(unzip -Z1 "$zip_file" | grep -cE '(^|/)frame_[0-9]{4}\.png$' || true)"
(( frames >= 1 )) || fail "zip sem nenhum frame_NNNN.png: $(unzip -Z1 "$zip_file" | head -5 | tr '\n' ' ')"
first_frame="$(unzip -Z1 "$zip_file" | grep -E '(^|/)frame_[0-9]{4}\.png$' | head -1)"
magic="$(unzip -p "$zip_file" "$first_frame" | head -c 8 | od -An -tx1 | tr -d ' \n')"
[[ "$magic" == "89504e470d0a1a0a" ]] || fail "$first_frame nao e um PNG valido (assinatura $magic)"
echo "    $frames frame(s) PNG validos no zip ($(wc -c <"$zip_file" | tr -d ' ') bytes)"

step "[5/7] upload de um arquivo que nao e video (extensao .mp4)"
invalid_file="$tmp_dir/nao-e-video.mp4"
echo "isto nao e um video — teste e2e $stamp" >"$invalid_file"
INVALID_ID="$(upload "$invalid_file" "$tmp_dir/upload-invalid.json")"
echo "    id=$INVALID_ID"
final="$(wait_terminal "$INVALID_ID")"
[[ "$(jq -r '.status' <<<"$final")" == "FAILED" ]] \
  || fail "arquivo invalido terminou como $(jq -r '.status' <<<"$final"), esperado FAILED"
error_message="$(jq -r '.errorMessage // empty' <<<"$final")"
[[ -n "$error_message" ]] || fail "video FAILED sem errorMessage"
echo "    FAILED: $error_message"

step "[6/7] notificacao de falha pelo canal EMAIL no log do notification-worker"
# Espera especificamente o EMAIL: com uma falha temporaria de SMTP, o webhook (alerta
# operacional) sai antes e a reentrega do SQS entrega o e-mail depois — isso e recuperacao
# correta, nao falha.
deadline=$(( $(date +%s) + TIMEOUT ))
notified=""
webhook_seen=""
while (( $(date +%s) < deadline )); do
  notification_logs="$(kubectl -n "$NAMESPACE" logs -l app.kubernetes.io/name=notification-worker \
    --since-time="$started_at" --tail=-1 --max-log-requests=10 2>/dev/null | grep "$INVALID_ID" || true)"
  grep -q 'enviada pelo canal WEBHOOK' <<<"$notification_logs" && webhook_seen="sim"
  if grep -q 'enviada pelo canal EMAIL' <<<"$notification_logs"; then
    notified="enviada pelo canal EMAIL"
    break
  fi
  sleep 5
done
if [[ -z "$notified" ]]; then
  if [[ -n "$webhook_seen" ]]; then
    fail "so o alerta por webhook saiu para o video $INVALID_ID; o e-mail ao usuario nao foi entregue em ${TIMEOUT}s"
  fi
  fail "nenhuma notificacao por e-mail para o video $INVALID_ID em ${TIMEOUT}s (ver logs do notification-worker)"
fi
echo "    notificacao $notified (destinatario $EMAIL)${webhook_seen:+ — antes, alerta por webhook: o e-mail se recuperou numa reentrega}"

step "[7/7] chegada do e-mail na caixa do destinatario (IMAP)"
delivery="nao verificada (sem SMTP_USER/SMTP_PASSWORD)"
if [[ -n "${SMTP_USER:-}" && -n "${SMTP_PASSWORD:-}" ]]; then
  require_cmd python3
  imap_host="${IMAP_HOST:-${SMTP_HOST:-smtp.gmail.com}}"
  imap_host="${imap_host/#smtp./imap.}"
  deadline=$(( $(date +%s) + TIMEOUT ))
  found=""
  while (( $(date +%s) < deadline )); do
    # So a INBOX: a copia em Enviados provaria o envio, nao a chegada.
    if found="$(IMAP_PASSWORD="$SMTP_PASSWORD" python3 - "$imap_host" "$SMTP_USER" "$EMAIL" "$INVALID_ID" <<'PY'
import email, imaplib, os, sys
host, user, to, video_id = sys.argv[1:]
try:
    box = imaplib.IMAP4_SSL(host)
    box.login(user, os.environ["IMAP_PASSWORD"])
except (OSError, imaplib.IMAP4.error) as e:
    print("IMAP %s: %s" % (host, e), file=sys.stderr)
    sys.exit(2)
box.select("INBOX", readonly=True)
_, data = box.search(None, "TO", '"%s"' % to)
for num in data[0].split():
    _, parts = box.fetch(num, "(RFC822)")
    msg = email.message_from_bytes(parts[0][1])
    for part in msg.walk():
        payload = part.get_payload(decode=True)
        if payload and video_id.encode() in payload:
            print("%s | %s" % (msg.get("Date"), msg.get("Subject")))
            sys.exit(0)
sys.exit(1)
PY
    )"; then
      break
    else
      rc=$?
      (( rc == 2 )) && fail "nao consegui entrar na caixa $SMTP_USER por IMAP em $imap_host (App Password?)"
    fi
    sleep 10
  done
  [[ -n "$found" ]] || fail "e-mail para $EMAIL com o video $INVALID_ID nao chegou na INBOX de $SMTP_USER em ${TIMEOUT}s"
  delivery="chegou na INBOX ($found)"
  echo "    $delivery"
else
  echo "    aviso: SMTP_USER/SMTP_PASSWORD ausentes — chegada do e-mail nao verificada" >&2
fi

echo
echo "OK: fluxo completo validado contra $BASE_URL"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## Teste E2E contra o ambiente implantado: OK"
    echo
    echo "- Upload real → \`COMPLETED\` → zip com **$frames** frame(s) PNG válidos"
    echo "- Arquivo inválido → \`FAILED\` (\`$error_message\`) → notificação $notified"
    echo "- E-mail para \`$EMAIL\`: $delivery"
  } >>"$GITHUB_STEP_SUMMARY"
fi
