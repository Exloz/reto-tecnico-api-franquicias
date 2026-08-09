variable "state_bucket_name" {
  description = "Globally unique S3 bucket used for Terraform state."
  type        = string
  default     = "franchise-127321794531-terraform-state"

  validation {
    condition     = var.state_bucket_name == "franchise-127321794531-terraform-state"
    error_message = "The bootstrap state bucket must be franchise-127321794531-terraform-state."
  }
}

variable "bootstrap_principal_arn" {
  description = "IAM principal allowed to administer the bootstrap resources and assume the apply role."
  type        = string
  default     = "arn:aws:iam::127321794531:user/franchise-admin-1"

  validation {
    condition     = var.bootstrap_principal_arn == "arn:aws:iam::127321794531:user/franchise-admin-1"
    error_message = "The bootstrap principal must be the current franchise administrator IAM user."
  }
}

variable "noncurrent_version_expiration_days" {
  description = "Days to retain noncurrent Terraform state object versions."
  type        = number
  default     = 90

  validation {
    condition     = var.noncurrent_version_expiration_days >= 30
    error_message = "Noncurrent state versions must be retained for at least 30 days."
  }
}

variable "enable_ci_identity" {
  description = "Authorizes direct state access for the account-level franchise CI roles after they are created."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags applied to bootstrap resources."
  type        = map(string)
  default = {
    Project   = "franchise"
    ManagedBy = "terraform"
    Scope     = "bootstrap"
  }
}
