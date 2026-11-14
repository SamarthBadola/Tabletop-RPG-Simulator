#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CHECK_SCRIPT="$SCRIPT_DIR/check-patch-whitespace.sh"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

git_configure() {
    git -C "$1" config user.name "CI Test"
    git -C "$1" config user.email "ci-test@example.invalid"
}

commit_file() {
    local repo="$1"
    local content="$2"
    local message="$3"

    printf '%s' "$content" > "$repo/probe.txt"
    git -C "$repo" add probe.txt
    git -C "$repo" commit -qm "$message"
    git -C "$repo" rev-parse HEAD
}

run_check() {
    local repo="$1"
    shift

    (
        cd "$repo"
        env "$@" bash "$CHECK_SCRIPT"
    )
}

expect_pass() {
    local name="$1"
    shift
    if ! "$@" >/dev/null; then
        echo "Expected pass: $name" >&2
        exit 1
    fi
    echo "PASS: $name"
}

expect_fail() {
    local name="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        echo "Expected failure: $name" >&2
        exit 1
    fi
    echo "PASS: $name"
}

REPO="$WORK_DIR/repo"
mkdir "$REPO"
git -C "$REPO" init -q --initial-branch=main
git_configure "$REPO"

BASE_SHA="$(commit_file "$REPO" $'clean\n' "base")"
git -C "$REPO" update-ref refs/remotes/origin/main "$BASE_SHA"

CLEAN_HEAD="$(commit_file "$REPO" $'clean\nnext\n' "clean change")"
expect_pass "push with an available ancestor base" run_check "$REPO" \
    EVENT_NAME=push BASE_SHA="$BASE_SHA" DEFAULT_BRANCH=main HEAD_SHA="$CLEAN_HEAD"

expect_pass "pull request with an available base" run_check "$REPO" \
    EVENT_NAME=pull_request BASE_SHA="$BASE_SHA" BASE_BRANCH=main HEAD_SHA="$CLEAN_HEAD"
# Simulate the force-push case: the event's previous SHA is no longer present.
DIRTY_HEAD="$(commit_file "$REPO" $'clean\ntrailing-space \n' "dirty change")"
MISSING_SHA="1111111111111111111111111111111111111111"
expect_fail "push with a missing base SHA falls back and detects whitespace" run_check "$REPO" \
    EVENT_NAME=push BASE_SHA="$MISSING_SHA" DEFAULT_BRANCH=main HEAD_SHA="$DIRTY_HEAD"

# Use an existing commit from a divergent branch as the reported push base.
git -C "$REPO" checkout -q -b side "$BASE_SHA"
SIDE_SHA="$(commit_file "$REPO" $'clean\nside\n' "side history")"
git -C "$REPO" checkout -q main
DIRTY_HEAD="$(commit_file "$REPO" $'clean\nmain-dirty \n' "main dirty change")"
expect_fail "push with a non-ancestor base falls back and detects whitespace" run_check "$REPO" \
    EVENT_NAME=push BASE_SHA="$SIDE_SHA" DEFAULT_BRANCH=main HEAD_SHA="$DIRTY_HEAD"

# PR base SHA missing: resolve the fetched base branch instead.
PR_DIRTY_HEAD="$(commit_file "$REPO" $'clean\npr-dirty \n' "PR dirty change")"
expect_fail "pull request with a missing base SHA resolves its base branch" run_check "$REPO" \
    EVENT_NAME=pull_request BASE_SHA="$MISSING_SHA" BASE_BRANCH=main HEAD_SHA="$PR_DIRTY_HEAD"

# First push has an all-zero before SHA and must inspect the initial commit.
INITIAL="$WORK_DIR/initial"
mkdir "$INITIAL"
git -C "$INITIAL" init -q --initial-branch=main
git_configure "$INITIAL"
INITIAL_HEAD="$(commit_file "$INITIAL" $'initial-dirty \n' "initial commit")"
expect_fail "initial push checks the initial commit" run_check "$INITIAL" \
    EVENT_NAME=push BASE_SHA=0000000000000000000000000000000000000000 DEFAULT_BRANCH=main HEAD_SHA="$INITIAL_HEAD"

# An unavailable base must not silently skip validation if the fallback ref is missing.
NO_FALLBACK="$WORK_DIR/no-fallback"
mkdir "$NO_FALLBACK"
git -C "$NO_FALLBACK" init -q --initial-branch=main
git_configure "$NO_FALLBACK"
NO_FALLBACK_HEAD="$(commit_file "$NO_FALLBACK" $'clean\n' "isolated commit")"
expect_fail "missing push base and missing default branch fail closed" run_check "$NO_FALLBACK" \
    EVENT_NAME=push BASE_SHA="$MISSING_SHA" DEFAULT_BRANCH=main HEAD_SHA="$NO_FALLBACK_HEAD"

echo "All patch-whitespace regression cases passed."
