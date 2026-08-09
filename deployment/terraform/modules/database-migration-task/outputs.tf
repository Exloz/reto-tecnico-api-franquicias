output "task_definition_arn" {
  value = try(aws_ecs_task_definition.this[0].arn, null)
}

output "task_definition_family" {
  value = try(aws_ecs_task_definition.this[0].family, null)
}

output "container_name" {
  value = "migration"
}

output "launch_type" {
  value = "FARGATE"
}

output "platform_version" {
  value = "1.4.0"
}

output "private_subnet_ids" {
  value = var.private_subnet_ids
}

output "security_group_id" {
  value = var.security_group_id
}

output "run_task_network_configuration" {
  value = {
    subnets          = var.private_subnet_ids
    security_groups  = [var.security_group_id]
    assign_public_ip = "DISABLED"
  }
}

output "execution_role_arn" {
  value = aws_iam_role.execution.arn
}

output "task_role_arn" {
  value = aws_iam_role.task.arn
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.this.name
}
