# ElastiCache (Redis) de 1 nó nas subnets privadas — só o rate limiting de borda usa. Sem TLS
# nem AUTH: mantém REDIS_HOST/REDIS_PORT como único ponto de configuração dos serviços.
resource "aws_security_group" "redis" {
  name        = "${var.project}-redis"
  description = "Redis a partir da VPC"
  vpc_id      = aws_vpc.this.id

  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project}-redis"
  }
}

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.project}-redis"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.project}-redis"
  }
}

resource "aws_elasticache_cluster" "this" {
  cluster_id           = "${var.project}-redis"
  engine               = "redis"
  engine_version       = var.redis_engine_version
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  port                 = 6379
  parameter_group_name = var.redis_parameter_group
  subnet_group_name    = aws_elasticache_subnet_group.this.name
  security_group_ids   = [aws_security_group.redis.id]
  apply_immediately    = true

  tags = {
    Name = "${var.project}-redis"
  }
}
