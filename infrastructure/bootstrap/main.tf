data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  apply_role_name       = "franchise-terraform-apply"
  runtime_boundary_name = "franchise-runtime-permissions-boundary"
  ci_boundary_name      = "franchise-boundary-ci"
  runtime_boundary_arn  = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${local.runtime_boundary_name}"
  ci_boundary_arn       = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${local.ci_boundary_name}"
  account_ci_role_arns = [
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-ci-account-plan",
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-ci-account-dev-apply",
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-ci-account-prod-apply"
  ]
  github_oidc_provider_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
}

data "aws_iam_policy_document" "runtime_boundary" {
  statement {
    sid = "RuntimeEcrAndLogs"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetAuthorizationToken",
      "ecr:GetDownloadUrlForLayer",
      "logs:CreateLogStream",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
      "logs:PutLogEvents"
    ]
    resources = ["*"]
  }

  statement {
    sid = "RuntimeDatabaseSecrets"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:PutSecretValue"
    ]
    resources = [
      "arn:aws:secretsmanager:*:${data.aws_caller_identity.current.account_id}:secret:/franchise/dev/database/*",
      "arn:aws:secretsmanager:*:${data.aws_caller_identity.current.account_id}:secret:/franchise/prod/database/*",
      "arn:aws:secretsmanager:*:${data.aws_caller_identity.current.account_id}:secret:rds!db-*"
    ]
  }
}

resource "aws_iam_policy" "runtime_boundary" {
  name        = local.runtime_boundary_name
  description = "Maximum permissions available to franchise runtime roles"
  policy      = data.aws_iam_policy_document.runtime_boundary.json
  tags        = var.tags
}

data "aws_iam_policy_document" "ci_boundary" {
  statement {
    sid       = "CiProjectInfrastructure"
    actions   = ["apigateway:*", "application-autoscaling:*", "cloudwatch:*", "ec2:*", "ecr:*", "ecs:*", "elasticloadbalancing:*", "logs:*", "rds:*", "secretsmanager:*", "sns:*"]
    resources = ["*"]
  }

  statement {
    sid       = "CiRdsKms"
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
    sid       = "CiSecretsManagerKms"
    actions   = ["kms:CreateGrant", "kms:Decrypt", "kms:DescribeKey", "kms:Encrypt", "kms:GenerateDataKey*", "kms:ReEncrypt*"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:CallerAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${data.aws_region.current.region}.amazonaws.com"]
    }
  }

  statement {
    sid       = "CiTerraformStateBucket"
    actions   = ["s3:GetBucketLocation", "s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.state_bucket_name}"]
  }

  statement {
    sid     = "CiTerraformStateObjects"
    actions = ["s3:DeleteObject", "s3:GetObject", "s3:PutObject"]
    resources = [
      "arn:aws:s3:::${var.state_bucket_name}/dev/infrastructure.tfstate",
      "arn:aws:s3:::${var.state_bucket_name}/dev/infrastructure.tfstate.tflock",
      "arn:aws:s3:::${var.state_bucket_name}/prod/infrastructure.tfstate",
      "arn:aws:s3:::${var.state_bucket_name}/prod/infrastructure.tfstate.tflock"
    ]
  }

  statement {
    sid       = "CiIamRead"
    actions   = ["iam:Get*", "iam:List*"]
    resources = ["*"]
  }

  statement {
    sid = "CiProjectIam"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreateOpenIDConnectProvider",
      "iam:CreatePolicy",
      "iam:CreatePolicyVersion",
      "iam:CreateRole",
      "iam:CreateServiceLinkedRole",
      "iam:DeleteOpenIDConnectProvider",
      "iam:DeletePolicy",
      "iam:DeletePolicyVersion",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:PutRolePolicy",
      "iam:Tag*",
      "iam:Untag*",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateOpenIDConnectProviderThumbprint"
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/franchise-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-*",
      local.github_oidc_provider_arn
    ]
  }

  statement {
    sid     = "CiPassRuntimeRoles"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-api-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-database-bootstrap-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-migration-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-vpc-flow-logs",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-api-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-database-bootstrap-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-migration-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-vpc-flow-logs"
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com", "vpc-flow-logs.amazonaws.com"]
    }
  }

  statement {
    sid       = "DenyBoundaryMutation"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary", "iam:PutRolePermissionsBoundary"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-*"]
  }
}

