locals {
  environment                      = "dev"
  name_prefix                      = "franchise-${local.environment}"
  runtime_permissions_boundary_arn = "arn:aws:iam::127321794531:policy/franchise-runtime-permissions-boundary"
  ci_permissions_boundary_arn      = "arn:aws:iam::127321794531:policy/franchise-boundary-ci"
  tags = merge(var.tags, {
    Environment = local.environment
    ManagedBy   = "terraform"
    Project     = "franchise"
  })
}

module "network" {
  count  = var.enable_environment ? 1 : 0
  source = "../../modules/network"

  name_prefix              = local.name_prefix
  vpc_cidr                 = "10.20.0.0/16"
  availability_zones       = ["us-east-1a", "us-east-1b"]
  flow_log_traffic_type    = "REJECT"
  permissions_boundary_arn = local.runtime_permissions_boundary_arn
  tags                     = local.tags
}

module "container_registry" {
  count  = var.enable_environment ? 1 : 0
  source = "../../modules/container-registry"

  name_prefix           = local.name_prefix
  image_retention_count = 5
  force_delete          = true
  tags                  = local.tags
}

module "database" {
  count  = var.enable_environment ? 1 : 0
  source = "../../modules/database"

  environment                    = local.environment
  database_name                  = "franchise"
  database_subnet_ids            = module.network[0].data_subnet_ids
  rds_security_group_id          = module.network[0].security_group_ids.rds
  engine_version                 = "17.10"
  instance_class                 = "db.t4g.micro"
  allocated_storage              = 20
  max_allocated_storage          = 100
  backup_retention_period        = 3
  multi_az                       = false
  deletion_protection            = false
  skip_final_snapshot            = true
  secret_recovery_window_in_days = var.database_secret_recovery_window_in_days
  tags                           = local.tags
}

module "ecs_api" {
  count  = var.enable_environment ? 1 : 0
  source = "../../modules/ecs-api-service"

  name_prefix              = local.name_prefix
  vpc_id                   = module.network[0].vpc_id
  application_subnet_ids   = module.network[0].application_subnet_ids
  integration_subnet_ids   = module.network[0].integration_subnet_ids
  alb_security_group_id    = module.network[0].security_group_ids.alb
  api_security_group_id    = module.network[0].security_group_ids.api
  workload_enabled         = var.enable_api_service
  permissions_boundary_arn = local.runtime_permissions_boundary_arn
  image_uri                = var.api_image_digest == null ? null : "${module.container_registry[0].repository_urls.api}@${var.api_image_digest}"
  ecr_repository_arn       = module.container_registry[0].repository_arns.api
  application_secret_arn   = module.database[0].application_secret_arn
  database_host            = module.database[0].database_address
  database_port            = module.database[0].database_port
  database_name            = "franchise"
  cpu                      = 512
  memory                   = 1024
  minimum_capacity         = 1
  maximum_capacity         = 2
  log_retention_days       = 7
  tags                     = local.tags
}

module "database_migration" {
  count  = var.enable_environment ? 1 : 0
  source = "../../modules/database-migration-task"

  name_prefix              = local.name_prefix
  security_group_id        = module.network[0].security_group_ids.flyway
  private_subnet_ids       = module.network[0].application_subnet_ids
  workload_enabled         = var.enable_migration_task
  permissions_boundary_arn = local.runtime_permissions_boundary_arn
  image_uri                = var.migration_image_digest == null ? null : "${module.container_registry[0].repository_urls.migrations}@${var.migration_image_digest}"
  ecr_repository_arn       = module.container_registry[0].repository_arns.migrations
  migrator_secret_arn      = module.database[0].migrator_secret_arn
  database_host            = module.database[0].database_address
  database_port            = module.database[0].database_port
  database_name            = "franchise"
  log_retention_days       = 7
  tags                     = local.tags
}

module "database_bootstrap" {
  count  = var.enable_environment ? 1 : 0
  source = "../../modules/database-bootstrap-task"

  name_prefix              = local.name_prefix
  security_group_id        = module.network[0].security_group_ids.bootstrap
  private_subnet_ids       = module.network[0].application_subnet_ids
  workload_enabled         = var.enable_bootstrap_task
  permissions_boundary_arn = local.runtime_permissions_boundary_arn
  image_uri                = var.bootstrap_image_digest == null ? null : "${module.container_registry[0].repository_urls.bootstrap}@${var.bootstrap_image_digest}"
  ecr_repository_arn       = module.container_registry[0].repository_arns.bootstrap
  master_secret_arn        = module.database[0].master_secret_arn
  migrator_secret_arn      = module.database[0].migrator_secret_arn
  application_secret_arn   = module.database[0].application_secret_arn
  database_host            = module.database[0].database_address
  database_port            = module.database[0].database_port
  database_name            = "franchise"
  log_retention_days       = 7
  tags                     = local.tags
}

