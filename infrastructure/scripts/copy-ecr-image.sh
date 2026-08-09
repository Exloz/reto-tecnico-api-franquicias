#!/bin/sh
set -eu

if [ "$#" -ne 4 ]; then
    printf 'usage: %s <source-repository> <source-reference> <destination-repository> <destination-tag>\n' "$0" >&2
    exit 2
fi

source_repository=$1
source_reference=$2
destination_repository=$3
destination_tag=$4
auth_file=$(mktemp)

cleanup() {
    rm -f "$auth_file"
}

trap cleanup EXIT HUP INT TERM

case "$source_reference" in
    sha256:*) source_image_id="imageDigest=$source_reference" ;;
    *) source_image_id="imageTag=$source_reference" ;;
esac

source_digest=$(aws ecr describe-images \
    --repository-name "$source_repository" \
    --image-ids "$source_image_id" \
    --query 'imageDetails[0].imageDigest' \
    --output text)

if [ -z "$source_digest" ] || [ "$source_digest" = None ]; then
    printf 'Source image did not provide a digest\n' >&2
    exit 1
fi

existing_digest=$(aws ecr describe-images \
    --repository-name "$destination_repository" \
    --image-ids "imageTag=$destination_tag" \
    --query 'imageDetails[0].imageDigest' \
    --output text 2>/dev/null || true)

if [ -n "$existing_digest" ] && [ "$existing_digest" != None ]; then
    if [ "$existing_digest" != "$source_digest" ]; then
        printf 'Immutable destination tag already points to %s instead of %s\n' \
            "$existing_digest" "$source_digest" >&2
        exit 1
    fi
    printf '%s\n' "$source_digest"
    exit 0
fi

account_id=${AWS_ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}
region=${AWS_REGION:-us-east-1}
registry="$account_id.dkr.ecr.$region.amazonaws.com"

aws ecr get-login-password --region "$region" | skopeo login \
    --authfile "$auth_file" \
    --username AWS \
    --password-stdin \
    "$registry" >/dev/null

skopeo copy \
    --authfile "$auth_file" \
    --preserve-digests \
    "docker://$registry/$source_repository@$source_digest" \
    "docker://$registry/$destination_repository:$destination_tag" >/dev/null

destination_digest=$(aws ecr describe-images \
    --repository-name "$destination_repository" \
    --image-ids "imageTag=$destination_tag" \
    --query 'imageDetails[0].imageDigest' \
    --output text)

if [ "$destination_digest" != "$source_digest" ]; then
    printf 'Digest changed during ECR copy: %s != %s\n' \
        "$destination_digest" "$source_digest" >&2
    exit 1
fi

printf '%s\n' "$destination_digest"