resource "aws_iam_policy" "ci_boundary" {
  name        = local.ci_boundary_name
  description = "Maximum permissions available to franchise CI roles"
  policy      = data.aws_iam_policy_document.ci_boundary.json
  tags        = var.tags
}

resource "aws_s3_bucket" "terraform_state" {
  bucket = var.state_bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  depends_on = [aws_s3_bucket_versioning.terraform_state]

  rule {
    id     = "expire-noncurrent-state-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.noncurrent_version_expiration_days
    }
  }
}

data "aws_iam_policy_document" "apply_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "AWS"
      identifiers = [var.bootstrap_principal_arn]
    }
  }

}

resource "aws_iam_role" "terraform_apply" {
  name                 = local.apply_role_name
  assume_role_policy   = data.aws_iam_policy_document.apply_assume_role.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "terraform_apply_power_user" {
  role       = aws_iam_role.terraform_apply.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "terraform_apply_iam" {
  statement {
    sid = "ManageFranchiseIamResources"
    actions = [
      "iam:CreatePolicy",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicy",
      "iam:DeletePolicyVersion",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
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
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/franchise-dev-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/franchise-prod-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-*"
    ]
  }

  statement {
    sid     = "CreateBoundedRuntimeRoles"
    actions = ["iam:CreateRole"]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-*"
    ]

    condition {
      test     = "ArnEquals"
      variable = "iam:PermissionsBoundary"
      values   = [local.runtime_boundary_arn]
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid       = "CreateBoundedCiRoles"
      actions   = ["iam:CreateRole"]
      resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-ci-*"]

      condition {
        test     = "ArnEquals"
        variable = "iam:PermissionsBoundary"
        values   = [local.ci_boundary_arn]
      }
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid = "ManageCiRoles"
      actions = [
        "iam:DeleteRole",
        "iam:DeleteRolePolicy",
        "iam:GetRole",
        "iam:GetRolePolicy",
        "iam:ListAttachedRolePolicies",
        "iam:ListRolePolicies",
        "iam:PutRolePolicy",
        "iam:TagRole",
        "iam:UntagRole",
        "iam:UpdateAssumeRolePolicy"
      ]
      resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-ci-*"]
    }
  }

  statement {
    sid       = "DenyProjectBoundaryRemoval"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-*"]
  }

  statement {
    sid     = "SetApprovedRuntimeBoundary"
    actions = ["iam:PutRolePermissionsBoundary"]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-*"
    ]

    condition {
      test     = "ArnEquals"
      variable = "iam:PermissionsBoundary"
      values   = [local.runtime_boundary_arn]
    }
  }

  statement {
    sid     = "DenyUnapprovedRuntimeBoundary"
    effect  = "Deny"
    actions = ["iam:PutRolePermissionsBoundary"]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-*"
    ]

    condition {
      test     = "ArnNotEquals"
      variable = "iam:PermissionsBoundary"
      values   = [local.runtime_boundary_arn]
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid       = "SetApprovedCiBoundary"
      actions   = ["iam:PutRolePermissionsBoundary"]
      resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-ci-*"]

      condition {
        test     = "ArnEquals"
        variable = "iam:PermissionsBoundary"
        values   = [local.ci_boundary_arn]
      }
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid       = "DenyUnapprovedCiBoundary"
      effect    = "Deny"
      actions   = ["iam:PutRolePermissionsBoundary"]
      resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-ci-*"]

      condition {
        test     = "ArnNotEquals"
        variable = "iam:PermissionsBoundary"
        values   = [local.ci_boundary_arn]
      }
    }
  }

  statement {
    sid = "AttachFranchiseManagedPolicies"
    actions = [
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy"
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-*"
    ]

    condition {
      test     = "ArnLike"
      variable = "iam:PolicyARN"
      values = [
        "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/franchise-dev-*",
        "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/franchise-prod-*",
        "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
      ]
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid = "ManageGithubOidcProvider"
      actions = [
        "iam:DeleteOpenIDConnectProvider",
        "iam:GetOpenIDConnectProvider",
        "iam:TagOpenIDConnectProvider",
        "iam:UntagOpenIDConnectProvider",
        "iam:UpdateOpenIDConnectProviderThumbprint"
      ]
      resources = [local.github_oidc_provider_arn]
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid       = "CreateGithubOidcProvider"
      actions   = ["iam:CreateOpenIDConnectProvider"]
      resources = ["*"]
    }
  }

  statement {
    sid     = "PassFranchiseRolesToApprovedServices"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-dev-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/franchise-prod-*"
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values = [
        "ecs-tasks.amazonaws.com",
        "vpc-flow-logs.amazonaws.com"
      ]
    }
  }
}

