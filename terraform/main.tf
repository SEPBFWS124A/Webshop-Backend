terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state – activate once S3 bucket and DynamoDB table exist
  # backend "s3" {
  #   bucket         = "webshop-terraform-state"
  #   key            = "prod/terraform.tfstate"
  #   region         = "eu-central-1"
  #   dynamodb_table = "webshop-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
}

module "vpc" {
  source = "./modules/vpc"

  environment = var.environment
  vpc_cidr    = var.vpc_cidr
}

module "database" {
  source = "./modules/database"

  environment     = var.environment
  vpc_id          = module.vpc.vpc_id
  private_subnets = module.vpc.private_subnet_ids
  db_name         = var.db_name
  db_username     = var.db_username
  db_password     = var.db_password
}

module "app" {
  source = "./modules/app"

  environment       = var.environment
  vpc_id            = module.vpc.vpc_id
  public_subnets    = module.vpc.public_subnet_ids
  private_subnets   = module.vpc.private_subnet_ids
  db_endpoint       = module.database.db_endpoint
  app_image         = var.app_image
  rabbitmq_host     = var.rabbitmq_host
  jwt_secret        = var.jwt_secret
  cors_origins      = var.cors_origins
}
