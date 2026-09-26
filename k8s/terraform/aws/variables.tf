variable "aws_region" {
  description = "Região AWS"
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefixo de nome/tag de todos os recursos (usado também pela varredura do destroy)"
  type        = string
  default     = "fiapx"
}

variable "cluster_name" {
  description = "Nome do cluster EKS"
  type        = string
  default     = "fiapx"
}

# Manter numa versão em STANDARD_SUPPORT (aws eks describe-cluster-versions):
# suporte estendido custa 6x mais por hora de control plane.
variable "kubernetes_version" {
  description = "Versão do Kubernetes do cluster (mudar recria o cluster, não faz upgrade in-place)"
  type        = string
  default     = "1.36"
}

variable "node_instance_type" {
  # m7i-flex.large: mesmo vCPU/RAM do t3.large (2 vCPU / 8 GiB), mas elegível para o Free
  # Tier desta conta — contas AWS novas ficam temporariamente restritas a tipos fora do
  # Free Tier (ex.: t3.large falha com "InvalidParameterCombination ... not eligible for
  # Free Tier" ao tentar subir o node group).
  description = "Tipo de instância dos nós"
  type        = string
  default     = "m7i-flex.large"
}

variable "node_desired_size" {
  description = "Quantidade inicial de nós"
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Mínimo de nós"
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Máximo de nós"
  type        = number
  default     = 3
}

variable "node_disk_size_gb" {
  description = "Disco (GiB) de cada nó"
  type        = number
  default     = 30
}

variable "vpc_cidr" {
  description = "CIDR da VPC"
  type        = string
  default     = "10.30.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDRs das subnets públicas (nós + Load Balancer), uma por AZ"
  type        = list(string)
  default     = ["10.30.0.0/20", "10.30.16.0/20"]
}

variable "private_subnet_cidrs" {
  description = "CIDRs das subnets privadas (control plane), uma por AZ"
  type        = list(string)
  default     = ["10.30.32.0/20", "10.30.48.0/20"]
}

# ---- Serviços gerenciados (RDS, ElastiCache, SQS, S3) ----
variable "db_password" {
  description = "Senha do usuário master do RDS. Vazia → gerada (random_password) e publicada no SSM; preenchida (TF_VAR_db_password = PROD_DB_PASSWORD) → usada como está. Nunca versionar."
  type        = string
  sensitive   = true
  default     = ""
}

variable "jwt_secret" {
  description = "Segredo HS256 dos JWTs. Vazio → gerado e publicado no SSM; preenchido (TF_VAR_jwt_secret = PROD_JWT_SECRET) → usado como está."
  type        = string
  sensitive   = true
  default     = ""
}

variable "admin_password" {
  description = "Senha do admin semeado (admin@fiapx.local). Vazia → gerada (random_password) e publicada no SSM; preenchida (TF_VAR_admin_password = PROD_ADMIN_PASSWORD) → usada como está. O hash no banco (migration V8) é só um placeholder inutilizável — a senha real só passa a valer depois que AdminPasswordSeeder aplicar este valor no primeiro boot do video-api."
  type        = string
  sensitive   = true
  default     = ""
}

variable "notification_webhook_url" {
  description = "URL do canal webhook da notificação (opcional; TF_VAR_notification_webhook_url = PROD_NOTIFICATION_WEBHOOK_URL). Vazia → parâmetro não é criado."
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_username" {
  description = "Usuário master do RDS — o mesmo DB_USER dos serviços"
  type        = string
  default     = "fiapx"
}

variable "db_name" {
  description = "Banco único; cada serviço usa o próprio schema (video_api, notification_worker)"
  type        = string
  default     = "fiapx"
}

variable "db_engine_version" {
  description = "Versão major do PostgreSQL no RDS"
  type        = string
  default     = "17"
}

# Learner Lab: só nano/micro/small/medium.
variable "db_instance_class" {
  description = "Classe da instância RDS"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage_gb" {
  description = "Armazenamento do RDS em GB (gp2, máximo 100 no Learner Lab)"
  type        = number
  default     = 20
}

variable "redis_node_type" {
  description = "Tipo do nó ElastiCache"
  type        = string
  default     = "cache.t3.micro"
}

variable "redis_engine_version" {
  description = "Versão do Redis no ElastiCache"
  type        = string
  default     = "7.1"
}

variable "redis_parameter_group" {
  description = "Parameter group do ElastiCache (família redis7)"
  type        = string
  default     = "default.redis7"
}

# Deve cobrir FFMPEG_TIMEOUT_MINUTES (15 min) — o consumer ainda estende por heartbeat.
variable "sqs_processing_visibility_timeout_seconds" {
  description = "Visibility timeout da fila de processamento"
  type        = number
  default     = 960
}

variable "sqs_max_receive_count" {
  description = "Recebimentos antes de mover para a DLQ (redrive)"
  type        = number
  default     = 3
}

variable "sqs_message_retention_seconds" {
  description = "Retenção das mensagens (filas e DLQs)"
  type        = number
  default     = 345600
}
