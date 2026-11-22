# Tabletop RPG Simulator: AI Build Brief (v1.30)

Oct 15, 2026 · @Samarth

Read this first. It tells an AI coding assistant what the project is, what is already decided, and how to start building without guessing. Where a more specific spec disagrees with this brief, the spec wins; where something is marked TODO, ask the human.

## 1. Rules for working on this project

1. Read this brief end to end before writing code. Companion documents, if provided: Requirements Specification, `Architecture`, Decision Log. The Content Schema Specification is a companion document too, and it is the authority for the format of content. The UX and API Specification is a companion document too, and it is the authority for routes, screens, the error responses of the web layer and the threat model. Its section 4.11 sets the look: Material 3 colour roles and type as CSS custom properties in a served stylesheet, and the components as our own CSS with no component library. If any of them disagree with this brief, tell the human.
2. The ruleset specification is the source of truth for D&D mechanics and is **written in parts; its roadmap table says which parts exist and which are decided**. Do not implement game rules from memory. Follow the build order in section 12: each step names the parts of the specification it may use (Steps 2 and 3 use Part A only; combat starts at Step 6 and uses Part B). Do not implement rules from a part before the step that needs it.
3. Keep it simple. Build the simplest thing that meets the current step. Add a seam only if it is in the seams register (5.6). Do not build deferred features.
4. Work in small tasks, each with a done-when you can demonstrate by running it. Commit per task using Conventional Commits.
5. Tests first for the engine. Never weaken, skip or edit a test to make it pass; fix the code. Report failures honestly instead of leaving them red or faking green.
6. The `engine` package imports only the JDK: no Spring, JPA, HTTP or file I/O. Ability scores are read and written only through `AbilityScores`. ArchUnit tests enforce both.
7. No I/O inside a database transaction (no HTTP calls, no SSE pushes, no pathfinding). One action is one short write transaction.
8. Security by default: deny-by-default routes, authorization at the data layer, input validation, no secrets in the repository or logs. Uploads and imports are untrusted.
9. Do not guess library versions or APIs from memory. Check current documentation, pin exact versions, and tell the human which versions you chose so they can be recorded in the Decision Log.
10. Use only SRD 5.1 content (CC-BY-4.0) with its attribution notice. No 2024-rules content, no non-SRD official text or artwork.
11. When something is ambiguous or blocked, stop and ask. When you finish a task, report what is done, what is left, and anything you added beyond the brief, so the human can update the Decision Log and Progress Tracker.
12. Prefer boring technology. If a new dependency seems necessary, justify it in one paragraph and ask first.
13. Build the backend and only demo elements of the frontend. Claude writes the real frontend from the Figma designs, in this same repository, so you never design a screen, style a page or edit a file in templates/ or static/. You build the engine, persistence, services, routes, form handling, fragment endpoints, view models and the demo elements of section 5.7. When the UX and API Specification gives no name for a route, fragment, field or view model that you need, stop and ask.

## 2. The project in one page

**What.** A self-hosted, browser-based tool for tabletop RPG Dungeon Masters (D&D 5e SRD 5.1 rules): a character manager plus an encounter runner. A rules engine automates the mathematics (dice, initiative, action economy, conditions, resources, spell slots, movement) and campaign state persists in PostgreSQL. It is not a virtual tabletop (no game engine, voice or video).

**How it is used.** The DM runs the stack on their own machine with `docker compose up`. Players on the same local network open the DM's address in a browser. The first account becomes the instance admin (typically the DM); later accounts need a DM-issued invite. Roles (DM or player) are per campaign. Plain HTTP on the LAN.

**Context.** A two-person course project (Enterprise Web Application, Java), about two months, no course rubric. Quality bar: correct rules, clean layering, tests, a one-command install.

| Release | Scope |
| --- | --- |
| v1 (MVP, milestone A) | DM-driven play for Fighter, Rogue, Paladin, Bard, Wizard at levels 1 to 5; multiclass-ready schema; dice; combat with reactions; curated tier-2 spells; SRD monsters as seed data with the stat block rules of Ruleset Specification Part G, the environment rules of version 1 in Ruleset Specification Part H (scene light, senses, cover chosen by the DM, fall damage, hazards, poisons and the madness and disease tracker), per-encounter rosters and monster image upload; square grid with distance-metric setting and movement; rests; sessions, scenes, game clock and audience in the schema with basic UI; LAN accounts with invites; single-step Docker |
| v1.5 (achievable) | Live player view (SSE) with audience filtering; journal and quests; checkpoints; campaign export and import with the campaign's access data; map upload with obstacle detection, creature placement and start areas, and the full-screen map; JSON pack import; custom monsters and homebrew spells; levels 6 to 12 |
| v2 (stretch) | Vision phases; players submitting actions; homebrew editor UI; Sorcerer and Warlock; multiclass rules and UI; hex geometry |

**Non-goals:** job queue (Redis, Kafka), gRPC, multi-instance deployment, Kubernetes, caching beyond static content, mobile app, 2024 rules, arbitrary non-5e game systems.

## 3. Tech stack

| Layer | Choice | Notes |
| --- | --- | --- |
| Language | Java 25 (LTS, Temurin) | Implemented in Step 1 (Temurin 25.0.4.1); this is the project baseline (DEC-084). |
| Build | Maven with wrapper | Profiles `integration` and `contract`. Unit tests `*Test`, integration `*IT`. |
| Framework | Spring Boot 4.1.1 (as implemented) | Web MVC, Validation, Security, Data JPA, Actuator. |
| Views | Thymeleaf plus HTMX | Self-hosted `htmx.js`, no CDN, no SPA. Controllers return HTML fragments for interactive actions. |
| Database | PostgreSQL 16 | Hibernate, Flyway. `spring.jpa.open-in-view=false`. Never H2. |
| Resilience | Resilience4j | Circuit breaker and timeouts only. No retries, bulkheads or rate limiters on the pathfinder. |
| Observability | Micrometer, Actuator, JSON logs | logstash-logback-encoder. Trace ID in the MDC. |
| Testing | JUnit 5, AssertJ, Testcontainers (PostgreSQL), ArchUnit, MockMvc |  |
| Formatting | Spotless or Checkstyle | Enforced in CI. |
| Pathfinder | C++20, CMake, cpp-httplib, nlohmann/json | Vendor the single headers in `third_party/`. GoogleTest or Catch2. |
| Containers | Docker, Compose v2, buildx | Multi-stage builds, non-root users, images for amd64 and arm64. |
| CI/CD | GitHub Actions, GHCR |  |
| Passwords | BCrypt or Argon2 | Through Spring Security. |

**Version policy.** Look up the current stable version of each dependency, pin exact versions in `pom.xml` and the Dockerfiles, and report them to the human. Do not rely on remembered version numbers.

## 4. Repository layout (monorepo)

```
rpg/
  app/                      Spring Boot service
    pom.xml, mvnw, .mvn/
    Dockerfile
    src/main/java/<group>/rpg/...        packages in 5.2
    src/main/resources/
      application.yml
      db/migration/                      Flyway V1__, V2__ ...
      templates/, static/                real frontend, written by Claude from Figma (5.7)
      templates-demo/, static-demo/      demo elements, written by the coding AI (5.7)
      content/srd-5.1/                   seed data for the SRD pack
      content-schema/                    JSON Schema files and vocabulary.json (CS-VAL-008, CS-VAL-010)
    src/test/java/...
  pathfinder/               C++ service
    CMakeLists.txt, Dockerfile, src/, include/, test/, contract/ (the shared corpus)
  deploy/
    docker-compose.yml, docker-compose.build.yml, .env.example
    scripts/init-env.sh, scripts/smoke.sh
  docs/                     requirements, architecture, decision log export
  .github/workflows/        ci.yml, release.yml, dependabot.yml
  NOTICE                    Creative Commons BY 4.0 attribution statement (CS-SRD-012)
  LICENSE, README.md
```

The group id and root package are a placeholder (`com.yourteam.rpg`). Use the real ones from the repository's pom.xml and architecture decision record; do not rename them.

## 5. Architecture the code must follow

### 5.1 Containers

Three services in Docker Compose: `app` (Spring Boot, port 8080, the only published port), `db` (PostgreSQL, named volume), `pathfinder` (C++, internal network only). The app also writes uploaded images to an `assets` volume. The pathfinder is optional: the app must run with `PATHFINDER_MODE=java`.

### 5.2 Packages and enforced rules

```
engine/        PURE rules (JDK only)
  kernel/      Ability, AbilityScores, Stat, Modifier, Resource, SlotPool, GameClock, Audience
  dice/        DiceExpression, parser, RandomSource
  combat/      Encounter, Combatant, turn state machine, reaction window, zones, readied actions
  action/      sealed Action types and resolvers
  feature/     trigger points, built-in effect types
  spell/       spell resolution, slots, concentration
  item/        ItemCatalog, Inventory, ArmorClassCalculator, HandsModel, AttackOptionBuilder, AttunementService, ChargeService, CurrencyLedger, EncumbranceCalculator, PlayerViewFilter (Ruleset Specification 11.12)
  monster/     MonsterCatalog, StatBlockValidator, MonsterSpawner, ZeroHitPointHook, TraitHandlers, LegendaryActions, EncounterXp (Architecture 4.17)
  grid/        GridGeometry, DistanceMetric, area shapes, cost maps; grid/geometry holds LineOfSight and CoverCalculator
  light/       LightModel (SceneAmbientLight, then SourcesAndZonesLight), emitters, zone flags (Architecture 4.18)
  vision/      VisionModel (FullVision by default), SightResolver
  fall/        fall events, FallDistanceProvider
  hazard/      hazard definitions, HazardTrigger
  affliction/  AfflictionTracker (madness and disease)
encounter/     web, application, persistence for combat
campaign/      campaigns, membership, invites, sessions, scenes, clock (simple layered CRUD)
character/     creation, level-up, derived sheet
content/       packs, SRD seed, importer, validation, immutable in-memory definitions
asset/         image upload, validation, re-encoding, storage
journal/       journal, checkpoints, export/import (v1.5)
identity/      users, login, invites
pathfinding/   Pathfinder port, JavaPathfinder, CppPathfinderClient
platform/      error handling, logging and trace filter, config, SSE hub
```

ArchUnit rules (a failing build, not a review comment):

1. `engine` imports no `org.springframework..`, `jakarta.persistence..`, `java.net.http..`, `java.io.File*` or `java.nio.file..`.
2. A feature's `web` package may not touch another feature's `persistence`. Cross-feature calls go through the other feature's `application` classes.
3. No cycles between feature packages.
4. Ability scores are accessed only through `AbilityScores` and `Ability`.
5. Content definitions are immutable once loaded; only `content` builds them.

Hexagonal style applies only to the engine. Campaigns, characters and users stay plain layered CRUD with JPA entities. Map the encounter aggregate between JPA entities and engine objects.

### 5.3 Request lifecycle for a state-changing action (for example a move)

