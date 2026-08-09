output "api_endpoint" {
  value = try(module.api_gateway[0].api_endpoint, null)
}

output "vpc_id" {
  value = try(module.network[0].vpc_id, null)
}

output "database_endpoint" {
  value = try(module.database[0].database_endpoint, null)
}

output "repository_urls" {
  value = try(module.container_registry[0].repository_urls, {})
}

output "repository_names" {
  value = try(module.container_registry[0].repository_names, {})
}

output "ecs_cluster_name" {
  value = try(module.ecs_api[0].cluster_name, null)
}

output "ecs_service_name" {
  value = try(module.ecs_api[0].service_name, null)
}

output "api_task_definition_arn" {
  value = try(module.ecs_api[0].task_definition_arn, null)
}

output "migration_task_definition_arn" {
  value = try(module.database_migration[0].task_definition_arn, null)
}

output "bootstrap_task_definition_arn" {
  value = try(module.database_bootstrap[0].task_definition_arn, null)
}

output "migration_run_task_network_configuration" {
  value = try(module.database_migration[0].run_task_network_configuration, null)
}

output "bootstrap_run_task_network_configuration" {
  value = try(module.database_bootstrap[0].run_task_network_configuration, null)
}

output "alarm_arns" {
  value = try(module.observability[0].alarm_arns, {})
}

output "dashboard_name" {
  value = try(module.observability[0].dashboard_name, null)
}

output "release_digests" {
  value = {
    api        = var.api_image_digest
    migrations = var.migration_image_digest
    bootstrap  = var.bootstrap_image_digest
  }
}

output "ci_role_arns" {
  value = try({
    plan   = module.ci_identity[0].plan_role_arn
    apply  = module.ci_identity[0].apply_role_arns
    deploy = module.ci_identity[0].deploy_role_arns
  }, {})
}
