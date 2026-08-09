variable "enable_environment" {
  type    = bool
  default = false

  validation {
    condition = !var.enable_environment || (
      var.production_confirmation == "deploy-franchise-prod-127321794531-us-east-1" &&
      var.expected_account_id == "127321794531" &&
      var.expected_region == "us-east-1" &&
      var.final_snapshot_identifier != null
    )
    error_message = "Production requires the exact confirmation string, account, region, and final snapshot identifier."
  }
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

variable "production_confirmation" {
  type     = string
  default  = null
  nullable = true
}

variable "expected_account_id" {
  type    = string
  default = ""
}

variable "expected_region" {
  type    = string
  default = ""
}

variable "final_snapshot_identifier" {
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
  default = 30
}

variable "tags" {
  type    = map(string)
  default = {}
}
