variable "aws_region" {
  description = "Região AWS (Learner Lab só libera us-east-1 e us-west-2)"
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

# Learner Lab não permite criar IAM roles: o cluster e os nós assumem roles pré-criadas,
# cujos nomes carregam prefixo/sufixo aleatórios — por isso a busca é por regex.
variable "cluster_role_name_regex" {
  description = "Regex do nome da IAM role do control plane (Learner Lab: *-LabEksClusterRole-*)"
  type        = string
  default     = ".*LabEksClusterRole.*"
}

# LabRole, não *-LabEksNodeRole-*: a LabEksNodeRole só tem as 3 políticas básicas de
# worker (sem EC2/EBS) e o driver EBS CSI, sem IRSA, herda as permissões do nó.
variable "node_role_name_regex" {
  description = "Regex do nome da IAM role dos nós (Learner Lab: LabRole, que tem as políticas amplas do lab)"
  type        = string
  default     = "^LabRole$"
}

variable "cluster_role_arn" {
  description = "ARN explícito da role do control plane (opcional; ignora a busca por regex)"
  type        = string
  default     = ""
}

variable "node_role_arn" {
  description = "ARN explícito da role dos nós (opcional; ex.: arn:aws:iam::<conta>:role/LabRole se o EBS CSI falhar por permissão)"
  type        = string
  default     = ""
}

# Learner Lab: tipos até "large", máximo 9 instâncias / 32 vCPU simultâneas.
variable "node_instance_type" {
  description = "Tipo de instância dos nós"
  type        = string
  default     = "t3.large"
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
