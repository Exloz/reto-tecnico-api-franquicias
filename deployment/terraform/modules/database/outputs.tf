output "database_instance_arn" {
  description = "ARN of the RDS PostgreSQL instance."
  value       = aws_db_instance.this.arn
}

output "database_instance_identifier" {
  description = "RDS PostgreSQL instance identifier."
  value       = aws_db_instance.this.identifier
}

output "database_endpoint" {
  description = "RDS endpoint including its port."
  value       = aws_db_instance.this.endpoint
}

output "database_address" {
  description = "RDS hostname."
  value       = aws_db_instance.this.address
}

output "database_port" {
  description = "RDS PostgreSQL port."
  value       = aws_db_instance.this.port
}

output "master_secret_arn" {
  description = "ARN of the RDS-managed master credential secret."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "migrator_secret_arn" {
  description = "ARN of the empty migrator credential secret."
  value       = aws_secretsmanager_secret.migrator.arn
}

output "application_secret_arn" {
  description = "ARN of the empty application credential secret."
  value       = aws_secretsmanager_secret.application.arn
}
