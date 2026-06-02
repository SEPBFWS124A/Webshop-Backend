variable "environment"     { type = string }
variable "vpc_id"          { type = string }
variable "public_subnets"  { type = list(string) }
variable "private_subnets" { type = list(string) }
variable "db_endpoint"     { type = string }
variable "app_image"       { type = string }
variable "rabbitmq_host"   { type = string }
variable "jwt_secret"      { type = string; sensitive = true }
variable "cors_origins"    { type = string }
