#!/usr/bin/env bash
set -euo pipefail

EVENT_NAME="${EVENT_NAME:?EVENT_NAME is required}"
HEAD_SHA="${HEAD_SHA:?HEAD_SHA is required}"

if [[ ! "$HEAD_SHA" =~ ^[0-9a-f]{40}$ ]] || ! git cat-file -e "${HEAD_SHA}^{commit}" 2>/dev/null; then
    echo "::error::HEAD_SHA is not an available commit."
    exit 1
fi

has_commit() {
    [[ "$1" =~ ^[0-9a-f]{40}$ ]] && git cat-file -e "${1}^{commit}" 2>/dev/null
}

remote_branch_ref() {
    local branch="$1"
    local ref="refs/remotes/origin/$branch"

    git check-ref-format "$ref" >/dev/null 2>&1 || return 1
    git rev-parse --verify "${ref}^{commit}" >/dev/null 2>&1 || return 1
    printf '%s\n' "$ref"
}

if [[ "$EVENT_NAME" == "pull_request" ]]; then
    BASE_SHA="${BASE_SHA:-}"
    BASE_BRANCH="${BASE_BRANCH:?BASE_BRANCH is required for pull requests}"

    if has_commit "$BASE_SHA"; then
        git diff --check "${BASE_SHA}...${HEAD_SHA}"
    else
        if ! BASE_REF="$(remote_branch_ref "$BASE_BRANCH")"; then
            echo "::error::Pull request base SHA is unavailable and origin/$BASE_BRANCH cannot be resolved; refusing to skip the whitespace check."
            exit 1
        fi

        echo "::notice::Pull request base SHA is unavailable; checking against the fetched base branch."
        git diff --check "${BASE_REF}...${HEAD_SHA}"
    fi
    exit
fi

if [[ "$EVENT_NAME" != "push" ]]; then
    echo "::error::Unsupported event for patch whitespace validation: $EVENT_NAME"
    exit 1
fi

BASE_SHA="${BASE_SHA:-}"
if [[ "$BASE_SHA" =~ ^0{40}$ ]]; then
    git show --check --format= "$HEAD_SHA"
    exit
fi

if has_commit "$BASE_SHA" && git merge-base --is-ancestor "$BASE_SHA" "$HEAD_SHA"; then
    git diff --check "$BASE_SHA" "$HEAD_SHA"
    exit
fi

DEFAULT_BRANCH="${DEFAULT_BRANCH:?DEFAULT_BRANCH is required when the push base is unavailable}"
if ! DEFAULT_REF="$(remote_branch_ref "$DEFAULT_BRANCH")"; then
    echo "::error::Push base is unavailable and origin/$DEFAULT_BRANCH cannot be resolved; refusing to skip the whitespace check."
    exit 1
fi

if ! MERGE_BASE="$(git merge-base "$DEFAULT_REF" "$HEAD_SHA")"; then
    echo "::error::Cannot calculate a merge base with origin/$DEFAULT_BRANCH; refusing to skip the whitespace check."
    exit 1
fi

echo "::notice::Push base is missing, rewritten, or not an ancestor; checking from the default-branch merge base."
if [[ "$MERGE_BASE" == "$HEAD_SHA" ]]; then
    git log --check --format= "$HEAD_SHA"
else
    git diff --check "$MERGE_BASE" "$HEAD_SHA"
fi
