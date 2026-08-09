# AWS infrastructure

Terraform provisions the phase 07 AWS platform in `us-east-1`. Reusable capabilities live under `modules/`; `environments/dev` and `environments/prod` are independent root modules and do not use workspaces.

Operational commands for status checks, AWS k6 tests, private database inspection, pause, destroy and staged recovery are documented in [RUNBOOK.md](RUNBOOK.md).

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

Trivy ignores `AVD-AWS-0132` because the state bucket deliberately uses the selected SSE-S3 encryption model instead of a customer-managed KMS key. It ignores `AWS-0104` because private ECS tasks require outbound HTTPS through the NAT Gateway for AWS public endpoints while cost-driven environments defer interface VPC endpoints. It ignores `AWS-0054` because TLS terminates at the public API Gateway endpoint and the VPC Link reaches the internal ALB listener over security-group-restricted HTTP as defined by the target architecture.

## Development

`dev.tfvars` keeps the API-serving stage active. The ECS cluster, API IAM roles, and API log group remain available when that stage is disabled; the internal ALB, target group, listener, API Gateway, VPC Link, routes, stage, access-log group, API alarms, and API dashboard widgets require `enable_api_service = true`. The dashboard and RDS alarms remain active before that stage.

Development VPC Flow Logs capture rejected traffic only, and development ECR repositories retain the five most recent images. Production keeps all VPC traffic in Flow Logs and retains ten images.

To plan:

```sh
terraform -chdir=infrastructure/environments/dev init
terraform -chdir=infrastructure/environments/dev plan -var-file=dev.tfvars
```

Initial database startup is deliberately sequential. Supplying immutable bootstrap and migration digests keeps both one-off task definitions available while the API remains active. Run bootstrap only for a new database, run Flyway before every API update, and require exit code `0` before continuing:

```hcl
bootstrap_image_digest = "sha256:<64 hexadecimal characters>"
migration_image_digest = "sha256:<64 hexadecimal characters>"

# After `migrate` and `validate` succeed:
enable_api_service = true
api_image_digest = "sha256:<64 hexadecimal characters>"
```

The root composes each repository URL with its digest. Tags such as `latest` are not accepted.

If the account already has the GitHub Actions OIDC provider, set `create_github_oidc_provider = false` and provide `existing_github_oidc_provider_arn`.

The centralized CI module remains disabled initially through dev `enable_ci_identity = false`. Activate it in this order:

1. Apply bootstrap with `enable_ci_identity = true` as the bootstrap administrator. The bucket policy refers to future CI role ARNs through `aws:PrincipalArn` conditions, so those roles do not need to exist yet.
2. Assume `franchise-terraform-apply` outside Terraform and apply the dev root with `enable_ci_identity = true` to create the single account-level OIDC provider and all five bounded CI roles.
3. Restrict GitHub environment `dev` to protected branch `development` and `prod` to protected branch `main`. Require approval from `Exloz` for `prod`. Each environment can assume only its matching apply and deploy roles; pull requests can assume only the plan role.

The bootstrap root creates separate runtime and CI permissions boundaries. Every project role must carry the matching boundary; both the human Terraform role and environment CI apply roles require the runtime boundary during `CreateRole`. CI cannot change or remove role boundaries. Environment deploy roles can pass and run only their own API, migration and database-bootstrap roles and task families. The PROD deploy role can read deployed DEV images but can write only to PROD repositories.

RDS generates and names the managed master credential secret (`rds!db-...`); that AWS-generated name supersedes any desired `/franchise/<environment>/database/master` path. The stable custom paths apply only to the migrator and application secrets. Their deletion recovery is configurable with `database_secret_recovery_window_in_days`; dev defaults to immediate deletion (`0`) and prod to 30 days.

## Production guard

`prod.tfvars` enables the production foundation with the explicit confirmation string, account, region and final snapshot identifier. The API remains disabled until CI promotes tested DEV digests, bootstraps the new database, runs Flyway and performs the final API apply. A hard precondition verifies the actual caller account and provider region. Production uses two availability zones, Multi-AZ RDS, deletion protection, 30-day backups/logs, and two-to-six API tasks.

## Delivery flow

Pull requests to `development` and `main` must pass application quality, mutation, Terraform, TFLint, Trivy and ARM64 container checks. Pull requests to `main` are accepted only from `development` and use merge commits so the promoted DEV commit remains auditable.

A push to `development` builds immutable `commit-<sha>` images, scans fixable CRITICAL vulnerabilities, runs Flyway, deploys DEV, executes readiness and functional smoke tests, and only then records `deployed-<sha>` tags. A push to `main` resolves the second parent of the approved merge, requires its validated DEV tags, copies the exact manifests to PROD without rebuilding, waits for the `prod` environment approval, runs bootstrap when required, runs Flyway, deploys ECS and records a deployment manifest.

ECS circuit breaker handles unhealthy rollouts. If Terraform deployment or smoke validation fails, CI reapplies the previous API digest. Database migrations are forward-only and must remain compatible with both the previous and new API revision.

## Outputs

Roots expose only stable, nonsecret identifiers: API endpoint, VPC ID, database endpoint, ECR repository URLs, ECS names, task definition ARNs, one-off task network configurations, alarm ARNs, dashboard name, the pull-request plan role ARN, and dev/prod apply and deploy role ARN maps. Bootstrap also exposes both permissions-boundary ARNs. Secret values are never Terraform outputs.
