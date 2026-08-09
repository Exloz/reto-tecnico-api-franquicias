#!/bin/sh
set -eu

query=${1:?Usage: deployment/scripts/db-query-readonly.sh "SELECT ..."}
region=${AWS_REGION:-us-east-1}
account_id=127321794531
cluster=franchise-dev-cluster
environment_directory=deployment/terraform/environments/dev
log_group=/ecs/franchise-dev-database-bootstrap
task_definition=

for dependency in aws jq terraform; do
    command -v "$dependency" > /dev/null 2>&1 || {
        printf '%s is required\n' "$dependency" >&2
        exit 1
    }
done

caller_arn=$(aws sts get-caller-identity --query Arn --output text)
case "$caller_arn" in
    *:assumed-role/franchise-terraform-apply/*)
        ;;
    *)
        credentials=$(aws sts assume-role \
            --role-arn "arn:aws:iam::$account_id:role/franchise-terraform-apply" \
            --role-session-name franchise-db-inspect \
            --output json)
        AWS_ACCESS_KEY_ID="$(printf '%s' "$credentials" | jq -r '.Credentials.AccessKeyId')"
        AWS_SECRET_ACCESS_KEY="$(printf '%s' "$credentials" | jq -r '.Credentials.SecretAccessKey')"
        AWS_SESSION_TOKEN="$(printf '%s' "$credentials" | jq -r '.Credentials.SessionToken')"
        export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
        unset AWS_PROFILE
        ;;
esac

cleanup() {
    if [ -n "$task_definition" ]; then
        aws ecs deregister-task-definition --task-definition "$task_definition" > /dev/null 2>&1 || true
    fi
}

trap cleanup EXIT HUP INT TERM

terraform -chdir="$environment_directory" init -input=false > /dev/null

source_arn=$(aws ecs list-task-definitions \
    --family-prefix franchise-dev-database-bootstrap \
    --status ACTIVE \
    --sort DESC \
    --query 'taskDefinitionArns[0]' \
    --output text)

if [ "$source_arn" = None ]; then
    source_arn=$(aws ecs list-task-definitions \
        --family-prefix franchise-dev-database-bootstrap \
        --status INACTIVE \
        --sort DESC \
        --query 'taskDefinitionArns[0]' \
        --output text)
fi

if [ "$source_arn" = None ]; then
    printf '%s\n' 'No database bootstrap task definition exists. Run the bootstrap stage first.' >&2
    exit 1
fi

database_host=$(aws rds describe-db-instances \
    --db-instance-identifier franchise-dev-database \
    --query 'DBInstances[0].Endpoint.Address' \
    --output text)
application_secret_arn=$(aws secretsmanager describe-secret \
    --secret-id /franchise/dev/database/application \
    --query ARN \
    --output text)
source_definition=$(aws ecs describe-task-definition \
    --task-definition "$source_arn" \
    --output json)
shell_command="secret=\$(aws secretsmanager get-secret-value --region \"\$AWS_REGION\" --secret-id \"\$APPLICATION_SECRET_ARN\" --query SecretString --output text) && export PGUSER=\$(printf \"%s\" \"\$secret\" | jq -r .username) PGPASSWORD=\$(printf \"%s\" \"\$secret\" | jq -r .password) PGHOST=\"\$DB_HOST\" PGPORT=\"\$DB_PORT\" PGDATABASE=\"\$DB_NAME\" PGSSLMODE=verify-full PGSSLROOTCERT=/etc/ssl/certs/aws-rds-global-bundle.pem PGOPTIONS=\"-c default_transaction_read_only=on\" && unset secret && exec psql -X --no-psqlrc --set ON_ERROR_STOP=on --command \"\$DB_QUERY\""

registration=$(printf '%s' "$source_definition" | jq -c \
    --arg host "$database_host" \
    --arg secret "$application_secret_arn" \
    --arg query "$query" \
    --arg command "$shell_command" \
    --arg region "$region" \
    '.taskDefinition | {
        family: "franchise-dev-db-inspect",
        taskRoleArn,
        executionRoleArn,
        networkMode,
        volumes,
        placementConstraints,
        requiresCompatibilities,
        cpu,
        memory,
        runtimePlatform,
        containerDefinitions: [
            .containerDefinitions[0]
            | .name = "db-inspect"
            | .entryPoint = ["/bin/sh", "-c"]
            | .command = [$command]
            | .environment = [
                {name: "AWS_REGION", value: $region},
                {name: "DB_HOST", value: $host},
                {name: "DB_PORT", value: "5432"},
                {name: "DB_NAME", value: "franchise"},
                {name: "APPLICATION_SECRET_ARN", value: $secret},
                {name: "DB_QUERY", value: $query}
            ]
            | .secrets = []
            | .logConfiguration.options["awslogs-stream-prefix"] = "db-inspect"
        ]
    }')

task_definition=$(aws ecs register-task-definition \
    --cli-input-json "$registration" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)
network=$(terraform -chdir="$environment_directory" output \
    -json bootstrap_run_task_network_configuration)
network_configuration=$(printf '%s' "$network" | jq -r \
    '"awsvpcConfiguration={subnets=[" + (.subnets | join(",")) + "],securityGroups=[" + (.security_groups | join(",")) + "],assignPublicIp=" + .assign_public_ip + "}"')
run_result=$(aws ecs run-task \
    --cluster "$cluster" \
    --launch-type FARGATE \
    --task-definition "$task_definition" \
    --network-configuration "$network_configuration" \
    --output json)

if [ "$(printf '%s' "$run_result" | jq '.failures | length')" -ne 0 ]; then
    printf '%s' "$run_result" | jq '.failures' >&2
    exit 1
fi

task_arn=$(printf '%s' "$run_result" | jq -r '.tasks[0].taskArn')
task_id=${task_arn##*/}
aws ecs wait tasks-stopped --cluster "$cluster" --tasks "$task_arn"
task_result=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" --output json)
exit_code=$(printf '%s' "$task_result" | jq -r '.tasks[0].containers[0].exitCode')
log_stream="db-inspect/db-inspect/$task_id"
attempt=0

until log_result=$(aws logs get-log-events \
    --log-group-name "$log_group" \
    --log-stream-name "$log_stream" \
    --start-from-head \
    --output json 2> /dev/null); do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 12 ]; then
        printf 'Logs were not available at %s/%s\n' "$log_group" "$log_stream" >&2
        break
    fi
    sleep 5
done

if [ -n "${log_result:-}" ]; then
    printf '%s' "$log_result" | jq -r '.events[].message'
fi

if [ "$exit_code" -ne 0 ]; then
    printf 'Database inspection failed with exit code %s: %s\n' \
        "$exit_code" \
        "$(printf '%s' "$task_result" | jq -r '.tasks[0].stoppedReason')" >&2
    exit "$exit_code"
fi
