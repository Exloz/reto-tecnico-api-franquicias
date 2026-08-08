#!/bin/sh
set -eu

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${MASTER_DB_USERNAME:?MASTER_DB_USERNAME is required}"
: "${MASTER_DB_PASSWORD:?MASTER_DB_PASSWORD is required}"
: "${MIGRATOR_SECRET_ARN:?MIGRATOR_SECRET_ARN is required}"
: "${APPLICATION_SECRET_ARN:?APPLICATION_SECRET_ARN is required}"
: "${AWS_REGION:?AWS_REGION is required}"

case "$DB_PORT" in
    *[!0-9]*)
        printf '%s\n' 'DB_PORT must be numeric' >&2
        exit 1
        ;;
esac

if [ "$DB_PORT" -lt 1 ] || [ "$DB_PORT" -gt 65535 ]; then
    printf '%s\n' 'DB_PORT must be between 1 and 65535' >&2
    exit 1
fi

umask 077
secret_directory=$(mktemp -d)

cleanup() {
    rm -rf "$secret_directory"
    unset migrator_password application_password secret_password MASTER_DB_PASSWORD PGPASSWORD
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

export AWS_PAGER=
export AWS_CLI_AUTO_PROMPT=off

load_or_create_secret() {
    secret_arn=$1
    expected_username=$2
    secret_file=$3
    error_file=$4
    secret_changed=false

    if aws secretsmanager get-secret-value \
        --region "$AWS_REGION" \
        --secret-id "$secret_arn" \
        --query SecretString \
        --output text > "$secret_file" 2> "$error_file"; then
        :
    elif grep -q 'ResourceNotFoundException' "$error_file"; then
        printf '%s\n' '{}' > "$secret_file"
    else
        printf '%s\n' "Unable to read $expected_username secret" >&2
        exit 1
    fi

    secret_username=$(jq --raw-output \
        'if (.username | type) == "string" then .username else "" end' \
        "$secret_file" 2>/dev/null) || {
        printf '%s\n' "Invalid $expected_username secret" >&2
        exit 1
    }
    secret_password=$(jq --raw-output \
        'if (.password | type) == "string" then .password else "" end' \
        "$secret_file" 2>/dev/null) || {
        printf '%s\n' "Invalid $expected_username secret" >&2
        exit 1
    }

    if [ -n "$secret_username" ] && [ "$secret_username" != "$expected_username" ]; then
        printf '%s\n' "Unexpected username in $expected_username secret" >&2
        exit 1
    fi
    if [ -z "$secret_username" ]; then
        secret_username=$expected_username
        secret_changed=true
    fi
    if [ -z "$secret_password" ]; then
        secret_password=$(openssl rand -hex 32)
        secret_changed=true
    fi

    if [ "$secret_changed" = true ]; then
        jq --compact-output --null-input \
            --arg username "$secret_username" \
            --arg password "$secret_password" \
            '{username: $username, password: $password}' > "$secret_file.new"
        mv "$secret_file.new" "$secret_file"
        if ! aws secretsmanager put-secret-value \
            --region "$AWS_REGION" \
            --secret-id "$secret_arn" \
            --secret-string "file://$secret_file" \
            --output json > /dev/null 2> "$error_file"; then
            printf '%s\n' "Unable to write $expected_username secret" >&2
            exit 1
        fi
    fi
}

load_or_create_secret \
    "$MIGRATOR_SECRET_ARN" franchise_migrator \
    "$secret_directory/migrator.json" "$secret_directory/migrator.error"
migrator_password=$secret_password

load_or_create_secret \
    "$APPLICATION_SECRET_ARN" franchise_app \
    "$secret_directory/application.json" "$secret_directory/application.error"
application_password=$secret_password
unset secret_password secret_username

export PGHOST="$DB_HOST"
export PGPORT="$DB_PORT"
export PGDATABASE="$DB_NAME"
export PGUSER="$MASTER_DB_USERNAME"
export PGPASSWORD="$MASTER_DB_PASSWORD"
export PGSSLMODE=verify-full
export PGSSLROOTCERT=/etc/ssl/certs/aws-rds-global-bundle.pem
export PGCONNECT_TIMEOUT=15

if ! psql -X --no-psqlrc \
    --set ON_ERROR_STOP=on \
    --set db_name="$DB_NAME" \
    --set migrator_password="$migrator_password" \
    --set application_password="$application_password" \
    > "$secret_directory/psql.output" 2>&1 <<'SQL'
BEGIN;
SELECT 'CREATE ROLE franchise_migrator' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'franchise_migrator') \gexec
SELECT 'CREATE ROLE franchise_app' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'franchise_app') \gexec
ALTER ROLE franchise_migrator WITH LOGIN PASSWORD :'migrator_password';
ALTER ROLE franchise_app WITH LOGIN PASSWORD :'application_password';
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname IN ('franchise_migrator', 'franchise_app')
          AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'Database role has forbidden administrative privileges';
    END IF;
END
$$;
REVOKE CREATE ON DATABASE :"db_name" FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT, CREATE ON DATABASE :"db_name" TO franchise_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO franchise_migrator;
GRANT CONNECT ON DATABASE :"db_name" TO franchise_app;
REVOKE CREATE ON DATABASE :"db_name" FROM franchise_app;
REVOKE CREATE ON SCHEMA public FROM franchise_app;
COMMIT;
SQL
then
    grep -E 'psql:|ERROR:|DETAIL:|FATAL:|certificate|connection' "$secret_directory/psql.output" \
        | sed -e "s/$migrator_password/[REDACTED]/g" -e "s/$application_password/[REDACTED]/g" >&2 || true
    printf '%s\n' 'Unable to converge database roles' >&2
    exit 1
fi

printf '%s\n' 'Database roles converged with Secrets Manager values'
