variable "enable_environment" {
  type    = bool
  default = false
}

variable "enable_bootstrap_task" {
  type    = bool
  default = false
}

variable "enable_migration_task" {
  type    = bool
  default = false
}

variable "enable_api_service" {
  type    = bool
  default = false

  validation {
    condition     = length([for enabled in [var.enable_bootstrap_task, var.enable_migration_task, var.enable_api_service] : enabled if enabled]) <= 1
    error_message = "Enable only one workload stage at a time: bootstrap, migration, or API service."
  }
}

variable "enable_ci_identity" {
  type    = bool
  default = false
}

variable "create_github_oidc_provider" {
  type    = bool
  default = true
}

variable "existing_github_oidc_provider_arn" {
  type     = string
  default  = null
  nullable = true
}

variable "api_image_digest" {
  type     = string
  default  = null
  nullable = true

  validation {
    condition     = var.api_image_digest == null || can(regex("^sha256:[0-9a-fA-F]{64}$", var.api_image_digest))
    error_message = "api_image_digest must be a sha256 digest."
  }
}

variable "migration_image_digest" {
  type     = string
  default  = null
  nullable = true

  validation {
    condition     = var.migration_image_digest == null || can(regex("^sha256:[0-9a-fA-F]{64}$", var.migration_image_digest))
    error_message = "migration_image_digest must be a sha256 digest."
  }
}

variable "bootstrap_image_digest" {
  type     = string
  default  = null
  nullable = true

  validation {
    condition     = var.bootstrap_image_digest == null || can(regex("^sha256:[0-9a-fA-F]{64}$", var.bootstrap_image_digest))
    error_message = "bootstrap_image_digest must be a sha256 digest."
  }
}

variable "notification_topic_arn" {
  type     = string
  default  = null
  nullable = true
}

variable "database_secret_recovery_window_in_days" {
  type    = number
  default = 0
}

variable "tags" {
  type    = map(string)
  default = {}
}
