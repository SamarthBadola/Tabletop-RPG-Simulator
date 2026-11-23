# Repository Governance and Main-Branch Protection

Last reconciled: 2026-10-12.

This repository is public. Community contributions are welcome, but the project policy is that only the repository owner, [@SamarthBadola](https://github.com/SamarthBadola), merges pull requests into `main`. A policy written in the repository does not itself enforce branch restrictions; the GitHub rulesets do.

## Current live rulesets

The following three repository rulesets are active and target the default branch (`~DEFAULT_BRANCH`, currently `main`). Settings below were read from the live GitHub rulesets on 2026-10-12.

### 1. `main-quality-gates`

- Enforcement: **Active**.
- Pull request required before merging.
- Required approving-review count: **0**. Review count is intentionally separated into `main-review-policy`.
- Require all review conversations to be resolved.
- Required status checks: `repo-hygiene`, `secrets`, `DCO`, `unit`, `integration`, and `compose-smoke`.
- Require linear history.
- Restrict deletion and block force pushes.
- Bypass list: **empty**; `current_user_can_bypass` is `never`.

This is the non-bypassable quality layer. The intended design is for these rules to remain in force even when an administrator uses the narrow bypass in the review-policy ruleset.

### 2. `main-review-policy`

- Enforcement: **Active**.
- Pull request required before merging.
- Required approving-review count: **1**.
- Dismiss stale approvals when new commits are pushed.
- Repository administrators bypass this ruleset **For pull requests only**.
- No mandatory Code Owner approval; `require_code_owner_review` is disabled in the live configuration.
- The live ruleset contains only the pull-request/review policy; deletion and non-fast-forward restrictions remain in the quality-gates/access rulesets.

The intended exception is narrow: an administrator who authored a PR cannot approve it themselves, so they may bypass the review-only rule when merging their own PR; they must still satisfy `main-quality-gates`.

### 3. `main-update-access`

- Enforcement: **Active**.
- Restrict updates is enabled.
- Deletion and non-fast-forward updates are blocked by this ruleset.
- Repository administrators have **Always allow** bypass in this access-only ruleset.

This access exception was added after a PR merge failed with `Cannot update this protected ref` when the update-access bypass was limited to pull requests only. It must remain confined to `main-update-access`; do not add a bypass actor to `main-quality-gates`.

## Expected behavior and verification gap

The intended layered behavior is:

1. Normal contributors use pull requests and need one approving review plus all required quality checks.
2. The repository administrator may bypass the review-only requirement on an administrator-authored pull request, but the separate no-bypass quality rules must still apply.
3. Direct pushes should be rejected by the no-bypass pull-request requirement in `main-quality-gates`, despite the access-only ruleset's administrator exception.
4. Force pushes and branch deletion remain blocked by the quality/access rules.
5. Squash merge is the project convention. Because linear history is required, avoid merge-commit merges.

The configuration is active, but not every behavior has been independently tested. In particular, a direct-push rejection from a non-administrator test account and a full test of the administrator PR exception vs. failing quality checks remain to be demonstrated. Do not describe those tests as passed until they have actually run.

## CODEOWNERS and the two-person team

`.github/CODEOWNERS` requests review from the repository owner for changes to any path. The live rulesets do **not** require Code Owner approval specifically. They require one approving review for normal PRs, and the project governance policy reserves merge permission to the owner. If requiring an owner/code-owner approval becomes necessary, confirm the team's GitHub identities and test the behavior when either teammate authors a PR. Do not guess Nihchal's GitHub username.

## DCO sign-off check

- The DCO GitHub App is installed/configured: the DCO App reported a successful `DCO` check on PR #4.
- `.github/dco.yml` uses `require.members: false`, the selected setup for exempting the personal repository owner from sign-off.
- `DCO` is present as a required status check in `main-quality-gates`.
- Every applicable non-owner contributor commit must carry a valid matching `Signed-off-by` trailer. See [CONTRIBUTING.md](../CONTRIBUTING.md).

Do not create a separate DCO ruleset. The status check belongs in the existing quality-gates ruleset.

## CI checks currently available

The active `.github/workflows/ci.yml` defines five GitHub Actions jobs:
- `repo-hygiene`: patch whitespace, required repository files, label manifest consistency and Java 25 baseline checks.
- `unit`: Spotless formatting check and Maven unit tests.
- `integration`: PostgreSQL Testcontainers integration tests.
- `secrets`: Gitleaks scan of repository history.
- `compose-smoke`: builds and starts the source-built Compose stack, then checks readiness, first-account registration and login.

All five jobs passed on [PR run #40](https://github.com/SamarthBadola/Tabletop-RPG-Simulator/actions/runs/38251836058) and [post-merge `main` run #41](https://github.com/SamarthBadola/Tabletop-RPG-Simulator/actions/runs/38252435012). The smoke job runs for pull requests and branch pushes after `integration`; it is not main-only. The live `main-quality-gates` ruleset requires all five jobs plus `DCO`. The DCO App previously passed on [PR #4](https://github.com/SamarthBadola/Tabletop-RPG-Simulator/pull/4).

Pathfinder has additional checks for C++ release, ASan/UBSan and TSan builds, HTTP contract tests, and smoke tests and scans for the amd64 and arm64 runtime images. The nightly HTTP benchmark uploads its C++ timings as CSV. These jobs run on Pathfinder changes. The Java/C++ comparison and image publishing are later delivery work. Only add a required status check after it exists and has reported.

## Verification checklist

- [x] All three rulesets are Active and target the default branch.
- [x] `main-quality-gates` has no bypass actors.
- [x] `repo-hygiene`, `secrets`, `DCO`, `unit`, `integration`, and `compose-smoke` are configured as required status checks.
- [x] DCO check has passed on a pull request.
- [x] Remove deletion and non-fast-forward rules from `main-review-policy`; the live ruleset now contains only the pull-request/review policy.
- [ ] From a non-admin test account, prove a direct push to `main` is rejected.
- [ ] Prove a normal contributor PR cannot merge without an approval or a required check.
- [ ] Prove an owner-authored PR can use the review-policy exception but cannot bypass failed quality checks.
- [ ] Revisit the ruleset and collaborator permissions if a second maintainer is given repository-admin access.
