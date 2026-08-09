output "cluster_arn" {
  value = aws_ecs_cluster.this.arn
}

output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "service_arn" {
  value = try(aws_ecs_service.this[0].id, null)
}

output "service_name" {
  value = try(aws_ecs_service.this[0].name, null)
}

output "task_definition_arn" {
  value = try(aws_ecs_task_definition.this[0].arn, null)
}

output "alb_arn" {
  value = try(aws_lb.this[0].arn, null)
}

output "alb_dns_name" {
  value = try(aws_lb.this[0].dns_name, null)
}

output "alb_listener_arn" {
  value = try(aws_lb_listener.http[0].arn, null)
}

output "target_group_arn" {
  value = try(aws_lb_target_group.this[0].arn, null)
}

output "api_security_group_id" {
  value = var.api_security_group_id
}

output "alb_security_group_id" {
  value = var.alb_security_group_id
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
