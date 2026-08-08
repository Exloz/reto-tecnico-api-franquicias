output "alarm_arns" {
  value = merge(
    {
      rds_cpu          = aws_cloudwatch_metric_alarm.rds_cpu.arn
      rds_connections  = aws_cloudwatch_metric_alarm.rds_connections.arn
      rds_free_storage = aws_cloudwatch_metric_alarm.rds_free_storage.arn
    },
    var.api_observability_enabled ? {
      alb_unhealthy_hosts = aws_cloudwatch_metric_alarm.alb_unhealthy_hosts[0].arn
      ecs_cpu             = aws_cloudwatch_metric_alarm.ecs_cpu[0].arn
      ecs_memory          = aws_cloudwatch_metric_alarm.ecs_memory[0].arn
    }
    : {}
  )
}

output "notification_topic_arn" {
  value = var.sns_topic_arn
}

output "dashboard_name" {
  value = aws_cloudwatch_dashboard.this.dashboard_name
}
