# Tabletop RPG Simulator: AI Build Brief (v1.6)

Oct 15, 2026 · @Samarth

Read this first. It tells an AI coding assistant what the project is, what is already decided, and how to start building without guessing. Where a more specific spec disagrees with this brief, the spec wins; where something is marked TODO, ask the human.

## 1. Rules for working on this project

1. Read this brief end to end before writing code. Companion documents, if provided: Requirements Specification, `Architecture (v5.0)`, Decision Log. If they disagree with this brief, tell the human.
2. The ruleset specification is the source of truth for D&D mechanics and is **only partly written: Part A (foundations) exists, Parts B to H do not**. Do not implement game rules from memory. Until the relevant part exists, build only what Part A covers (dice engine and kernel types, with the Part A worked examples as tests) plus skeleton, schema, auth and CI.
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

## 2. The project in one page

**What.** A self-hosted, browser-based tool for tabletop RPG Dungeon Masters (D&D 5e SRD 5.1 rules): a character manager plus an encounter runner. A rules engine automates the mathematics (dice, initiative, action economy, conditions, resources, spell slots, movement) and campaign state persists in PostgreSQL. It is not a virtual tabletop (no game engine, voice or video).

**How it is used.** The DM runs the stack on their own machine with `docker compose up`. Players on the same local network open the DM's address in a browser. The first account becomes the instance admin (typically the DM); later accounts need a DM-issued invite. Roles (DM or player) are per campaign. Plain HTTP on the LAN.

**Context.** A two-person course project (Enterprise Web Application, Java), about two months, no course rubric. Quality bar: correct rules, clean layering, tests, a one-command install.

| Release | Scope |
| --- | --- |
| v1 (MVP, milestone A) | DM-driven play for Fighter, Rogue, Paladin, Bard, Wizard at levels 1 to 5; multiclass-ready schema; dice; combat with reactions; curated tier-2 spells; SRD monster subset with per-encounter rosters and artwork upload; square grid with distance-metric setting and movement; rests; sessions, scenes, game clock and audience in the schema with basic UI; LAN accounts with invites; single-step Docker |
| v1.5 (achievable) | Live player view (SSE) with audience filtering; journal and quests; checkpoints; campaign export and import; JSON pack import; custom monsters and homebrew spells; levels 6 to 12 |
| v2 (stretch) | Vision phases; players submitting actions; homebrew editor UI; Sorcerer and Warlock; multiclass rules and UI; hex geometry |

**Non-goals:** job queue (Redis, Kafka), gRPC, multi-instance deployment, Kubernetes, caching beyond static content, mobile app, 2024 rules, arbitrary non-5e game systems.

## 3. Tech stack

| Layer | Choice | Notes |
| --- | --- | --- |
| Language | Java 21 (LTS, Temurin) | The newest LTS is acceptable if the toolchain supports it; record the choice. |
| Build | Maven with wrapper | Profiles `integration` and `contract`. Unit tests `*Test`, integration `*IT`. |
| Framework | Spring Boot (newest stable) | Web MVC, Validation, Security, Data JPA, Actuator. |
| Views | Thymeleaf plus HTMX | Self-hosted `htmx.js`, no CDN, no SPA. Controllers return HTML fragments for interactive actions. |
| Database | PostgreSQL 16 | Hibernate, Flyway. `spring.jpa.open-in-view=false`. Never H2. |
| Resilience | Resilience4j | Circuit breaker and timeouts only. No retries, bulkheads or rate limiters on the pathfinder. |
| Observability | Micrometer, Actuator, JSON logs | logstash-logback-encoder. Trace ID in the MDC. |
| Testing | JUnit 5, AssertJ, Testcontainers (PostgreSQL), ArchUnit, MockMvc |  |
| Formatting | Spotless or Checkstyle | Enforced in CI. |
| Pathfinder | C++17, CMake, cpp-httplib, nlohmann/json | Vendor the single headers in `third_party/`. GoogleTest or Catch2. |
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
      templates/, static/                Thymeleaf, htmx.js, css
      content/srd-5.1/                   seed data for the SRD pack
    src/test/java/...
  pathfinder/               C++ service
    CMakeLists.txt, Dockerfile, src/, tests/, third_party/
  deploy/
    docker-compose.yml, docker-compose.build.yml, .env.example
    scripts/init-env.sh, scripts/smoke.sh
  docs/                     requirements, architecture, decision log export
  .github/workflows/        ci.yml, release.yml, dependabot.yml
  NOTICE                    SRD 5.1 attribution, copied verbatim from the SRD
  LICENSE, README.md
