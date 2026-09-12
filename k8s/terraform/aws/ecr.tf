# Um repositório por imagem (fiapx/<serviço>), mesmo nome usado por cd-aws.yml e
# scripts/k8s-deploy-aws.sh.
locals {
  services = ["video-gateway", "video-api", "video-worker", "notification-worker", "web"]
}

resource "aws_ecr_repository" "service" {
  for_each = toset(local.services)

  name                 = "${var.project}/${each.key}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.project}/${each.key}"
  }
}

# Mantém só as 10 imagens mais recentes por repositório.
resource "aws_ecr_lifecycle_policy" "service" {
  for_each = aws_ecr_repository.service

  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