1. Controller authenticates and validates the request (bean validation on a form DTO, never an entity). Every `form carries actionId (minted by the server when it renders the form`) and `expectedVersion`.
2. Application service reads an encounter snapshot (short read-only transaction).
3. If the action needs a path, call the `Pathfinder` port now, with no transaction open. Build the per-mover cost map first (section 6).
4. Open the write transaction. Load the encounter. If its version differs from `expectedVersion`, fail with `ENCOUNTER_VERSION_CONFLICT`. If `actionId` already exists for the encounter, return the stored result.
5. Call `RuleEngine.resolve(...)`. The engine **re-validates** the path against the grid and remaining movement.
6. Save state and append events (with dice results and audience). Commit.
7. After commit (`@TransactionalEventListener(AFTER_COMMIT)`), notify SSE viewers.
8. Return an HTML fragment. A result of `AwaitingReaction` is persisted in the pending\_reaction table (for an encounter or a scene) and resolved by a later ReactionDecision request; the engine resumes by replaying the action from its recorded dice and decisions.

### 5.4 Error contract

Caller faults are 4xx, not logged at ERROR. System faults are 5xx, logged with the trace ID. One `@ControllerAdvice` maps them; HTMX requests receive an error fragment with the right status, other clients `application/problem+json`. The cause of each code is in Architecture section 9, and the response bodies are in the UX and API Specification (3.3).

| Code | HTTP | Meaning |
| --- | --- | --- |
| `NOT_YOUR_TURN`, `NO_ACTIONS_LEFT`, `TARGET_OUT_OF_RANGE`, `INVALID_PATH` | 422 | rule violations |
| `REACTION_NOT_AVAILABLE`, `REACTION_OPTION_INVALID`, `CONTENT_INVALID`, `PACK_NOT_ENABLED`, `ASSET_INVALID`, `PLACEMENT_INVALID`, `SETTING_INVALID`, `REST_NOT_ALLOWED`, `REST_TOO_SHORT`, `HIT_DICE_UNAVAILABLE` | 422 | invalid request content |
| `ENCOUNTER_VERSION_CONFLICT`, `CAMPAIGN_VERSION_CONFLICT`, `IDEMPOTENCY_KEY_CONFLICT`, `REACTION_PENDING`, `SESSION_NOT_ACTIVE`, `ENCOUNTER_ACTIVE`, `ENCOUNTER_STARTED`, `SETTING_DECISION_REQUIRED`, `PACK_REVIEW_STALE`, `MAP_IN_USE`, `LAST_ADMINISTRATOR` | 409 | stale or conflicting state |
| `VALIDATION_FAILED` | 400 | malformed input with field details |
| `INVITE_INVALID`, `FORBIDDEN`, `CSRF_INVALID`, `REACTION_NOT_PERMITTED` | 403 | invite unknown, expired or used up; the role does not allow the route; the form token is missing or wrong; the principal does not control the decider |
| UNAUTHENTICATED | 401 | no valid session |
| RATE\_LIMITED | 429 | too many requests; the Retry-After header and retryAfterSeconds say when to try again |
| `NOT_FOUND` | 404 | also returned for resources in other campaigns |
| `SERVICE_UNAVAILABLE`, `INTERNAL_ERROR` | 503, 500 | system faults |

### 5.5 Security rules

- Every route requires authentication except register, login, static assets and health.
- Authorization lives in the application layer. Repository queries take the principal and filter by campaign membership. Foreign IDs return `NOT_FOUND`. Write negative tests for every per-ID route.
- Separate view models for DM and player. Player responses and SSE events are filtered by `audience` on the server; never send data and hide it in the template.
- CSRF enabled (HTMX sends the token in a header). Cookies `HttpOnly`, `SameSite=Lax`, `Secure` controlled by `APP_COOKIE_SECURE` (default false for LAN HTTP). Strict CSP without inline scripts. Login throttling. Sessions are stored in PostgreSQL (Spring Session JDBC) with an 8 hour idle limit and a 14 day absolute limit, and a password change ends the user's other sessions. A form that creates something carries a single-use form token. Rate limits apply to login, invite redemption, pack import, image upload and obstacle detection; the numbers are in the UX and API Specification (3.8).
- Invite codes are random, hashed at rest, expiring, limited-use and rate-limited.
- Uploaded images: check declared and sniffed type (PNG, JPEG, WebP), cap a monster image at 5 MB and 4096 pixels on a side and a map at 20 MB, 8192 pixels on a side and 40 million pixels in all, read the header dimensions before any decode, refuse SVG and animated images, decode and re-encode to strip metadata, name by content hash, store outside the web root, serve through an authenticated endpoint with `X-Content-Type-Options: nosniff`.
- Imported packs and homebrew are data only: validate against the schema, cap size, depth and counts, never fetch URLs inside them, never execute anything, escape on output.
- Exports and checkpoints are DM-only. An export names people: it holds the campaign's access data by username and never a password hash, a session, an account id or an invite code (SEC-022), and each export is entered in the campaign log (SEC-021).

### 5.6 Seams register (add a seam only if it is listed here)

| Seam | Simple today | Later |
| --- | --- | --- |
| `Ability`, `AbilityScores`, `pc_ability` rows | six fixed abilities | custom abilities |
| `GridGeometry`, `DistanceMetric` | square grid, equal-cost diagonals | hex, other metrics |
| `VisionModel` | `FullVision` | radius, then SRD light and senses |
| `Pathfinder` port | Java A\*, optional C++ service | gRPC, other algorithms |
| `EncounterNotifier` | in-memory SSE hub | Postgres LISTEN/NOTIFY or Redis |
| `RandomSource`, `Clock` | `SecureRandom`, system clock | seeded replay, simulations |
| `GameClock` | DM-advanced game time | calendars, weather |
| `Audience` on events | DM, all, player set | whispers, per-player secrets |
| `AssetStore` | files on a volume | object storage |
| Snapshot serializer with `schemaVersion` | none yet | checkpoints, export/import |
| `ruleset` and `pack` fields on content | SRD 5.1 only | other 5e-family packs |
| Trigger points and effect-type catalog | MVP class features | richer homebrew |
| Feature flags | a few config switches | merge unfinished work |

### 5.7 Frontend ownership and the seam

The coding AI and Claude work in the same repository, and each owns a separate area. The coding AI builds the backend and only demo elements of the frontend. Claude has the Figma designs and writes the real frontend from them: the templates, fragments, stylesheet, scripts and htmx wiring that people see. Neither of you edits the other's area (Decision Log DEC-108).

| Area | Owner | What is in it |
| --- | --- | --- |
| app/src/main/java, db/migration, content, content-schema, application.yml, tests | Coding AI | Everything that decides, stores or sends: the engine, services, controllers, form objects, view models and the SSE hub. A controller returns a view name and a model and contains no markup. |
| app/src/main/resources/templates-demo/ and static-demo/ | Coding AI | Demo elements: plain HTML that exercises a route so the human can click through it. |
| app/src/main/resources/templates/ and static/ | Claude | The real screens and fragments, the stylesheet with the Material 3 roles (UX and API Specification 4.11), the htmx wiring and the accessibility work (UXD-9). |

The rules of the seam:

- The seam is the UX and API Specification: its routes, fragment ids, form field names, view model names and fields, error responses and audience rules. A template name is the view name that the controller returns. Use the names that the specification gives.
- There are two template locations. A view name is rendered from templates/ when the file exists there, and from templates-demo/ otherwise. Configure the two Thymeleaf resolvers in that order, so a real screen takes over from its demo element with no change to Java code.
- A demo element is plain, semantic HTML with the browser's own look: a form with its fields, a list, a table, the fragment target with its id. It has no CSS framework, no layout work and no script except the htmx file. It needs enough to run the route's success path and its refusals by hand, and no more. Do not polish it.
- The server mints the CSRF token, the action id and the expected version (UXD-5), and puts them in the model under the names the specification gives. Both a demo element and the real template render them as hidden fields.
- Controller and fragment tests check the status, the headers, the fragment id, the model contents and the form field names. No test depends on CSS classes, layout or page chrome, because the real templates will change all of that.
- Text that the server builds in Java, such as the standard sentences, the validation messages and the audience labels, is yours and follows the UX writing rules of the UX and API Specification. Text that sits in a template is Claude's.
- If a route needs data that the specification's view model does not list, add it to the model, report it, and let the specification be updated. Do not work around it in a template.
- When the real template for a view name lands, Claude deletes the demo element of the same name in the same pull request.

## 6. Kernel and engine contracts

These are the shapes to implement. Names may be refined, but the behavior is fixed.

```java
public enum Ability { STR, DEX, CON, INT, WIS, CHA }   // the only place abilities are enumerated

public final class AbilityScores {                      // the only accessor
    public int score(Ability a) { /* base plus improvements, then ability stat modifiers, clamped to 0..ceiling (default 30) */ }
    public int modifier(Ability a) { return Math.floorDiv(score(a) - 10, 2); }
}

public enum ModOp { SET, ADD, MULTIPLY, MIN, MAX, ADVANTAGE, DISADVANTAGE }

public record Modifier(String targetStat, ModOp op, Ratio value,
                       String activeWhen,   // optional condition key
                       DurationSpec duration, String stackKey, String source,
                       int potency, long applicationSequence) {}
```

**Stat computation.** A stat is computed, never stored. Resolve each `stackKey` by potency, then application sequence. Across different keys the `SET` with the highest application sequence wins, even if it lowers the value; losing `SET`s remain stored as suppressed and resume when the winner ends. The sequence is a monotonic counter on the modifier instance, not game or wall-clock time. Apply that `SET`, all `ADD`s, all exact `MULTIPLY` operands with one `floorDiv`, then `MIN` and `MAX`; the breakdown names the winning `SET` and lists overridden ones. `Modifier.value` is a normalized `Ratio(int numerator, int denominator)` with a positive denominator and no floating point. `SET`, `ADD`, `MIN` and `MAX` on integer stats reject non-integer operands at construction. Only `skill:<skill>:prof_multiplier` is ratio-valued and is never rounded; its component is `floor(proficiency bonus x multiplier)`. Use `long` with `Math.multiplyExact`; zero remains zero. Maximum HP is derived from stored raw hit die results and the current Constitution modifier, then adjusted by modifiers; current HP is clamped to it. Roll bonuses remain a separate type (applied when a roll is made; fixed or dice; may be consumed). Advantage and disadvantage sources carry a scope (roll kind, ability, conditions). See Ruleset Specification section 3.9.

**Ability scores (DEC-074).** An ability score is a stat: the stored base plus recorded improvements, then the ability stat's modifiers, clamped to a ruleset ceiling (default 30) and a floor of 0. Each ability also has a maximum (default 20, raised by some effects together with the score) that caps level-up and feat improvements when they are chosen; any excess is lost. A score of 0 records an ability-at-zero event and the causing effect decides the consequence. The stored base stays between 1 and 30; extra points come from modifiers. Feats are content (DEC-073): a character can hold any number, each stored with its source and choices. An improvement that would pass the maximum shows a warning before it is applied, with the excess that would be lost. Ability score generation at creation uses a registry of built-in methods (standard array, point buy, roll 4d6 drop lowest, roll 3d6, manual entry) that the DM selects per campaign; DM-created methods are version 2.

