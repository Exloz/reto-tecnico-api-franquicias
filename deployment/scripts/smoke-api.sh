#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
    printf 'usage: %s <api-base-url>\n' "$0" >&2
    exit 2
fi

base_url=${1%/}
correlation_id="deployment-smoke-$(date -u +%Y%m%d%H%M%S)"
response_file=$(mktemp)

cleanup() {
    rm -f "$response_file"
}

trap cleanup EXIT HUP INT TERM

curl --fail-with-body --silent --show-error \
    --connect-timeout 5 \
    --max-time 320 \
    --retry 30 \
    --retry-all-errors \
    --retry-delay 10 \
    --retry-max-time 300 \
    "$base_url/actuator/health/readiness" \
    -o "$response_file"
jq -e '.status == "UP"' "$response_file" >/dev/null

status=$(curl --silent --show-error \
    --connect-timeout 5 \
    --max-time 30 \
    --output "$response_file" \
    --write-out '%{http_code}' \
    --header "X-Correlation-ID: $correlation_id" \
    "$base_url/api/v1/franchises/00000000-0000-0000-0000-000000000000/branches/top-stock-products?limit=1")

if [ "$status" != 404 ]; then
    printf 'Functional smoke returned HTTP %s: ' "$status" >&2
    cat "$response_file" >&2
    printf '\n' >&2
    exit 1
fi

jq -e --arg correlation_id "$correlation_id" \
    '.type == "urn:franchise-api:problem:resource-not-found" and
     .status == 404 and
     .correlationId == $correlation_id' "$response_file" >/dev/null
printf 'Readiness and functional smoke passed with correlation ID %s\n' "$correlation_id"
