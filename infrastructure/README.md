# AWS infrastructure

Terraform provisions the phase 07 AWS platform in `us-east-1`. Reusable capabilities live under `modules/`; `environments/dev` and `environments/prod` are independent root modules and do not use workspaces.

## Prerequisites

- Terraform 1.10 or newer.
- AWS account `127321794531` authenticated without static access keys.
- State bucket `franchise-127321794531-terraform-state` provisioned from `bootstrap/`.
- Role `arn:aws:iam::127321794531:role/franchise-terraform-apply` available for the human operator to assume outside Terraform.

Environment roots do not assume roles. The human operator must invoke Terraform with credentials already assumed into `franchise-terraform-apply`; GitHub Actions invokes Terraform with credentials from the intended OIDC role. Backend and provider operations therefore use the same caller identity without role chaining.

The centralized account-level CI identity consists of `franchise-ci-account-plan` for pull requests and environment-specific `franchise-ci-account-{dev,prod}-{apply,deploy}` roles. The plan role can read both real state objects, manage only their lock objects, and perform provider reads. Each apply role accesses only its matching state key and environment infrastructure; each deploy role is restricted to its matching ECR repositories, ECS resources, task families and pass roles. No CI policy can call `sts:AssumeRole`.

## Validate

Validation does not require access to either remote backend:

```sh
terraform -chdir=infrastructure/environments/dev init -backend=false
terraform -chdir=infrastructure/environments/dev validate
terraform -chdir=infrastructure/environments/prod init -backend=false
terraform -chdir=infrastructure/environments/prod validate
```

Run `terraform fmt -recursive infrastructure` before validation.

Trivy ignores `AVD-AWS-0132` because the state bucket deliberately uses the selected SSE-S3 encryption model instead of a customer-managed KMS key.

## Development

`dev.tfvars` creates foundational resources but leaves the API-serving stage disabled. The ECS cluster, API IAM roles, and API log group remain available as foundation; the internal ALB, target group, listener, API Gateway, VPC Link, routes, stage, access-log group, API alarms, and API dashboard widgets are created only with `enable_api_service = true`. The dashboard and RDS alarms remain active before that stage.

Development VPC Flow Logs capture rejected traffic only, and development ECR repositories retain the five most recent images. Production keeps all VPC traffic in Flow Logs and retains ten images.

To plan:

```sh
terraform -chdir=infrastructure/environments/dev init
terraform -chdir=infrastructure/environments/dev plan -var-file=dev.tfvars
```

Initial database startup is deliberately sequential. Enable exactly one stage per apply, run the emitted task definition with its root `run_task_network_configuration`, confirm success, then disable that stage before enabling the next one:

```hcl
enable_bootstrap_task = true
bootstrap_image_digest = "sha256:<64 hexadecimal characters>"

# After bootstrap succeeds:
enable_bootstrap_task = false
enable_migration_task = true
migration_image_digest = "sha256:<64 hexadecimal characters>"

# After `migrate` and `validate` succeed:
enable_migration_task = false
enable_api_service = true
api_image_digest = "sha256:<64 hexadecimal characters>"
```

The root composes each repository URL with its digest. Tags such as `latest` are not accepted.

If the account already has the GitHub Actions OIDC provider, set `create_github_oidc_provider = false` and provide `existing_github_oidc_provider_arn`.

The centralized CI module remains disabled initially through dev `enable_ci_identity = false`. Activate it in this order:

1. Apply bootstrap with `enable_ci_identity = true` as the bootstrap administrator. The bucket policy refers to future CI role ARNs through `aws:PrincipalArn` conditions, so those roles do not need to exist yet.
2. Assume `franchise-terraform-apply` outside Terraform and apply the dev root with `enable_ci_identity = true` to create the single account-level OIDC provider and all five bounded CI roles.
3. Keep GitHub environments `dev` and `prod` restricted to the protected `main` branch. Each environment can assume only its matching apply and deploy roles; pull requests can assume only the plan role.

The bootstrap root creates separate runtime and CI permissions boundaries. Every project role must carry the matching boundary; both the human Terraform role and environment CI apply roles require the runtime boundary during `CreateRole`. CI cannot change or remove role boundaries. Environment deploy roles can pass only their own API and migration roles and can run only their own API and migration task families, never the database-bootstrap family.

RDS generates and names the managed master credential secret (`rds!db-...`); that AWS-generated name supersedes any desired `/franchise/<environment>/database/master` path. The stable custom paths apply only to the migrator and application secrets. Their deletion recovery is configurable with `database_secret_recovery_window_in_days`; dev defaults to immediate deletion (`0`) and prod to 30 days.

## Production guard

`prod.tfvars` keeps `enable_environment = false`. Enabling production requires all of `production_confirmation = "deploy-franchise-prod-127321794531-us-east-1"`, `expected_account_id = "127321794531"`, `expected_region = "us-east-1"`, and an explicit unique `final_snapshot_identifier`. A hard precondition also verifies the actual caller account and provider region. Production values are prepared for two availability zones, Multi-AZ RDS, deletion protection, 30-day backups/logs, and two-to-six API tasks.

## Outputs

Roots expose only stable, nonsecret identifiers: API endpoint, VPC ID, database endpoint, ECR repository URLs, ECS names, task definition ARNs, one-off task network configurations, alarm ARNs, dashboard name, the pull-request plan role ARN, and dev/prod apply and deploy role ARN maps. Bootstrap also exposes both permissions-boundary ARNs. Secret values are never Terraform outputs.