**Campaign settings and HP rules from the Ruleset Specification.** Campaign settings so far: hit point method (player chooses per level between rolled and fixed average by default, or the DM forces one); advancement (milestone by default, XP as a later option, with the XP column and thresholds kept in the data); natural 20 and natural 1 on ability checks and saves (off by default; the DM can enable it for checks, saves or both); game clock advance per combat round (default 6 seconds, 0 for manual); ability score generation method; feats enabled; whether damage absorbed by temporary hit points at 0 HP still causes a death save failure (yes by default, with a house-rule option); optional mid-combat surprise (off by default); duplicate proficiency replacement (any two sources by default; the DM can narrow it to the background or turn it off). When a combatant joins a running encounter the DM chooses join options (initiative roll mode, timing, starting conditions, first-turn restriction). Maximum HP is a modifiable stat. When it rises, current HP rises by the same amount; when it falls below current HP, current HP becomes the new maximum; when a temporary increase ends, only the clamp applies; a creature at 0 HP is not raised (decided).

**Resources.** `Resource(key, current, max, recharge)` with recharge in `TURN, SHORT_REST, LONG_REST, DAWN, NEVER`. `SlotPool` holds slots by spell level; only the used counts are stored, totals derive from class levels.

**Durations.** One `DurationKind` has 13 exact names: `INSTANT`, `ROUNDS(amount)`, `CLOCK(amount, seconds)`, `CONCENTRATION(max)`, `UNTIL_TURN_START`, `UNTIL_TURN_END` (for a named creature), `UNTIL_DISPELLED`, `UNTIL_SHORT_OR_LONG_REST`, `UNTIL_LONG_REST`, `UNTIL_USED`, `PERMANENT`, `WHILE_EQUIPPED`, `WHILE_ATTUNED`. Store an absolute end or end condition, never a counter (E-82). `ROUNDS` and turn kinds store an end round and creature; `CLOCK` stores absolute seconds. When an encounter ends, `ROUNDS` becomes `CLOCK` at 6 seconds per remaining round. Rest kinds end on `RestResolver` events. `INSTANT` is never active. `UNTIL_USED` ends when its roll bonus is consumed. A timed and consumed bonus such as Bardic Inspiration is `CLOCK` plus consumed on use; its second end condition is decided in Step 7. A round is 6 seconds of game time. Game time advances only through the DM, a rest or travel, never with real time. In combat, each completed round advances the clock by a DM-set amount (default 6 seconds; decision E-9). The rules constant of 6 seconds per round (10 rounds per minute) does not change.

**Dice.**

```java
public interface RandomSource { int nextInt(int bound); }   // 0..bound-1, uniform; production: SecureRandom

public record DieRoll(int rolled, Integer rerollFace, int value, boolean kept, boolean rerolled, boolean floored, boolean critical) {}
public record RollTerm(String label, String source, int sign, String expression, List<DieRoll> dice, int fixed, int subtotal, boolean doublesOnCrit, boolean criticalNotApplied) {}  // D-16 runtime REJECT marker; Step 7 reads it. RollResult holds List<RollTerm>, total, naturalD20, mode

public final class DiceExpression {
    public static DiceExpression parse(String s);            // throws on malformed or over-limit input
    public RollResult roll(RandomSource rng);
}
```

Grammar: `NdX` plus optional keep or drop (`kh`, `kl`, `dh`, `dl` with a count), reroll and minimum-value modifiers, and `+` or `-` an integer. Examples: `2d20kh1` (advantage), `4d6dl1` (ability scores), `3d8+4`. Limits: X from 2 to 1000, N from 1 to 100, at most 200 dice, 20 terms and 200 characters per expression, 400 dice after critical doubling, and integer literals up to 1,000,000. Draw each die with the bounded `nextInt` (never `random() * X` or modulo). Record every face and whether it was kept. A roll is an ordered list of labeled, signed terms (dice groups or fixed numbers; bonus and penalty dice such as Bless and Bane are extra terms), and the grammar accepts sums like d8+3d6+3 and d20+3-d4. A reroll replaces a die once; a d20 floor (Reliable Talent) applies to each d20 before the kept die is chosen; each dice term carries a crit-doubling flag (a critical hit doubles dice, not modifiers); the natural d20 is recorded; the critical threshold is a rule parameter, not a hard-coded 20. See Ruleset Specification sections 3.8 and 3.10.

**Actions and results.**

```java
public sealed interface Action
    permits AttackAction, CastSpellAction, MoveAction, EndTurnAction, CheckAction, SaveAction, ReactionDecision, CancelReaction {}

public sealed interface ResolutionResult permits Completed, AwaitingReaction {}

public interface RuleEngine {   // pure: no I/O. MoveAction carries a path the engine re-validates.
    ResolutionResult resolve(Encounter encounter, Action action, RandomSource rng);
}
```

`resolve` throws `RuleViolation` (with a stable error code) for illegal actions. A miss or a failed save is a normal result. Every `CombatEvent` records dice results and an `Audience` (`DM_ONLY`, `ALL`, or a player set).

**Combat state machine.** Setup, RollingInitiative, TurnStart, AwaitingAction, Resolving, AwaitingReaction (persisted), TurnEnd, Finished. A pending reaction rejects other actions in its encounter or scene with `REACTION_PENDING`.

**The reaction window (one mechanism, decided 2026-11-16, ADR-31).** The reaction window is the engine's only pause. Build it to Ruleset Specification RS-REA-001 to RS-REA-010 and Architecture 5.4: it opens at defined points of an action only when someone has an eligible option; options carry a cost (the reaction, a resource, nothing) and an effect (change the roll or damage, start an action, cancel); several creatures can decide in one window; the order is acting side, then defending side, then damage changes; windows nest to depth 6 and the cap is logged; it works in encounters and scenes (`CheckAction` and `SaveAction` for scene-level rolls); the engine resumes by replay with a `ReplayingRandomSource` that returns the recorded draws first; a cancel keeps the dice in the log; eligibility never depends on information the decider lacks; answers are authorized per decider (`REACTION_NOT_PERMITTED`). The open window is stored in the `pending_reaction` table, not as a column on the encounter. `RuleEngine.resolve` takes the pre-action snapshot, the action, the continuation so far (recorded draws and decisions) and a `RandomSource`. Do not add a second kind of window. The step that builds it is done only when the reaction window suite T-22 to T-39 of Architecture 12.1 passes.

