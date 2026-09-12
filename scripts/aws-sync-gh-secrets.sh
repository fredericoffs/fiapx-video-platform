#!/usr/bin/env bash
# Renova as credenciais temporarias do Learner Lab no GitHub Environment "AWS" em um
# comando (elas expiram a cada sessao do lab). Alternativa ao cadastro manual em
# Settings > Environments > AWS. Le AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY /
# AWS_SESSION_TOKEN do ambiente (copiados de Learner Lab > AWS Details).
set -euo pipefail

ENVIRONMENT="${GH_ENVIRONMENT:-AWS}"
REPO="${GH_REPO:-}"

usage() {
  cat <<'EOF'
Uso:
  export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_SESSION_TOKEN=...
  scripts/aws-sync-gh-secrets.sh [--repo owner/nome] [--environment AWS]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="${2:-}"; shift 2 ;;
    --environment) ENVIRONMENT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage; exit 1 ;;
  esac
done

command -v gh >/dev/null 2>&1 || { echo "Erro: gh CLI nao encontrado" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Erro: rode 'gh auth login' antes" >&2; exit 1; }

for var in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN; do
  [[ -n "${!var:-}" ]] || { echo "Erro: $var nao definida" >&2; exit 1; }
done

repo_args=()
[[ -n "$REPO" ]] && repo_args=(--repo "$REPO")

for var in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN; do
  gh secret set "$var" --env "$ENVIRONMENT" "${repo_args[@]}" --body "${!var}"
  echo "secret $var atualizado no environment $ENVIRONMENT"
done
