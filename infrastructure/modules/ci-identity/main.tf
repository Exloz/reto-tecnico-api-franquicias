data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 1 : 0

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
  tags            = var.tags
}

locals {
  oidc_provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.existing_oidc_provider_arn
  state_bucket_arn  = "arn:aws:s3:::${var.state_bucket_name}"
  state_object_arns = {
    for environment, config in var.environments : environment => "${local.state_bucket_arn}/${config.state_key}"
  }
  lock_object_arns = {
    for environment, arn in local.state_object_arns : environment => "${arn}.tflock"
  }
  ecs_cluster_arns = {
    for environment, config in var.environments : environment => "arn:aws:ecs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:cluster/${config.infrastructure_name_prefix}-cluster"
  }
  ecs_service_arns = {
    for environment, config in var.environments : environment => "arn:aws:ecs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:service/${config.infrastructure_name_prefix}-cluster/${config.infrastructure_name_prefix}-api"
  }
  ecs_task_arns = {
    for environment, config in var.environments : environment => "arn:aws:ecs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:task/${config.infrastructure_name_prefix}-cluster/*"
  }
  deploy_task_definitions = {
    for environment, config in var.environments : environment => [
      "arn:aws:ecs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:task-definition/${config.infrastructure_name_prefix}-api:*",
      "arn:aws:ecs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:task-definition/${config.infrastructure_name_prefix}-migration:*",
      "arn:aws:ecs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:task-definition/${config.infrastructure_name_prefix}-database-bootstrap:*"
    ]
  }
}

check "oidc_provider" {
  assert {
    condition     = var.create_oidc_provider != (var.existing_oidc_provider_arn != null)
    error_message = "Create the GitHub OIDC provider or supply an existing provider ARN, but not both."
  }
}

data "aws_iam_policy_document" "plan_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["${var.github_oidc_subject_prefix}:pull_request"]
    }
  }
}

data "aws_iam_policy_document" "environment_trust" {
  for_each = var.environments

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["${var.github_oidc_subject_prefix}:environment:${each.key}"]
    }
  }
}

resource "aws_iam_role" "plan" {
  name                 = "${var.name_prefix}-plan"
  assume_role_policy   = data.aws_iam_policy_document.plan_trust.json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = var.tags
}

resource "aws_iam_role" "apply" {
  for_each = var.environments

  name                 = "${var.name_prefix}-${each.key}-apply"
  assume_role_policy   = data.aws_iam_policy_document.environment_trust[each.key].json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = merge(var.tags, { Environment = each.key })
}

resource "aws_iam_role" "deploy" {
  for_each = var.environments

  name                 = "${var.name_prefix}-${each.key}-deploy"
  assume_role_policy   = data.aws_iam_policy_document.environment_trust[each.key].json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = merge(var.tags, { Environment = each.key })
}

data "aws_iam_policy_document" "provider_read" {
  statement {
    actions = [
      "apigateway:GET",
      "application-autoscaling:Describe*",
      "application-autoscaling:ListTagsForResource",
      "cloudwatch:Describe*",
      "cloudwatch:Get*",
      "cloudwatch:List*",
      "ec2:Describe*",
      "ecr:Describe*",
      "ecr:GetLifecyclePolicy",
      "ecr:GetRepositoryPolicy",
      "ecr:ListTagsForResource",
      "ecs:Describe*",
      "ecs:List*",
      "elasticloadbalancing:Describe*",
      "iam:Get*",
      "iam:List*",
      "logs:Describe*",
      "logs:ListTagsForResource",
      "rds:Describe*",
      "rds:ListTagsForResource",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetResourcePolicy",
      "secretsmanager:ListSecretVersionIds",
      "sns:GetTopicAttributes",
      "sns:ListTagsForResource"
    ]
    resources = ["*"]
  }
}

data "aws_iam_policy_document" "plan" {
  source_policy_documents = [data.aws_iam_policy_document.provider_read.json]

  statement {
    actions   = ["s3:GetBucketLocation"]
    resources = [local.state_bucket_arn]
  }

  statement {
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket_arn]

    condition {
      test     = "StringEquals"
      variable = "s3:prefix"
      values   = concat([for config in values(var.environments) : config.state_key], [for config in values(var.environments) : "${config.state_key}.tflock"])
    }
  }

  statement {
    actions   = ["s3:GetObject"]
    resources = values(local.state_object_arns)
  }

  statement {
    actions   = ["s3:DeleteObject", "s3:GetObject", "s3:PutObject"]
    resources = values(local.lock_object_arns)
  }
}

resource "aws_iam_role_policy" "plan" {
  name   = "${var.name_prefix}-plan"
  role   = aws_iam_role.plan.id
  policy = data.aws_iam_policy_document.plan.json
}

