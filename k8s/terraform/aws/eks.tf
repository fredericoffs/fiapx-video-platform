# O EKS só faz upgrade in-place de uma minor por vez. Como o cluster é efêmero
# (sobe pra validar/gravar e é destruído em seguida), uma mudança de versão recria
# cluster, node group e add-ons de uma vez, em vez de encadear upgrades.
resource "terraform_data" "cluster_version" {
  input = var.kubernetes_version
}

resource "aws_eks_cluster" "this" {
  name     = var.cluster_name
  role_arn = local.cluster_role_arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids              = concat(aws_subnet.public[*].id, aws_subnet.private[*].id)
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  tags = {
    Name = var.cluster_name
  }

  lifecycle {
    replace_triggered_by = [terraform_data.cluster_version]
  }

  depends_on = [aws_route_table_association.public]
}

# Nós em subnet pública (sem NAT Gateway) — precisam de IP público pra puxar imagens
# do ECR e charts Helm.
resource "aws_eks_node_group" "this" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.cluster_name}-ng"
  node_role_arn   = local.node_role_arn
  version         = aws_eks_cluster.this.version
  subnet_ids      = aws_subnet.public[*].id
  instance_types  = [var.node_instance_type]
  ami_type        = "AL2023_x86_64_STANDARD"
  disk_size       = var.node_disk_size_gb

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = var.node_min_size
    max_size     = var.node_max_size
  }

  update_config {
    max_unavailable = 1
  }

  tags = {
    Name = "${var.cluster_name}-ng"
  }

  lifecycle {
    replace_triggered_by = [terraform_data.cluster_version]
  }
}

resource "aws_eks_addon" "vpc_cni" {
  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = "vpc-cni"
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"

  lifecycle {
    replace_triggered_by = [terraform_data.cluster_version]
  }
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = "kube-proxy"
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"

  lifecycle {
    replace_triggered_by = [terraform_data.cluster_version]
  }
}

resource "aws_eks_addon" "coredns" {
  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = "coredns"
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"

  lifecycle {
    replace_triggered_by = [terraform_data.cluster_version]
  }

  depends_on = [aws_eks_node_group.this]
}

# Obrigatório pros PersistentVolumeClaims de k8s/infra (Postgres, Redis, RabbitMQ,
# MinIO): em EKS >= 1.23 o provisionador in-tree de EBS não existe mais. Sem IRSA
# (Learner Lab não permite criar roles), o driver herda as permissões do node role.
resource "aws_eks_addon" "ebs_csi" {
  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = "aws-ebs-csi-driver"
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"

  lifecycle {
    replace_triggered_by = [terraform_data.cluster_version]
  }

  depends_on = [aws_eks_node_group.this]
}
