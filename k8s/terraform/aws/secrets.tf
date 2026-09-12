# Segredos da aplicação no SSM Parameter Store (/<project>/...). Gerados aqui quando as
# variáveis vêm vazias (random_password fica no state: um novo apply não os troca) ou
# recebidos por TF_VAR_* quando o GitHub já tem o secret. O deploy
# (scripts/k8s-deploy-aws.sh) lê os parâmetros por nome e monta o Secret do Kubernetes —
# o job de deploy não precisa mais dos PROD_* do GitHub.
resource "random_password" "db" {
  length  = 32
  special = false # RDS não aceita / @ " e espaço
}

resource "random_password" "jwt" {
  length  = 64 # ≥ 256 bits pro HS256 (a aplicação usa os bytes da string)
  special = false
}

locals {
  ssm_prefix  = "/${var.project}"
  db_password = var.db_password != "" ? var.db_password : random_password.db.result
  jwt_secret  = var.jwt_secret != "" ? var.jwt_secret : random_password.jwt.result
}

resource "aws_ssm_parameter" "db_username" {
  name  = "${local.ssm_prefix}/db/username"
  type  = "String"
  value = var.db_username
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.ssm_prefix}/db/password"
  type  = "SecureString"
  value = local.db_password
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "${local.ssm_prefix}/jwt/secret"
  type  = "SecureString"
  value = local.jwt_secret
}

# Opcional: só existe se a URL foi informada (canal webhook da notificação — não há SMTP na AWS).
resource "aws_ssm_parameter" "notification_webhook_url" {
  count = var.notification_webhook_url != "" ? 1 : 0
  name  = "${local.ssm_prefix}/notification/webhook-url"
  type  = "SecureString"
  value = var.notification_webhook_url
}