```

The group id and root package are a placeholder (`com.yourteam.rpg`). Ask the human for the real one before creating the project.

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
  grid/        GridGeometry, DistanceMetric, area shapes, cost maps
  vision/      VisionModel (FullVision by default)
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

1. Controller authenticates and validates the request (bean validation on a form DTO, never an entity). Every request carries `actionId` (client UUID) and `expectedVersion`.
2. Application service reads an encounter snapshot (short read-only transaction).
3. If the action needs a path, call the `Pathfinder` port now, with no transaction open. Build the per-mover cost map first (section 6).
4. Open the write transaction. Load the encounter. If its version differs from `expectedVersion`, fail with `ENCOUNTER_VERSION_CONFLICT`. If `actionId` already exists for the encounter, return the stored result.
5. Call `RuleEngine.resolve(...)`. The engine **re-validates** the path against the grid and remaining movement.
6. Save state and append events (with dice results and audience). Commit.
7. After commit (`@TransactionalEventListener(AFTER_COMMIT)`), notify SSE viewers.
8. Return an HTML fragment. A result of `AwaitingReaction` is persisted on the encounter and resolved by a later `ReactionDecision` request.

### 5.4 Error contract

Caller faults are 4xx, not logged at ERROR. System faults are 5xx, logged with the trace ID. One `@ControllerAdvice` maps them; HTMX requests receive an error fragment with the right status, other clients `application/problem+json`.

| Code | HTTP | Meaning |
| --- | --- | --- |
| `NOT_YOUR_TURN`, `NO_ACTIONS_LEFT`, `TARGET_OUT_OF_RANGE`, `INVALID_PATH` | 422 | rule violations |
| `REACTION_NOT_AVAILABLE`, `CONTENT_INVALID`, `PACK_NOT_ENABLED`, `ASSET_INVALID` | 422 | invalid request content |
| `ENCOUNTER_VERSION_CONFLICT`, `REACTION_PENDING`, `SESSION_NOT_ACTIVE` | 409 | stale or conflicting state |
| `VALIDATION_FAILED` | 400 | malformed input with field details |
| `INVITE_INVALID` | 403 | invite unknown, expired or used up |
| `NOT_FOUND` | 404 | also returned for resources in other campaigns |
| `SERVICE_UNAVAILABLE`, `INTERNAL_ERROR` | 503, 500 | system faults |

### 5.5 Security rules

- Every route requires authentication except register, login, static assets and health.
- Authorization lives in the application layer. Repository queries take the principal and filter by campaign membership. Foreign IDs return `NOT_FOUND`. Write negative tests for every per-ID route.
- Separate view models for DM and player. Player responses and SSE events are filtered by `audience` on the server; never send data and hide it in the template.
- CSRF enabled (HTMX sends the token in a header). Cookies `HttpOnly`, `SameSite=Lax`, `Secure` controlled by `APP_COOKIE_SECURE` (default false for LAN HTTP). Strict CSP without inline scripts. Login throttling.
- Invite codes are random, hashed at rest, expiring, limited-use and rate-limited.
- Uploaded images: check declared and sniffed type (PNG, JPEG, WebP), cap 5 MB and dimensions, decode and re-encode to strip metadata, name by content hash, store outside the web root, serve through an authenticated endpoint with `X-Content-Type-Options: nosniff`.
- Imported packs and homebrew are data only: validate against the schema, cap size, depth and counts, never fetch URLs inside them, never execute anything, escape on output.
- Exports and checkpoints are DM-only.

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

## 6. Kernel and engine contracts

These are the shapes to implement. Names may be refined, but the behavior is fixed.

```java
public enum Ability { STR, DEX, CON, INT, WIS, CHA }   // the only place abilities are enumerated

