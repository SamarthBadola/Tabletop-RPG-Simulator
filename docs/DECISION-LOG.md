# Decision Log

> **Authority note (2026-10-21):** Current product/system decisions and milestone status are maintained in [Tabletop RPG Simulator Decision Log and Progress Tracker v1.5](Tabletop%20RPG%20Simulator%20Decision%20Log%20and%20Progress%20Tracker.md). This file preserves earlier repository/implementation ADR detail; where a product or architecture decision conflicts with the consolidated specification set, consult the linked current documents. Live GitHub ruleset settings remain documented in `REPOSITORY-GOVERNANCE.md`.

Record non-obvious choices here. Update this log in the same PR as the implementation that establishes the decision. Last reconciled: 2026-10-12.

| ID | Topic | Status | Decision / note |
|---|---|---|---|
| ADR-001 | Repository topology | Accepted | One monorepo: \`app/\`, \`pathfinder/\`, \`deploy/\`, \`docs/\`, and \`.github/\`. The walking skeleton is merged on \`main\`; the C++ pathfinder implementation remains deferred. |
| ADR-002 | Application runtime and build tool | Accepted | Use Temurin Java 25 LTS; local version reported as \`25.0.4.1+1\`. Pin Spring Boot \`4.1.1\` and Maven Wrapper \`3.10.0\`. The Maven Wrapper distribution is pinned to the versioned Apache Maven archive; wrapper dependency resolution and the application CI build have passed in GitHub Actions. |
| ADR-003 | Persistence | Accepted | PostgreSQL \`16.15\` image for the initial baseline (the architecture remains on major version 16) with Flyway; never use H2. Upgrade to a reviewed patched PostgreSQL 16 image. |
| ADR-004 | CI provider and registry | Accepted | GitHub Actions and GHCR. CI currently runs \`repo-hygiene\`, \`unit\`, \`integration\`, \`secrets\`, and \`compose-smoke\` on pull requests and branch pushes; all five passed on PR run #40 and post-merge \`main\` run #41. No image publishing/release workflow is enabled yet. |
| ADR-005 | Git workflow | Accepted | Short-lived branches, Conventional Commits, pull requests, and linear \`main\`; squash merge is the project convention. |
| ADR-006 | Action pinning | In progress | Pin third-party GitHub Actions to verified full-length commit SHAs with readable version comments and update through Dependabot. |
| ADR-007 | Software license | Accepted | License project-owned code and documentation under Apache License 2.0 (\`Apache-2.0\`). SRD and third-party materials retain their own licenses. |
| ADR-008 | SRD attribution | Accepted | Include the SRD 5.1 attribution statement specified in the project brief in \`README.md\` and \`NOTICE\`. Keep SRD terms distinct from the project's Apache-2.0 license. |
| ADR-009 | Image publish/release flow | Open — document discrepancy | The build brief describes building/pushing version-tagged images on \`v*\`; architecture v4 says publish commit-SHA images on \`main\`, then promote/re-tag the same image for a version release. Prefer build-once/promote-same-digest, but confirm with the human before enabling CD. |
| ADR-010 | Maven coordinates and Java root package | Accepted | Use Maven \`groupId\` \`com.github.sbadolankaur\`, \`artifactId\` \`tabletop-rpg-simulator\`, and Java root package \`com.github.sbadolankaur.rpg\`. |
| ADR-011 | GitHub owner, repository name and visibility | Accepted | Public repository \`SamarthBadola/Tabletop-RPG-Simulator\`. |
| ADR-012 | Developer tooling baseline | Accepted | Local versions reported by the project owner: Git \`2.54.0.windows.1\`; Temurin Java \`25.0.4.1+1\`; Docker Engine/CLI \`29.8.2\`; Compose \`5.5.1\`; Buildx \`0.37.2\`. Use Docker Desktop's supplied Compose and Buildx. |
| ADR-013 | Main-branch governance | Active; behavior verification pending | Three active rulesets target the default branch. \`main-quality-gates\` has no bypass actors and requires PR, resolved conversations, \`repo-hygiene\`, \`secrets\`, \`DCO\`, \`unit\`, \`integration\`, \`compose-smoke\`, linear history and deletion/force-push protections, with 0 approvals because review is in a separate ruleset. \`main-review-policy\` requires 1 approval and offers repository administrators a PR-only bypass; the live ruleset contains only the pull-request/review policy. \`main-update-access\` restricts updates with an administrator Always-allow exception. Direct-push and quality-bypass behavior remain unverified end-to-end. See \`docs/REPOSITORY-GOVERNANCE.md\`. |
| ADR-014 | DCO policy | Configured and checked on PR #4 | The DCO App passed on PR #4, and \`DCO\` is required in \`main-quality-gates\`. Non-owner contributors must add a matching \`Signed-off-by\` trailer to every applicable commit. |
| ADR-015 | Durable AI handoff | Accepted | \`docs/AI-HANDOFF.md\` records verified project state, constraints, and next milestone. Keep it aligned with \`docs/PROGRESS.md\` after meaningful changes. |
| ADR-016 | Bootstrap account lifecycle | Merged in PR #6; automated tests and laptop first-start acceptance passed (2026-10-12) | A singleton \`instance_state\` row is pessimistically locked during registration. The first successful registration creates the only instance-administrator account and atomically closes public registration. Later account creation is intentionally unavailable until the invitation flow in Step 4. A database uniqueness constraint prevents multiple instance-admin rows. |
| ADR-017 | Password and session baseline | Merged in PR #6; automated unit/integration tests and laptop first-start acceptance passed (2026-10-12) | Passwords are hashed with BCrypt (cost factor 12); registration requires at least 12 characters and rejects passwords over 72 UTF-8 bytes. CSRF remains enabled, session cookies are HttpOnly/SameSite=Lax, Secure is controlled by \`APP_COOKIE_SECURE\`, and security headers include a restrictive CSP. Login throttling is a bounded in-memory 15-minute window (10 failures per username/IP and 60 per IP); it is single-instance and resets on restart. |
| ADR-018 | Code-comment convention | Accepted | Prefer self-documenting names and simple functions. Do not add comments that restate code; document public contracts, meaningful guarantees/failure modes, and non-obvious reasoning or invariants. Keep comments short and synchronized with behavior. |

## Baseline versions

- Temurin Java: 25 LTS; local version reported as \`25.0.4.1+1\`.
- Spring Boot: \`4.1.1\`.
- Maven Wrapper distribution: \`3.10.0\`.
- PostgreSQL: \`16.15\`.
- CMake: \`4.4.4\` selected for future C++ pathfinder work.
- Local Git/Docker tooling versions are recorded in ADR-012.

## Dependency/version policy

Pin versions in build manifests and Dockerfiles. Verify releases and dependency APIs against current primary documentation before pinning. Use reviewed Dependabot updates instead of floating project dependencies to \`latest\`.

## Open questions

- Resolve ADR-009 before building release automation.
- The detailed ruleset specification is not written; do not implement game mechanics from memory.
- Later human decisions include spell/monster subsets, surprise handling, DM overrides, corner-cutting default, upload limits, and journal event capture. Ask when those topics reach implementation.
