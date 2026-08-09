output "repository_names" {
  description = "ECR repository names keyed by component."
  value       = { for key, repository in aws_ecr_repository.this : key => repository.name }
}

output "repository_arns" {
  description = "ECR repository ARNs keyed by component."
  value       = { for key, repository in aws_ecr_repository.this : key => repository.arn }
}

output "repository_urls" {
  description = "ECR repository URLs keyed by component."
  value       = { for key, repository in aws_ecr_repository.this : key => repository.repository_url }
}

output "registry_id" {
  description = "AWS registry ID hosting the repositories."
  value       = one(distinct([for repository in aws_ecr_repository.this : repository.registry_id]))
}
