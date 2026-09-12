# VPC + cluster EKS + node group + repositórios ECR na AWS (Learner Lab).
# Ver ADR-012 (docs/architecture/hld-lld-adr-rfc.md).
terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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

locals {
  account_id       = data.aws_caller_identity.current.account_id
  cluster_role_arn = "arn:aws:iam::${local.account_id}:role/${var.cluster_role_name}"
  node_role_arn    = "arn:aws:iam::${local.account_id}:role/${var.node_role_name}"
  ecr_registry     = "${local.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}