module "api_gateway" {
  count  = var.enable_environment && var.enable_api_service ? 1 : 0
  source = "../../modules/api-gateway"

  name_prefix                 = local.name_prefix
  vpc_link_subnet_ids         = module.network[0].integration_subnet_ids
  vpc_link_security_group_ids = [module.network[0].security_group_ids.vpc_link]
  alb_listener_arn            = module.ecs_api[0].alb_listener_arn
  throttling_rate_limit       = 100
  throttling_burst_limit      = 200
  log_retention_days          = 7
  tags                        = local.tags
}

module "observability" {
  count  = var.enable_environment ? 1 : 0
  source = "../../modules/observability"

  name_prefix                  = local.name_prefix
  alb_arn                      = module.ecs_api[0].alb_arn
  target_group_arn             = module.ecs_api[0].target_group_arn
  ecs_cluster_name             = module.ecs_api[0].cluster_name
  ecs_service_name             = module.ecs_api[0].service_name
  api_gateway_id               = try(module.api_gateway[0].api_id, null)
  application_log_group_name   = module.ecs_api[0].log_group_name
  migration_log_group_name     = module.database_migration[0].log_group_name
  minimum_task_count           = 1
  api_observability_enabled    = var.enable_api_service
  database_instance_identifier = module.database[0].database_instance_identifier
  sns_topic_arn                = var.notification_topic_arn
  tags                         = local.tags
}

module "ci_identity" {
  count  = var.enable_ci_identity ? 1 : 0
  source = "../../modules/ci-identity"

  name_prefix                = "franchise-ci-account"
  github_oidc_subject_prefix = "repo:Exloz@45303078/api-franquicias@1324521851"
  create_oidc_provider       = var.create_github_oidc_provider
  existing_oidc_provider_arn = var.existing_github_oidc_provider_arn
  state_bucket_name          = "franchise-127321794531-terraform-state"
  environments = {
    dev = {
      state_key                  = "dev/infrastructure.tfstate"
      infrastructure_name_prefix = "franchise-dev"
      ecr_pull_repository_arns = [
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-api",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-migrations",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-bootstrap"
      ]
      ecr_push_repository_arns = [
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-api",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-migrations",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-bootstrap"
      ]
      ecs_pass_role_arns = [
        "arn:aws:iam::127321794531:role/franchise-dev-api-execution",
        "arn:aws:iam::127321794531:role/franchise-dev-api-task",
        "arn:aws:iam::127321794531:role/franchise-dev-migration-execution",
        "arn:aws:iam::127321794531:role/franchise-dev-migration-task",
        "arn:aws:iam::127321794531:role/franchise-dev-database-bootstrap-execution",
        "arn:aws:iam::127321794531:role/franchise-dev-database-bootstrap-task"
      ]
    }
    prod = {
      state_key                  = "prod/infrastructure.tfstate"
      infrastructure_name_prefix = "franchise-prod"
      ecr_pull_repository_arns = [
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-api",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-migrations",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-dev-bootstrap",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-prod-api",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-prod-migrations",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-prod-bootstrap"
      ]
      ecr_push_repository_arns = [
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-prod-api",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-prod-migrations",
        "arn:aws:ecr:us-east-1:127321794531:repository/franchise-prod-bootstrap"
      ]
      ecs_pass_role_arns = [
        "arn:aws:iam::127321794531:role/franchise-prod-api-execution",
        "arn:aws:iam::127321794531:role/franchise-prod-api-task",
        "arn:aws:iam::127321794531:role/franchise-prod-migration-execution",
        "arn:aws:iam::127321794531:role/franchise-prod-migration-task",
        "arn:aws:iam::127321794531:role/franchise-prod-database-bootstrap-execution",
        "arn:aws:iam::127321794531:role/franchise-prod-database-bootstrap-task"
      ]
    }
  }
  permissions_boundary_arn = local.ci_permissions_boundary_arn
  tags = merge(var.tags, {
    ManagedBy = "terraform"
    Project   = "franchise"
    Scope     = "account"
  })
}
