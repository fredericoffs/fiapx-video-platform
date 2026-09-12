output "cluster_name" {
  value = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}

output "cluster_role_arn" {
  value = local.cluster_role_arn
}

output "node_role_arn" {
  value = local.node_role_arn
}

output "vpc_id" {
  value = aws_vpc.this.id
}

output "ecr_registry" {
  description = "Host do registro ECR — prefixo das imagens fiapx/<serviço>"
  value       = local.ecr_registry
}

output "kubeconfig_command" {
  value = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.this.name}"
}
