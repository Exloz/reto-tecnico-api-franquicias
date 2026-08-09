#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
    printf 'usage: %s <main-sha> <owner/repository>\n' "$0" >&2
    exit 2
fi

main_sha=$1
repository=$2
parent_line=$(git rev-list --parents -n 1 "$main_sha")

if [ "$(printf '%s\n' "$parent_line" | wc -w | tr -d ' ')" -ne 3 ]; then
    printf 'Production promotion requires a two-parent merge commit\n' >&2
    exit 1
fi

development_sha=$(git rev-parse "${main_sha}^2")
pull_requests=$(gh api \
    -H 'Accept: application/vnd.github+json' \
    "repos/${repository}/commits/${main_sha}/pulls")
matches=$(printf '%s' "$pull_requests" | jq \
    --arg main_sha "$main_sha" \
    --arg development_sha "$development_sha" \
    '[.[] | select(
        .base.ref == "main" and
        .head.ref == "development" and
        .merge_commit_sha == $main_sha and
        .head.sha == $development_sha and
        .merged_at != null
    )]')

if [ "$(printf '%s' "$matches" | jq 'length')" -ne 1 ]; then
    printf 'The main commit is not the unique merge of development approved for promotion\n' >&2
    exit 1
fi

printf '%s\n' "$development_sha"
