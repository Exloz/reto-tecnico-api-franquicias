variable "name_prefix" {
  type = string
}

variable "alb_arn" {
  type     = string
  default  = null
  nullable = true
}

variable "target_group_arn" {
  type     = string
  default  = null
  nullable = true
}

variable "ecs_cluster_name" {
  type     = string
  default  = null
  nullable = true
}

variable "ecs_service_name" {
  type     = string
  default  = null
  nullable = true
}

variable "database_instance_identifier" {
  type = string
}

variable "api_observability_enabled" {
  type    = bool
  default = false

  validation {
    condition = !var.api_observability_enabled || alltrue([
      var.alb_arn != null,
      var.target_group_arn != null,
      var.ecs_cluster_name != null,
      var.ecs_service_name != null
    ])
    error_message = "api_observability_enabled requires ALB, target group, ECS cluster, and ECS service inputs."
  }
}

variable "sns_topic_arn" {
  type     = string
  default  = null
  nullable = true
}

variable "ecs_cpu_threshold" {
  type    = number
  default = 80
}

variable "ecs_memory_threshold" {
  type    = number
  default = 80
}

variable "rds_cpu_threshold" {
  type    = number
  default = 80
}

variable "rds_connections_threshold" {
  type    = number
  default = 60
}

variable "rds_free_storage_threshold_bytes" {
  type    = number
  default = 2147483648
}

variable "tags" {
  type    = map(string)
  default = {}
}
