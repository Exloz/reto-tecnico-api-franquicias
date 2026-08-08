data "aws_region" "current" {}

data "aws_iam_policy_document" "ecs_tasks_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name_prefix}-migration"
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

resource "aws_iam_role" "execution" {
  name                 = "${var.name_prefix}-migration-execution"
  assume_role_policy   = data.aws_iam_policy_document.ecs_tasks_assume_role.json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = var.tags
}

resource "aws_iam_role_policy" "execution" {
  name = "${var.name_prefix}-migration-execution"
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = var.ecr_repository_arn
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.this.arn}:*"
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.migrator_secret_arn
      }
    ]
  })
}

resource "aws_iam_role" "task" {
  name                 = "${var.name_prefix}-migration-task"
  assume_role_policy   = data.aws_iam_policy_document.ecs_tasks_assume_role.json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = var.tags
}

resource "aws_ecs_task_definition" "this" {
  count = var.workload_enabled ? 1 : 0

  family                   = "${var.name_prefix}-migration"
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "migration"
      image     = var.image_uri
      essential = true
      command   = ["migrate", "validate"]
      environment = [
        { name = "DB_HOST", value = var.database_host },
        { name = "DB_PORT", value = tostring(var.database_port) },
        { name = "DB_NAME", value = var.database_name },
        { name = "DB_SSL_MODE", value = "VERIFY_FULL" },
        { name = "FLYWAY_URL", value = "jdbc:postgresql://${var.database_host}:${var.database_port}/${var.database_name}?sslmode=verify-full&sslrootcert=/flyway/conf/aws-rds-global-bundle.pem" }
      ]
      secrets = [
        { name = "FLYWAY_USER", valueFrom = "${var.migrator_secret_arn}:username::" },
        { name = "FLYWAY_PASSWORD", valueFrom = "${var.migrator_secret_arn}:password::" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.this.name
          awslogs-region        = data.aws_region.current.region
          awslogs-stream-prefix = "migration"
        }
      }
    }
  ])

  lifecycle {
    precondition {
      condition     = var.image_uri != null && can(regex("@sha256:[0-9a-fA-F]{64}$", var.image_uri))
      error_message = "workload_enabled requires image_uri pinned by sha256 digest."
    }
  }

  tags = var.tags
}
