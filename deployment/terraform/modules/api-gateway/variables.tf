variable "name_prefix" {
  type = string
}

variable "vpc_link_subnet_ids" {
  type = list(string)

  validation {
    condition     = length(var.vpc_link_subnet_ids) >= 2
    error_message = "At least two VPC Link subnet IDs are required."
  }
}

variable "vpc_link_security_group_ids" {
  type = list(string)

  validation {
    condition     = length(var.vpc_link_security_group_ids) >= 1
    error_message = "At least one VPC Link security group ID is required."
  }
}

variable "alb_listener_arn" {
  type = string
}

variable "throttling_rate_limit" {
  type    = number
  default = 100
}

variable "throttling_burst_limit" {
  type    = number
  default = 200
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}
