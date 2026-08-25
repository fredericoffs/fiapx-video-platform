#!/usr/bin/env bash
# Dispara N uploads em paralelo contra o video-gateway pra validar o autoscaling do
# KEDA (video-worker). Requer ffmpeg local (gera o vídeo-fixture em /tmp).
set -euo pipefail

N="${1:-20}"
GATEWAY="http://localhost:8080"
FIXTURE="/tmp/fiapx-loadtest-fixture.mp4"
EMAIL="loadtest@fiapx.local"
PASSWORD="loadtest-password-123"

command -v ffmpeg >/dev/null || { echo "ffmpeg não encontrado no PATH"; exit 1; }

if [ ! -f "$FIXTURE" ]; then
  echo "==> gerando vídeo-fixture sintético (2s, 320x240)"
  ffmpeg -loglevel error -f lavfi -i "testsrc=duration=2:size=320x240:rate=5" -y "$FIXTURE"
fi

echo "==> autenticando usuário de carga (${EMAIL})"
curl -s -o /dev/null -X POST "${GATEWAY}/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}" || true

TOKEN="$(curl -s -X POST "${GATEWAY}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}" \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)"

[ -n "$TOKEN" ] || { echo "falha ao autenticar usuário de carga"; exit 1; }

echo "==> disparando ${N} uploads em paralelo"
for i in $(seq 1 "$N"); do
  curl -s -o /dev/null -w "upload ${i}: %{http_code}\n" \
    -X POST "${GATEWAY}/videos" \
    -H "Authorization: Bearer ${TOKEN}" \
    -F "file=@${FIXTURE};type=video/mp4" &
done
wait

echo "==> uploads disparados. Acompanhe o autoscaling com:"
echo "    kubectl -n fiapx get scaledobject video-worker -w"
echo "    kubectl -n fiapx get pods -l app.kubernetes.io/name=video-worker -w"
