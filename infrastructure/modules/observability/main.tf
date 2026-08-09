data "aws_region" "current" {}

locals {
  alarm_actions                = var.sns_topic_arn == null ? [] : [var.sns_topic_arn]
  application_metric_namespace = "${var.name_prefix}/Application"
  load_balancer_suffix         = var.alb_arn == null ? null : join("/", slice(split("/", var.alb_arn), 1, 4))
  target_group_suffix          = var.target_group_arn == null ? null : split(":", var.target_group_arn)[5]
}

resource "aws_cloudwatch_log_metric_filter" "application_requests" {
  count = var.api_observability_enabled ? 1 : 0

  name           = "${var.name_prefix}-application-requests"
  log_group_name = var.application_log_group_name
  pattern        = "{ $.event = \"request.completed\" }"

  metric_transformation {
    name      = "RequestCount"
    namespace = local.application_metric_namespace
    value     = "1"
    dimensions = {
      Operation = "$.operation"
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "application_duration" {
  count = var.api_observability_enabled ? 1 : 0

  name           = "${var.name_prefix}-application-duration"
  log_group_name = var.application_log_group_name
  pattern        = "{ $.event = \"request.completed\" && $.durationMs = * }"

  metric_transformation {
    name      = "RequestDuration"
    namespace = local.application_metric_namespace
    value     = "$.durationMs"
    unit      = "Milliseconds"
    dimensions = {
      Operation = "$.operation"
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "application_errors" {
  count = var.api_observability_enabled ? 1 : 0

  name           = "${var.name_prefix}-application-errors"
  log_group_name = var.application_log_group_name
  pattern        = "{ $.event = \"request.completed\" && $.outcome = \"server_error\" }"

  metric_transformation {
    name          = "ServerErrorCount"
    namespace     = local.application_metric_namespace
    value         = "1"
    default_value = 0
  }
}

resource "aws_cloudwatch_log_metric_filter" "flyway_errors" {
  name           = "${var.name_prefix}-flyway-errors"
  log_group_name = var.migration_log_group_name
  pattern        = "?ERROR ?Exception ?\"Migration failed\" ?\"Unable to\""

  metric_transformation {
    name          = "FlywayFailureCount"
    namespace     = local.application_metric_namespace
    value         = "1"
    default_value = 0
  }
}

resource "aws_cloudwatch_metric_alarm" "flyway_errors" {
  alarm_name          = "${var.name_prefix}-flyway-failure"
  alarm_description   = "Flyway emitted an error while running a database migration."
  namespace           = local.application_metric_namespace
  metric_name         = "FlywayFailureCount"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "alb_healthy_hosts" {
  count = var.api_observability_enabled ? 1 : 0

  alarm_name          = "${var.name_prefix}-alb-healthy-hosts-low"
  alarm_description   = "ALB healthy target count is below the ECS minimum capacity."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "LessThanThreshold"
  threshold           = var.minimum_task_count
  treat_missing_data  = "breaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    LoadBalancer = local.load_balancer_suffix
    TargetGroup  = local.target_group_suffix
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "api_gateway_5xx" {
  count = var.api_observability_enabled ? 1 : 0

  alarm_name          = "${var.name_prefix}-api-gateway-5xx"
  alarm_description   = "API Gateway returned server errors."
  namespace           = "AWS/ApiGateway"
  metric_name         = "5xx"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = var.api_gateway_5xx_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    ApiId = var.api_gateway_id
    Stage = var.api_gateway_stage_name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "application_errors" {
  count = var.api_observability_enabled ? 1 : 0

  alarm_name          = "${var.name_prefix}-application-errors"
  alarm_description   = "The application completed requests with errors."
  namespace           = local.application_metric_namespace
  metric_name         = "ServerErrorCount"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = var.application_error_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

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

resource "aws_cloudwatch_metric_alarm" "ecs_running_tasks" {
  count = var.api_observability_enabled ? 1 : 0

  alarm_name          = "${var.name_prefix}-ecs-running-tasks-low"
  alarm_description   = "ECS running task count is below the configured minimum."
  namespace           = "ECS/ContainerInsights"
  metric_name         = "RunningTaskCount"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "LessThanThreshold"
  threshold           = var.minimum_task_count
  treat_missing_data  = "breaching"
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
              [".", "FreeStorageSpace", ".", "."],
              [".", "FreeableMemory", ".", "."],
              [".", "ReadLatency", ".", "."],
              [".", "WriteLatency", ".", "."],
              [".", "Deadlocks", ".", ".", { stat = "Sum" }]
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
            title  = "API Gateway traffic, errors, and latency"
            region = data.aws_region.current.region
            stat   = "Average"
            period = 60
            metrics = [
              ["AWS/ApiGateway", "Count", "ApiId", var.api_gateway_id, "Stage", var.api_gateway_stage_name, { stat = "Sum" }],
              [".", "4xx", ".", ".", ".", ".", { stat = "Sum" }],
              [".", "5xx", ".", ".", ".", ".", { stat = "Sum" }],
              [".", "Latency", ".", ".", ".", ".", { stat = "p95" }],
              [".", "IntegrationLatency", ".", ".", ".", ".", { stat = "p95" }]
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
            title  = "Application requests, errors, and latency"
            region = data.aws_region.current.region
            period = 60
            metrics = [
              [{ expression = "SEARCH('{${local.application_metric_namespace},Operation} MetricName=\"RequestCount\"', 'Sum', 60)", id = "requests" }],
              [{ expression = "SEARCH('{${local.application_metric_namespace},Operation} MetricName=\"RequestDuration\"', 'p95', 60)", id = "latency" }],
              [local.application_metric_namespace, "ServerErrorCount", { stat = "Sum" }]
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
            title  = "ALB requests, errors, and target latency"
            region = data.aws_region.current.region
            stat   = "Average"
            period = 300
            metrics = [
              ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", local.load_balancer_suffix, { stat = "Sum" }],
              [".", "HealthyHostCount", ".", ".", "TargetGroup", local.target_group_suffix, { stat = "Minimum" }],
              [".", "UnHealthyHostCount", ".", ".", ".", ".", { stat = "Maximum" }],
              [".", "HTTPCode_ELB_5XX_Count", ".", ".", { stat = "Sum" }],
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
              [".", "MemoryUtilization", ".", ".", ".", "."],
              ["ECS/ContainerInsights", "DesiredTaskCount", "ClusterName", var.ecs_cluster_name, "ServiceName", var.ecs_service_name],
              [".", "RunningTaskCount", ".", ".", ".", "."],
              [".", "PendingTaskCount", ".", ".", ".", "."]
            ]
          }
        }
      ] : [],
      var.api_observability_enabled ? [
        {
          type   = "log"
          width  = 24
          height = 6
          properties = {
            title  = "Recent application request failures"
            region = data.aws_region.current.region
            view   = "table"
            query  = "SOURCE '${var.application_log_group_name}' | fields @timestamp, correlationId, apiGatewayRequestId, method, route, operation, status, durationMs, errorCode | filter event = 'request.completed' and outcome != 'success' | sort @timestamp desc | limit 50"
          }
        }
      ] : [],
      var.api_observability_enabled ? [
        {
          type   = "alarm"
          width  = 24
          height = 6
          properties = {
            title = "Alarm status"
            alarms = concat(
              [
                aws_cloudwatch_metric_alarm.rds_cpu.arn,
                aws_cloudwatch_metric_alarm.rds_connections.arn,
                aws_cloudwatch_metric_alarm.rds_free_storage.arn,
                aws_cloudwatch_metric_alarm.flyway_errors.arn
              ],
              [
                aws_cloudwatch_metric_alarm.alb_healthy_hosts[0].arn,
                aws_cloudwatch_metric_alarm.api_gateway_5xx[0].arn,
                aws_cloudwatch_metric_alarm.application_errors[0].arn,
                aws_cloudwatch_metric_alarm.ecs_cpu[0].arn,
                aws_cloudwatch_metric_alarm.ecs_memory[0].arn,
                aws_cloudwatch_metric_alarm.ecs_running_tasks[0].arn
              ]
            )
          }
        }
      ] : []
    )
  })
}
