# VPC + cluster EKS + node group + repositórios ECR na AWS (Learner Lab).
# Ver docs/architecture/adr/ADR-012-aws-eks.md.
terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Backend remoto: bucket S3 com lock nativo (use_lockfile). Bucket/região vêm de
  # -backend-config em runtime (scripts/aws-tf-init.sh), que também cria o bucket
  # se ainda não existir.
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

# As roles pré-criadas do Learner Lab têm prefixo/sufixo aleatórios no nome
# (ex.: c2215...-LabEksClusterRole-hLR76TS0IpSF), então são localizadas por regex
# em vez de nome fixo. Um ARN explícito (cluster_role_arn/node_role_arn) tem
# prioridade sobre a descoberta.
data "aws_iam_roles" "cluster" {
  name_regex = var.cluster_role_name_regex
}

data "aws_iam_roles" "node" {
  name_regex = var.node_role_name_regex
}

locals {
  account_id       = data.aws_caller_identity.current.account_id
  cluster_role_arn = var.cluster_role_arn != "" ? var.cluster_role_arn : sort(tolist(data.aws_iam_roles.cluster.arns))[0]
  node_role_arn    = var.node_role_arn != "" ? var.node_role_arn : sort(tolist(data.aws_iam_roles.node.arns))[0]
  ecr_registry     = "${local.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}
