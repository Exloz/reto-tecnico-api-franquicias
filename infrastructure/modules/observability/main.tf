data "aws_region" "current" {}

locals {
  alarm_actions        = var.sns_topic_arn == null ? [] : [var.sns_topic_arn]
  load_balancer_suffix = var.alb_arn == null ? null : join("/", slice(split("/", var.alb_arn), 1, 4))
  target_group_suffix  = var.target_group_arn == null ? null : join("/", slice(split("/", var.target_group_arn), 1, 4))
}

resource "aws_cloudwatch_metric_alarm" "alb_unhealthy_hosts" {
  count = var.api_observability_enabled ? 1 : 0

  alarm_name          = "${var.name_prefix}-alb-unhealthy-hosts"
  alarm_description   = "At least one ALB target is unhealthy."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    LoadBalancer = local.load_balancer_suffix
    TargetGroup  = local.target_group_suffix
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  count = var.api_observability_enabled ? 1 : 0

  alarm_name          = "${var.name_prefix}-ecs-cpu-high"
  alarm_description   = "ECS service CPU utilization is high."
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = var.ecs_cpu_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.ecs_service_name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory" {
  count = var.api_observability_enabled ? 1 : 0

  alarm_name          = "${var.name_prefix}-ecs-memory-high"
  alarm_description   = "ECS service memory utilization is high."
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = var.ecs_memory_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.ecs_service_name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${var.name_prefix}-rds-cpu-high"
  alarm_description   = "RDS CPU utilization is high."
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = var.rds_cpu_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    DBInstanceIdentifier = var.database_instance_identifier
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${var.name_prefix}-rds-connections-high"
  alarm_description   = "RDS database connection count is high."
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = var.rds_connections_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    DBInstanceIdentifier = var.database_instance_identifier
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage" {
  alarm_name          = "${var.name_prefix}-rds-free-storage-low"
  alarm_description   = "RDS free storage is low."
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "LessThanOrEqualToThreshold"
  threshold           = var.rds_free_storage_threshold_bytes
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    DBInstanceIdentifier = var.database_instance_identifier
  }

  tags = var.tags
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "${var.name_prefix}-operations"
  dashboard_body = jsonencode({
    widgets = concat(
      [
        {
          type   = "metric"
          width  = 12
          height = 6
          properties = {
            title  = "RDS capacity and latency"
            region = data.aws_region.current.region
            stat   = "Average"
            period = 300
            metrics = [
              ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", var.database_instance_identifier],
              [".", "DatabaseConnections", ".", "."],
              [".", "ReadLatency", ".", "."],
              [".", "WriteLatency", ".", "."]
            ]
          }
        }
      ],
      var.api_observability_enabled ? [
        {
          type   = "metric"
          width  = 12
          height = 6
          properties = {
            title  = "ALB requests, errors, and target latency"
            region = data.aws_region.current.region
            stat   = "Average"
            period = 300
            metrics = [
              ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", local.load_balancer_suffix, { stat = "Sum" }],
              [".", "HTTPCode_Target_5XX_Count", ".", ".", { stat = "Sum" }],
              [".", "TargetResponseTime", ".", ".", "TargetGroup", local.target_group_suffix, { stat = "p95" }]
            ]
          }
        }
      ] : [],
      var.api_observability_enabled ? [
        {
          type   = "metric"
          width  = 12
          height = 6
          properties = {
            title  = "ECS service utilization"
            region = data.aws_region.current.region
            stat   = "Average"
            period = 300
            metrics = [
              ["AWS/ECS", "CPUUtilization", "ClusterName", var.ecs_cluster_name, "ServiceName", var.ecs_service_name],
              [".", "MemoryUtilization", ".", ".", ".", "."]
            ]
          }
        }
      ] : []
    )
  })
}
