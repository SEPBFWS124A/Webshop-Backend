resource "aws_security_group" "alb" {
  name        = "webshop-${var.environment}-alb-sg"
  description = "Allow HTTP/HTTPS inbound to ALB"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "webshop-${var.environment}-alb-sg", Environment = var.environment }
}

resource "aws_lb" "main" {
  name               = "webshop-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnets

  tags = { Name = "webshop-${var.environment}-alb", Environment = var.environment, ManagedBy = "terraform" }
}

resource "aws_lb_target_group" "backend" {
  name        = "webshop-${var.environment}-backend-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/api/health"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_ecs_cluster" "main" {
  name = "webshop-${var.environment}"
  tags = { Environment = var.environment, ManagedBy = "terraform" }
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "webshop-${var.environment}-backend"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.environment == "production" ? "1024" : "512"
  memory                   = var.environment == "production" ? "2048" : "1024"

  container_definitions = jsonencode([
    {
      name  = "backend"
      image = var.app_image

      portMappings = [{ containerPort = 8080, protocol = "tcp" }]

      environment = [
        { name = "SPRING_DATASOURCE_URL",      value = "jdbc:postgresql://${var.db_endpoint}/webshop" },
        { name = "SPRING_RABBITMQ_HOST",        value = var.rabbitmq_host },
        { name = "APP_CORS_ALLOWED_ORIGINS",    value = var.cors_origins },
      ]

      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "/webshop/${var.environment}/db_password" },
        { name = "APP_JWT_SECRET",              valueFrom = "/webshop/${var.environment}/jwt_secret" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/webshop-${var.environment}-backend"
          "awslogs-region"        = "eu-central-1"
          "awslogs-stream-prefix" = "backend"
        }
      }
    }
  ])
}
