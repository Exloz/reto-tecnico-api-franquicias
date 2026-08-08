enable_environment    = true
enable_bootstrap_task = false
enable_migration_task = false
enable_api_service    = false
enable_ci_identity    = false

api_image_digest       = "sha256:2eb4ac7c53039756248757dd9b324452722493c9221eb731428b3d5eb6e34def"
migration_image_digest = "sha256:31fb01a5059f737ee8e0f95a9650453d7914e10552fa2036dcbfa5afe84d12a7"
bootstrap_image_digest = "sha256:039b31e804490bcdc0bc4225170fdf379fbccc589aa80978642f8a16bb2a0726"

tags = {
  CostCenter = "technical-challenge"
}