**Reaction window details (decided 2026-11-16).** Use one list of point names everywhere, in the engine and in the `pending_reaction` check: `ON_MOVE_STEP`, `BEFORE_ATTACK_ROLL`, `AFTER_ATTACK_ROLL`, `AFTER_HIT`, `AFTER_DAMAGE_ROLL`, `AFTER_SAVE_ROLL`, `AFTER_SAVE_FAIL`, `AFTER_CHECK_ROLL`, `ON_CAST`, `AFTER_DAMAGE_TAKEN`, `ON_READIED_TRIGGER`. The list is owned by the Ruleset Specification (E-68); ON\_FALL joins it in version 1.5 under E-84 and E-142, when the engine runs falling (Ruleset Specification 13.6). ON\_TURN\_END joins it with the monster step; the Ruleset Specification owns it (E-119). Legendary actions are spent under the cost kind LEGENDARY\_ACTION between other creatures' turns (Ruleset Specification 12.6). The older trigger names above are aliases (`ON_ATTACK_ROLL` is `AFTER_ATTACK_ROLL`, `ON_DAMAGE_ROLL` is `AFTER_DAMAGE_ROLL`, `ON_SAVE` is `AFTER_SAVE_ROLL`, `ON_MOVE` is `ON_MOVE_STEP`, `ON_DAMAGE_TAKEN` is `AFTER_DAMAGE_TAKEN`). A window at a point runs in four ordered steps (acting side raises its result; defending side lowers it or changes the outcome; acting side changes damage; defending side changes damage), and a decider is asked at each step where it has an eligible option, so passing Shield does not forfeit Uncanny Dodge; within the defending side's step the order of options is E-83 (Ruleset Specification 10.11). An option can carry a mandatory follow-up choice by the same decider (Savage Attacker's choice of total). While a window is open, reject any level-up, rest, clock advance, DM edit or other action touching a creature in it with `REACTION_PENDING`; the continuation pins the settings and content definition versions the action has read. An action spawned by a reaction that needs an undecided setting after dice were rolled uses the default and leaves the setting undecided; never ask after a die is rolled. The orders of a saving throw (roll, WS1, outcome, WS2 on a failed roll, final outcome, then Evasion and similar adjustments; an automatic failure opens no window, except that with the setting legendaryResistanceOverridesAutoFail on, it opens a window at AFTER\_SAVE\_FAIL for Legendary Resistance only (Ruleset Specification 12.12)) and of an ability check are RS-REA-013 in Ruleset Specification Part B.

**Trigger points.** The reaction window points of Ruleset Specification RS-REA-001 (such as `ON_MOVE_STEP`, `AFTER_ATTACK_ROLL`, `AFTER_DAMAGE_ROLL`, `AFTER_SAVE_ROLL`, `ON_CAST`, `AFTER_DAMAGE_TAKEN` and `ON_TURN_END`), together with `ON_DAWN` and `START_OF_OWN_TURN`. The full list is in the Content Schema Specification, section 7, and the older names such as `ON_ATTACK_ROLL` are aliases (Ruleset Specification section 7.2). Resources refresh through recharge rules on rests, not through a trigger. A feature is data: trigger, conditions, an effect type with parameters, an optional resource cost, a usage limit. The engine ships a catalog of effect types in Java (`add_damage_dice`, `grant_advantage`, `grant_resistance`, `modifier`, `spend_resource`, `condition`, `heal`, `reaction_option` and more; the registry of the Content Schema Specification, section 7, is the authority for names and fields). Data may only combine known effect types; a genuinely new mechanic is a new effect type in code.

**Grid and movement.**

```java
public interface GridGeometry {
    List<Cell> neighbors(Cell c);
    int stepCost(Cell from, Cell to, DistanceMetric metric);
    int distance(Cell a, Cell b, DistanceMetric metric);
    Set<Cell> areaCells(AreaShape shape, Cell origin, Facing facing);
    boolean lineOfSight(Cell a, Cell b, GridMap map);
    Set<Cell> footprint(Cell anchor, Size size);
}   // SquareGeometry now
```

| Metric | Straight step | Diagonal step |
| --- | --- | --- |
| `EQUAL_COST` (default) | 100 | 100 |
| `TRUE_DIAGONAL` | 100 | 141 |
| `ALTERNATING` (5-10-5) | 100 | 100 then 200 alternately (search state includes parity) |

Costs are scaled integers (100 per 5 ft) so budgets are deterministic: speed 30 ft is 600. Fractions round down. **Reach and adjacency always use the 8 neighbors** under every metric; movement cost, ranges and area radii use the chosen metric. Diagonal steps past blocked corners are disallowed by default (a setting).

**Per-mover cost map** (built by the engine, not the pathfinder): difficult terrain costs double; another creature's space counts as difficult terrain; a hostile creature's space is blocked unless the size difference is at least two steps; non-hostile spaces can be passed through but not stopped in (`noStop`); walls are blocked. The map is sent as digits (0 blocked, 1 to 9 the cost multiplier); extra costs add (E-16); creatures larger than Medium get an anchor-cell map. The exact format is in the Grid and Pathfinder Specification.

**Vision seam.** `VisionModel.visibleCells(viewer, scene, map)` returns a `Visibility`. Only `FullVision` exists in v1. The data model already has sense stats, scene ambient light (`BRIGHT`, `DIM`, `DARK`) and per-cell `blocksSight`.

## 7. Data model

PostgreSQL, Flyway migrations (never edit an applied migration; prefer additive changes). Reserved words: use `app_user` and `player_character`. Store choices and current state; derive numbers (modifiers, AC, proficiency bonus, save DCs, attack bonuses, max HP, slot totals).

| Group | Tables |
| --- | --- |
| Identity | `app_user`, `campaign`, `campaign_member (role DM or PLAYER)`, `invite`, `campaign_pack`, the Spring Session JDBC tables |
| World | `game_clock`, `game_session (ACTIVE, PAUSED, ENDED)`, `scene (ambient_light), map (Architecture 6.1)`, `zone` |
| Content | `content_pack`, `class_def`, `subclass_def`, `class_level_def`, `feature_def`, `spell_def`, `item_def`, `monster_def`. Common columns: `id`, `pack_id`, `key`, `ruleset`, `name`, `definition` (JSONB), `license`, `attribution`; UNIQUE (`pack_id`, `key`) |
| Characters | `player_character`, `pc_ability` (one row per ability), `pc_class` (one row per class), `pc_hit_dice` (per die size), `pc_asi` (by class and class level), `pc_proficiency`, `pc_resource`, `pc_slot`, `pc_spell`, `inventory_item` |
| Encounters | `encounter` (round, turn index, `version`), pending\_reaction (the open reaction window; Architecture 5.4), `encounter_roster` (with the placement of each group), `combatant`, `active_effect`, `readied_action`, `combat_event` (`seq`, `action_id`, type, payload JSONB, `audience`) |
| Support | `asset` (metadata and the kind MONSTER or MAP; files on the volume), rolled\_set, form\_token, `checkpoint`, `journal_entry` (v1.5) |

Character state lives on the character (HP, temp HP, hit dice spent, death saves, conditions, resources, slots used, spells, inventory). A combatant for a player character references it and holds only encounter state (initiative, position, reaction used, concentration, active effects). Monster combatants hold their own HP.

Schema constraints to create (the engine enforces the HP upper bound because max HP is derived):

```sql
ALTER TABLE player_character
  ADD CONSTRAINT hp_non_negative CHECK (hp_current >= 0 AND hp_temp >= 0);
ALTER TABLE pc_ability
  ADD CONSTRAINT one_score_per_ability UNIQUE (player_character_id, ability),
  ADD CONSTRAINT score_range CHECK (score BETWEEN 1 AND 30),
  ADD CONSTRAINT known_ability CHECK (ability IN ('STR','DEX','CON','INT','WIS','CHA'));
ALTER TABLE pc_class
  ADD CONSTRAINT one_row_per_class UNIQUE (player_character_id, class_key),
  ADD CONSTRAINT level_range CHECK (level BETWEEN 1 AND 20);
ALTER TABLE pc_hit_dice
  ADD CONSTRAINT one_row_per_die UNIQUE (player_character_id, die_size),
  ADD CONSTRAINT valid_die CHECK (die_size IN (6, 8, 10, 12) AND spent >= 0);
ALTER TABLE pc_asi
  ADD CONSTRAINT one_asi_per_level UNIQUE (player_character_id, class_key, class_level);
ALTER TABLE pc_slot
  ADD CONSTRAINT slot_bounds CHECK (slot_level BETWEEN 1 AND 9 AND used >= 0);
ALTER TABLE combatant
  ADD CONSTRAINT initiative_unique UNIQUE (encounter_id, initiative_slot),
  ADD CONSTRAINT monster_hp_bounds CHECK (monster_hp_current IS NULL
      OR (monster_hp_current >= 0 AND monster_hp_current <= monster_hp_max));
ALTER TABLE campaign_member
  ADD CONSTRAINT one_membership UNIQUE (campaign_id, user_id),
  ADD CONSTRAINT valid_role CHECK (role IN ('DM', 'PLAYER'));
ALTER TABLE combat_event
  ADD CONSTRAINT action_once UNIQUE (encounter_id, action_id),
  ADD CONSTRAINT seq_once UNIQUE (encounter_id, seq),
  ADD CONSTRAINT valid_audience CHECK (audience IN ('DM', 'ALL', 'PLAYERS'));
ALTER TABLE game_clock
  ADD CONSTRAINT one_clock UNIQUE (campaign_id), ADD CONSTRAINT clock_ok CHECK (game_seconds >= 0);
ALTER TABLE game_session
  ADD CONSTRAINT valid_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED'));
CREATE UNIQUE INDEX one_open_session ON game_session (campaign_id)
  WHERE status IN ('ACTIVE', 'PAUSED');
ALTER TABLE scene
  ADD CONSTRAINT valid_light CHECK (ambient_light IN ('BRIGHT', 'DIM', 'DARK'));
ALTER TABLE asset
  ADD CONSTRAINT asset_size CHECK (size_bytes > 0 AND ((kind = 'MONSTER' AND size_bytes <= 5242880) OR (kind = 'MAP' AND size_bytes <= 20971520)));
```

Schema constraints for inventory, created in the equipment step's migration (Architecture 4.16): quantity above zero; charges and every currency amount never negative; the hand or slot position from the fixed set of Ruleset Specification 11.4; an item held by a character or on the ground, never both. Do not constrain the equipment slot limits in the schema, because the DM can override them (E-111); the engine enforces them. Test the constraints on a real database.

**Schema additions for monsters**, created in the monster step's migration (Architecture 4.17): `combatant` gains `form` (text), `legendary_points` (smallint, not null, default 0, at least 0), `controller_user_id` (uuid referencing `app_user`; empty means the DM), `created` (boolean, not null, default false) and the trait flags `regen_suppressed`, `dying_by_trait` and `in_sunlight` (boolean, not null, default false). `encounter` gains `in_lair` (boolean, not null, default false). `inventory_item` gains `corrosion` (smallint, not null, default 0, at most 0). The item material lives in the definition JSON, not in a column. Do not encode the corrosion and destruction limits of Ruleset Specification Part G in the schema; the engine enforces them. Test the constraints on a real database.

**Schema additions for the environment.** The tables and columns, their constraints and the version in which each arrives are in the schema sketch of Ruleset Specification 13.9 (Architecture 4.18). Create each in the migration of the step that needs it: the scene and combatant columns and the tables `hazard_instance`, `hazard_trigger_log` and `affliction` in the version 1 step, the map cell columns and the tables `zone_flag` and `fall_event` in the version 1.5 step, and `map_cell.floor_ft` and `combatant.altitude_ft` in the version 2 step. Give every added column a constant default so that no table is rewritten, put every enumeration in a CHECK constraint, and test the constraints on a real database. The engine enforces the rules, and the schema enforces only what the sketch lists.

**The environment packages and the pure-engine boundary.** `engine.light`, `engine.vision`, `engine.fall`, `engine.hazard`, `engine.affliction` and `engine.grid.geometry` follow the rule of the rest of the engine: the JDK and the engine core only, with no Spring, web or persistence. `LightModel`, `SightResolver`, `FallDistanceProvider` and `HazardTrigger` are Java interfaces in the pure engine, and their adapters live in the application layer. Write the import-boundary test T-100 with the first of the six packages, so that it fails on the first forbidden import.

Schema additions for maps, placement and access (Architecture 6.1; the names are suggestions). Create each in the migration of the step that needs it. A map belongs to a campaign: name, state (DRAFT or READY), the asset of kind MAP, the square size and offset in pixels, the width and height in squares (each from 1 to 200), the per-square flags as compact arrays with the source of each obstacle or difficult square (DETECTED or CONFIRMED), and a version that rises with every save of the squares. Only confirmed squares count in play. encounter gains a nullable reference to a map, and a map in use cannot be deleted (MAP\_IN\_USE). Each roster group stores its placement choice (BY\_HAND, ANYWHERE or AREA), its area, the start area for the player characters and the preview layout. Preview positions bind nothing until the encounter starts, and the layout chosen at the start is written to the log so that a replay places the same squares. player\_character gains held (boolean, not null, default false) and intended\_owner (the username an import named, empty unless held). rolled\_set holds, for one player in one campaign, the ability score method, the six values, who rolled and when, and is removed when a character is created from it. form\_token holds a single-use token for a creation that is not in the action log. asset gains kind (MONSTER or MAP); a monster image is mapped by the monster's content key, so one image serves every copy. Test the constraints on a real database.

Query discipline: one explicit fetch graph for an encounter with combatants and effects; projections for list pages; indexes on every foreign key and on `combat_event (encounter_id, seq)`; a connection pool of about 10; the event log is cursor-paginated by `seq`.

## 8. Pathfinder service (C++)

Stateless HTTP service on port 9000 (internal network only). Endpoints: `POST /v1/path` (A\*), `POST /v1/reach` (all cells within a budget), `GET /health`; later `/v1/los` and `/v1/vision`. It knows nothing about D&D: it searches a weighted grid.

```json
{ "geometry": "square", "metric": "equal_cost", "corners": "forbid_if_blocked",
  "rows": ["....#...", "..~.#...", "........"],
  "noStop": [[3, 1]],
  "start": [0, 0], "goal": [7, 2], "budget": 600 }
```

```json
{ "found": true, "path": [[0,0],[1,1],[2,2]], "cost": 200 }
```

Cell characters: `#` blocked, `.` normal (cost 100), `~` difficult (200). `metric` is `equal_cost`, `true_diagonal` or `alternating`. `noStop` cells may be passed through but not stopped in (never the goal; excluded from `/v1/reach` destinations). `geometry` is the extension point for other grids; only `square` is implemented.

**Behavior.** 8-neighbor A\* with scaled integer costs. Heuristic: Chebyshev times 100 for `equal_cost`, octile (100 and 141) for `true_diagonal`. `alternating` expands the state (cell, diagonal parity). Corner cutting is forbidden when either orthogonal neighbor is blocked, unless `corners` says otherwise. `/v1/reach` is Dijkstra bounded by `budget`.

**Limits (reject or cut off, never hang or crash).** Body at most 1 MB; grid at most 200 by 200; a node-expansion cap; a per-request deadline of 200 ms; strict JSON validation with bounds checks. Run as a non-root user. The binary supports `--healthcheck` (calls its own `/health`, exits 0 or 1) so the image needs no `curl`. Log JSON lines including the `X-Request-Id` header as `trace_id`.

**C++ conventions.** C++20, CMake, cpp-httplib and nlohmann/json vendored as single headers in `third_party/`, GoogleTest or Catch2. CI builds tests with `-fsanitize=address,undefined`. Prefer static linking of the C++ runtime in the final image.

**Java side.**

```java
public interface Pathfinder {
    PathResult findPath(CostMap map, Cell start, Cell goal, Settings settings);
    ReachResult reach(CostMap map, Cell start, int budget, Settings settings);
}
```

`JavaPathfinder` implements the same algorithm and is always available. `CppPathfinderClient` uses one shared `HttpClient` with keep-alive, connect timeout 100 ms, read timeout 250 ms, a Resilience4j circuit breaker, no retry, and falls back to `JavaPathfinder` on timeout, error or an open circuit. `pathfinder.mode` is `java`, `cpp` or `auto` (default `auto`). Forward the trace ID as `X-Request-Id`. Metrics: `pathfinder.call.duration`, `pathfinder.fallback`, breaker state. Readiness must not depend on the pathfinder (the fallback keeps the app healthy). Settings carries the metric, the corner rule and the starting parity. The Grid and Pathfinder Specification is the authoritative contract for the service, the cost-map format, the search algorithm and the client policy. Contract tests run the shared corpus in pathfinder/contract against both implementations; they must return identical paths, costs and expansion counts. A benchmark (Java in-process versus C++ over HTTP at 30x20, 100x100 and 200x200 grids) runs nightly and its results go in the project report.

## 9. Environment and setup

### 9.1 Prerequisites

JDK 25, Git, Docker with Compose v2 and buildx. CMake 3.25 or newer and a C++20 compiler (GCC 13 or Clang 17) only if you work on the pathfinder.

### 9.2 First run and dev loop

```
git clone <repo> && cd rpg
./deploy/scripts/init-env.sh
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up
# open http://localhost:8080 and register the first account (it becomes the admin)

# development
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up db
cd app && ./mvnw spring-boot:run
./mvnw test                      # unit and architecture tests
./mvnw verify -Pintegration      # Testcontainers
```

### 9.3 `deploy/.env.example`

```
RPG_VERSION=0.1.0
DB_PASSWORD=run-init-env-to-generate
APP_COOKIE_SECURE=false
PATHFINDER_MODE=auto
```

### 9.4 `deploy/scripts/init-env.sh`

```
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then echo '.env already exists; leaving it'; exit 0; fi
pw="$(openssl rand -base64 24 | tr -d '=+/')"
printf 'RPG_VERSION=0.1.0\nDB_PASSWORD=%s\nAPP_COOKIE_SECURE=false\nPATHFINDER_MODE=auto\n' "$pw" > .env
echo 'Wrote deploy/.env'
```

### 9.5 `deploy/docker-compose.yml`

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: rpg
      POSTGRES_USER: rpg
      POSTGRES_PASSWORD: ${DB_PASSWORD:?run deploy/scripts/init-env.sh first}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ['CMD-SHELL', 'pg_isready -U rpg -d rpg']
      interval: 5s
      retries: 10
    restart: unless-stopped

  pathfinder:
    image: ghcr.io/OWNER/rpg-pathfinder:${RPG_VERSION:?set RPG_VERSION}
    healthcheck:
      test: ['CMD', '/app/pathfinder', '--healthcheck']
      interval: 10s
    restart: unless-stopped

  app:
    image: ghcr.io/OWNER/rpg-app:${RPG_VERSION:?set RPG_VERSION}
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/rpg
      SPRING_DATASOURCE_USERNAME: rpg
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      PATHFINDER_MODE: ${PATHFINDER_MODE:-auto}
      PATHFINDER_URL: http://pathfinder:9000
      APP_COOKIE_SECURE: ${APP_COOKIE_SECURE:-false}
      APP_ASSET_DIR: /data/assets
    volumes:
      - assets:/data/assets
    ports:
      - '8080:8080'
    restart: unless-stopped

volumes:
  pgdata:
  assets:
```

`deploy/docker-compose.build.yml` adds local builds:

```yaml
services:
  app:
    build: ../app
  pathfinder:
    build: ../pathfinder
```

Publishing `8080:8080` exposes the app on all interfaces so LAN players can reach the host's address; use `127.0.0.1:8080:8080` for single-machine use. The join screen derives the address from the DM's browser URL and warns when it is `localhost`. Back up both the `pgdata` and `assets` volumes.

### 9.6 Dockerfiles

```
# app/Dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

Both images run as a non-root user; keep the double-quoted exec form for `ENTRYPOINT` in the real file.

```
# pathfinder/Dockerfile
FROM debian:bookworm-slim AS build
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake ca-certificates \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
RUN cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j

FROM debian:bookworm-slim
RUN useradd --system --uid 10001 app
COPY --from=build /src/build/pathfinder /app/pathfinder
USER app
EXPOSE 9000
ENTRYPOINT ["/app/pathfinder"]
```

### 9.7 Configuration

All configuration comes from environment variables.

| Variable | Property | Default | Purpose |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | `spring.datasource.*` | none | database connection |
| `PATHFINDER_MODE` | `pathfinder.mode` | `auto` | `java`, `cpp` or `auto` |
| `PATHFINDER_URL` | `pathfinder.url` | `http://pathfinder:9000` | service address |
| `APP_COOKIE_SECURE` | `app.cookie-secure` | `false` | set `true` behind HTTPS |
| `APP_ASSET_DIR` | `app.asset-dir` | `/data/assets` | uploaded image storage |

Fixed properties: `server.shutdown=graceful`, `spring.jpa.open-in-view=false`, `spring.flyway.enabled=true`, `management.endpoint.health.probes.enabled=true`, health and info (and Prometheus) exposed. Keep the pathfinder health indicator out of the readiness group.

## 10. CI/CD

Principles: `main` is always deployable; short-lived branches; every change verified automatically before review; build once and tag images by commit; rollback means starting the previous tag. For this self-hosted product, the CI compose smoke test acts as staging.

### 10.1 `.github/workflows/ci.yml` (starting point; verify and pin action versions)

```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
jobs:
  unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {distribution: temurin, java-version: '25', cache: maven}
      - run: ./mvnw -B -ntp spotless:check test
        working-directory: app
  integration:
    needs: unit
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {distribution: temurin, java-version: '25', cache: maven}
      - run: ./mvnw -B -ntp verify -Pintegration
        working-directory: app
  pathfinder:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build and test with sanitizers
        working-directory: pathfinder
        run: |
          cmake -B build -DCMAKE_BUILD_TYPE=Debug -DCMAKE_CXX_FLAGS='-fsanitize=address,undefined'
          cmake --build build -j
          ctest --test-dir build --output-on-failure
  contract:
    needs: [unit, pathfinder]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {distribution: temurin, java-version: '25', cache: maven}
      - run: docker build -t pathfinder:ci pathfinder
      - run: ./mvnw -B -ntp verify -Pcontract -Dpathfinder.image=pathfinder:ci
        working-directory: app
  secrets:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: {fetch-depth: 0}
      - uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  compose-smoke:
    if: github.ref == 'refs/heads/main'
    needs: [integration, contract]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Bring up the stack from source
        working-directory: deploy
        run: |
          ./scripts/init-env.sh
          docker compose -f docker-compose.yml -f docker-compose.build.yml --env-file .env up -d --build
      - name: Smoke test
        run: ./deploy/scripts/smoke.sh http://localhost:8080
      - if: failure()
        run: docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs
```

`deploy/scripts/smoke.sh` polls `/actuator/health/readiness` until ready (with a timeout), registers the first user, creates a campaign, and exits non-zero on any failure.

### 10.2 `.github/workflows/release.yml` (on tags `v*`)

Steps: checkout; set up QEMU and buildx; log in to GHCR with the workflow token; derive the version from the tag; build and push `rpg-app` and `rpg-pathfinder` for `linux/amd64,linux/arm64` tagged with the version; create a GitHub Release attaching `deploy/docker-compose.yml`, `deploy/.env.example` and `deploy/scripts/init-env.sh`. Permissions: `contents: write`, `packages: write`. Only tag commits whose CI run is green.

### 10.3 Dependabot, branches, checks

`.github/dependabot.yml`: weekly updates for `maven` (`/app`), `docker` (`/app` and `/pathfinder`) and `github-actions` (`/`).

Branch protection on `main`: no direct pushes, pull request required with one review, linear history (squash merge), required status checks `unit`, `integration`, `pathfinder`, `contract`, `secrets`. Branch names `feat/...`, `fix/...`, `chore/...`. Unfinished work merges behind a feature flag (for example `features.player-live-view=false`).

## 11. Testing rules

| Layer | Tool and rule |
| --- | --- |
| Engine rules | Plain JUnit 5, seeded `RandomSource`, no Spring. Every rule in the ruleset spec has at least one worked example (inputs, seeded dice, expected outcome). Assert named outcomes, not raw log snapshots. |
| Dice | Property tests (range, keep and drop, limits, rejection of malformed input) plus a seeded uniformity check. |
| Architecture | ArchUnit rules from 5.2. |
| Persistence | `@DataJpaTest` with Testcontainers PostgreSQL, including constraint violations and the encounter fetch graph. Never H2. |
| Web and security | MockMvc: unauthenticated access, cross-campaign access denied, CSRF, duplicate `actionId`, stale version, audience filtering, upload rejection. |
| Reactions and resume | Pending reactions, readied actions and zones survive a simulated restart; pause, restart and resume reproduce identical state and legal actions. The reaction window suite T-22 to T-39 (Architecture 12.1) is part of done for the reaction window. |
| Pathfinder | Contract tests against both implementations; C++ unit tests under sanitizers. |
| Smoke | CI compose job from 10.1. |

Every bug fix starts with a failing regression test. Do not test getters or framework plumbing. Naming: `*Test` (unit), `*IT` (integration, run by the `integration` profile), `*ContractTest` (the `contract` profile).

## 12. Build order

**Step 0, nothing to ask the human before coding.** The values are settled. Java 25 (Temurin 25.0.4.1), Spring Boot 4.1.1 and PostgreSQL 16.15 are fixed by Decision Log DEC-084. The GitHub owner for GHCR is `samarthbadola`, with images at version 0.1.0. The repository name, group id and root package are already in the repository's `pom.xml` and its architecture decision record; use them as they are and do not rename them. The Creative Commons attribution statement of the Content Schema Specification (CS-SRD-012) is verified against the SRD 5.1 Creative Commons PDF (Ruleset Specification, section 1; Decision Log OQ-07); copy its first paragraph, which is the official text, word for word into `NOTICE`.

Frontend in every step. Every step that has a screen is built as routes, fragment endpoints, view models and demo elements (section 5.7). The real screen is written separately by Claude from the Figma designs. So a done-when that names a screen is met by the demo element: the route works, authorization and validation hold, and the fragment carries the fields and ids that the UX and API Specification names.

**Step 1, walking skeleton.** Maven project with wrapper; Spring Boot app with health endpoints; PostgreSQL via Compose; Flyway baseline; register and login (the first user is the instance admin); Dockerfile; compose file; CI with the required checks; README quick start. *Done when:* a fresh clone runs `init-env.sh` then `docker compose up`, the first account registers at `http://localhost:8080`, and CI is green on the pull request.

**Step 2, dice engine.** Grammar, limits, `RandomSource`, `RollResult`. *Done when:* parser, property and uniformity tests pass, over-limit or malformed input is rejected, and the Ruleset Specification section 3.8 examples (term lists, bonus and penalty dice, natural d20, reroll once, d20 floor) pass. The notation, limits, error codes, composition API and test contract are in Ruleset Specification section 3.10; D-1 to D-16 are decided (DEC-085, DEC-086, DEC-097, DEC-098 and DEC-109), so implement them as written. D-15 marks dice added by critical doubling; D-16 marks only a flagged keep/drop term that D-14 REJECT leaves undoubled. Step 2 emits no event or log; Step 7 consumes `criticalNotApplied` for the dice-event marker and warning.

**Step 2 additions (decided, DEC-095).** Build these with the dice engine; Step 2 is not done until they exist. They run on every push unless noted.

- **Seeded random source:** `SeededRandom` uses an explicitly specified algorithm written in the project (for example SplitMix64), not a JDK class whose sequence could change, with golden-vector tests for several seeds and bounds.
- **Test vectors as data:** the cases of Ruleset Specification 3.10 as JSON in the engine's test resources, run by parameterized tests; one test asserts that every `DiceErrorCode` is produced by at least one case.
- **Differential test:** enumerate every outcome of small dice groups (up to 3d4, with each modifier) through `ForcedDice` and compare with a naive reference implementation inside the test (keep, drop, reroll, floor, ties).
- **Parser robustness:** property and fuzz tests asserting that only `DiceParseException` is thrown, within a time bound, and that every valid expression round-trips through its canonical form; a short run on every push and a longer run nightly.
- **Locale and platform independence:** parse with ASCII-only checks and `Locale.ROOT`; run the engine tests a second time under the Turkish locale and an unusual time zone (the CI job below).
- **Forbidden APIs:** engine code and game-logic application code cannot call `Math.random`, `java.util.Random`, `ThreadLocalRandom`, `UUID.randomUUID`, `System.currentTimeMillis`, `Instant.now`, `LocalDateTime.now`, `LocalDate.now`, `new Date()`, or `toLowerCase` and `toUpperCase` without a locale. Use the forbiddenapis Maven plugin for the `engine` module and ArchUnit rules for the game-logic packages; the web layer may still generate action ids.
- **Distribution and bias:** exact distribution tests (for example advantage on a d20 against the enumerated probabilities) and bias tests of `nextInt` at awkward bounds (powers of two plus one, 1000).
- **Immutability and concurrency:** returned collections cannot be changed; one plan evaluated from several threads with separate sources gives the sequential results.
- **Test hygiene:** random test order, no `Thread.sleep`, no `@Disabled` without an issue link.
- **Spec drift:** a test fails if the limit constants or the error code names differ from Ruleset Specification 3.10.
- **Compiler strictness:** `-Xlint:all -Werror` for the `engine` module.
- **Nightly:** PIT mutation testing of the dice package (minimum score 80%), the longer fuzz run, a non-blocking build on the newest JDK, and a non-blocking timing guard at the dice limits.

Starting points for the CI jobs (adapt the names and paths):

```yaml
  engine-locale:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25', cache: maven }
      - name: Engine tests under the Turkish locale and an unusual time zone
        run: ./mvnw -q -pl engine test -DargLine="-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati"

  migration-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - name: Lint changed migrations with squawk
        run: |
          npm install --global squawk-cli
          git diff --name-only origin/main...HEAD -- 'app/src/main/resources/db/migration/*.sql' | xargs -r squawk

  mutation:   # in the nightly workflow
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25', cache: maven }
      - run: ./mvnw -q -pl engine org.pitest:pitest-maven:mutationCoverage -DtargetClasses='com.yourteam.rpg.engine.dice.*' -Dthreshold=80
```

Signature file for the forbidden-API check of the `engine` module (the plugin's built-in unsafe-JDK signatures already forbid locale-less case conversion):

```
java.lang.Math#random() @ Use RandomSource
java.util.Random @ Use RandomSource
java.util.concurrent.ThreadLocalRandom @ Use RandomSource
java.util.UUID#randomUUID() @ Use an injected id source
java.lang.System#currentTimeMillis() @ Use the injected clock
java.time.Instant#now() @ Use the injected clock
java.time.LocalDateTime#now() @ Use the injected clock
java.time.LocalDate#now() @ Use the injected clock
java.util.Date#<init>() @ Use the injected clock
```

**Tests and checks for later steps (decided, DEC-093).** Build them with the step that creates the thing they test. The full list is Architecture section 12.1 (T-14 to T-21): clock and time-of-day tests and the rest conformance tests (T-14, T-15) with the rest step; the rest transaction, save/resume, determinism replay and audience tests on a real database (T-16 to T-18); the migration upgrade test, JPA schema validation and migration lint (T-19) from the first migration in Step 4; the settings tests (T-21) when the settings registry lands; and a short rest and a long rest in the compose smoke test (T-20). The required status checks gain `architecture-tests`, `migration-lint` and the contract corpus job. The spell tests T-40 to T-47 (defined in Ruleset Specification 10.13, listed in Architecture 12.1) are built with the spells step, which is done only when they pass. The equipment tests T-48 to T-61 (defined in Ruleset Specification 11.11, listed in Architecture 12.1) are built with the equipment step, which is done only when they pass; T-61 is also run at the player endpoint on a real database, where the response body must not contain the hidden properties. The monster tests T-62 to T-79 (defined in Ruleset Specification 12.14, listed in Architecture 12.1) are built with the monster step, which is done only when they pass; T-62 runs in the content build, and T-69 includes a restart test on a real database. The environment tests T-80 to T-100 (defined in Ruleset Specification 13.14, listed in Architecture 12.1) are built with the environment steps, each step done only when its tests pass; T-83 compares the geometry with a slow reference, T-98 also runs at the player endpoint on a real database, where the body must not contain the hidden facts, T-99 is a benchmark job that reports a missed budget, and T-100 checks the imports of the six pure packages.

**Step 3, kernel and architecture tests.** `Ability`, `AbilityScores`, `Stat` and `Modifier` computation with the order and rounding rules, durations, ArchUnit rules active, and the Ruleset Specification Part A rules (RS-ABL, RS-PRF, RS-D20, RS-ADV, RS-CHK, RS-SAV, RS-LVL, RS-MOD, RS-TIM) with their worked examples as tests. *Done when:* tests prove same-key potency-before-recency selection; cross-key SET recency and resumption (10 then 14 gives 14, ending 14 restores 10, ADD +2 gives 16/12, and a later SET 1 beats SET 14); SET then MAX; exact ratio multiplication and one floor (`5 x 1/2 x 3 = 7`, max HP `31 x 1/2 = 15`, proficiency `3 x 1/2 = 1`, `3 x 2 = 6`, negative `-3 x 1/2 = -2`, zero remains zero); non-integer integer-stat operands are rejected; and a deliberate violation of each ArchUnit rule fails the build.

**Step 4, campaign, session and access.** Migrations for campaigns, members, invites, sessions, scenes, game clock and audience columns; the invite flow; per-campaign authorization; the settings registry with defaults and the campaign\_setting table (Architecture 4.15), with the House rules panel data and the settings event log. Settings are not a JSON column on the campaign. The spell settings of Ruleset Specification 10.11 join the registry when the spells step lands, and the equipment settings of Ruleset Specification 11.9 join it when the equipment step lands, and the two monster settings of Ruleset Specification 12.12 join it when the monster step lands, and the four environment settings of Ruleset Specification 13.12.1 join it when the environment step lands. *Done when:* a second user can join only with a valid invite, cannot read another campaign (404), and a paused session blocks actions with `SESSION_NOT_ACTIVE`.

**Step 5, content pack skeleton.** Implement the Content Schema Specification: the `content_pack`, `campaign_pack`, `content_pack_dependency` and `content_definition` tables with the guards of CS-STO-003 to CS-STO-005; the pack loader and its nine validation stages (CS-VAL-001 to CS-VAL-011), using networknt json-schema-validator 3.x on Jackson 3 (confirm the exact release when the step starts); the registries in Java, with `vocabulary.json` generated and checked in (CS-VAL-008); the conformance tests T-101 to T-116 that apply at this step; immutable in-memory loading as one content set per campaign (CS-STO-008, CS-STO-009); the attribution page; and a shipped SRD pack at version `0.1.0` with a manifest, a NOTICE file and exactly one real SRD definition. Use Prone unless it requires a registry name the engine does not yet have, in which case use the simplest definition without references. Preserve the SRD text verbatim and verify it against the Creative Commons source. Seed the pack at startup through the same loader and validation stages as all other packs. On later starts compare the shipped and stored hashes for that version and refuse startup if they differ. Tests verify startup loads exactly one definition and the loader refuses zero definitions. The first full SRD build (CS-SRD-008) is a later task, uses the Creative Commons edition as its source and ships under a higher version.

**Steps 6 to 20, the rest of the build.** Every specification the steps need now exists: Ruleset Specification Parts A to H, the Grid and Pathfinder Specification, the Content Schema Specification and the UX and API Specification (companion versions above). Do the steps in this order. The weeks in Architecture section 18 are a guide to pace; the order here is binding, and a step is finished only when its done-when is shown by running it. For every step: build only engine decisions marked decided in the Ruleset Specification; the routes, errors and screens come from the UX and API Specification; each screen named below is built as a demo element (section 5.7) and Claude writes the real one from Figma; and a screen marked B or V2 in the UX and API Specification is not built before its step here.

**Step 6, character creation and the derived sheet (Fighter).** Ruleset Specification Part D, sections 9.1 to 9.3 (Fighter, levels 1 to 5), with Requirements Specification 3.4 and 3.5 (FR-CHR and the classes). The character model is multiclass-ready from the start. Derived values (armor class, hit points, proficiency, saves, skills, attack and damage lines) come from the kernel, never from stored copies. Routes for characters, level-up and the party, with the access rules of FR-CHR-010 to FR-CHR-016. Screens: SCR-PLY-002, SCR-PLY-003, SCR-PLY-004 (six steps), SCR-PLY-005 (four steps, with the lost-points warning) and SCR-DM-007. Tests: the Part D worked examples and the Part A tests that the sheet reuses. *Done when:* a player creates a Fighter, levels it from 1 to 5, and the derived sheet equals the Part D worked examples to the point; a player cannot read or edit another player's character (404), and the DM can edit any character in the campaign.

**Step 7, combat core.** Ruleset Specification Part B (section 7, all of 7.1 to 7.11) and Architecture sections 4 and 5 for encounters, the event log with audience, idempotency keys and optimistic versioning. Initiative, turn order, the action economy, attacks, critical hits, damage with resistance, death saves, conditions, cover chosen by the DM, and the error contract. DM overrides follow UX-059 to UX-061 and API-ENC-015 of the UX and API Specification: the server sets who sees a correction, and a quiet correction is refused when a player can see the value. the requirements FR-ENC, FR-ACT and FR-AUD (Requirements Specification 3.7, 3.8, 3.13 and 12.2). Screens: SCR-DM-008, SCR-DM-009 (monsters come from a test pack until Step 12), SCR-DM-010 and SCR-DM-012. *Done when:* a scripted fight between two Fighters and a test creature runs from setup to a death, every Part B worked example passes, a repeated request with the same idempotency key changes nothing, a stale version is rejected with the documented error, and a DM-only event never appears in a player's response.

**Step 8, class features and the reaction window.** Ruleset Specification Part D section 9.3 (Rogue, Paladin, Bard, and the level 3 subclass features) and section 9.4, with the reaction window of Ruleset Specification 7.2 and Architecture section 5.4: one window, the trigger points named there, nesting to depth 6, resume by replay, cancel keeps the dice. Features are data with trigger points; do not hard-code a class in the engine. Opportunity attacks use the window. Screen: SCR-DM-011 (the player side, SCR-PLY-007, waits for Step 16). Tests: T-22 to T-39 and the PIT mutation job on the reaction package (Architecture 12.1), each also run as a restart-in-the-middle test on a real database. *Done when:* an opportunity attack, a class-feature reaction and a Divine Smite timing case from Ruleset Specification 9.4 each run through the window, a restart in the middle of a window resumes to the same result, and T-22 to T-39 pass.

**Step 9, rests, pause and resume, the clock and scenes.** Ruleset Specification Part C (section 8) and Requirements Specification 3.9 (FR-RST) and the session, clock and scene requirements FR-SES, FR-CLK and FR-SCN (section 12.2). Short and long rests with hit dice and recharge, session pause and resume with the basic recap, the game clock, and scenes with ambient light as a value only (what light does comes in Step 13). Screens: SCR-DM-003, SCR-DM-013 and SCR-DM-014 (SCR-PLY-008 waits for Step 16). Tests: T-14 to T-18, T-20 and T-21 as listed in the tests paragraph above, and the Part C edge cases E-32 to E-45. *Done when:* a short rest and a long rest in the compose smoke test restore exactly what Part C says, a paused game resumes mid-encounter to the same state after a restart, and the event log records each rest.

**Step 10, spells.** Ruleset Specification Part E (section 10) and Requirements Specification 3.6: slots, preparation, concentration, areas as shapes (drawn on the grid in Step 14), the curated tier-2 list, Wizard preparation and Bard casting. The spell settings of 10.11 join the settings registry now. Screen: SCR-DM-016 (spells tab). Tests: T-40 to T-47. *Done when:* T-40 to T-47 pass and a Wizard casts a concentration spell, loses it on a failed save, and the slot is spent exactly as 10.13 states.

**Step 11, equipment and items.** Ruleset Specification Part F (section 11) and Requirements Specification 3.15: armor, weapons and weapon properties, armor class, the hidden-property rule, and the equipment settings of 11.9. Screens: the equipment step of SCR-PLY-004, the inventory on SCR-PLY-003 and the items tab of SCR-DM-016. Tests: T-48 to T-61; T-61 also runs at the player endpoint on a real database, where the body must not contain the hidden properties. *Done when:* T-48 to T-61 pass and a player's response for an unidentified item carries no hidden property.

**Step 12, monsters and encounters.** Ruleset Specification Part G (section 12) and Requirements Specification 3.16: the SRD roster as decided in 12.10, stat blocks, challenge rating and experience, level bands for encounter building, legendary actions at the turn-end window, lair actions, and the two monster settings of 12.12. Monster image upload for stat blocks (FR-AST-001, FR-AST-003; the placement images of FR-AST-004 come in Step 18). Screens: SCR-DM-009 with the roster, SCR-DM-016 (monsters tab) and the monster pick in SCR-DM-012. Tests: T-62 to T-79; T-62 runs in the content build, and T-69 includes a restart test on a real database. *Done when:* T-62 to T-79 pass, the full SRD 5.1 monster roster loads from the pack, and an encounter built from the roster runs through Step 7's flow including a legendary action.

**Step 13, the environment of version 1.** Ruleset Specification Part H (section 13) with Requirements Specification 3.17 and Architecture 4.18, in the version 1 phase only: scene light and the DM's overrides, senses as data, cover chosen by the DM, the fall calculator, hazards and the seed traps, breath, poisons, and the madness and disease tracker, with the four environment settings of 13.12.1. Everything that needs the map waits for Step 14. Screens: the light and hazard parts of SCR-DM-014 and SCR-DM-010. Tests: the tests of 13.14 that need no grid, and T-100 (the imports of the six pure packages). *Done when:* those tests pass, a DM sets a scene to dim light and the check helper shows the right result, and a hazard and an affliction survive a restart.

**Step 14, grid, movement and the Java pathfinder.** The Grid and Pathfinder Specification and Ruleset Specification section 7.3 (movement) with the rest of Part H that needs the map: emitters and zones, sight, line of sight and cover, reach checked on every step, the fall event, and hazards triggered by movement. The grid in feet with the metric setting, movement with `JavaPathfinder`, reach highlighting, area shapes for spells. Requirements Specification 3.10 (FR-GRD, the version 1 items). Screens: the map area of SCR-DM-010. Tests: T-80 to T-100 not yet run (T-83 compares the geometry with a slow reference; T-99 is a benchmark job that reports a missed budget). *Done when:* those tests pass, a creature moves along a path that avoids walls and costs the right movement, and an opportunity attack fires when it leaves reach (through the Step 8 window).

**Step 15, the C++ pathfinder, observability and Milestone A.** The `pathfinder` service from the Grid and Pathfinder Specification with limits, the client adapter, the circuit breaker, the Java fallback and the contract tests run against both implementations; C++ unit tests under sanitizers. A second person can build the C++ service in parallel from the specification once Step 14 has fixed the contract. Observability and the install and operate requirements (Requirements Specification 3.14, FR-OPS), the screens SCR-ADM-001 and SCR-ADM-002, the README quick start with LAN setup, the attribution page and the release tag with the compose bundle. *Done when:* the contract tests pass against both implementations, stopping the `pathfinder` container makes movement fall back with no error to the player, the compose smoke test passes from a clean machine, and every release A item in the release table of the UX and API Specification has a route that works. This is **Milestone A (version 1)**.

**Step 16, the live player view.** Release B. Server-sent events behind the feature flag, with audience filtering, the DM and player view models, and the player side of the table: SCR-PLY-006 (without the full-screen mode until Step 18), SCR-PLY-007 (my reaction), SCR-PLY-008 (my rest) and SCR-PLY-009 (rules, read only), with SCR-DM-017 (the setting decision flow). Requirements Specification 3.12 (FR-LIV-001 to FR-LIV-003). Tests: the release B tests of UX and API Specification section 7.2 that belong to these routes (T-132, T-133 for the player side, T-136, T-151 and T-155), and the benchmark of Architecture section 18 week 7. *Done when:* a second browser session sees only what its audience allows, a dropped connection resumes without missing an event, and the player side of a reaction and a rest works end to end.

**Step 17, journal, checkpoints, export and import, pack upload.** Release B. SCR-DM-018 and SCR-PLY-010 (journal and quests, with the candidate events the DM keeps or dismisses, DEC-108), SCR-DM-019 (checkpoints, export and import, with the campaign's access data and the held characters shown on SCR-DM-004) and pack upload on SCR-DM-005. Requirements Specification FR-JRN, FR-CHK, FR-EXP and FR-CHR-017. A checkpoint restore is atomic and logged; an export carries its own format version; an import creates a new campaign owned by the importer. Tests: the release B tests of UX and API Specification section 7.2 for these routes (T-167, T-169 and the others listed there). *Done when:* an exported campaign imports into a fresh database and plays on, a restore returns the exact earlier state, and a malformed or oversized file is rejected with `CONTENT_INVALID` or `RATE_LIMITED`.

**Step 18, maps: upload, obstacle detection, placement, full-screen map.** Release B. Requirements Specification FR-GRD-010 to FR-GRD-015 and FR-AST-004, the Grid and Pathfinder Specification for the obstacle grid, and the rate limits decided for detection. Detection runs inside the request, with no job queue (D-09); keep it within the limits that the specification states and return `RATE_LIMITED` beyond them. Screens: SCR-DM-020 (upload, set the grid, review obstacles), SCR-DM-021 (place creatures by hand, at random, or at random inside a start area, with monster images) and the full-screen map mode of SCR-PLY-006. Tests: T-166 (for maps), T-171 to T-174 and the others of section 7.2 for these routes. *Done when:* a DM uploads a map image, sets the grid, edits the detected obstacles, places a start area and creatures at random inside it, and a player sees the full-screen map with only what the audience allows.

**Step 19, levels 6 to 12.** Release B, last. Ruleset Specification Part D tables for levels 6 to 12 as content, with their tests. Ask the human before starting; this step is cut first if time runs out.

**Step 20, release.** README, attribution, the release tag and the compose bundle for version 1.5, a final benchmark, and the report of what is done and left.

**Stop and ask.** Do not build player action submission (SCR-PLY-011, API-LIV-001), the vision phases and automatic environment effects of version 2, or anything the Ruleset Specification marks as proposed. If a step needs a decision that no document records, stop at that step, report it, and ask the human.

## 13. Definition of done, commits and pull requests

A task is done when:

- its done-when is demonstrated by running it, new behavior has tests that failed before the change, and the tests this brief lists for the step pass;
- all CI checks, including ArchUnit, pass;
- new endpoints have authentication, authorization at the data layer, input validation and audience handling, each with a negative test, and the route and error codes match the UX and API Specification;
- every route that returns a page or a fragment has a demo element in templates-demo/, and nothing in templates/ or static/ was changed by you;
- migrations are additive and run on a fresh database;
- no secret, TODO without an issue, or unformatted code is committed;
- you reported what is done, what is left, and anything beyond the brief.

Commits use Conventional Commits, for example `feat(engine): parse keep-highest dice modifiers`, with types `feat`, `fix`, `test`, `refactor`, `docs`, `ci`, `chore`. One task per commit where practical; squash merge at the end.

## 14. Open items to confirm with the human

- Nothing about monsters is open: the roster is decided (Ruleset Specification 12.10), as is the tier-2 spell list (Ruleset Specification 10.9).
- The SRD 5.1 lists and the attribution text are verified (Decision Log OQ-07, Ruleset Specification section 1); nothing to confirm.
- Versions are decided (DEC-084): Java 25, Spring Boot 4.1.1 and PostgreSQL 16.15; nothing to confirm.
- The journal captures the events of FR-JRN-002 as candidates that the DM keeps or dismisses (decided, DEC-108); nothing to confirm.
- Anything in this brief that conflicts with a more specific document. Engine decisions (the E-numbers) are recorded in the Ruleset Specification with their status; implement only the decided ones.
- First-build time (tracked, not part of any step): the first cold Docker build took about 198 seconds, of which Maven dependency resolution took about 146, and it will grow as the engine, the pathfinder service and the rulesets are added. Optimizing build and startup time is a later item in the Decision Log; do not trade correctness or the build steps for it.

## 15. Version and revision history

Current version: **1.29**. Companion document versions this brief was written against: Ruleset Specification 1.4, Content Schema Specification 1.2, Grid and Pathfinder Specification 0.4, UX and API Specification 1.5, Requirements Specification 1.20, Decision Log 1.35, Architecture 5.17.

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-10-11 | Created: working rules, project summary, tech stack, repository layout, architecture rules, kernel and engine contracts, data model, pathfinder, setup, CI/CD, testing, build order |
| 1.1 | 2026-10-12 | Dockerfile exec form fixed; rule 2 and the Step 3 criteria updated after Ruleset Specification Part A |
| 1.2 | 2026-10-14 | Part A audit applied: roll terms, two modifier types, stacking, advantage scope, ability score model (DEC-074), feats as content; Step 2 and Step 3 done-when criteria |
| 1.3 | 2026-10-14 | Ability score generation registry, game clock note, decided engine decisions |
| 1.4 | 2026-10-14 | Campaign settings and hit point rules paragraph |
| 1.5 | 2026-10-15 | Part B (combat and conditions) referenced in the stop-here note; more campaign settings |
| 1.6 | 2026-10-16 | Version numbering and revision history added; all Part B engine decisions closed |
| 1.7 | 2026-10-21 | Reconciliation: the Java baseline now matches the repository (Java 25, Spring Boot 4.1.1); the spec-status wording and build-order rule fixed (Parts A and B exist, each step names the parts it may use); Step 2 points to the dice notation in Ruleset Specification 3.10, with the result records, limits and error behavior updated |
| 1.8 | 2026-10-21 | Dice notation decisions D-1 to D-6 are decided, so Step 2 implements Ruleset Specification 3.10 as written; companion versions updated |
| 1.9 | 2026-10-30 | Dice engine refinements from the Step 2 review: DieRoll gains rerollFace; decisions D-1 to D-13 (Ruleset Specification 3.10 and 3.11) are decided. Corrections: the open items no longer list surprise handling or the Spring Boot version; companion versions updated |
| 1.9.1 | 2026-10-30 | Correction: companion versions updated (Ruleset Specification 0.10, which clears verification debt V-1; Decision Log 1.9); no change to the build steps or contracts |
| 1.10 | 2026-11-14 | New campaign setting for duplicate proficiency replacement (E-22); the stacking rule keeps suppressed instances and covers same-named features; companion versions updated |
| 1.10.1 | 2026-11-14 | Correction: companion versions updated (Ruleset Specification 0.12, Requirements 1.7, Decision Log 1.11); no change to the build steps or contracts |
| 1.11 | 2026-11-15 | Pathfinder contract moved to the Grid and Pathfinder Specification: cost map as digits, anchor-cell maps, determinism and identical-path contract tests, benchmark sizes corrected, C++ toolchain (C++20) and repository layout; the stop-here note now says the specification exists |
| 1.11.1 | 2026-11-15 | Correction: companion versions updated (Grid and Pathfinder Specification 0.2, whose decisions are now decided; Decision Log 1.13); no change to the build steps or contracts |
| 1.11.2 | 2026-11-16 | Correction: companion versions updated (Ruleset Specification 0.13 with the Part C draft, Decision Log 1.14) and the stop-here note says Part C exists as a draft; no change to the build steps or contracts |
| 1.11.3 | 2026-11-16 | Correction: companion versions updated (Ruleset Specification 0.14, Requirements 1.8, Decision Log 1.15, Architecture 5.3); Part C is no longer marked draft; no change to the build steps or contracts |
| 1.11.4 | 2026-11-16 | Correction: companion versions updated (Ruleset Specification 0.15, Decision Log 1.16); no change to the build steps or contracts. The proposed Step 2 test and CI additions (DEC-095) and the proposed dice decision D-14 are not part of the brief until accepted |
| 1.12 | 2026-11-16 | Step 2: dice decision D-14 (critical dice mode) is decided and D-15 is proposed; Step 4: the settings registry and the campaign\_setting table replace a settings column (Architecture 4.15); companion versions updated |
| 1.13 | 2026-11-16 | Step 2 additions (tests and CI checks, DEC-095) and the later-step tests (DEC-093) added with CI job starting points and the forbidden-API list; DieRoll gains the critical flag (D-15, decided); companion versions updated |
| 1.13.1 | 2026-11-16 | Correction: companion versions updated (Ruleset Specification 0.18 with the Part D draft, Decision Log 1.19); no change to the build steps or contracts. Part D decisions E-46 to E-60 are proposed and not part of the brief until accepted |
| 1.13.2 | 2026-11-16 | Correction: companion versions updated (Ruleset Specification 0.19, Decision Log 1.20); no change to the build steps or contracts |
| 1.14 | 2026-11-16 | The reaction window is one generalized mechanism (ADR-31): new paragraph in the combat description, pending\_reaction table instead of an encounter column, two new error codes, CheckAction, SaveAction and CancelReaction, replay-based resume, and the T-22 to T-36 suite as part of done; companion versions updated |
| 1.15 | 2026-11-16 | Reaction window rectifications: canonical point names, ordered steps, follow-up choices, frozen state and pins, settings during a chain, the order of saves and checks; the suite is now T-22 to T-39; companion versions updated |
| 1.16 | 2026-11-18 | Spells (Ruleset Specification Part E, section 10) added to the build order by reference, with tests T-40 to T-47 part of done for the spells step; rule 2 and the stop-here note no longer list which Ruleset parts exist (its roadmap does) and the open items are brought up to date; first-build time recorded as a tracked item; the cross-reference to Architecture no longer carries a version number; companion versions updated (Ruleset Specification 0.23, Requirements 1.13, Decision Log 1.24, Architecture 5.8) |
| 1.17 | 2026-11-19 | Equipment and items (Ruleset Specification Part F, section 11) added to the build order by reference, after spells, with tests T-48 to T-61 part of done for the equipment step (T-61 also at the player endpoint); the engine package item/ added; the equipment settings join the registry with that step; schema constraints for inventory added after the constraint block; companion versions updated (Ruleset Specification 0.24, Requirements 1.14, Decision Log 1.25, Architecture 5.9) |
| 1.18 | 2026-11-19 | Monsters and encounters (Ruleset Specification Part G, section 12) added to the build order by reference, after equipment, with tests T-62 to T-79 part of done for the monster step; the engine package monster/ added; the two monster settings join the registry with that step; the reaction window list gains ON\_TURN\_END and the Legendary Resistance window at AFTER\_SAVE\_FAIL; schema additions for monsters added after the inventory constraint block; the monster roster question removed from the open items; companion versions updated (Ruleset Specification 0.25, Requirements 1.15, Decision Log 1.26, Architecture 5.10) |
| 1.19 | 2026-11-19 | Ruleset Specification Part H applied by reference: the environment build steps (the version 1 part now, the parts that need the map after the grid UI and the Java pathfinder), the pure-engine boundary for the six environment packages, the tests T-80 to T-100, a pointer to the schema sketch of Ruleset Specification 13.9, the ON\_FALL note, the environment settings in Step 4, the package tree and the release scope; companion versions updated (Ruleset Specification 1.0, Requirements Specification 1.16, Decision Log 1.27, Architecture 5.11) |
| 1.20 | 2026-11-21 | Content Schema Specification, accepted by the owner on 2026-11-21, applied by reference: Step 5 now implements it (tables, loader with nine validation stages, JSON Schema library, registries with the generated vocabulary file, conformance tests), the stop-here note no longer lists it as missing, the repository layout names the schema folder and describes NOTICE as the Creative Commons BY 4.0 statement instead of text copied from the SRD, Step 0 asks for that statement, and the first full SRD build uses the Creative Commons edition as its source. The trigger points and effect type names of the combat description now follow the registry. Companion versions updated (Ruleset Specification 1.1, Content Schema Specification 1.0, Requirements Specification 1.17, Decision Log 1.28, Architecture 5.12). |
| 1.21 | 2026-11-21 | UX and API Specification, accepted by the owner on 2026-11-21, applied by reference: it joins the companion documents and the stop-here note no longer lists it as missing; the build order marks monster image upload for version 1 and map upload, placement, export and import for version 1.5; the error table follows Architecture section 9 (the codes of the web layer added, and the statuses of REACTION\_OPTION\_INVALID and REACTION\_NOT\_PERMITTED corrected); the security rules gain stored sessions, form tokens, rate limits, the image limits of 5 MB and 20 MB and the access data of an export; the data model gains maps, placement, held characters, rolled sets and form tokens; the action id is minted by the server; the C++ convention reads C++20, as in the Grid and Pathfinder Specification; the open items no longer list the corner-cutting default and the upload limits. Companion versions updated (Ruleset Specification 1.1, Content Schema Specification 1.1, Grid and Pathfinder Specification 0.3, UX and API Specification 1.0, Requirements Specification 1.18, Decision Log 1.29, Architecture 5.13). |
| 1.22 | 2026-11-21 | Companion versions updated (UX and API Specification 1.1, Architecture 5.14) after the detection limits were raised: the numbers stay in the UX and API Specification (3.8), which this brief points to. No other change. |
| 1.23 | 2026-11-22 | The frontend is split (Decision Log DEC-108): the coding AI builds the backend and only demo elements of the frontend, and Claude writes the real frontend from the Figma designs in the same repository. New section 5.7 sets the two areas and the seam, with rule 12 of section 1, the repository layout, a build order note and the definition of done following it. The open items lose the SRD verification (done, OQ-07) and the journal events (decided, DEC-108), and the override item now points to the proposal in the UX and API Specification. Step 0 notes that the attribution text is verified. Companion versions updated (Ruleset Specification 1.2, UX and API Specification 1.2, Grid and Pathfinder Specification 0.4, Decision Log 1.30). |
| 1.24 | 2026-11-22 | Companion version updated (UX and API Specification 1.3, which draws the Figma frames for the proposed override correction and fixes one screen reference). No build rule changed. |
| 1.25 | 2026-11-22 | Steps 6 to 20 written (character creation and the derived sheet, combat core, class features and the reaction window, rests and sessions, spells, equipment, monsters, the version 1 environment, grid and Java pathfinder, the C++ pathfinder and Milestone A, then the version 1.5 steps: live player view, journal, checkpoints and export, maps, levels 6 to 12, release). Each names its specification sections, screens (built as demo elements), tests and a done-when. The old stop-here note is replaced by a stop-and-ask rule for version 2 and for anything not decided. No contract or earlier step changed. Companion versions unchanged. |
| 1.26 | 2026-11-22 | Document review fixes. The stack table now names C++20, as the Architecture and the Grid and Pathfinder Specification do. Step 0 no longer asks the human for values that are settled (Java, Spring Boot and PostgreSQL versions, GHCR owner, attribution text, and the repository name, group id and root package that the repository already records), and the group id placeholder note points to the repository instead. The 1.25 history row, which had been left blank, is filled. Companion versions updated (UX and API Specification 1.4, Requirements Specification 1.19, Decision Log 1.31, Architecture 5.15). No build rule or contract changed. |
| 1.27 | 2026-11-22 | The owner accepted how DM overrides are shown to players (UXD-11, UXQ-24, UX-059 to UX-061). The open item that told the coding AI to wait is removed, and Step 7 now says the server sets who sees a correction and refuses a quiet correction when a player can see the value. Companion versions updated (UX and API Specification 1.5, Requirements Specification 1.20, Decision Log 1.32, Architecture 5.16). No other build rule changed. |
| 1.28 | 2026-11-22 | Companion Decision Log and Progress Tracker updated to 1.33 for the accepted UXD-11 citation correction. D-16 is recorded in the Step 2 result model and done-when guidance; the companion Ruleset and Decision Log versions are now 1.3 and 1.34. Step 2 is complete; implementation proceeds to Step 3. |
| 1.29 | 2026-11-22 | Step 3 decisions E-154 and E-155 are applied: SET precedence and suppression, exact Ratio arithmetic, pack ratio limits, and all 13 duration kinds and end conditions. Companion versions updated (Ruleset Specification 1.4, Content Schema Specification 1.2, Decision Log 1.35, Architecture 5.17). |
| 1.30 | 2026-11-22 | Step 5 ships one verbatim SRD seed definition at version 0.1.0 and loads it through the ordinary loader. The tests cover the at-least-one-definition rule and startup hash verification. The full SRD build uses a higher version. |
