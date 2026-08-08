output "oidc_provider_arn" {
  value = local.oidc_provider_arn
}

output "plan_role_arn" {
  value = aws_iam_role.plan.arn
}

output "apply_role_arns" {
  value = { for environment, role in aws_iam_role.apply : environment => role.arn }
}

output "deploy_role_arns" {
  value = { for environment, role in aws_iam_role.deploy : environment => role.arn }
}
