# Tabletop RPG Simulator: Decision Log and Progress Tracker (v1.5)

Oct 15, 2026 · @Samarth

Every major decision for the project, plus where the work stands. Edit statuses inline, or tell Claude what is done, what is next and what changed, and this log is updated to match.

## How to read this

Decision status: **Decided**, **Proposed** (needs confirmation), **Open**, **Superseded**. Work status: Not started, In progress, Done, Blocked, Cut. Refs point to the Requirements Specification (R) and `the Architecture document (v5.0)` (A, with ADR numbers).

## Decision log

| ID | Area | Decision | Why | Status | Refs |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | Product | A self-hosted, browser-based DM tool: character manager plus encounter runner on a rules engine. Not a virtual tabletop. | Focus on the rules engine and state management | Decided | R 1.1 |
| DEC-002 | Scope | Release split: v1 MVP (milestone A), v1.5 achievable goals, v2 stretch goals. No hard deadline; the two-month window decides how far v1.5 gets. | Scope control | Decided | R 11, A 18 |
| DEC-003 | Scope | DM-only play in v1; live read-only player view in v1.5; player-submitted actions in v2. | Multiplayer is costly | Decided | R D-10 |
| DEC-004 | Scope | No job queue (Redis or Kafka) for now. | Pathfinding is synchronous; a queue adds a moving part | Decided | A 15.2 |
| DEC-005 | Principle | Keep the implementation simple and leave a seam for each plausible upgrade (seams register). | Avoid speculative building | Decided | A 15 |
| DEC-006 | Course | No rubric; stack is free apart from Java; team split deferred. | Course has no technical requirement | Decided | R 10 |
| DEC-010 | Ruleset | D&D SRD 5.1 (2014 rules) under CC-BY-4.0 with attribution. Editions are not mixed; content records carry a ruleset field. | Most input data is 2014-based | Decided | ADR-13 |
| DEC-011 | Ruleset | Content cap is level 12. Milestone A is levels 1 to 5, then 5 to 12. | Where most campaigns end | Decided | R D-02 |
| DEC-012 | Classes | MVP: Fighter, Rogue, Paladin, Bard, Wizard. Stretch: Sorcerer, Warlock. Assassin is a Rogue subclass and is not in the SRD. | Mechanic variety, licence | Decided | R D-03 |
| DEC-013 | Subclasses | SRD built-ins, openly licensed packs, user imports and declarative homebrew. Popular community content is not bundled without a licence. Editor UI is v2. | Licence safety | Decided | R D-04 |
| DEC-014 | Content | Content packs with licence and attribution on every record. SRD pack is seeded; users import JSON packs. | Licence safety, extensibility | Decided | ADR-16 |
| DEC-015 | Spells | Automation tiers: 0 reference, 1 slot and dice, 2 full effects. Curated tier-2 set. Homebrew spells use the same schema with caps derived from SRD spells. | About 300 SRD spells cannot all be automated | Decided | A 4.9, 4.12 |
| DEC-016 | Monsters | SRD subset with at least 2 to 3 per level band. The DM picks the roster per encounter. Custom monsters are v1.5. Our own placeholder art only. | Official art is copyrighted | Decided | R A-05 |
| DEC-017 | Rulesets | The ruleset seam covers 5e-family rulesets, not arbitrary game systems. | Kernel assumes d20, HP, AC | Decided | A 15.1 |
| DEC-018 | Characters | One class per character for now. Schema is multiclass-ready: hit dice by die size, ability score improvements by class level, subclass on the class row. | Avoid a later migration | Decided | A 6.1 |
| DEC-020 | Engine | Pure Java engine with no I/O or framework imports, enforced by ArchUnit. | Testability | Decided | ADR-2 |
| DEC-021 | Kernel | Closed kernel (stats, modifiers, resources, slot pools, dice, action economy) plus open content. Modifier operations: set, add, multiply, min, max, advantage, disadvantage, with an optional activeWhen. Order: set, add, multiply, min/max; fractions round down. | Certainty on base variables | Decided | ADR-14 |
| DEC-022 | Abilities | Six fixed abilities, accessed only through Ability and AbilityScores, stored as rows. | Custom abilities stay possible | Decided | A 4.1 |
| DEC-023 | Combat | Reaction window is persisted engine state (AwaitingReaction). | Reactions interrupt turns | Decided | ADR-15 |
| DEC-024 | Features | Class features run through trigger points and an effect-type catalog, not per-class engine branches. A new mechanic means a new effect type in code. | Extensibility | Decided | A 4.4 |
| DEC-025 | Dice | Any die from d2 to d1000, up to 100 dice. Keep/drop, reroll and minimum-value modifiers. Injectable random source, uniform bounded draws, every face stored. | Homebrew and determinism | Decided | ADR-17 |
| DEC-026 | Initiative | Ties are decided by the DM (d20 roll-off available). Optional single roll for a group of identical monsters. | The SRD says the DM decides | Decided | R FR-ENC-002 |
| DEC-027 | Resilience | Every action carries an actionId and an expectedVersion; unique per encounter. | Duplicate submits and stale tabs | Decided | ADR-9 |
| DEC-028 | Errors | Stable error codes, caller versus system faults, one ControllerAdvice. | Operability | Decided | ADR-10 |
| DEC-030 | State | Stateless server, state in PostgreSQL, one action per short write transaction, no I/O inside transactions. | Resilience | Decided | ADR-3, ADR-8 |
| DEC-031 | Characters | State lives on the character; combatants reference it; derived numbers are never stored. | Single source of truth | Decided | A 6 |
| DEC-032 | Schema | Invariants as CHECK and UNIQUE constraints; Flyway; additive migrations. | Bugs cannot persist impossible state | Decided | ADR-7 |
| DEC-033 | Save and resume | Four layers: autosave, session pause with recap, checkpoints, export and import. Content versions pinned. Pausing never ticks game time. Checkpoints and export are v1.5. | Exact resume after a week | Decided | ADR-22 |
| DEC-034 | World | Campaign, Session, Scene, Encounter hierarchy. GameClock, audience on every event, Zones and ReadiedActions as entities. In the MVP schema. | Storytelling, resume, secrets | Decided | ADR-21 |
| DEC-035 | Vision | Seam now (sense stats, scene light, cell sight-blocking, VisionModel with full as default). Feature phases in v2. | Depth at low cost now | Decided | ADR-24 |
| DEC-036 | Journal | DM journal and quests with automatic capture of notable events, in v1.5. | Recap and story record | Decided | R FR-JRN |
| DEC-040 | Grid | Square grid, 8 neighbors. Equal-cost diagonals by default; true diagonal and alternating 5-10-5 are settings. Reach and adjacency always use 8 neighbors. Scaled integer costs. Hex later. | SRD fit, familiarity | Decided | ADR-20 |
| DEC-041 | Pathfinder | C++ service behind a Pathfinder port with Java fallback. Connect 100 ms, read 250 ms, circuit breaker, no retry. Benchmarked. | Performance trade-off with resilience | Decided | ADR-6 |
| DEC-042 | Pathfinder | The service is a generic weighted-grid search. The engine builds a per-mover cost map. | SRD movement rules depend on the mover | Decided | ADR-25 |
| DEC-043 | Transport | REST and JSON with keep-alive. gRPC deferred. | Simplicity | Decided | A 7.5 |
| DEC-050 | UI | Thymeleaf and HTMX, server-rendered fragments, self-hosted htmx, no SPA. | Team capacity | Decided | ADR-4 |
| DEC-051 | Live updates | Server-Sent Events from server to browser, behind an EncounterNotifier seam. | Simple and enough | Decided | ADR-5 |
| DEC-052 | UI | English only, modern desktop browsers, responsive layout. | Players may use phones on the LAN | Decided | R A-06 |
| DEC-053 | Accounts | Local username and password. First account is the instance admin (typically the DM). Invite-only after that. Roles per campaign. | LAN play | Decided | ADR-23 |
| DEC-060 | Security | Deny by default, per-campaign authorization at the data layer, 404 for foreign IDs, separate DM and player view models, CSRF, strong password hashing, secure headers. | Campaign secrets | Decided | A 10 |
| DEC-061 | Uploads | Images: sniffed type, 5 MB cap, re-encode, content-hash names, authenticated serving. | Untrusted input | Decided | A 10 |
| DEC-062 | Content | Imports and homebrew are data only: schema and size caps, no code execution, escaped on output. | Untrusted input | Decided | ADR-16 |
| DEC-063 | Deployment | Self-hosted Docker Compose (app, Postgres, pathfinder), LAN access, no default secrets, init script, named volumes, amd64 and arm64 images, single instance. | Anyone can run it | Decided | ADR-18 |
| DEC-064 | Observability | JSON logs with a trace ID forwarded to the pathfinder, Micrometer metrics, readiness ignores the pathfinder. | Debuggability | Decided | A 11 |
| DEC-070 | CI/CD | GitHub Actions, short-lived branches, required checks, build once and tag by commit, release is a tag plus a compose bundle, CI compose smoke test acts as staging. | Smooth delivery | Decided | ADR-12 |
| DEC-071 | Testing | Worked-example rule tests, ArchUnit, real PostgreSQL via Testcontainers (no H2), save/resume round trips, audience tests. | Correctness | Decided | A 12 |
| DEC-072 | Documents | Spec set: Requirements, Architecture, Ruleset spec, Content schema spec, Grid and geometry spec, UX and API spec with threat model, implementation plan. | Build from specs | Decided | R 10 |
| DEC-073 | Characters | Feats are supported and a character can hold any number. A feat is content: its effects, prerequisites and any choices live in its definition, and each character stores a feat row with its source. Normally taken in place of an ability score improvement; the DM can grant extras. A campaign setting enables feats (default on). The SRD ships only Grappler; others come from packs or homebrew. | Feats reuse modifiers, resources and trigger points, so no kernel change | Decided | Ruleset spec RS-LVL-005, E-2 |
| DEC-074 | Kernel | Ability scores are computed through the modifier pipeline (a stat per ability: base plus modifiers), so feats, magic items and spells can change them. Each ability also has a maximum (20 by default) that caps level-up and feat improvements and that some effects raise together with the score. A ceiling setting (default 30) clamps the final score; the floor is 0. | Needed for feat score bonuses, belts and curses; fits the Ability accessor seam | Decided | Ruleset spec RS-ABL-004 |
| DEC-075 | Characters | Ability score generation: version 1 ships a built-in list of common-practice methods (standard array, point buy, roll 4d6 drop lowest, roll 3d6, manual entry by the DM) from which the DM selects per campaign. DM-created methods are version 2. | Common practice, not SRD text; keeps v1 simple and leaves a registry seam | Decided | Ruleset spec RS-ABL-007, E-10 |
| DEC-076 | Engine | Roll bonuses (fixed or dice, possibly consumed) are a separate type from stat modifiers. The same source does not stack with itself (most potent, then most recent); different sources add. A d20 floor effect applies to each die before the kept die is chosen. | Needed for Bless, Bardic Inspiration, Reliable Talent and correct stacking | Decided | Ruleset spec RS-MOD-001, RS-MOD-002, RS-DIE-005 |
| DEC-077 | UX | An ability improvement that would pass the maximum shows a warning before it is applied, with the excess that would be lost, so the player can reallocate; if they proceed the excess is lost. | Avoids silent loss | Decided | Ruleset spec RS-ABL-005, E-8 |
| DEC-078 | Rules | No automatic success on a natural 20 and no automatic failure on a natural 1 for ability checks and saving throws (attack rolls keep their own rule). A campaign setting lets the DM enable it for checks, saves or both. | The SRD gives none for checks and saves; keep a house-rule option | Decided | Ruleset spec RS-D20-002, E-1 |
| DEC-079 | Characters | Hit points per level: the player chooses per level between the rolled value and the fixed average (the rulebook's default); a campaign setting lets the DM force either. Variants such as roll with a minimum of half the die are not built in for version 1. | Community research: in one forum poll of 121 groups about 44% let players choose, 26% force the average, 9% force rolls | Decided | Ruleset spec RS-LVL-003, E-3 |
| DEC-080 | Characters | Advancement is by milestone by default. XP advancement is a DM-selectable option planned for v1.5; the XP column and thresholds stay in the data model from v1. | Simplest for v1 without closing off XP play | Decided | Ruleset spec RS-LVL-002, E-4 |
| DEC-081 | Rules | When maximum HP rises, current HP rises by the same amount. When it falls below current HP, current HP becomes the new maximum. When a temporary increase ends, only the clamp applies. A creature at 0 HP is not raised. | Matches how effects like Aid and level-ups feel in play; avoids silent revival | Decided | Ruleset spec RS-LVL-007, E-5 |
| DEC-082 | Rules | Combat defaults: the DM confirms surprise (the engine suggests Stealth versus passive Perception; on a tie the DM decides); overlapping movement costs add; cover is set by the DM per attack in v1 (automatic from the grid in v1.5); monsters die at 0 HP unless marked to use the player rules; Tiny creatures occupy one cell in v1; 'can see' is true unless blinded, invisible or marked obscured until the vision work. | The SRD is silent or ambiguous; simple defaults with DM control | Decided | Ruleset spec E-14, E-16, E-17, E-19, E-20, E-21 |
| DEC-083 | Rules | A combatant joining a running fight is rolled into the initiative order by default, and the DM can choose join options (initiative roll mode, timing, starting conditions, first-turn restriction). Mid-combat surprise on existing combatants is an optional house rule, off by default. Damage absorbed by temporary hit points at 0 HP still counts as damage taken (death save failure, concentration check), with a house-rule setting. | Community practice for joiners; official clarification for temporary hit points | Decided | Ruleset spec E-15, E-18, RS-CMB-007 to RS-CMB-009 |

## Superseded decisions

| Was | Now | Why |
| --- | --- | --- |
| SRD 5.2 (2024 rules) as the ruleset | SRD 5.1 (2014 rules) | Most input data is 2014-based |
| Hex grid as the default | Square grid; hex later behind GridGeometry | SRD implies eight neighbors; simpler rendering and footprints |
| True diagonal length as the default metric | Equal-cost diagonals by default; true length is a setting | Familiar to most players |
| Initiative ties broken by higher Dexterity | Ties decided by the DM with a d20 roll-off | The SRD says the DM decides |
| Fog of war out of scope | Vision seam in the MVP schema; feature in v2 | Storytelling depth at low cost now |
| Pathfinder called from inside the engine | Pathfinding before the transaction; the engine re-validates the path | No I/O inside transactions |

## Open questions and assumptions

| ID | Question | Needed for | Status |
| --- | --- | --- | --- |
| OQ-01 | Which spells form the tier-2 (full automation) set? | Ruleset spec | Open |
| OQ-02 | Which SRD monsters per level band (1-3, 3-5, 5-6, 6-9, 10-12)? | Content schema, seed data | Open |
| OQ-03 | How is surprise handled? | Ruleset spec | Open |
| OQ-04 | How are DM overrides shown to players? | UX spec | Open |
| OQ-05 | Corner-cutting default for diagonal moves past walls (proposed: forbid) | Grid spec | Proposed |
| OQ-06 | Final upload limits (proposed: 5 MB, dimension cap) | Threat model | Proposed |
| OQ-07 | Verify SRD 5.1 lists (spells, monsters, conditions) and copy the attribution text verbatim | Ruleset spec, NOTICE file | Open |
| OQ-08 | Which versions to pin. Step 1 chose Java 25 (Temurin 25.0.4.1) and PostgreSQL 16.15; the Spring Boot version still needs recording here. | Step 1 | decided (Temurin java 25 due to LTS and springboot 4) |
| OQ-09 | Vision phasing (proposed: DM-revealed areas, radius, then SRD light and senses, all v2) | v2 plan | Proposed |
| OQ-10 | Which events are captured automatically for the journal? | Journal design | Open |
| OQ-11 | Team split by module | Planning | Deferred |
| OQ-12 | Optional demo hosting | Release | Deferred |
| OQ-13 | Resolved 2026-10-14: the rules constant stays at 6 seconds per round (10 rounds per minute), and how far the DM's game clock advances per combat round is a DM setting with a default of 6 seconds (decision E-9). | Ruleset spec E-9, game clock UI | Resolved |
| OQ-14 | Resolved 2026-10-16: E-15 (join options for combatants entering a running fight) and E-18 (damage absorbed by temporary hit points at 0 HP still counts as damage taken, with a house-rule setting) are decided; with E-14, E-16, E-17, E-19, E-20 and E-21 all Part B engine decisions are closed. | Combat core | Resolved |

## Progress tracker

### Documentation

| Item | Status | Notes |
| --- | --- | --- |
| Requirements Specification | Done | Draft, updated with review 2 |
| Architecture document (v4) | Done |  |
| Decision Log and Progress Tracker | Done | This document |
| AI Build Brief | Done |  |
| Project Design Guide for Collaborators | Done |  |
| Ruleset specification | In progress | Parts A (foundations, settled) and B (combat, drafted 2026-10-14) done; Parts C to H pending. Source of truth for D&D mechanics. |
| Content schema specification | Not started | Contract for packs and homebrew |
| Grid and geometry specification | Not started | Metrics, footprints, area shapes, cost map |
| UX and API specification, threat model | Not started |  |
| Implementation plan with acceptance criteria | Not started |  |

### v1 (MVP, milestone A)

| Week | Item | Status | Notes |
| --- | --- | --- | --- |
| 1 | Repo, Compose stack and image build (verified 2026-10-12); required CI checks (not yet confirmed) | done | setup the github repo, created CI pipeline, used github actions to enforce code hygenie and check runs, setup rulesets and created documentation |
| 1 | First-run admin (verified 2026-10-12); invite flow (Step 4) | In progress | first-run admin sucessfully completed, the invitation flow is pending |
| 1 | Dice engine and parser with property tests | In progress |  |
| 1 | ArchUnit rules (engine purity, ability accessor) | In progress |  |
| 1 | Schema for sessions, scenes, game clock, audience | Not started |  |
| 2 | Kernel: abilities, stats, modifiers, resources | Not started |  |
| 2 | Content pack loader and SRD seed pipeline | Not started |  |
| 2 | Character creation and derived sheet (Fighter) | Not started |  |
| 3 | Combat core: initiative, action economy, attacks, damage, death saves, conditions | Not started |  |
| 3 | Event log with audience, idempotency, versioning, error contract | Not started |  |
| 4 | Rogue, Paladin and Bard features; level-3 subclass features | Not started |  |
| 4 | Reaction window and opportunity attacks | Not started |  |
| 4 | Rests; session pause and resume with recap; game clock; scene light | Not started |  |
| 5 | Spells: tiers 1 and 2, slots, concentration, Wizard and Bard casting | Not started |  |
| 5 | Grid UI, distance metric setting, Java pathfinder, reach highlighting, area shapes | Not started |  |
| 5 | Monster roster per encounter and artwork upload | Not started |  |
| 6 | C++ pathfinder, adapter, circuit breaker, fallback, contract tests | Not started |  |
| 6 | Observability; milestone A complete | Not started |  |
| 7 | Live player view with audience filtering | Not started | First v1.5 item |
| 7 | Pathfinder benchmark | Not started |  |
| 8 | README quick start (LAN setup), attribution, release tag, compose bundle | Not started |  |

### v1.5 (achievable goals)

| Item | Status | Notes |
| --- | --- | --- |
| Live player view (full) | Not started | Proposed order 1 |
| Journal and quests | Not started | Order 2 |
| Campaign export and import | Not started | Order 3 |
| Checkpoints | Not started | Order 4 |
| JSON content-pack import | Not started | Order 5 |
| Custom monsters and homebrew spells | Not started |  |
| Levels 6 to 12 as data increments | Not started | Order 6 |
| Image upload for characters, scenes, handouts | Not started |  |
| XP advancement option | Not started | Milestone is the default |

### v2 (stretch goals)

| Item | Status | Notes |
| --- | --- | --- |
| Vision phases | Not started |  |
| Players submitting actions | Not started |  |
| Homebrew editor UI | Not started |  |
| Sorcerer and Warlock | Not started |  |
| Multiclass rules and UI | Not started |  |
| Hex geometry | Not started |  |
| DM-created ability score generation methods | Not started | Version 1 ships a built-in list |

## Extras and change requests

| Date | Request | Impact | Outcome |
| --- | --- | --- | --- |
| 2026-10-11 | Per-character vision depth for the player map | Kernel seam now, feature in v2 | Accepted |
| 2026-10-11 | Save, pause and resume; story journal | Session, scene, clock and audience in the MVP schema; journal, checkpoints and export in v1.5 | Accepted |
| 2026-10-11 | Monster artwork supplied by the DM | Asset upload and validation in v1 | Accepted |
| 2026-10-11 | Homebrew spells with the same restrictions as built-in ones | Same schema, SRD-derived caps; v1.5 | Accepted |

## Change log

| Date | Change |
| --- | --- |
| 2026-10-11 | Log created from the decisions made up to this date. |
| 2026-10-12 | Step 1 verified; performance notes and the first-build optimization item added; Java 25 and PostgreSQL 16.15 recorded. |
| 2026-10-12 | Ruleset Specification Part A drafted; DEC-073 (feats) decided; DEC-074 (ability scores as modifiable stats with a per-ability maximum, a ceiling and a floor) confirmed. |
| 2026-10-12 | Part A revised after an edge-case audit: new dice and roll composition (3.8) and modifiers and stacking (3.9) sections, new rules for ability scores, proficiency, advantage scope, saves, hit points and time, and new engine decisions E-8 to E-13 awaiting confirmation. |
| 2026-10-14 | Part A decisions closed: E-1, E-3 (player chooses hit points per level), E-4, E-5 (including the 0 HP exception), E-8 to E-13, and E-9 (6 second rules constant; DM-set clock advance). DEC-078 to DEC-081 recorded. E-6 and E-7 wait for Parts D and E. |
| 2026-10-14 | Ruleset Specification Part B (combat and conditions) drafted from the full SRD Combat and Conditions chapters; engine decisions E-14 to E-21 proposed (OQ-14). |

## Performance notes

### Update 2026-10-12: walking skeleton verified

Step 1 (walking skeleton) is verified: the Compose stack builds and runs, the database is healthy, and the first registered account becomes the instance administrator. The coding agent chose Java 25 (Temurin 25.0.4.1) and PostgreSQL 16.15; images are published under the `samarthbadola` GHCR owner at version 0.1.0.

### Baseline: cold build from source

| Step | Time | Note |
| --- | --- | --- |
| Total build | about 198 s | `docker compose ... up --build -d` on a fresh machine |
| Maven dependency resolution (`dependency:go-offline`) | about 146 s | The dominant cost; a one-time layer as long as `pom.xml` is unchanged |
| Base image layers (JDK, JRE, PostgreSQL) | about 35 s, in parallel | Network bound |
| Compile and package | about 7 s |  |
| Image export | about 4 s |  |
| Containers healthy | about 6 s after the build |  |

A code-only rebuild should reuse the dependency layer and take seconds; this still needs measuring. The cost recurs when `pom.xml` changes, in CI without a cache, and on a fresh machine. Users installing from release images pull prebuilt images and never build, so this matters mainly to developers and CI.

### Tracked item: first-build and startup optimization

Status: Not started. Deferred until the engine, rulesets and pathfinder exist. Re-measure at the end of Step 5 and before the first release, because the build and start-up time will grow with them.

Candidate levers (none decided): BuildKit cache mounts for the Maven repository; a CI layer cache; a leaner dependency-resolution step; a slimmer runtime image; faster JVM start-up (class data sharing, ahead-of-time processing); keeping the pathfinder image small.

Measure each time: cold build, warm code-only rebuild, rebuild after a `pom.xml` change, image sizes, and time until the app reports ready.

## Version and revision history

Current version: **1.5**. This is a living document: the version number goes up with every batch of decisions or status changes.

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-10-11 | Created: decision log DEC-001 to DEC-072, superseded decisions, open questions, progress tracker |
| 1.1 | 2026-10-12 | Step 1 verified; performance notes and the first-build optimization item; Java 25 and PostgreSQL 16.15 recorded; DEC-073 (feats) and DEC-074 (ability scores) added; Ruleset Specification row in progress |
| 1.2 | 2026-10-14 | DEC-074 confirmed; DEC-075 to DEC-077 (generation methods, roll bonuses and stacking, pre-warning); OQ-13; DM-created generation methods added to v2 |
| 1.3 | 2026-10-14 | DEC-078 to DEC-081 (natural 20 option, hit point method, advancement, maximum HP changes); XP advancement added to v1.5 |
| 1.4 | 2026-10-15 | Part A closed; Ruleset Specification Part B drafted; DEC-082 (combat defaults); OQ-14 |
| 1.5 | 2026-10-16 | E-15 and E-18 decided (DEC-083); OQ-14 resolved; version numbering and revision history added |