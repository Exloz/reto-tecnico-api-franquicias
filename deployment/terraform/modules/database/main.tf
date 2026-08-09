locals {
  name = "franchise-${var.environment}-database"
  tags = merge(var.tags, {
    Environment = var.environment
    ManagedBy   = "terraform"
    Project     = "franchise"
  })
}

resource "aws_db_subnet_group" "this" {
  name       = "${local.name}-subnets"
  subnet_ids = var.database_subnet_ids
  tags       = local.tags
}

resource "aws_db_parameter_group" "this" {
  name   = "${local.name}-postgres17"
  family = "postgres17"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  tags = local.tags
}

resource "aws_db_instance" "this" {
  identifier = local.name

  engine                      = "postgres"
  engine_version              = var.engine_version
  instance_class              = var.instance_class
  db_name                     = var.database_name
  username                    = var.master_username
  manage_master_user_password = true

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  parameter_group_name   = aws_db_parameter_group.this.name
  vpc_security_group_ids = [var.rds_security_group_id]
  publicly_accessible    = false

  backup_retention_period   = var.backup_retention_period
  multi_az                  = var.multi_az
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : var.final_snapshot_identifier
  copy_tags_to_snapshot     = true

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  auto_minor_version_upgrade      = false

  tags = local.tags

  lifecycle {
    precondition {
      condition     = var.skip_final_snapshot || var.final_snapshot_identifier != null
      error_message = "final_snapshot_identifier is required when skip_final_snapshot is false."
    }
  }
}

resource "aws_secretsmanager_secret" "migrator" {
  name                    = "/franchise/${var.environment}/database/migrator"
  description             = "PostgreSQL migrator credentials populated by the database bootstrap task"
  recovery_window_in_days = var.secret_recovery_window_in_days
  tags                    = local.tags
}

resource "aws_secretsmanager_secret" "application" {
  name                    = "/franchise/${var.environment}/database/application"
  description             = "PostgreSQL application credentials populated by the database bootstrap task"
  recovery_window_in_days = var.secret_recovery_window_in_days
  tags                    = local.tags
}
