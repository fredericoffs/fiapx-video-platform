#!/usr/bin/env bash
# Grava as credenciais AWS (chave do usuario IAM) no GitHub Environment "AWS" em um
# comando. Fontes, na ordem em que sao tentadas:
#   1. env vars AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN
#   2. --from-stdin: um bloco "[default] aws_access_key_id=..." (ex.: o CSV/credentials
#      da chave gerada no console IAM; pbpaste | este script)
#   3. --profile <nome>: le do ~/.aws/credentials via "aws configure get"
# Com --save-profile, o bloco lido de stdin tambem e gravado em ~/.aws/credentials,
# deixando o aws CLI local pronto pra usar (aws sts get-caller-identity).
set -euo pipefail

ENVIRONMENT="${GH_ENVIRONMENT:-AWS}"
REPO="${GH_REPO:-}"
PROFILE=""
FROM_STDIN="false"
SAVE_PROFILE="false"
AWS_REGION_DEFAULT="us-east-1"

usage() {
  cat <<'EOF'
Uso:
  # (a) colar o bloco de credenciais direto da area de transferencia (macOS):
  pbpaste | scripts/aws-sync-gh-secrets.sh --from-stdin --save-profile

  # (b) a partir de um perfil ja gravado em ~/.aws/credentials:
  scripts/aws-sync-gh-secrets.sh --profile default

  # (c) a partir de env vars ja exportadas:
  export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=...
  scripts/aws-sync-gh-secrets.sh

Opcoes:
  --from-stdin           Le o bloco aws_access_key_id=... de stdin.
  --save-profile         Com --from-stdin: grava tambem em ~/.aws/credentials (perfil default),
                         mas so depois de validar as credenciais.
  --profile <nome>       Le as credenciais desse perfil do aws CLI.
  --repo owner/nome      Repositorio (padrao: o do diretorio atual).
  --environment <nome>   GitHub Environment (padrao: AWS).
  -h, --help             Exibe esta ajuda.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from-stdin) FROM_STDIN="true"; shift ;;
    --save-profile) SAVE_PROFILE="true"; shift ;;
    --profile) PROFILE="${2:-}"; shift 2 ;;
    --repo) REPO="${2:-}"; shift 2 ;;
    --environment) ENVIRONMENT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 1 ;;
  esac
done

command -v gh >/dev/null 2>&1 || { echo "Erro: gh CLI nao encontrado (brew install gh)" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Erro: rode 'gh auth login' antes" >&2; exit 1; }

# Extrai "chave=valor" (ou "chave = valor") de um bloco no formato do ~/.aws/credentials.
ini_get() {
  local key="$1"
  sed -n -E "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//p" <<< "$2" | head -1 | tr -d '\r[:space:]'
}

if [[ "$FROM_STDIN" == "true" ]]; then
  BLOCK="$(cat)"
  # Aceita o formato do ~/.aws/credentials (aws_access_key_id=...) e o de env vars
  # (AWS_ACCESS_KEY_ID=... / export AWS_ACCESS_KEY_ID=...).
  AWS_ACCESS_KEY_ID="$(ini_get aws_access_key_id "$BLOCK")"
  [[ -n "$AWS_ACCESS_KEY_ID" ]] || AWS_ACCESS_KEY_ID="$(ini_get '(export[[:space:]]+)?AWS_ACCESS_KEY_ID' "$BLOCK")"
  AWS_SECRET_ACCESS_KEY="$(ini_get aws_secret_access_key "$BLOCK")"
  [[ -n "$AWS_SECRET_ACCESS_KEY" ]] || AWS_SECRET_ACCESS_KEY="$(ini_get '(export[[:space:]]+)?AWS_SECRET_ACCESS_KEY' "$BLOCK")"
  AWS_SESSION_TOKEN="$(ini_get aws_session_token "$BLOCK")"
  [[ -n "$AWS_SESSION_TOKEN" ]] || AWS_SESSION_TOKEN="$(ini_get '(export[[:space:]]+)?AWS_SESSION_TOKEN' "$BLOCK")"
elif [[ -n "$PROFILE" ]]; then
  command -v aws >/dev/null 2>&1 || { echo "Erro: aws CLI nao encontrado" >&2; exit 1; }
  AWS_ACCESS_KEY_ID="$(aws configure get aws_access_key_id --profile "$PROFILE" || true)"
  AWS_SECRET_ACCESS_KEY="$(aws configure get aws_secret_access_key --profile "$PROFILE" || true)"
  AWS_SESSION_TOKEN="$(aws configure get aws_session_token --profile "$PROFILE" || true)"
fi

# AWS_SESSION_TOKEN so existe em credenciais temporarias (Learner Lab, STS); a chave de um
# usuario IAM nao tem, entao e opcional e o workflow nao o usa.
for var in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
  [[ -n "${!var:-}" ]] || { echo "Erro: $var vazia (ver --help pras 3 formas de informar)" >&2; exit 1; }
done
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
if [[ -n "${AWS_SESSION_TOKEN:-}" ]]; then export AWS_SESSION_TOKEN; else unset AWS_SESSION_TOKEN; fi

if command -v aws >/dev/null 2>&1; then
  if ACCOUNT="$(aws sts get-caller-identity --query Account --output text 2>/dev/null)"; then
    echo "credenciais validas — conta AWS ${ACCOUNT}"
  else
    echo "Erro: credenciais invalidas ou expiradas (chave desativada ou digitada errada?)" >&2
    exit 1
  fi
fi

# So grava o perfil local depois de validar: com credenciais vazias ou invalidas isso
# sobrescreveria um perfil que ja funciona.
if [[ "$SAVE_PROFILE" == "true" ]]; then
  command -v aws >/dev/null 2>&1 || { echo "Erro: aws CLI nao encontrado pra --save-profile" >&2; exit 1; }
  aws configure set aws_access_key_id "$AWS_ACCESS_KEY_ID"
  aws configure set aws_secret_access_key "$AWS_SECRET_ACCESS_KEY"
  # Sem token (chave IAM), limpa o que sobrou de uma sessao temporaria anterior: chave nova
  # + token velho no mesmo perfil faz toda chamada falhar com InvalidClientTokenId.
  aws configure set aws_session_token "${AWS_SESSION_TOKEN:-}"
  aws configure set region "$AWS_REGION_DEFAULT"
  echo "perfil default gravado em ~/.aws/credentials (regiao ${AWS_REGION_DEFAULT})"
fi

repo_args=()
[[ -n "$REPO" ]] && repo_args=(--repo "$REPO")

for var in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
  gh secret set "$var" --env "$ENVIRONMENT" "${repo_args[@]}" --body "${!var}"
  echo "secret $var atualizado no environment $ENVIRONMENT"
done

# Os workflows nao leem mais AWS_SESSION_TOKEN; um valor velho do lab so confunde.
if [[ -z "${AWS_SESSION_TOKEN:-}" ]]; then
  if gh secret delete AWS_SESSION_TOKEN --env "$ENVIRONMENT" "${repo_args[@]}" 2>/dev/null; then
    echo "secret AWS_SESSION_TOKEN (obsoleto) removido do environment $ENVIRONMENT"
  fi
fi
