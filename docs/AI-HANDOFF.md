# AI and Contributor Handoff

Last reconciled: 2026-10-21. Re-check the files and live GitHub state before acting on time-sensitive details.

## Read these sources first

1. [Requirements Specification v1.4](Tabletop%20RPG%20Simulator%20Requirements%20Specification.md)
2. [Architecture v5.0](architecture.md)
3. [AI Build Brief v1.6](Tabletop%20RPG%20Simulator%20AI%20Build%20Brief.md)
4. [Ruleset Specification v0.6 (draft)](Tabletop%20RPG%20Simulator%20Ruleset%20Specification.md)
5. [Decision Log and Progress Tracker v1.5](Tabletop%20RPG%20Simulator%20Decision%20Log%20and%20Progress%20Tracker.md)
6. [Repository governance](REPOSITORY-GOVERNANCE.md)
7. `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `NOTICE`, `LICENSE`, and `.github/workflows/ci.yml` as relevant.

Use the ruleset specification as the source of truth for D&D mechanics. More specific specifications outrank the build brief. Do not silently reconcile conflicting requirements or invent unstated rules; record the conflict and ask the owner.

## Verified repository baseline

- Repository: `SamarthBadola/Tabletop-RPG-Simulator`.
- Step 1 (walking skeleton) passed the owner-reported laptop acceptance test on 2026-10-12: the source-built Compose stack started, PostgreSQL became healthy, the app started, and first-account registration plus sign-in succeeded.
- Current implementation baseline: Java 25 (Temurin 25.0.4.1), Spring Boot 4.1.1, Maven Wrapper 3.10.0, PostgreSQL 16.15.
- Initial cold image build was approximately 197.8 seconds. Treat this as a measurement to investigate, not as a reason for speculative build changes. Track cold/warm rebuild time, dependency download, compilation/package, image export/size, and app readiness separately.
- PR #14 (`actions/setup-java` 6.1.0) was merged by the owner on 2026-10-19. Keep third-party Actions pinned to full commit SHAs with readable version comments.
- Never merge PRs on the owner's behalf. Create the PR, report evidence and limitations, and wait for the owner to review and merge it.

## Current milestone: Step 2 — dice engine

Implement only after the owner confirms the unresolved syntax/limit details below. The planned engine is pure Java/JDK-only. Its core concepts are `RandomSource`, `DiceExpression` and parser, `DieRoll`, `RollTerm`, and `RollResult`. Use bounded uniform `nextInt(bound)` draws (no modulo or floating-point scaling), retain every die face and whether it was kept, retain ordered signed terms and natural d20/mode metadata, and inject randomness so worked tests can force results.

Step 2 done-when from Build Brief v1.6: malformed and over-limit expressions are rejected; parser/property/uniformity tests pass; and Ruleset Specification section 3.8 examples cover term lists, bonus/penalty dice, natural d20, one reroll per die, and d20 floor behavior. The build guide calls for tests first and no weakening/skipping tests.

Do not implement Step 3 kernel/modifier behavior or the combat engine as part of this dice-engine task unless the owner explicitly expands the scope. The architecture and supplied specifications should be treated as design references, not authorization to jump ahead in the build order.

## Conflicts and open questions to resolve before coding

1. **Java-version conflict in the supplied documents.** Requirements Specification v1.4 section 5 and Build Brief v1.6 section 3 mention Java 21, while the existing project code, `app/pom.xml`, Dockerfile, and previous owner-approved decision use Java 25. Keep Java 25 for the existing implementation; ask before changing the baseline. The source documents are kept verbatim in this PR so the discrepancy remains visible for owner review.
2. **Stale Part A/Part B status within Build Brief v1.6.** Section 1 says Parts B–H do not exist, but later Stop-here text refers to Part B and the supplied Ruleset Specification v0.6 includes a drafted Part B. The supplied tracker also records Part B as drafted with its engine decisions closed. Treat Part A section 3.8 as the Step 2 rules source; do not implement Part B combat yet.
3. **Reroll/floor modifier syntax is not specified.** The ruleset says to support reroll and minimum-value/d20-floor modifiers, but does not define their literal expression tokens or precedence/combination grammar. Confirm the exact parser notation before implementation.
4. **Numeric safety caps are incomplete.** Die size (2–1000) and group dice count (1–100) are specified, but maximum total dice per expression and maximum expression length are only referred to generically. Choose and record concrete limits before implementing validation.
5. **Term labels and sources are required, but are not encoded by the shown expression examples.** Confirm whether parsed expressions receive generic terms and higher-level callers attach labels/sources, or whether you expect a separate composition API.
6. **Parser edge behavior needs decisions** where the specs do not define it: whitespace/case handling, keep/drop counts equal to or greater than the group size, repeated modifiers, and integer overflow. Propose defaults only for owner approval; do not quietly choose semantics.
7. **Ruleset verification debt V-1 remains open.** The ruleset marks Inspiration, Multiclassing, Backgrounds, class chapters, Spellcasting and item wording as needing verification. That does not block a generic dice primitive, but do not claim dependent class/game-rule behavior is verified.

## Next action

First, have the owner confirm the dice grammar tokens, total-dice cap, expression-length cap, term-label/source boundary, and relevant malformed-input semantics. Then implement Step 2 on a short-lived branch with worked-example and property tests, open a PR, and leave merge responsibility with the owner.

Stop after Step 5. Obtain the content-schema, grid/geometry, and UX/API-with-threat-model specifications before moving to their corresponding later implementation steps.
