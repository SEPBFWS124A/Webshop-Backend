variable "aws_region" {
  description = "AWS region to deploy into (e.g. eu-central-1 for GDPR compliance)"
  type        = string
  default     = "eu-central-1"
}

variable "environment" {
  description = "Deployment environment: staging or production"
  type        = string
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be 'staging' or 'production'."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "webshop"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "PostgreSQL master password"
  type        = string
  sensitive   = true
}

variable "app_image" {
  description = "Docker image for the Spring Boot backend (e.g. ghcr.io/org/webshop-backend:latest)"
  type        = string
}

variable "rabbitmq_host" {
  description = "RabbitMQ host (internal DNS or managed service endpoint)"
  type        = string
  default     = "rabbitmq"
}

variable "jwt_secret" {
  description = "JWT signing secret (min. 32 characters)"
  type        = string
  sensitive   = true
}

variable "cors_origins" {
  description = "Comma-separated list of allowed CORS origins"
  type        = string
  default     = "https://webshop.example.com"
}
