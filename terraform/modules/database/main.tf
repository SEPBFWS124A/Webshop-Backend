resource "aws_db_subnet_group" "main" {
  name       = "webshop-${var.environment}-db-subnet-group"
  subnet_ids = var.private_subnets

  tags = {
    Name        = "webshop-${var.environment}-db-subnet-group"
    Environment = var.environment
  }
}

resource "aws_security_group" "rds" {
  name        = "webshop-${var.environment}-rds-sg"
  description = "Allow PostgreSQL access from app tier only"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "webshop-${var.environment}-rds-sg"
    Environment = var.environment
  }
}

resource "aws_db_instance" "postgres" {
  identifier             = "webshop-${var.environment}-postgres"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.environment == "production" ? "db.t3.medium" : "db.t3.micro"
  allocated_storage      = var.environment == "production" ? 50 : 20
  max_allocated_storage  = var.environment == "production" ? 200 : 50
  storage_encrypted      = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # GDPR: automated backups kept for 7 days (production) / 1 day (staging)
  backup_retention_period = var.environment == "production" ? 7 : 1
  deletion_protection     = var.environment == "production"
  skip_final_snapshot     = var.environment != "production"

  tags = {
    Name        = "webshop-${var.environment}-postgres"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
