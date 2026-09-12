# RDS PostgreSQL nas subnets privadas (sem rota pra internet), acessível só de dentro da VPC.
# Limites do Learner Lab: classes até "medium", armazenamento gp2 ≤ 100 GB, sem Enhanced
# Monitoring. Ambiente efêmero (reset do lab a cada sessão): sem snapshot final nem proteção.
resource "aws_security_group" "rds" {
  name        = "${var.project}-rds"
  description = "PostgreSQL a partir da VPC"
  vpc_id      = aws_vpc.this.id

  ingress {
    from_port   = 5432
    to_port     = 5432
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
    Name = "${var.project}-rds"
  }
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.project}-rds"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.project}-rds"
  }
}

resource "aws_db_instance" "this" {
  identifier             = "${var.project}-postgres"
  engine                 = "postgres"
  engine_version         = var.db_engine_version
  instance_class         = var.db_instance_class
  allocated_storage      = var.db_allocated_storage_gb
  storage_type           = "gp2"
  storage_encrypted      = true
  db_name                = var.db_name
  username               = var.db_username
  password               = local.db_password
  port                   = 5432
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  skip_final_snapshot          = true
  deletion_protection          = false
  backup_retention_period      = 0
  monitoring_interval          = 0
  performance_insights_enabled = false
  apply_immediately            = true
  auto_minor_version_upgrade   = true

  tags = {
    Name = "${var.project}-postgres"
  }
}
