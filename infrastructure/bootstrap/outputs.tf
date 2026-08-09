output "state_bucket_name" {
  description = "Name of the Terraform state bucket."
  value       = aws_s3_bucket.terraform_state.id
}

output "state_bucket_arn" {
  description = "ARN of the Terraform state bucket."
  value       = aws_s3_bucket.terraform_state.arn
}

output "state_bucket_region" {
  description = "Region containing the Terraform state bucket."
  value       = aws_s3_bucket.terraform_state.region
}

output "terraform_apply_role_name" {
  description = "Name of the role used for Terraform apply operations."
  value       = aws_iam_role.terraform_apply.name
}

output "terraform_apply_role_arn" {
  description = "ARN of the role used for Terraform apply operations."
  value       = aws_iam_role.terraform_apply.arn
}

output "runtime_permissions_boundary_arn" {
  description = "Permissions boundary required on franchise runtime roles."
  value       = aws_iam_policy.runtime_boundary.arn
}

output "ci_permissions_boundary_arn" {
  description = "Permissions boundary required on franchise CI roles."
  value       = aws_iam_policy.ci_boundary.arn
}