resource "aws_iam_role_policy" "terraform_apply_iam" {
  name   = "franchise-terraform-apply-iam"
  role   = aws_iam_role.terraform_apply.id
  policy = data.aws_iam_policy_document.terraform_apply_iam.json
}

data "aws_iam_policy_document" "terraform_state" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.terraform_state.arn,
      "${aws_s3_bucket.terraform_state.arn}/*"
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  statement {
    sid     = "DenyBootstrapPrincipalEnvironmentState"
    effect  = "Deny"
    actions = ["s3:DeleteObject", "s3:GetObject", "s3:GetObjectVersion", "s3:PutObject"]
    resources = [
      "${aws_s3_bucket.terraform_state.arn}/dev/*",
      "${aws_s3_bucket.terraform_state.arn}/prod/*"
    ]

    principals {
      type        = "AWS"
      identifiers = [var.bootstrap_principal_arn]
    }
  }

  statement {
    sid     = "DenyUnapprovedStatePrincipals"
    effect  = "Deny"
    actions = ["s3:DeleteObject", "s3:GetObject", "s3:GetObjectVersion", "s3:PutObject"]
    resources = [
      "${aws_s3_bucket.terraform_state.arn}/dev/infrastructure.tfstate",
      "${aws_s3_bucket.terraform_state.arn}/dev/infrastructure.tfstate.tflock",
      "${aws_s3_bucket.terraform_state.arn}/prod/infrastructure.tfstate",
      "${aws_s3_bucket.terraform_state.arn}/prod/infrastructure.tfstate.tflock"
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "ArnNotEquals"
      variable = "aws:PrincipalArn"
      values   = concat([aws_iam_role.terraform_apply.arn], var.enable_ci_identity ? local.account_ci_role_arns : [])
    }
  }

  statement {
    sid    = "DenyApplyRoleBootstrapManagement"
    effect = "Deny"
    not_actions = [
      "s3:DeleteObject",
      "s3:GetBucketLocation",
      "s3:GetObject",
      "s3:ListBucket",
      "s3:PutObject"
    ]
    resources = [
      aws_s3_bucket.terraform_state.arn,
      "${aws_s3_bucket.terraform_state.arn}/*"
    ]

    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.terraform_apply.arn]
    }
  }

  statement {
    sid    = "AllowBootstrapManagement"
    effect = "Allow"
    actions = [
      "s3:DeleteBucket",
      "s3:DeleteBucketPolicy",
      "s3:GetLifecycleConfiguration",
      "s3:GetBucketLocation",
      "s3:GetBucketPolicy",
      "s3:GetBucketPublicAccessBlock",
      "s3:GetBucketTagging",
      "s3:GetBucketVersioning",
      "s3:GetEncryptionConfiguration",
      "s3:ListBucket",
      "s3:PutLifecycleConfiguration",
      "s3:PutBucketPolicy",
      "s3:PutBucketPublicAccessBlock",
      "s3:PutBucketTagging",
      "s3:PutBucketVersioning",
      "s3:PutEncryptionConfiguration"
    ]
    resources = [aws_s3_bucket.terraform_state.arn]

    principals {
      type        = "AWS"
      identifiers = [var.bootstrap_principal_arn]
    }
  }

  statement {
    sid    = "AllowBootstrapObjectManagement"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:GetObject",
      "s3:GetObjectVersion",
      "s3:PutObject"
    ]
    resources = ["${aws_s3_bucket.terraform_state.arn}/*"]

    principals {
      type        = "AWS"
      identifiers = [var.bootstrap_principal_arn]
    }
  }

  statement {
    sid       = "AllowTerraformStateBucketLocation"
    effect    = "Allow"
    actions   = ["s3:GetBucketLocation"]
    resources = [aws_s3_bucket.terraform_state.arn]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:PrincipalArn"
      values   = concat([aws_iam_role.terraform_apply.arn], var.enable_ci_identity ? local.account_ci_role_arns : [])
    }
  }

  statement {
    sid       = "AllowHumanTerraformStateBucketList"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.terraform_state.arn]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:PrincipalArn"
      values   = [aws_iam_role.terraform_apply.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "s3:prefix"
      values = [
        "dev/infrastructure.tfstate",
        "dev/infrastructure.tfstate.tflock",
        "prod/infrastructure.tfstate",
        "prod/infrastructure.tfstate.tflock"
      ]
    }
  }

  statement {
    sid    = "AllowHumanTerraformStateObjects"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject"
    ]
    resources = [
      "${aws_s3_bucket.terraform_state.arn}/dev/infrastructure.tfstate",
      "${aws_s3_bucket.terraform_state.arn}/dev/infrastructure.tfstate.tflock",
      "${aws_s3_bucket.terraform_state.arn}/prod/infrastructure.tfstate",
      "${aws_s3_bucket.terraform_state.arn}/prod/infrastructure.tfstate.tflock"
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:PrincipalArn"
      values   = [aws_iam_role.terraform_apply.arn]
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid       = "AllowCiPlanStateBucketList"
      effect    = "Allow"
      actions   = ["s3:ListBucket"]
      resources = [aws_s3_bucket.terraform_state.arn]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "ArnEquals"
        variable = "aws:PrincipalArn"
        values   = [local.account_ci_role_arns[0]]
      }

      condition {
        test     = "StringEquals"
        variable = "s3:prefix"
        values = [
          "dev/infrastructure.tfstate",
          "dev/infrastructure.tfstate.tflock",
          "prod/infrastructure.tfstate",
          "prod/infrastructure.tfstate.tflock"
        ]
      }
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid       = "AllowCiPlanStateRead"
      effect    = "Allow"
      actions   = ["s3:GetObject"]
      resources = [for key in ["dev/infrastructure.tfstate", "prod/infrastructure.tfstate"] : "${aws_s3_bucket.terraform_state.arn}/${key}"]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "ArnEquals"
        variable = "aws:PrincipalArn"
        values   = [local.account_ci_role_arns[0]]
      }
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? [1] : []

    content {
      sid     = "AllowCiPlanStateLocks"
      effect  = "Allow"
      actions = ["s3:DeleteObject", "s3:GetObject", "s3:PutObject"]
      resources = [
        "${aws_s3_bucket.terraform_state.arn}/dev/infrastructure.tfstate.tflock",
        "${aws_s3_bucket.terraform_state.arn}/prod/infrastructure.tfstate.tflock"
      ]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "ArnEquals"
        variable = "aws:PrincipalArn"
        values   = [local.account_ci_role_arns[0]]
      }
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? { dev = local.account_ci_role_arns[1], prod = local.account_ci_role_arns[2] } : {}

    content {
      sid       = "AllowCi${title(statement.key)}ApplyStateBucketList"
      effect    = "Allow"
      actions   = ["s3:ListBucket"]
      resources = [aws_s3_bucket.terraform_state.arn]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "ArnEquals"
        variable = "aws:PrincipalArn"
        values   = [statement.value]
      }

      condition {
        test     = "StringEquals"
        variable = "s3:prefix"
        values = [
          "${statement.key}/infrastructure.tfstate",
          "${statement.key}/infrastructure.tfstate.tflock"
        ]
      }
    }
  }

  dynamic "statement" {
    for_each = var.enable_ci_identity ? { dev = local.account_ci_role_arns[1], prod = local.account_ci_role_arns[2] } : {}

    content {
      sid     = "AllowCi${title(statement.key)}ApplyState"
      effect  = "Allow"
      actions = ["s3:DeleteObject", "s3:GetObject", "s3:PutObject"]
      resources = [
        "${aws_s3_bucket.terraform_state.arn}/${statement.key}/infrastructure.tfstate",
        "${aws_s3_bucket.terraform_state.arn}/${statement.key}/infrastructure.tfstate.tflock"
      ]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "ArnEquals"
        variable = "aws:PrincipalArn"
        values   = [statement.value]
      }
    }
  }
}

resource "aws_s3_bucket_policy" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  policy = data.aws_iam_policy_document.terraform_state.json

  depends_on = [aws_s3_bucket_public_access_block.terraform_state]
}
