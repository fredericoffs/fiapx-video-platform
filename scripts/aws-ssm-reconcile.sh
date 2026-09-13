#!/usr/bin/env bash
# Importa pro state do Terraform os parametros SSM de secrets.tf que ja existem na conta
# mas nao estao no state (o Learner Lab as vezes reseta so parte dos recursos entre
# sessoes — mesmo motivo do bucket de state em aws-tf-init.sh, so que aqui o recurso e
# gerenciado pelo Terraform normalmente, nao por um script a parte, porque o valor
# publicado no SSM precisa ser sempre o mesmo que o Terraform usou pra criar o RDS
# (aws_db_instance.this.password = local.db_password) — nao da pra gerar um valor
# independente fora do Terraform como foi feito pros buckets de video. Sem isso, o
# "aws_ssm_parameter" da erro ParameterAlreadyExists no primeiro apply da sessao. So
# importa quando o parametro existe na AWS e ainda nao esta no state; se o valor
# importado for diferente do que o Terraform calculou nesta sessao (random_password
# gerado de novo porque o state anterior tambem se perdeu), o proprio apply seguinte
# corrige com um update — sem conflito.
set -euo pipefail

TF_DIR="${1:?uso: aws-ssm-reconcile.sh <diretorio-terraform>}"
PROJECT="${PROJECT:-fiapx}"

command -v aws >/dev/null 2>&1 || { echo "Erro: aws CLI nao encontrado" >&2; exit 1; }
command -v terraform >/dev/null 2>&1 || { echo "Erro: terraform nao encontrado" >&2; exit 1; }

reconcile() {
  local address="$1" name="$2"
  if [[ -n "$(terraform -chdir="$TF_DIR" state list "$address" 2>/dev/null)" ]]; then
    return # ja esta no state, nada a fazer
  fi
  if aws ssm get-parameter --region "$AWS_REGION" --name "$name" >/dev/null 2>&1; then
    echo "parametro ${name} ja existe na AWS e nao esta no state — importando para ${address}"
    terraform -chdir="$TF_DIR" import "$address" "$name"
  fi
}

AWS_REGION="${AWS_REGION:-us-east-1}"

reconcile "aws_ssm_parameter.db_username" "/${PROJECT}/db/username"
reconcile "aws_ssm_parameter.db_password" "/${PROJECT}/db/password"
reconcile "aws_ssm_parameter.jwt_secret" "/${PROJECT}/jwt/secret"
# So existe no desenho (count) quando a variavel vem preenchida — mesma condicao de secrets.tf.
if [[ -n "${TF_VAR_notification_webhook_url:-}" ]]; then
  reconcile "aws_ssm_parameter.notification_webhook_url[0]" "/${PROJECT}/notification/webhook-url"
fi