public final class AbilityScores {                      // the only accessor
    public int score(Ability a) { /* base plus improvements, then ability stat modifiers, clamped to 0..ceiling (default 30) */ }
    public int modifier(Ability a) { return Math.floorDiv(score(a) - 10, 2); }
}

public enum ModOp { SET, ADD, MULTIPLY, MIN, MAX, ADVANTAGE, DISADVANTAGE }

public record Modifier(String targetStat, ModOp op, int value,
                       Condition activeWhen,   // nullable; reads scene or actor state
                       DurationSpec duration, String source) {}
```

**Stat computation.** A stat is computed, never stored: base value, then `SET`, then `ADD`, then `MULTIPLY` (fractions round down, as in the SRD), then `MIN` and `MAX`. Advantage and disadvantage are collected separately: extra sources of the same kind never stack, and any advantage together with any disadvantage cancels to a normal roll. Skill, save and attack totals are formulas with a separate proficiency component, floor(proficiency bonus x multiplier); MULTIPLY targets that multiplier, never the total (Ruleset Specification, Part A). Maximum HP is derived from stored raw hit die results and the current Constitution modifier, then adjusted by modifiers; current HP is clamped to it. There are two modifier types: stat modifiers (persistent integers) and roll bonuses (applied when a roll is made; fixed or dice; may be consumed). Modifiers carry a stack key and a potency: the same source does not stack with itself (most potent, then most recent) and different sources add. Advantage and disadvantage sources carry a scope (roll kind, ability, conditions). See Ruleset Specification section 3.9. Kernel stats: AC, initiative, speed, max HP, attack bonus, save bonuses, skill bonuses, spell save DC, `sense:sight`, `sense:darkvision`, `sense:blindsight`, `sense:truesight`.

**Ability scores (DEC-074).** An ability score is a stat: the stored base plus recorded improvements, then the ability stat's modifiers, clamped to a ruleset ceiling (default 30) and a floor of 0. Each ability also has a maximum (default 20, raised by some effects together with the score) that caps level-up and feat improvements when they are chosen; any excess is lost. A score of 0 records an ability-at-zero event and the causing effect decides the consequence. The stored base stays between 1 and 30; extra points come from modifiers. Feats are content (DEC-073): a character can hold any number, each stored with its source and choices. An improvement that would pass the maximum shows a warning before it is applied, with the excess that would be lost. Ability score generation at creation uses a registry of built-in methods (standard array, point buy, roll 4d6 drop lowest, roll 3d6, manual entry) that the DM selects per campaign; DM-created methods are version 2.

**Campaign settings and HP rules from the Ruleset Specification.** Campaign settings so far: hit point method (player chooses per level between rolled and fixed average by default, or the DM forces one); advancement (milestone by default, XP as a later option, with the XP column and thresholds kept in the data); natural 20 and natural 1 on ability checks and saves (off by default; the DM can enable it for checks, saves or both); game clock advance per combat round (default 6 seconds, 0 for manual); ability score generation method; feats enabled; whether damage absorbed by temporary hit points at 0 HP still causes a death save failure (yes by default, with a house-rule option); optional mid-combat surprise (off by default). When a combatant joins a running encounter the DM chooses join options (initiative roll mode, timing, starting conditions, first-turn restriction). Maximum HP is a modifiable stat. When it rises, current HP rises by the same amount; when it falls below current HP, current HP becomes the new maximum; when a temporary increase ends, only the clamp applies; a creature at 0 HP is not raised (decided).

**Resources.** `Resource(key, current, max, recharge)` with recharge in `TURN, SHORT_REST, LONG_REST, DAWN, NEVER`. `SlotPool` holds slots by spell level; only the used counts are stored, totals derive from class levels.

**Durations.** `ROUNDS(n, start or end of a named turn)`, `UNTIL_END_OF_TURN`, `GAME_TIME(seconds)`, `UNTIL_REST(short or long)`, `PERMANENT`. A round is 6 seconds of game time. Game time advances only through the DM, a rest or travel, never with real time. In combat, each completed round advances the clock by a DM-set amount (default 6 seconds; decision E-9). The rules constant of 6 seconds per round (10 rounds per minute) does not change.

**Dice.**

```java
public interface RandomSource { int nextInt(int bound); }   // 0..bound-1, uniform; production: SecureRandom

