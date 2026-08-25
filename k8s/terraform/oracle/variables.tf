variable "tenancy_ocid" {
  description = "OCID da tenancy OCI"
  type        = string
}

variable "user_ocid" {
  description = "OCID do usuário dono da chave de API"
  type        = string
}

variable "fingerprint" {
  description = "Fingerprint da chave de API"
  type        = string
}

variable "private_key_pem" {
  description = "Conteúdo PEM da chave privada de API (não o caminho do arquivo)"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "Região OCI onde o Always Free foi ativado (ex.: sa-saopaulo-1)"
  type        = string
}

variable "compartment_ocid" {
  description = "OCID do compartment onde criar o cluster"
  type        = string
}

variable "ssh_public_key" {
  description = "Chave pública SSH para acesso aos nós (opcional)"
  type        = string
  default     = ""
}

variable "cluster_name" {
  description = "Nome do cluster OKE"
  type        = string
  default     = "fiapx"
}

variable "kubernetes_version" {
  description = "Versão do Kubernetes do cluster"
  type        = string
  default     = "v1.31.1"
}

# Always Free: até 4 OCPU / 24GB no total entre todas as instâncias A1.Flex da tenancy.
variable "node_pool_size" {
  description = "Quantidade de nós no pool"
  type        = number
  default     = 1
}

variable "node_pool_ocpus" {
  description = "OCPUs por nó (A1.Flex) — node_pool_size × isto não pode passar de 4 no Always Free"
  type        = number
  default     = 4
}

variable "node_pool_memory_gb" {
  description = "Memória (GB) por nó (A1.Flex) — node_pool_size × isto não pode passar de 24 no Always Free"
  type        = number
  default     = 24
}
