# AI and Contributor Handoff

Last reconciled: 2026-10-31. Re-check the files and live GitHub state before relying on time-sensitive settings or CI results.

## Canonical documents

1. [Requirements Specification v1.5](Tabletop%20RPG%20Simulator%20Requirements%20Specification.md)
2. [Architecture v5.0](architecture.md)
3. [AI Build Brief v1.8](Tabletop%20RPG%20Simulator%20AI%20Build%20Brief.md)
4. [Ruleset Specification v0.8 (draft)](Tabletop%20RPG%20Simulator%20Ruleset%20Specification.md)
5. [Decision Log and Progress Tracker v1.7](Tabletop%20RPG%20Simulator%20Decision%20Log%20and%20Progress%20Tracker.md)
6. [Repository governance](REPOSITORY-GOVERNANCE.md)

Ruleset Specification is authoritative for D&D mechanics. Steps 2 and 3 may use Part A only. Part B exists as a draft but is reserved for the combat step specified by Build Brief v1.8. Parts C–H and the later content/grid/UX specifications remain incomplete.

## Verified platform and workflow constraints

- Repository: `SamarthBadola/Tabletop-RPG-Simulator`.
- Step 1 was accepted on the owner's laptop: source-built Compose stack started, PostgreSQL became healthy, application started, and first administrator registration/sign-in succeeded.
- Implemented baseline: Java 25 (Temurin 25.0.4.1), Spring Boot 4.1.1, Maven Wrapper 3.10.0, PostgreSQL 16.15.
- Initial cold image build was approximately 197.8 seconds. The consolidated tracker identifies Maven dependency resolution as the main measured cost (~146 seconds); re-measure cold/warm builds after Step 5 before optimizing.
- PR #14 (`actions/setup-java` 6.1.0) was merged by the owner. Third-party Actions stay pinned to full commit SHAs with readable version comments.
- The active `main-quality-gates` ruleset requires `repo-hygiene`, `secrets`, `DCO`, `unit`, `integration`, and `compose-smoke` (verified live 2026-10-31).
- Never merge a PR on the owner's behalf. Create/update the PR, report evidence and limitations, and leave all merge decisions to the owner.
- Never weaken, skip, or edit tests to make them pass. Engine tests are written first.

## Step 2 scope: dice engine only

Build Brief v1.8 section 12 and Ruleset Specification v0.8 section 3.10/3.11 now decide the core parser contract (DEC-085, D-1 to D-6). Implement it as written once the residual questions below are settled.

- Pure Java/JDK-only `engine.dice` package; no Spring, JPA, HTTP or filesystem I/O.
- `DiceExpression.parse(String)` parses a label-free ordered expression; canonicalization is lowercase, explicit dice count, no whitespace, no leading plus.
- Grammar: dice groups and signed integer terms joined by `+`/`-`; `khN`, `klN`, `dhN`, `dlN`, `r<=N`, and `minN` modifiers.
- Validity: 1–100 dice/group, die size 2–1000, keep/drop constraints, reroll threshold 1..size-1, floor 2..size, modifier conflicts rejected, integer literals 0..1,000,000; ASCII only; spaces/tabs only around operators.
- Limits: 200 dice/expression before keep/drop, 20 terms, 200 characters, 400 dice after critical doubling.
- Stable parser errors: `DICE_EMPTY`, `DICE_SYNTAX`, `DICE_VALUE_OUT_OF_RANGE`, `DICE_MODIFIER_CONFLICT`, `DICE_LIMIT_EXCEEDED`; web boundary maps these to `VALIDATION_FAILED` (400).
- Evaluation: terms left-to-right; per group all initial draws first, then one reroll pass in die order, then floor, then keep/drop and subtotal. Tie selection favours earlier dice as specified.
- `RandomSource.nextInt(bound)` is uniform and bounded. Production uses `SecureRandom`; tests use seeded and forced-dice sources, asserting every forced face was consumed.
- `RollPlan` composes labeled/source terms and can designate the d20, its mode, and dice terms that double on critical hits. A critical doubles dice counts, never fixed modifiers.
- Step 2 stops at producing a correct `RollResult`. No check-vs-DC comparison, combat, critical-hit adjudication, advantage-source resolution, or probability-distribution preview (FR-DIC-008).

## Step 2 residual questions before implementation

These were found by cross-reading Ruleset Specification v0.8 sections 3.3, 3.8 and 3.10. The previously identified Java 21/25 conflict, Part A/Part B status conflict, modifier notation, numeric caps, broad parser whitespace/case rules, and high-level error contract have been addressed by the new documents. The following details are still ambiguous or internally inconsistent:

1. **Every face must be retained, but `DieRoll` cannot represent every face when reroll and floor both apply.** The record currently contains `rolled` (first face), `value` (after reroll and floor), and booleans. Example: `1d20r<=9min10` rolls 2, rerolls to 7, then floors to 10; the result records 2 and 10 but loses the actual reroll face 7. Proposed fix: add an optional reroll face (`Integer rerolledValue`) or an ordered `List<Integer> faces`; retain the post-floor `value` separately.
2. **Advantage/disadvantage d20 reroll targeting conflicts with group reroll semantics.** RS-ADV-004 says that when a rule permits replacing one of two d20s, the player chooses only one. The generic `r<=N` modifier rerolls every die in a group that meets the threshold. `RollPlan.d20(mode)` mentions optional reroll settings but does not say how one die is selected when the mode rolls two. The API needs a target index/selection input for the one-die d20 replacement case, distinct from generic per-die `r<=N` behavior.
3. **`RollPlan.dice(...)` does not define whether its `DiceExpression` must contain exactly one dice group.** `RollTerm` represents one dice group or one fixed integer, but `dice(label, source, expression, ...)` accepts a `DiceExpression`, whose grammar permits multiple terms such as `d8+3d6+3`. Decide whether `dice(...)` rejects anything except one dice-group expression (and callers add each labeled group/fixed term separately), or whether the API expands a composite expression into several `RollTerm`s and defines how labels/sources are assigned.
4. **Composition-level limits are not explicit.** Parser limits apply to one expression, while `RollPlan` can combine multiple expressions and fixed terms. Define that the 20-term/200-dice limits apply to the complete `RollPlan` as well (recommended), and that `doubleDiceForCritical()` rechecks the 400-dice post-doubling cap across the complete plan. Because `RollResult` has one `naturalD20` and one `mode`, also state whether a plan may contain at most one designated `.d20(...)` term (recommended).
5. **Signed fixed terms in `RollPlan` are unspecified.** The parser supports `+` and `-` integer terms, while the composition example only shows `fixed(..., 3)` and the result has a separate `sign`. Decide whether `fixed(label, source, signedValue)` accepts negative values and derives `sign`, or whether the API requires an explicit sign/magnitude (or a separate penalty-fixed method).
6. **One parser test vector has two simultaneous violations.** The test table expects `100d6+101d6` to produce `DICE_LIMIT_EXCEEDED` for 201 total dice, but the second group also violates the maximum of 100 dice/group and should produce `DICE_VALUE_OUT_OF_RANGE`. Use a valid-group aggregate case such as `100d6+100d6+1d6` for the total-dice limit test, or explicitly define validation precedence.
7. **The meaning of `naturalD20` after reroll and floor is contradictory.** RS-DIE-003 says the natural roll is the face before modifiers (or the kept die under advantage); RS-DIE-004 says a reroll replaces the old die and must be used; RS-DIE-005 floors each d20 before selection; but the result-model comment says `naturalD20` is the kept value. For example, with advantage, one die rerolled from 2 to 7 and then floored to 10, while the other die is 9: selection uses 10, but what is `naturalD20`—the original 2, the replacement face 7, or the final floored value 10? Recommended: `naturalD20` is the selected die's effective face after any replacement reroll but before the floor modifier; record the original, rerolled, and post-floor values distinctly. The current expected result is ambiguous.

### Implementation details that can be handled defensively once the contract is settled

- Parse numeric tokens without integer overflow; an arbitrarily large numeric token (within the 200-character expression cap) must yield the specified stable range/limit exception, never leak `NumberFormatException`.
- Keep parser error precedence deterministic when multiple constraints fail. Prefer a cheap input-length guard, then lexical/syntax and per-field range checks, then aggregate expression limits, unless the owner chooses another ordering.
- Clarify whether newline-only input is `DICE_EMPTY` or `DICE_SYNTAX`; the grammar permits only spaces and tabs as whitespace.
- Make result and expression collections immutable with defensive copies (`List.copyOf`) so the records cannot be mutated after evaluation.
- Validate builder inputs (non-null/nonblank labels; define whether a missing source is represented as `null`) and reject an empty `RollPlan`.
- Add explicit tests for leading-zero canonicalization (`007` -> `7`), overflow-sized numeric tokens, newline input, plan-level cap boundaries, reroll+floor face preservation, and raw/effective `naturalD20` under advantage plus reroll/floor.
- Add deterministic tie-selection tests for all four keep/drop modes; the spec gives the tie rule, but the current minimum test contract only covers a `kh` tie.

## Cross-document reconciliation notes

- Requirements Specification v1.5 still lists 'write the ruleset specification' as an open question even though Ruleset Specification v0.8 now exists. Its surprise-handling open question also overlaps with Part B E-14, which now decides that the engine suggests Stealth vs passive Perception and the DM confirms (with ties decided by the DM); what remains open is how DM overrides are presented in the UI.
- Decision Log and Progress Tracker v1.7 still labels the architecture row as v4 and says the required CI checks are not confirmed. The repository contains Architecture v5.0, and the live `main-quality-gates` ruleset now requires the six contexts listed above. Its OQ-03 'How is surprise handled?' is also stale relative to E-14.
- Build Brief v1.8 section 14 still lists surprise handling and recording the Spring Boot version as open items, although E-14 and DEC-084 resolve the relevant baseline/decision. DM override presentation remains an open UX question.
- The Requirements Specification section 8 says undo/replay is out of scope, while FR-LOG-003 lists undoing the last action as a 'Could' milestone-B requirement. This is a later scope contradiction, not a Step 2 blocker.

## Next action

Have the owner decide the five API/data-model questions and whether to correct the conflicting test vector as suggested. Then implement Step 2 tests-first on a short-lived branch, run the full required gates, open/update a PR, and leave it open for the owner to review and merge.

Stop after Step 5. Obtain the content-schema, grid/geometry, and UX/API-with-threat-model specifications before implementing the corresponding later features.