public record DieRoll(int face, boolean kept) {}
public record RollTerm(String label, String source, List<DieRoll> dice, int fixed, int signedSubtotal) {}  // RollResult holds List<RollTerm>, total, naturalD20, mode

public final class DiceExpression {
    public static DiceExpression parse(String s);            // throws on malformed or over-limit input
    public RollResult roll(RandomSource rng);
}
```

Grammar: `NdX` plus optional keep or drop (`kh`, `kl`, `dh`, `dl` with a count), reroll and minimum-value modifiers, and `+` or `-` an integer. Examples: `2d20kh1` (advantage), `4d6dl1` (ability scores), `3d8+4`. Limits: X from 2 to 1000, N from 1 to 100, caps on total dice and expression length. Draw each die with the bounded `nextInt` (never `random() * X` or modulo). Record every face and whether it was kept. A roll is an ordered list of labeled, signed terms (dice groups or fixed numbers; bonus and penalty dice such as Bless and Bane are extra terms), and the grammar accepts sums like d8+3d6+3 and d20+3-d4. A reroll replaces a die once; a d20 floor (Reliable Talent) applies to each d20 before the kept die is chosen; each dice term carries a crit-doubling flag (a critical hit doubles dice, not modifiers); the natural d20 is recorded; the critical threshold is a rule parameter, not a hard-coded 20. See Ruleset Specification section 3.8.

**Actions and results.**

```java
public sealed interface Action
    permits AttackAction, CastSpellAction, MoveAction, EndTurnAction, ReactionDecision {}

public sealed interface ResolutionResult permits Completed, AwaitingReaction {}

public interface RuleEngine {   // pure: no I/O. MoveAction carries a path the engine re-validates.
    ResolutionResult resolve(Encounter encounter, Action action, RandomSource rng);
}
```

`resolve` throws `RuleViolation` (with a stable error code) for illegal actions. A miss or a failed save is a normal result. Every `CombatEvent` records dice results and an `Audience` (`DM_ONLY`, `ALL`, or a player set).

**Combat state machine.** Setup, RollingInitiative, TurnStart, AwaitingAction, Resolving, AwaitingReaction (persisted), TurnEnd, Finished. A pending reaction rejects other actions with `REACTION_PENDING`.

**Trigger points.** `ON_ATTACK_ROLL`, `ON_DAMAGE_ROLL`, `ON_DAMAGE_TAKEN`, `ON_SAVE`, `ON_CAST`, `ON_MOVE`, `ON_TURN_START`, `ON_TURN_END`, `ON_SHORT_REST`, `ON_LONG_REST`. A feature is data: trigger, conditions, an effect type with parameters, an optional resource cost, a usage limit. The engine ships a catalog of effect types in Java (`add_damage_dice`, `grant_advantage`, `grant_resistance`, `add_modifier`, `spend_resource`, `apply_condition`, `heal`, `grant_reaction`). Data may only combine known effect types; a genuinely new mechanic is a new effect type in code.

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

**Per-mover cost map** (built by the engine, not the pathfinder): difficult terrain costs double; another creature's space counts as difficult terrain; a hostile creature's space is blocked unless the size difference is at least two steps; non-hostile spaces can be passed through but not stopped in (`noStop`); walls are blocked.

**Vision seam.** `VisionModel.visibleCells(viewer, scene, map)` returns a `Visibility`. Only `FullVision` exists in v1. The data model already has sense stats, scene ambient light (`BRIGHT`, `DIM`, `DARK`) and per-cell `blocksSight`.

## 7. Data model

PostgreSQL, Flyway migrations (never edit an applied migration; prefer additive changes). Reserved words: use `app_user` and `player_character`. Store choices and current state; derive numbers (modifiers, AC, proficiency bonus, save DCs, attack bonuses, max HP, slot totals).

| Group | Tables |
| --- | --- |
| Identity | `app_user`, `campaign`, `campaign_member (role DM or PLAYER)`, `invite`, `campaign_pack` |
| World | `game_clock`, `game_session (ACTIVE, PAUSED, ENDED)`, `scene (map as arrays, ambient_light)`, `zone` |
| Content | `content_pack`, `class_def`, `subclass_def`, `class_level_def`, `feature_def`, `spell_def`, `item_def`, `monster_def`. Common columns: `id`, `pack_id`, `key`, `ruleset`, `name`, `definition` (JSONB), `license`, `attribution`; UNIQUE (`pack_id`, `key`) |
| Characters | `player_character`, `pc_ability` (one row per ability), `pc_class` (one row per class), `pc_hit_dice` (per die size), `pc_asi` (by class and class level), `pc_proficiency`, `pc_resource`, `pc_slot`, `pc_spell`, `inventory_item` |
| Encounters | `encounter` (round, turn index, `version`, pending reaction), `encounter_roster`, `combatant`, `active_effect`, `readied_action`, `combat_event` (`seq`, `action_id`, type, payload JSONB, `audience`) |
| Support | `asset` (metadata; files on the volume), `checkpoint`, `journal_entry` (v1.5) |

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
  ADD CONSTRAINT asset_size CHECK (size_bytes > 0 AND size_bytes <= 5242880);
```

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

