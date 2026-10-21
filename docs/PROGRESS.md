# Progress Tracker

> **Canonical tracker:** [Tabletop RPG Simulator Decision Log and Progress Tracker v1.5](Tabletop%20RPG%20Simulator%20Decision%20Log%20and%20Progress%20Tracker.md). This file is retained as a quick progress mirror for compatibility with existing repository checks. Record authoritative milestone/status changes in the consolidated tracker and keep this summary aligned.

Last reconciled: 2026-10-12.

## Current milestone: Step 2 — dice engine (walking skeleton accepted)

Step 1 was accepted on 2026-10-12 after the project owner reported that the source-built Compose stack started successfully on a laptop, PostgreSQL became healthy, and first-account registration and sign-in succeeded. All five CI jobs passed on [PR #12 run](https://github.com/SamarthBadola/Tabletop-RPG-Simulator/actions/runs/38260399502) and [post-merge `main` run](https://github.com/SamarthBadola/Tabletop-RPG-Simulator/actions/runs/38260733720). The initial full image build took approximately 197.8 seconds; see the build-performance follow-up below.

### Completed repository bootstrap and governance

- [x] Read the AI build brief and architecture v4.
- [x] Define the monorepo structure and repository hygiene conventions.
- [x] Add \`.gitignore\`, \`.gitattributes\`, \`.editorconfig\`, contribution/security guidance, PR template, decision log, progress tracker, and governance guide.
- [x] Add bootstrap CI with \`repo-hygiene\` and \`secrets\`.
- [x] Verify bootstrap CI on PRs and \`main\`.
- [x] Confirm GitHub Actions uses read-only repository permissions for CI.
- [x] Add weekly Dependabot checks for GitHub Actions.
- [x] Record repository identity, Maven coordinates, Java package and selected baseline versions.
- [x] Select Apache-2.0 for project-owned code/documentation.
- [x] Record SRD 5.1 attribution in \`README.md\` and \`NOTICE\`.
- [x] Add \`.github/CODEOWNERS\`, DCO guidance and \`.github/dco.yml\`.
- [x] Verify the DCO App check passed on PR #4.
- [x] Confirm \`main-quality-gates\`, \`main-review-policy\`, and \`main-update-access\` are Active.
- [x] Record live ruleset configuration and remaining cleanup in \`docs/REPOSITORY-GOVERNANCE.md\`.
- [x] Remove the deletion and non-fast-forward rules from \`main-review-policy\`; the live ruleset now contains only the pull-request/review policy.
- [ ] Behaviorally verify direct-push rejection, normal-contributor review enforcement, and that an administrator cannot bypass the quality gates.
- [ ] Resolve the build brief/architecture image-publishing discrepancy (ADR-009) before implementing release automation.

### Step 1 — walking skeleton

The implementation is merged on \`main\`. The project owner reported that the laptop startup test succeeded: the source-built app image built, the PostgreSQL container became healthy, the application started, and first-account registration and sign-in succeeded.

- [x] Create the Maven project with fixed coordinates and the Maven Wrapper configuration.
- [x] Create the Spring Boot app, login/registration controllers, and health/readiness configuration.
- [x] Add a PostgreSQL schema migration and Flyway configuration.
- [x] Implement serialized first-account registration: only the first account becomes the instance administrator; later public registration is closed pending invites.
- [x] Implement BCrypt password storage, validation, Spring Security form login, CSRF, session-cookie settings, restrictive CSP, and basic bounded login throttling.
- [x] Add the application container image and Compose configuration with private PostgreSQL and a named database volume.
- [x] Add a secret-generating environment initializer and a clean-stack smoke script.
- [x] Add unit tests and Testcontainers-backed integration tests for registration, login, CSRF, readiness, and login throttling.
- [x] Add CI jobs for formatting/unit tests, PostgreSQL integration tests, secret scanning, repository checks, and source-built Compose smoke on pull requests and branch pushes.
- [x] Update README local startup steps and document module setup.
- [x] Verify \`spotless:check test\` and \`verify -Pintegration\` pass in GitHub Actions on PR run #40 and post-merge run #41.
- [x] Verify the Compose smoke job passes on PR run #40 and post-merge \`main\` run #41.
- [x] Verify the active \`main-quality-gates\` ruleset requires \`repo-hygiene\`, \`secrets\`, \`DCO\`, \`unit\`, \`integration\`, and \`compose-smoke\`.
- [x] Run the laptop source-build Compose startup, confirm PostgreSQL healthy and the application started, and verify first-account registration and sign-in (project-owner report, 2026-10-12).
- [x] Record the local acceptance result in the README, progress tracker and AI handoff.

### Step 2 — dice engine (after Step 1 is complete)

- [ ] Define the dice-expression grammar and limits from project specifications.
- [ ] Implement \`RandomSource\`, parser, and immutable \`RollResult\`.
- [ ] Add parser, malformed/over-limit rejection, range/property, keep/drop, and seeded uniformity tests.

### Step 3 — kernel and architecture tests

- [ ] Implement the kernel types and modifier semantics with specified order/rounding.
- [ ] Add ArchUnit rules and demonstrate that deliberate violations fail the build.

### Step 4 — campaign, session, and access

- [ ] Add campaign/member/invite/session/scene/game-clock/audience schema and service flows.
- [ ] Test invite enforcement, cross-campaign isolation (foreign resource is 404), and inactive-session rejection.

### Step 5 — content-pack skeleton

- [ ] Add content-pack and definition tables, JSON schema validation, immutable in-memory loading, an attribution page, and an empty SRD pack manifest.
- [ ] No SRD game data yet.

**Stop after Step 5.** Ask for the ruleset specification, content schema specification, grid/geometry specification, and UX/API specification with threat model before implementing later gameplay features. Do not invent D&D rules from memory.

## CI status

The active \`.github/workflows/ci.yml\` defines \`repo-hygiene\`, \`unit\`, \`integration\`, \`secrets\`, and \`compose-smoke\`. All five jobs passed on [PR #12 run](https://github.com/SamarthBadola/Tabletop-RPG-Simulator/actions/runs/38260399502) and [post-merge run on \`main\`](https://github.com/SamarthBadola/Tabletop-RPG-Simulator/actions/runs/38260733720). Compose smoke runs on pull requests and branch pushes after integration tests; it is not main-only. The live \`main-quality-gates\` ruleset requires all five jobs plus \`DCO\`.

The `/deploy` Dependabot entry uses the \`docker-compose\` ecosystem and ignores PostgreSQL semver-major updates to preserve the PostgreSQL 16 baseline. PR #11's PostgreSQL 18.6 proposal was closed after its Compose smoke test failed on the changed volume layout.

## Build-performance follow-up

The first laptop source-build startup took approximately **197.8 seconds** for the initial image build. This is a single cold-build observation and includes image/dependency downloads; it is not a measured compiler-only duration.

- [ ] Record separate cold-build and warm-rebuild timings with the same checkout and Docker environment.
- [ ] Inspect BuildKit output to distinguish base-image pulls, Maven dependency resolution, compilation/package time, and image export.
- [ ] Evaluate Maven dependency/cache layering and Docker/BuildKit cache reuse without sacrificing reproducibility or clean-clone builds.
- [ ] Record image size and time-to-healthy/startup as separate metrics.
- [ ] Re-measure after the dice engine and future service/ruleset layers are introduced, then implement only optimizations supported by the measurements.

Treat this as a tracked quality improvement before the full stack grows substantially; it does not invalidate the accepted Step 1.

## Working agreement

1. Read \`docs/AI-HANDOFF.md\`, this tracker, \`docs/DECISION-LOG.md\`, \`docs/REPOSITORY-GOVERNANCE.md\`, the full build brief, and architecture v4 before coding.
2. Work one focused PR at a time from a short-lived branch. Use Conventional Commits and do not push directly to \`main\`.
3. Keep code efficient and simple. Prefer clear names over redundant comments; document public contracts and non-obvious constraints only.
4. Define observable done-when criteria, run the relevant tests, report actual outcomes, and update docs in the same PR.
5. Never claim a test passed unless the corresponding job actually passed.
