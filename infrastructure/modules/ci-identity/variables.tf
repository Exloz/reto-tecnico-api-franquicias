variable "name_prefix" {
  type = string
}

variable "github_oidc_subject_prefix" {
  type = string
}

variable "create_oidc_provider" {
  type    = bool
  default = true
}

variable "existing_oidc_provider_arn" {
  type     = string
  default  = null
  nullable = true
}

variable "state_bucket_name" {
  type = string
}

variable "environments" {
  type = map(object({
    state_key                  = string
    infrastructure_name_prefix = string
    ecr_pull_repository_arns   = list(string)
    ecr_push_repository_arns   = list(string)
    ecs_pass_role_arns         = list(string)
  }))

  validation {
    condition     = length(var.environments) > 0 && alltrue([for environment, config in var.environments : config.state_key == "${environment}/infrastructure.tfstate"])
    error_message = "Each CI environment must use its matching <environment>/infrastructure.tfstate key."
  }
}

variable "permissions_boundary_arn" {
  type = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