**C++ conventions.** C++17, CMake, cpp-httplib and nlohmann/json vendored as single headers in `third_party/`, GoogleTest or Catch2. CI builds tests with `-fsanitize=address,undefined`. Prefer static linking of the C++ runtime in the final image.

**Java side.**

```java
public interface Pathfinder {
    PathResult findPath(CostMap map, Cell start, Cell goal, Settings settings);
    ReachResult reach(CostMap map, Cell start, int budget, Settings settings);
}
```

`JavaPathfinder` implements the same algorithm and is always available. `CppPathfinderClient` uses one shared `HttpClient` with keep-alive, connect timeout 100 ms, read timeout 250 ms, a Resilience4j circuit breaker, no retry, and falls back to `JavaPathfinder` on timeout, error or an open circuit. `pathfinder.mode` is `java`, `cpp` or `auto` (default `auto`). Forward the trace ID as `X-Request-Id`. Metrics: `pathfinder.call.duration`, `pathfinder.fallback`, breaker state. Readiness must not depend on the pathfinder (the fallback keeps the app healthy). Contract tests run the same cases against both implementations; they must agree on cost and validity. A benchmark (Java in-process versus C++ over HTTP at 30x20, 100x100 and 500x500 grids) runs nightly and its results go in the project report.

## 9. Environment and setup

### 9.1 Prerequisites

JDK 21, Git, Docker with Compose v2 and buildx. CMake and a C++17 compiler only if you work on the pathfinder.

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
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
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
        with: {distribution: temurin, java-version: '21', cache: maven}
      - run: ./mvnw -B -ntp spotless:check test
        working-directory: app
  integration:
    needs: unit
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {distribution: temurin, java-version: '21', cache: maven}
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
        with: {distribution: temurin, java-version: '21', cache: maven}
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
| Reactions and resume | Pending reactions, readied actions and zones survive a simulated restart; pause, restart and resume reproduce identical state and legal actions. |
| Pathfinder | Contract tests against both implementations; C++ unit tests under sanitizers. |
| Smoke | CI compose job from 10.1. |

Every bug fix starts with a failing regression test. Do not test getters or framework plumbing. Naming: `*Test` (unit), `*IT` (integration, run by the `integration` profile), `*ContractTest` (the `contract` profile).

## 12. Build order

**Step 0, from the human before coding:** repository name, group id and root package; GitHub owner for GHCR; chosen Java and Spring Boot versions; the SRD 5.1 attribution text (copied verbatim from the SRD) for `NOTICE`.

**Step 1, walking skeleton.** Maven project with wrapper; Spring Boot app with health endpoints; PostgreSQL via Compose; Flyway baseline; register and login (the first user is the instance admin); Dockerfile; compose file; CI with the required checks; README quick start. *Done when:* a fresh clone runs `init-env.sh` then `docker compose up`, the first account registers at `http://localhost:8080`, and CI is green on the pull request.

**Step 2, dice engine.** Grammar, limits, `RandomSource`, `RollResult`. *Done when:* parser, property and uniformity tests pass, over-limit or malformed input is rejected, and the Ruleset Specification section 3.8 examples (term lists, bonus and penalty dice, natural d20, reroll once, d20 floor) pass.

