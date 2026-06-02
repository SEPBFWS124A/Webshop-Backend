output "app_url" {
  description = "Public URL of the deployed application load balancer"
  value       = module.app.alb_dns_name
}

output "db_endpoint" {
  description = "RDS PostgreSQL endpoint (internal)"
  value       = module.database.db_endpoint
  sensitive   = true
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}
