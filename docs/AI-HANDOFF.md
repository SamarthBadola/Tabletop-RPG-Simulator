# AI and Contributor Handoff

Last reconciled: 2026-11-14. Re-check repository files, PR state and CI before relying on time-sensitive details.

## Canonical documents

1. [Requirements Specification v1.7.1](Tabletop%20RPG%20Simulator%20Requirements%20Specification.md)
2. [Architecture v5.1](architecture.md)
3. [AI Build Brief v1.10.2](Tabletop%20RPG%20Simulator%20AI%20Build%20Brief.md)
4. [Ruleset Specification v0.12.1 (draft)](Tabletop%20RPG%20Simulator%20Ruleset%20Specification.md)
5. [Decision Log and Progress Tracker v1.11.1](Tabletop%20RPG%20Simulator%20Decision%20Log%20and%20Progress%20Tracker.md)
6. [Repository governance](REPOSITORY-GOVERNANCE.md)

The Ruleset Specification is authoritative for D&D mechanics. More-specific specifications take precedence over the AI Build Brief. Steps 2 and 3 use Part A only. Part B is reserved for the combat build step; Parts C–H and the later content, grid/geometry and UX/API specifications remain incomplete.

## Project and platform

- Repository: `SamarthBadola/Tabletop-RPG-Simulator`.
- Product: self-hosted, browser-based D&D SRD 5.1 (2014) character manager and encounter runner; it is not a virtual tabletop.
- Implemented baseline: Java 25 (Temurin 25.0.4.1), Spring Boot 4.1.1, Maven Wrapper 3.10.0 and PostgreSQL 16.15.
- Step 1 was accepted on the owner's laptop: source-built Compose startup, healthy PostgreSQL, application startup, first administrator registration and sign-in succeeded.
- Initial cold image build was approximately 197.8 seconds; the tracker records Maven dependency resolution as the dominant measured cost (~146 seconds). Re-measure after Step 5 before optimizing.
- Maven coordinates: `com.github.sbadolankaur:tabletop-rpg-simulator`; root package `com.github.sbadolankaur.rpg`.
- Keep third-party GitHub Actions pinned to verified full commit SHAs with readable version comments.
- Never merge a PR on the owner's behalf. Update branches and PRs, provide test/CI evidence, and leave every PR open for the owner to review and merge unless the owner explicitly instructs otherwise for that specific PR.

## Current milestone: Step 2 — dice engine

The Step 2 decisions are specified in Ruleset Specification 0.12.1 sections 3.10 and 3.11 and recorded as DEC-085/DEC-086. D-1 to D-13 are decided. Implement the defined contract; do not reopen the seven questions from the prior review.

### Contract to implement

- Pure Java/JDK-only `engine.dice`; no Spring, JPA, HTTP, filesystem I/O or other framework dependencies.
- `DiceExpression.parse(String)` produces a label-free expression. Grammar supports signed terms and dice groups with `khN`, `klN`, `dhN`, `dlN`, `r<=N` and `minN`.
- Parsing is ASCII-only. Space and tab are allowed at the expression edges and around operators, never inside a term. Letter case is ignored; canonical form is lowercase, has an explicit count, no whitespace and no leading plus.
- Limits: 1–100 dice per group; die size 2–1000; 200 dice and 20 terms per expression; 200 characters; integer literals up to 1,000,000; 400 dice after critical doubling.
- Stable parser errors: `DICE_EMPTY`, `DICE_SYNTAX`, `DICE_VALUE_OUT_OF_RANGE`, `DICE_MODIFIER_CONFLICT` and `DICE_LIMIT_EXCEEDED`. The web boundary maps parser failures to `VALIDATION_FAILED` (HTTP 400).
- Error precedence is deterministic: empty input, length guard, left-to-right syntax/range/conflict checks, term cap on the 21st term, then aggregate dice cap.
- Evaluation is left-to-right. Within each dice group: draw all initial dice in order, reroll eligible dice once in die order, apply the floor, then keep/drop with the specified earlier-die tie-break.
- `RandomSource.nextInt(bound)` must be uniformly bounded. Production uses `SecureRandom`; tests use seeded randomness and `ForcedDice`, which validates faces and asserts that every supplied face was consumed.
- `DieRoll` preserves the initial face, optional reroll face and effective post-floor value. Expressions, terms, results and collections are immutable defensive copies.
- `RollPlan` composes labeled/sourced signed terms. `dice(...)` accepts exactly one dice group; fixed terms accept signed integers; plan-wide term/dice/doubling limits are enforced.
- A plan can designate at most one d20 test term. That term contains one physical die in NORMAL mode and two in ADVANTAGE/DISADVANTAGE mode. The plan-wide dice limit counts physical dice.
- A d20 floor applies to each physical d20 before selection. Group rerolls remain per-eligible-die; `replaceD20(index, rng)` replaces exactly one selected d20. `naturalD20` is the effective value of the kept d20 after reroll and floor.
- Critical doubling creates a new plan and doubles only dice counts on terms flagged `doublesOnCrit`; fixed modifiers are not doubled.
- The parser is label-free; the composition API attaches labels and sources. For a bare expression, each term is labeled by its canonical text and has no source.

### Step 2 test and acceptance scope

Implement tests first. Cover the Ruleset Specification 3.10 test contract and 3.8 worked examples, including:

- grammar, canonical round trips, whitespace/case/ASCII behavior and numeric normalization;
- per-field, per-group, expression-wide and plan-wide boundaries;
- stable error codes and deterministic error precedence, including oversized numeric tokens without leaking `NumberFormatException`;
- all four keep/drop modes and tie-breaking for each mode, rerolls, floors and complete reroll history;
- advantage/disadvantage d20 selection, one-die `replaceD20`, and `naturalD20` after reroll/floor;
- signed fixed terms, bonus/penalty dice, critical-doubling copies and immutable collections;
- `ForcedDice` draw order and full consumption;
- seeded uniformity: 200,000 d20 rolls, each face between 9,500 and 10,500 occurrences.

Step 2 is done only when parser, property, forced-dice and uniformity tests pass; malformed/over-limit input is rejected; and the specified result examples pass. Do not add d20-vs-DC comparison, contests, combat, critical-hit adjudication, advantage-source resolution or probability-distribution previews. Those are outside Step 2.

## Build order and stop conditions

- Step 3: kernel and ArchUnit rules, using Part A only.
- Steps 4–5: campaigns/sessions/access and content-pack skeleton.
- Stop after Step 5. The later gameplay steps require the remaining Ruleset Specification parts and the content-schema, grid/geometry and UX/API-with-threat-model specifications. Ask for those before implementing the corresponding features.

## Working agreement

- Work in small, reviewable tasks on short-lived branches; use Conventional Commits.
- Tests first for engine behavior. Never weaken, skip or modify a test merely to make it pass; report failures honestly.
- Keep the implementation simple, modular and efficient. Add seams only where Architecture/Build Brief registers them.
- Update the canonical tracker and handoff in the same PR when milestone status or decisions change.
- Report what changed, tests actually run, CI results, remaining work and any deviation from the specifications.
- Do not merge PRs unless the owner explicitly tells you to merge that specific PR.
