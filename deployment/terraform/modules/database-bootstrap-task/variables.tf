variable "name_prefix" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "At least two private subnet IDs are required."
  }
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

variable "master_secret_arn" {
  type = string
}

variable "migrator_secret_arn" {
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
}

variable "database_name" {
  type = string
}

variable "cpu" {
  type    = number
  default = 256
}

variable "memory" {
  type    = number
  default = 512
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}