**Step 3, kernel and architecture tests.** `Ability`, `AbilityScores`, `Stat` and `Modifier` computation with the order and rounding rules, durations, ArchUnit rules active, and the Ruleset Specification Part A rules (RS-ABL, RS-PRF, RS-D20, RS-ADV, RS-CHK, RS-SAV, RS-LVL, RS-MOD, RS-TIM) with their worked examples as tests. *Done when:* tests prove the modifier order (`SET`, `ADD`, `MULTIPLY` with floor, `MIN`/`MAX`) and a deliberate violation of each ArchUnit rule fails the build.

**Step 4, campaign, session and access.** Migrations for campaigns, members, invites, sessions, scenes, game clock and audience columns; the invite flow; per-campaign authorization. *Done when:* a second user can join only with a valid invite, cannot read another campaign (404), and a paused session blocks actions with `SESSION_NOT_ACTIVE`.

**Step 5, content pack skeleton.** `content_pack` and definition tables, JSON schema validation, immutable in-memory loading, the attribution page, an empty SRD pack manifest. No game data yet.

**Stop here.** Steps 6 onward need more specifications. The Ruleset Specification now has Part A (foundations) and Part B (combat and conditions, section 7); Parts C to H, the content schema specification, the grid and geometry specification, and the UX and API specification with the threat model are still needed before the matching steps. Ask the human for them. After that the order is: character creation (Fighter) with the derived sheet; combat core; Rogue, Paladin and Bard features with trigger points; reaction window; rests, pause and resume; spells and concentration; grid UI, movement and the Java pathfinder; monster roster and artwork upload; the C++ pathfinder with fallback and contract tests; then the live player view.

## 13. Definition of done, commits and pull requests

A task is done when:

- its done-when is demonstrated by running it, and new behavior has tests that failed before the change;
- all CI checks, including ArchUnit, pass;
- new endpoints have authentication, authorization at the data layer, input validation and audience handling, each with a negative test;
- migrations are additive and run on a fresh database;
- no secret, TODO without an issue, or unformatted code is committed;
- you reported what is done, what is left, and anything beyond the brief.

Commits use Conventional Commits, for example `feat(engine): parse keep-highest dice modifiers`, with types `feat`, `fix`, `test`, `refactor`, `docs`, `ci`, `chore`. One task per commit where practical; squash merge at the end.

## 14. Open items to confirm with the human

- Which spells form the tier-2 set, and which SRD monsters go in each level band.
- Surprise handling, how DM overrides are shown to players, corner-cutting default, final upload limits.
- Exact SRD 5.1 lists (spells, monsters, conditions) and the attribution text, verified against the SRD document.
- Spring Boot version to record (Java 25 and PostgreSQL 16.15 were chosen in Step 1).
- Which events are captured automatically for the journal (v1.5).
- Anything in this brief that conflicts with a more specific document. Engine decisions E-1 to E-5 and E-8 to E-13 are decided (see the Ruleset Specification); E-6 and E-7 wait for the spellcasting and class parts.

## 15. Version and revision history

Current version: **1.6**. Companion document versions this brief was written against: Ruleset Specification 0.6, Requirements Specification 1.4, Decision Log 1.5, Architecture 5.0.

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-10-11 | Created: working rules, project summary, tech stack, repository layout, architecture rules, kernel and engine contracts, data model, pathfinder, setup, CI/CD, testing, build order |
| 1.1 | 2026-10-12 | Dockerfile exec form fixed; rule 2 and the Step 3 criteria updated after Ruleset Specification Part A |
| 1.2 | 2026-10-14 | Part A audit applied: roll terms, two modifier types, stacking, advantage scope, ability score model (DEC-074), feats as content; Step 2 and Step 3 done-when criteria |
| 1.3 | 2026-10-14 | Ability score generation registry, game clock note, decided engine decisions |
| 1.4 | 2026-10-14 | Campaign settings and hit point rules paragraph |
| 1.5 | 2026-10-15 | Part B (combat and conditions) referenced in the stop-here note; more campaign settings |
| 1.6 | 2026-10-16 | Version numbering and revision history added; all Part B engine decisions closed |