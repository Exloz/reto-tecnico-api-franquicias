variable "name_prefix" {
  description = "Prefix used for network resource names."
  type        = string

  validation {
    condition     = length(trimspace(var.name_prefix)) > 0
    error_message = "The name prefix cannot be empty."
  }
}

variable "vpc_cidr" {
  description = "IPv4 /16 CIDR assigned to the VPC."
  type        = string
  default     = "10.20.0.0/16"

  validation {
    condition     = can(cidrsubnet(var.vpc_cidr, 8, 255)) && can(regex("/16$", var.vpc_cidr))
    error_message = "The VPC CIDR must be a valid /16 IPv4 network."
  }
}

variable "availability_zones" {
  description = "Exactly two availability zones used in deterministic order."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2 && length(distinct(var.availability_zones)) == 2
    error_message = "Exactly two distinct availability zones must be provided."
  }
}

variable "alb_listener_port" {
  description = "Internal ALB listener port."
  type        = number
  default     = 80

  validation {
    condition     = var.alb_listener_port >= 1 && var.alb_listener_port <= 65535
    error_message = "The ALB listener port must be between 1 and 65535."
  }
}

variable "application_port" {
  description = "API container port."
  type        = number
  default     = 8080

  validation {
    condition     = var.application_port >= 1 && var.application_port <= 65535
    error_message = "The application port must be between 1 and 65535."
  }
}

variable "postgres_port" {
  description = "PostgreSQL listener port."
  type        = number
  default     = 5432

  validation {
    condition     = var.postgres_port >= 1 && var.postgres_port <= 65535
    error_message = "The PostgreSQL port must be between 1 and 65535."
  }
}

variable "flow_log_retention_days" {
  description = "CloudWatch retention in days for VPC flow logs."
  type        = number
  default     = 7

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.flow_log_retention_days)
    error_message = "The flow log retention must be a value supported by CloudWatch Logs."
  }
}

variable "flow_log_traffic_type" {
  description = "Traffic type captured by VPC flow logs."
  type        = string
  default     = "ALL"

  validation {
    condition     = contains(["ALL", "ACCEPT", "REJECT"], var.flow_log_traffic_type)
    error_message = "The flow log traffic type must be ALL, ACCEPT, or REJECT."
  }
}

variable "permissions_boundary_arn" {
  description = "Permissions boundary attached to project IAM roles."
  type        = string
}

variable "tags" {
  description = "Tags added to every resource."
  type        = map(string)
  default     = {}
}
