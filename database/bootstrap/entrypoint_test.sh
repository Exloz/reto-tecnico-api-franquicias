#!/bin/sh
set -eu

test_directory=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
entrypoint="$test_directory/entrypoint.sh"
temporary_root=$(mktemp -d)

cleanup() {
    rm -rf "$temporary_root"
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
    printf 'not ok - %s\n' "$1" >&2
    exit 1
}

assert_equals() {
    expected=$1
    actual=$2
    message=$3
    [ "$actual" = "$expected" ] || fail "$message: expected [$expected], got [$actual]"
}

assert_file_contains() {
    expected=$1
    file=$2
    message=$3
    grep -Fq -- "$expected" "$file" || fail "$message"
}

assert_file_excludes() {
    unexpected=$1
    file=$2
    message=$3
    if grep -Fq -- "$unexpected" "$file"; then
        fail "$message"
    fi
}

read_file() {
    file_value=
    while IFS= read -r file_line || [ -n "$file_line" ]; do
        if [ -n "$file_value" ]; then
            file_value="$file_value
$file_line"
        else
            file_value=$file_line
        fi
    done < "$1"
    printf '%s' "$file_value"
}

assert_output_excludes_secrets() {
    output_directory=$1
    shift
    for secret_value do
        assert_file_excludes "$secret_value" "$output_directory/stdout" "stdout exposed a secret value"
        assert_file_excludes "$secret_value" "$output_directory/stderr" "stderr exposed a secret value"
    done
}

create_mocks() {
    mock_directory=$1
    mkdir -p "$mock_directory/bin" "$mock_directory/state"

    cat > "$mock_directory/bin/aws" <<'MOCK'
#!/bin/sh
set -eu

operation=$2
shift 2
secret_id=
secret_string=
while [ "$#" -gt 0 ]; do
    case "$1" in
        --secret-id)
            secret_id=$2
            shift 2
            ;;
        --secret-string)
            secret_string=$2
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

case "$secret_id" in
    "$MIGRATOR_SECRET_ARN")
        secret_name=migrator
        ;;
    "$APPLICATION_SECRET_ARN")
        secret_name=application
        ;;
    *)
        printf '%s\n' 'unexpected secret id' >&2
        exit 2
        ;;
esac

secret_file="$MOCK_STATE/$secret_name.json"
case "$operation" in
    get-secret-value)
        printf 'get:%s\n' "$secret_name" >> "$MOCK_STATE/events"
        if [ ! -f "$secret_file" ]; then
            printf '%s\n' 'ResourceNotFoundException' >&2
            exit 254
        fi
        IFS= read -r secret_value < "$secret_file"
        printf '%s\n' "$secret_value"
        ;;
    put-secret-value)
        printf 'put:%s\n' "$secret_name" >> "$MOCK_STATE/events"
        if [ "${FAIL_APPLICATION_PUT_ONCE:-0}" = 1 ] &&
            [ "$secret_name" = application ] &&
            [ ! -f "$MOCK_STATE/application-put-failed" ]; then
            : > "$MOCK_STATE/application-put-failed"
            printf '%s\n' 'simulated write failure' >&2
            exit 1
        fi
        source_file=${secret_string#file://}
        IFS= read -r secret_value < "$source_file"
        printf '%s\n' "$secret_value" > "$secret_file"
        printf '%s\n' '{}'
        ;;
    *)
        printf '%s\n' 'unexpected aws operation' >&2
        exit 2
        ;;
esac
MOCK

    cat > "$mock_directory/bin/openssl" <<'MOCK'
#!/bin/sh
set -eu

counter=0
if [ -f "$MOCK_STATE/openssl-count" ]; then
    IFS= read -r counter < "$MOCK_STATE/openssl-count"
fi
counter=$((counter + 1))
printf '%s\n' "$counter" > "$MOCK_STATE/openssl-count"
printf 'generated-password-%s\n' "$counter"
MOCK

    cat > "$mock_directory/bin/jq" <<'MOCK'
