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

output "rds_endpoint" {
  description = "Host do PostgreSQL (DB_HOST)"
  value       = aws_db_instance.this.address
}

output "redis_endpoint" {
  description = "Host do Redis (REDIS_HOST)"
  value       = aws_elasticache_cluster.this.cache_nodes[0].address
}

output "sqs_queue_urls" {
  description = "URLs das filas principais, por chave lógica"
  value       = { for key, queue in aws_sqs_queue.main : key => queue.url }
}

output "ssm_parameter_prefix" {
  description = "Prefixo dos parâmetros SSM com os segredos da aplicação (db/username, db/password, jwt/secret, notification/webhook-url)"
  value       = local.ssm_prefix
}

output "s3_buckets" {
  description = "Buckets de vídeos (raw/processed) — criados por scripts/aws-buckets-init.sh"
  value       = local.video_buckets
}
