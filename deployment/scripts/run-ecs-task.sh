#!/bin/sh
set -eu

if [ "$#" -ne 4 ]; then
    printf 'usage: %s <cluster> <task-definition> <network-json> <container>\n' "$0" >&2
    exit 2
fi

cluster=$1
task_definition=$2
network_json=$3
container=$4
task_arn=
task_timeout_seconds=${ECS_TASK_TIMEOUT_SECONDS:-900}

case $task_timeout_seconds in
    ''|*[!0-9]*)
        printf 'ECS_TASK_TIMEOUT_SECONDS must be a positive integer\n' >&2
        exit 2
        ;;
    0)
        printf 'ECS_TASK_TIMEOUT_SECONDS must be greater than zero\n' >&2
        exit 2
        ;;
esac

cleanup() {
    if [ -n "$task_arn" ]; then
        aws ecs stop-task --cluster "$cluster" --task "$task_arn" \
            --reason 'CI task interrupted' >/dev/null 2>&1 || true
    fi
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

network_configuration=$(printf '%s' "$network_json" | jq -c '{
    awsvpcConfiguration: {
        subnets: .subnets,
        securityGroups: .security_groups,
        assignPublicIp: .assign_public_ip
    }
}')

run_result=$(aws ecs run-task \
    --cluster "$cluster" \
    --task-definition "$task_definition" \
    --launch-type FARGATE \
    --platform-version 1.4.0 \
    --network-configuration "$network_configuration" \
    --count 1 \
    --output json)

if [ "$(printf '%s' "$run_result" | jq '.failures | length')" -ne 0 ]; then
    printf '%s\n' "$run_result" | jq '.failures' >&2
    exit 1
fi

task_arn=$(printf '%s' "$run_result" | jq -r '.tasks[0].taskArn // empty')
if [ -z "$task_arn" ]; then
    printf 'ECS did not return a task ARN\n' >&2
    exit 1
fi

deadline=$(($(date +%s) + task_timeout_seconds))
while :; do
    if ! task_status=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" \
        --query 'tasks[0].lastStatus' --output text 2>/dev/null); then
        task_status=UNKNOWN
    fi
    [ "$task_status" = STOPPED ] && break
    if [ "$(date +%s)" -ge "$deadline" ]; then
        printf 'ECS task did not stop within %s seconds\n' "$task_timeout_seconds" >&2
        exit 1
    fi
    sleep 10
done
task_result=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" --output json)
exit_code=$(printf '%s' "$task_result" | jq -r --arg container "$container" \
    '.tasks[0].containers[] | select(.name == $container) | .exitCode // empty')

if [ "$exit_code" != 0 ]; then
    printf '%s\n' "$task_result" | jq --arg container "$container" '{
        stoppedReason: .tasks[0].stoppedReason,
        container: (.tasks[0].containers[] | select(.name == $container) | {
            name,
            exitCode,
            reason
        })
    }' >&2
    exit 1
fi

completed_task_arn=$task_arn
task_arn=
trap - EXIT HUP INT TERM
printf '%s\n' "$completed_task_arn"