#!/bin/sh
set -eu

mode=read
field=
username=
password=
input_file=
while [ "$#" -gt 0 ]; do
    case "$1" in
        --null-input)
            mode=write
            shift
            ;;
        --arg)
            case "$2" in
                username) username=$3 ;;
                password) password=$3 ;;
            esac
            shift 3
            ;;
        *username*)
            field=username
            shift
            ;;
        *password*)
            field=password
            shift
            ;;
        -* )
            shift
            ;;
        *)
            input_file=$1
            shift
            ;;
    esac
done

if [ "$mode" = write ]; then
    printf '{"username":"%s","password":"%s"}\n' "$username" "$password"
    exit 0
fi

IFS= read -r json < "$input_file"
case "$field:$json" in
    username:\{\})
        printf '\n'
        ;;
    password:\{\})
        printf '\n'
        ;;
    username:*)
        value=${json#*\"username\":\"}
        printf '%s\n' "${value%%\"*}"
        ;;
    password:*)
        value=${json#*\"password\":\"}
        printf '%s\n' "${value%%\"*}"
        ;;
    *)
        exit 1
        ;;
esac
MOCK

    cat > "$mock_directory/bin/psql" <<'MOCK'
#!/bin/sh
set -eu

migrator_password=
application_password=
while [ "$#" -gt 0 ]; do
    case "$1" in
        migrator_password=*) migrator_password=${1#*=} ;;
        application_password=*) application_password=${1#*=} ;;
    esac
    shift
done
printf '%s\n' "$migrator_password" > "$MOCK_STATE/psql-migrator-password"
printf '%s\n' "$application_password" > "$MOCK_STATE/psql-application-password"
printf '%s\n' 'psql' >> "$MOCK_STATE/events"

if [ "${PSQL_FAIL:-0}" = 1 ]; then
    printf '%s\n' "$migrator_password"
    printf '%s\n' "$application_password" >&2
    exit 1
fi
MOCK

    chmod +x "$mock_directory/bin/aws" "$mock_directory/bin/openssl" \
        "$mock_directory/bin/jq" "$mock_directory/bin/psql"
    : > "$mock_directory/state/events"
}

run_entrypoint() {
    run_directory=$1
    set +e
    PATH="$run_directory/bin:$PATH" \
        MOCK_STATE="$run_directory/state" \
        DB_HOST=database.example \
        DB_PORT=5432 \
        DB_NAME=franchise \
        MASTER_DB_USERNAME=master_user \
        MASTER_DB_PASSWORD=master-password \
        MIGRATOR_SECRET_ARN=migrator-secret \
        APPLICATION_SECRET_ARN=application-secret \
        AWS_REGION=us-east-1 \
        sh "$entrypoint" > "$run_directory/stdout" 2> "$run_directory/stderr"
    run_status=$?
    set -e
}

test_existing_credentials_are_reused() {
    run_directory="$temporary_root/existing"
    create_mocks "$run_directory"
    printf '%s\n' '{"username":"franchise_migrator","password":"existing-migrator-password"}' \
        > "$run_directory/state/migrator.json"
    printf '%s\n' '{"username":"franchise_app","password":"existing-application-password"}' \
        > "$run_directory/state/application.json"

    run_entrypoint "$run_directory"

    assert_equals 0 "$run_status" 'entrypoint failed with existing credentials'
    events=$(read_file "$run_directory/state/events")
    assert_equals "get:migrator
get:application
psql" "$events" 'existing credentials caused generation or rotation'
    [ ! -f "$run_directory/state/openssl-count" ] || fail 'openssl ran for existing credentials'
    assert_equals existing-migrator-password \
        "$(read_file "$run_directory/state/psql-migrator-password")" \
        'psql did not receive the existing migrator credential'
    assert_equals existing-application-password \
        "$(read_file "$run_directory/state/psql-application-password")" \
        'psql did not receive the existing application credential'
    assert_output_excludes_secrets "$run_directory" master-password \
        existing-migrator-password existing-application-password
    printf '%s\n' 'ok - existing secret credentials are reused without rotation'
}

