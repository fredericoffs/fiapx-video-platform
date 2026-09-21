# VPC + cluster EKS + node group + repositórios ECR na AWS.
# Ver ADR-012 (docs/architecture/hld-lld-adr-rfc.md).
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

locals {
  account_id = data.aws_caller_identity.current.account_id
  # Roles criadas em iam.tf (o eks.tf depende dos attachments antes de usá-las).
  cluster_role_arn = aws_iam_role.cluster.arn
  node_role_arn    = aws_iam_role.node.arn
  ecr_registry     = "${local.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}
