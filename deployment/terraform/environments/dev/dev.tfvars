enable_environment    = true
enable_bootstrap_task = false
enable_migration_task = false
enable_api_service    = true
enable_ci_identity    = true

api_image_digest       = "sha256:c266b10fbbf2b02ba2ae2f9b40ab37557af809ec31e1b189a8f50a4b8ef99f9a"
migration_image_digest = "sha256:31fb01a5059f737ee8e0f95a9650453d7914e10552fa2036dcbfa5afe84d12a7"
bootstrap_image_digest = "sha256:039b31e804490bcdc0bc4225170fdf379fbccc589aa80978642f8a16bb2a0726"

tags = {
  CostCenter = "technical-challenge"
}