test_missing_credentials_are_stored_before_psql() {
    run_directory="$temporary_root/missing"
    create_mocks "$run_directory"

    run_entrypoint "$run_directory"

    assert_equals 0 "$run_status" 'entrypoint failed with missing credentials'
    events=$(read_file "$run_directory/state/events")
    assert_equals "get:migrator
put:migrator
get:application
put:application
psql" "$events" 'credentials were not stored before psql'
    assert_file_contains '"password":"generated-password-1"' \
        "$run_directory/state/migrator.json" 'migrator credential was not stored'
    assert_file_contains '"password":"generated-password-2"' \
        "$run_directory/state/application.json" 'application credential was not stored'
    assert_output_excludes_secrets "$run_directory" master-password \
        generated-password-1 generated-password-2
    printf '%s\n' 'ok - missing credentials are generated and stored before psql'
}

test_partial_write_failure_is_recoverable() {
    run_directory="$temporary_root/recovery"
    create_mocks "$run_directory"
    export FAIL_APPLICATION_PUT_ONCE=1

    run_entrypoint "$run_directory"

    assert_equals 1 "$run_status" 'partial secret write failure unexpectedly succeeded'
    events=$(read_file "$run_directory/state/events")
    assert_equals "get:migrator
put:migrator
get:application
put:application" "$events" 'psql ran after a secret write failure'
    assert_output_excludes_secrets "$run_directory" master-password \
        generated-password-1 generated-password-2
    unset FAIL_APPLICATION_PUT_ONCE
    : > "$run_directory/state/events"

    run_entrypoint "$run_directory"

    assert_equals 0 "$run_status" 'rerun did not recover from partial secret write failure'
    events=$(read_file "$run_directory/state/events")
    assert_equals "get:migrator
get:application
put:application
psql" "$events" 'rerun rotated the stored credential or used psql too early'
    assert_file_contains '"password":"generated-password-1"' \
        "$run_directory/state/migrator.json" 'rerun rotated the persisted migrator credential'
    assert_equals generated-password-1 \
        "$(read_file "$run_directory/state/psql-migrator-password")" \
        'rerun did not reuse the persisted migrator credential'
    assert_equals generated-password-3 \
        "$(read_file "$run_directory/state/psql-application-password")" \
        'rerun did not create the still-missing application credential'
    assert_output_excludes_secrets "$run_directory" master-password \
        generated-password-1 generated-password-3
    printf '%s\n' 'ok - partial secret write failure is recoverable on rerun'
}

test_psql_output_cannot_expose_credentials() {
    run_directory="$temporary_root/redaction"
    create_mocks "$run_directory"
    printf '%s\n' '{"username":"franchise_migrator","password":"redacted-migrator-password"}' \
        > "$run_directory/state/migrator.json"
    printf '%s\n' '{"username":"franchise_app","password":"redacted-application-password"}' \
        > "$run_directory/state/application.json"
    export PSQL_FAIL=1

    run_entrypoint "$run_directory"

    unset PSQL_FAIL
    assert_equals 1 "$run_status" 'simulated psql failure unexpectedly succeeded'
    assert_output_excludes_secrets "$run_directory" master-password \
        redacted-migrator-password redacted-application-password
    assert_file_contains 'Unable to converge database roles' "$run_directory/stderr" \
        'entrypoint did not report the psql failure safely'
    printf '%s\n' 'ok - secret values are absent from success and failure output'
}

test_existing_credentials_are_reused
test_missing_credentials_are_stored_before_psql
test_partial_write_failure_is_recoverable
test_psql_output_cannot_expose_credentials
printf '%s\n' '1..4'
