variable "name_prefix" {
  description = "Prefix used for ECR repository names."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]+(?:[._/-][a-z0-9]+)*$", var.name_prefix))
    error_message = "The name prefix must be a valid lowercase ECR repository prefix."
  }
}

variable "image_retention_count" {
  description = "Number of most recently pushed images retained in each repository."
  type        = number
  default     = 10

  validation {
    condition     = var.image_retention_count >= 1
    error_message = "At least one image must be retained."
  }
}

variable "force_delete" {
  description = "Allow repository deletion when images remain. Keep false in production."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags added to every repository."
  type        = map(string)
  default     = {}
}