data "aws_iam_policy_document" "apply" {
  for_each                = var.environments
  source_policy_documents = [data.aws_iam_policy_document.provider_read.json]

  statement {
    sid       = "UseRdsManagedKmsKey"
    actions   = ["kms:CreateGrant", "kms:DescribeKey"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:CallerAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["rds.${data.aws_region.current.region}.amazonaws.com"]
    }
  }

  statement {
    sid       = "StateBucketLocation"
    actions   = ["s3:GetBucketLocation"]
    resources = [local.state_bucket_arn]
  }

  statement {
    sid       = "StateBucketList"
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket_arn]

    condition {
      test     = "StringEquals"
      variable = "s3:prefix"
      values   = [each.value.state_key, "${each.value.state_key}.tflock"]
    }
  }

  statement {
    sid       = "StateObjects"
    actions   = ["s3:DeleteObject", "s3:GetObject", "s3:PutObject"]
    resources = [local.state_object_arns[each.key], local.lock_object_arns[each.key]]
  }

  statement {
    sid = "EnvironmentInfrastructure"
    actions = [
      "apigateway:*",
      "application-autoscaling:*",
      "cloudwatch:*",
      "ec2:*",
      "ecr:*",
      "ecs:*",
      "elasticloadbalancing:*",
      "logs:*",
      "rds:*",
      "secretsmanager:*",
      "sns:*"
    ]
    resources = ["*"]
  }

  statement {
    sid    = "DenyOtherEnvironmentResources"
    effect = "Deny"
    actions = [
      "apigateway:*",
      "application-autoscaling:*",
      "cloudwatch:*",
      "ec2:*",
      "ecr:*",
      "ecs:*",
      "elasticloadbalancing:*",
      "logs:*",
      "rds:*",
      "secretsmanager:*",
      "sns:*"
    ]
    resources = ["*"]

    condition {
      test     = "Null"
      variable = "aws:ResourceTag/Environment"
      values   = ["false"]
    }

    condition {
      test     = "StringNotEquals"
      variable = "aws:ResourceTag/Environment"
      values   = [each.key]
    }
  }

  statement {
    sid    = "DenyOtherEnvironmentRequests"
    effect = "Deny"
    actions = [
      "apigateway:*",
      "application-autoscaling:*",
      "cloudwatch:*",
      "ec2:*",
      "ecr:*",
      "ecs:*",
      "elasticloadbalancing:*",
      "logs:*",
      "rds:*",
      "secretsmanager:*",
      "sns:*"
    ]
    resources = ["*"]

    condition {
      test     = "Null"
      variable = "aws:RequestTag/Environment"
      values   = ["false"]
    }

    condition {
      test     = "StringNotEquals"
      variable = "aws:RequestTag/Environment"
      values   = [each.key]
    }
  }

  statement {
    sid = "ManageEnvironmentIam"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreatePolicy",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicy",
      "iam:DeletePolicyVersion",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:ListPolicyVersions",
      "iam:ListRolePolicies",
      "iam:PutRolePolicy",
      "iam:TagPolicy",
      "iam:TagRole",
      "iam:UntagPolicy",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy"
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${each.value.infrastructure_name_prefix}-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${each.value.infrastructure_name_prefix}-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${var.name_prefix}-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.name_prefix}-*"
    ]
  }

  statement {
    sid       = "CreateBoundedEnvironmentRoles"
    actions   = ["iam:CreateRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${each.value.infrastructure_name_prefix}-*"]

    condition {
      test     = "ArnEquals"
      variable = "iam:PermissionsBoundary"
      values   = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/franchise-runtime-permissions-boundary"]
    }
  }

  statement {
    sid       = "PassEnvironmentRoles"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${each.value.infrastructure_name_prefix}-*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com", "vpc-flow-logs.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "apply" {
  for_each = var.environments

  name   = "${var.name_prefix}-${each.key}-apply"
  role   = aws_iam_role.apply[each.key].id
  policy = data.aws_iam_policy_document.apply[each.key].json
}

data "aws_iam_policy_document" "deploy" {
  for_each = var.environments

  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer"
    ]
    resources = each.value.ecr_pull_repository_arns
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchDeleteImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]
    resources = each.value.ecr_push_repository_arns
  }

  statement {
    actions = [
      "ecs:DescribeServices",
      "ecs:DescribeTasks",
      "ecs:ListTasks",
      "ecs:StopTask",
      "ecs:UpdateService"
    ]
    resources = [local.ecs_cluster_arns[each.key], local.ecs_service_arns[each.key], local.ecs_task_arns[each.key]]
  }

  statement {
    actions   = ["ecs:DescribeTaskDefinition"]
    resources = ["*"]
  }

  statement {
    actions   = ["ecs:RunTask"]
    resources = local.deploy_task_definitions[each.key]

    condition {
      test     = "ArnEquals"
      variable = "ecs:cluster"
      values   = [local.ecs_cluster_arns[each.key]]
    }
  }

  statement {
    actions   = ["iam:PassRole"]
    resources = each.value.ecs_pass_role_arns

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "deploy" {
  for_each = var.environments

  name   = "${var.name_prefix}-${each.key}-deploy"
  role   = aws_iam_role.deploy[each.key].id
  policy = data.aws_iam_policy_document.deploy[each.key].json
}
