variable "environment" {
  description = "Deployment environment used in resource names."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*$", var.environment))
    error_message = "environment must start with a lowercase letter and contain only lowercase letters, numbers, and hyphens."
  }
}

variable "database_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "franchise"

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,62}$", var.database_name))
    error_message = "database_name must start with a letter and contain at most 63 letters, numbers, or underscores."
  }
}

variable "database_subnet_ids" {
  description = "Private data subnet IDs for the DB subnet group."
  type        = list(string)

  validation {
    condition     = length(var.database_subnet_ids) >= 2
    error_message = "database_subnet_ids must contain at least two subnets."
  }
}

variable "rds_security_group_id" {
  description = "Security group attached to the RDS instance."
  type        = string
}

variable "engine_version" {
  description = "Exact PostgreSQL engine version."
  type        = string
  default     = "17.10"
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Initial gp3 storage in GiB."
  type        = number
  default     = 20

  validation {
    condition     = var.allocated_storage >= 20
    error_message = "allocated_storage must be at least 20 GiB."
  }
}

variable "max_allocated_storage" {
  description = "Maximum autoscaled storage in GiB."
  type        = number
  default     = 100

  validation {
    condition     = var.max_allocated_storage >= 20
    error_message = "max_allocated_storage must be at least 20 GiB."
  }
}

variable "backup_retention_period" {
  description = "Automated backup retention in days."
  type        = number
  default     = 3

  validation {
    condition     = var.backup_retention_period >= 0 && var.backup_retention_period <= 35
    error_message = "backup_retention_period must be between 0 and 35 days."
  }
}

variable "multi_az" {
  description = "Whether RDS maintains a synchronous standby in another availability zone."
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Whether RDS deletion protection is enabled."
  type        = bool
  default     = false
}

variable "skip_final_snapshot" {
  description = "Whether deletion skips a final database snapshot."
  type        = bool
  default     = true
}

variable "final_snapshot_identifier" {
  description = "Explicit final snapshot identifier required when final snapshots are enabled."
  type        = string
  default     = null
  nullable    = true
}

variable "secret_recovery_window_in_days" {
  description = "Secrets Manager recovery window for application and migrator credentials."
  type        = number
  default     = 0

  validation {
    condition     = var.secret_recovery_window_in_days == 0 || (var.secret_recovery_window_in_days >= 7 && var.secret_recovery_window_in_days <= 30)
    error_message = "secret_recovery_window_in_days must be 0 or between 7 and 30."
  }
}

variable "master_username" {
  description = "PostgreSQL master username whose password is managed by RDS."
  type        = string
  default     = "franchise_admin"
}

variable "tags" {
  description = "Additional tags applied to every resource."
  type        = map(string)
  default     = {}
}
