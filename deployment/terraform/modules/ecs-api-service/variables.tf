variable "name_prefix" {
  type = string

  validation {
    condition     = can(regex("^franchise-[a-z0-9-]+$", var.name_prefix))
    error_message = "name_prefix must start with franchise- and contain only lowercase letters, numbers, and hyphens."
  }
}

variable "vpc_id" {
  type = string
}

variable "application_subnet_ids" {
  type = list(string)

  validation {
    condition     = length(var.application_subnet_ids) >= 2
    error_message = "At least two application subnet IDs are required."
  }
}

variable "integration_subnet_ids" {
  type = list(string)

  validation {
    condition     = length(var.integration_subnet_ids) >= 2
    error_message = "At least two integration subnet IDs are required."
  }
}

variable "alb_security_group_id" {
  type = string
}

variable "api_security_group_id" {
  type = string
}

variable "workload_enabled" {
  type    = bool
  default = false
}

variable "permissions_boundary_arn" {
  type = string
}

variable "image_uri" {
  type     = string
  default  = null
  nullable = true

  validation {
    condition     = var.image_uri == null || can(regex("^[0-9]+\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9._/-]+@sha256:[0-9a-fA-F]{64}$", var.image_uri))
    error_message = "image_uri must be an ECR URI pinned by sha256 digest."
  }
}

variable "ecr_repository_arn" {
  type = string
}

variable "application_secret_arn" {
  type = string
}

variable "database_host" {
  type = string
}

variable "database_port" {
  type    = number
  default = 5432

  validation {
    condition     = var.database_port >= 1 && var.database_port <= 65535
    error_message = "database_port must be a valid TCP port."
  }
}

variable "database_name" {
  type = string
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "minimum_capacity" {
  type    = number
  default = 1

  validation {
    condition     = var.minimum_capacity >= 1
    error_message = "minimum_capacity must be at least one."
  }
}

variable "maximum_capacity" {
  type    = number
  default = 2

  validation {
    condition     = var.maximum_capacity >= 1
    error_message = "maximum_capacity must be at least one."
  }
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "health_check_grace_period_seconds" {
  type    = number
  default = 60
}

variable "deregistration_delay_seconds" {
  type    = number
  default = 30
}

variable "tags" {
  type    = map(string)
  default = {}
}
