# Tabletop RPG Simulator: Requirements Specification (v1.4)

Enterprise Web Application (Java) course project. Draft 1, 11 October 2026. Ruleset: D&D System Reference Document 5.1 (2014 rules), CC-BY-4.0. Companion to `Architecture (v5.0)`.

## 1. Introduction

### 1.1 Purpose and scope

A self-hosted, browser-based tool for Dungeon Masters: a character manager plus an encounter runner built around a rules engine. It automates the mathematics of tabletop play (dice, initiative, action economy, conditions, resources, spell slots) and keeps campaign state in a database. It is **not** a virtual tabletop: there is no 2D game engine and no voice or video; monsters and characters carry uploaded images, and fog of war (vision) is a v2 feature.

**In scope:** accounts and campaigns with sessions, scenes and an in-game clock; character creation and level-up (levels 1 to 12 content); combat encounters on a square grid or in theatre-of-mind; spells with tiered automation; rests; content packs; imported and declarative homebrew content; single-step Docker install.

**Out of scope:** see section 8.

### 1.2 Users

| Role | Description |
| --- | --- |
| Instance administrator | First registered user on a fresh install. Manages the installation. |
| Dungeon Master (DM) | Creates and runs campaigns. A per-campaign role. |
| Player | Plays one or more characters in a campaign. A per-campaign role. |

### 1.3 Decisions log

| ID | Decision | Status |
| --- | --- | --- |
| D-01 | Ruleset is SRD 5.1 (2014), used under CC-BY-4.0 with attribution. Editions are not mixed. A `ruleset` field on content keeps the door open to other editions. | Decided |
| D-02 | Content target is levels 1 to 12. Milestone A delivers levels 1 to 5 across all MVP classes; milestone B widens toward 12 as data increments. | Decided (milestone split proposed) |
| D-03 | MVP classes: Fighter, Rogue, Paladin, Bard, Wizard. Stretch: Sorcerer, Warlock. | Decided |
| D-04 | Subclasses: SRD built-ins, openly licensed packs, user-imported JSON, and declarative homebrew. Popular community content is not bundled unless its licence allows it. The homebrew editor UI is post-MVP. | Decided |
| D-05 | The six abilities are fixed, accessed only through one `Ability` type and accessor. Custom abilities are a later upgrade. | Decided |
| D-06 | Any die from d2 to d1000 is supported, with limits. | Decided |
| D-07 | Self-hosted, single-step Docker Compose install. | Decided |
| D-08 | Design principle: keep the implementation simple and keep seams for future upgrades. | Decided |
| D-09 | No job queue (Redis/Kafka) for now. | Decided |
| D-10 | Players: DM-only in v1; live read-only view in v1.5; player-submitted actions in v2 only if cheap. | Decided |
| D-11 | C++ pathfinder is a measured trade-off, behind a port, with a Java fallback. | Decided |

### 1.4 Assumptions (confirm or correct)

| ID | Assumption |
| --- | --- |
| A-01 | One class per character in the MVP. The schema supports several class rows so multiclassing can be added later. |
| A-02 | Square grid with 8 neighbors and 5 ft squares. Diagonals cost the same as straight steps by default; the true diagonal length and the alternating 5-10-5 rule are settings. Reach and adjacency always use the 8 neighbors. Hex is a later geometry behind the same interface. |
| A-03 | Local username/password accounts only, with no external identity providers. The DM is the instance administrator, players join over the same local network, and registration after the first account is by DM-issued invite. |
| A-04 | Spells have automation tiers: 0 = reference text only, 1 = spends a slot and rolls dice, 2 = full effect resolution. Tier 2 covers a curated set. |
| A-05 | A subset of SRD monsters is playable in the MVP, with at least 2 to 3 per level band (levels 1-3, 3-5, 5-6, 6-9, then 10-12 for milestone B). The DM chooses the roster for each encounter and can add artwork. |
| A-06 | English only; modern desktop browsers; responsive layout (players may use phones or tablets on the local network). |
| A-07 | No official non-SRD rules text is bundled. |

### 1.5 Glossary

**Actor**: a character or monster. **Combatant**: an actor taking part in an encounter. **Kernel**: the fixed vocabulary the engine understands (stats, modifiers, resources, slots, dice). **Content pack**: a versioned set of definitions (classes, spells, items, monsters) with licence and attribution. **Seam**: a narrow interface or accessor that keeps a future upgrade cheap. **Tier**: spell automation level.

## 2. Use cases

| ID | Use case | Actor | Priority |
| --- | --- | --- | --- |
| UC-01 | Create a campaign and enable content packs | DM | Must |
| UC-02 | Create a character from class, species, background, ability scores and equipment | Player, DM | Must |
| UC-03 | Level up a character | Player, DM | Must |
| UC-04 | Build an encounter from characters and monsters | DM | Must |
| UC-05 | Run a turn: move, attack, cast, end turn | DM | Must |
| UC-06 | Resolve a reaction (opportunity attack, a reaction spell) | DM, Player | Must |
| UC-07 | Take a short or long rest | DM | Must |
| UC-08 | Import a content pack from JSON | DM, Admin | Should |
| UC-09 | Watch an encounter live | Player | Should (v1.5) |
| UC-10 | Create a homebrew subclass | DM | Could (editor post-MVP) |

## 3. Functional requirements

Priority: **M** must, **S** should, **C** could. Milestone: **A** levels 1 to 5, **B** later. Statements follow EARS patterns.

### 3.1 Accounts and access (FR-ACC)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-ACC-001 | The system shall let a visitor register with a username and password. | M | A |
| FR-ACC-002 | The system shall store passwords only as adaptive hashes and shall never log them. | M | A |
| FR-ACC-003 | When the first account is registered on a fresh installation, the system shall make it the instance administrator (typically the DM); after that, the system shall accept registration only with a DM-issued invite code. | M | A |
| FR-ACC-004 | The system shall require authentication for every page except registration, login and static assets. | M | A |
| FR-ACC-005 | The system shall assign DM and player roles per campaign, not globally. | M | A |
| FR-ACC-006 | If a user requests a campaign, character or encounter they are not a member of, then the system shall respond as if it does not exist. | M | A |
| FR-ACC-007 | The system shall expire idle sessions and let a user sign out. | S | A |

### 3.2 Campaigns (FR-CAM)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-CAM-001 | The system shall let an authenticated user create a campaign and become its DM. | M | A |
| FR-CAM-002 | The system shall let a DM enable or disable content packs for a campaign. | S | A |
| FR-CAM-003 | The system shall let a DM invite users to a campaign as players. | S | B |
| FR-CAM-004 | The system shall let a DM archive a campaign without deleting its data. | C | B |

### 3.3 Dice (FR-DIC)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-DIC-001 | The system shall evaluate expressions of the form NdX with an optional integer modifier. | M | A |
| FR-DIC-002 | The system shall support keep-highest, keep-lowest, drop-highest and drop-lowest (for example `2d20kh1`, `4d6dl1`). | M | A |
| FR-DIC-003 | The system shall accept any die size from 2 to 1000 and any dice count from 1 to 100. | M | A |
| FR-DIC-004 | If an expression is malformed or exceeds the limits, then the system shall reject it with `VALIDATION_FAILED` and shall not roll. | M | A |
| FR-DIC-005 | The system shall draw each die uniformly from its range using an injectable random source. | M | A |
| FR-DIC-006 | The system shall return every face rolled and whether it was kept, with each result. | M | A |
| FR-DIC-007 | The system shall support reroll and minimum-value modifiers on individual dice. | S | B |
| FR-DIC-008 | The system shall be able to compute the exact outcome distribution of an expression for previews. | C | B |

### 3.4 Characters (FR-CHR)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-CHR-001 | The system shall let a user create a character by choosing class, species, background (where available), ability scores and starting equipment. | M | A |
| FR-CHR-002 | The system shall let the DM select, per campaign, from a built-in list of ability score generation methods (standard array, point buy, roll 4d6 drop lowest, roll 3d6, manual entry by the DM); DM-created methods are v2. | S | A |
| FR-CHR-003 | The system shall store, per character: six base ability scores; class levels and subclass; proficiencies with their source; HP gained per level; current and temporary HP; hit dice spent; death save counts; conditions; resource uses; spell slots used; known and prepared spells; inventory and currency. | M | A |
| FR-CHR-004 | The system shall derive modifiers, proficiency bonus, AC, initiative, speed, saving throw and skill bonuses, spell save DC and spell attack bonus from stored data and active modifiers, and shall not store them. | M | A |
| FR-CHR-005 | When a character gains a level, the system shall apply that level's progression: hit points, features, proficiencies and spell slots. | M | A |
| FR-CHR-006 | The system shall limit characters to level 12 in the MVP while keeping the data model capable of levels 1 to 20. | M | A |
| FR-CHR-007 | The system shall read and write ability scores only through a single `Ability` type and accessor. | M | A |
| FR-CHR-008 | The system shall keep a character's persistent state (HP, slots, resources) on the character, not copied into encounters. | M | A |
| FR-CHR-009 | The system shall export and import a character as JSON. | C | B |

### 3.5 Classes and features (FR-CLS)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-CLS-001 | The system shall provide the Fighter, Rogue, Paladin, Bard and Wizard classes with their SRD 5.1 features. | M | A |
| FR-CLS-002 | The system shall provide the SRD 5.1 subclass of each MVP class. | M | A |
| FR-CLS-003 | Where a feature has limited uses, the system shall track the uses as a resource with a recharge type (turn, short rest, long rest, dawn, never). | M | A |
| FR-CLS-004 | The system shall apply features through trigger points in action resolution (for example on attack roll, on damage roll, on turn start), not through class-specific branches in the core engine. | M | A |
| FR-CLS-005 | The system shall support extending to the Sorcerer and Warlock classes by adding data and, where needed, feature behaviors. | C | B |

### 3.6 Spells (FR-SPL)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-SPL-001 | The system shall provide SRD 5.1 spells of levels 0 to 6 as reference data with full text. | M | A |
| FR-SPL-002 | The system shall mark each spell with an automation tier (0, 1 or 2). | M | A |
| FR-SPL-003 | The system shall implement tier 2 for a curated set covering damage, healing, saving-throw, condition and buff spells. | M | A |
| FR-SPL-004 | When a character casts a spell with a slot, the system shall verify and spend a slot of sufficient level. | M | A |
| FR-SPL-005 | While a character is concentrating, if it casts another concentration spell or fails a concentration check, then the system shall end the first spell. | M | A |
| FR-SPL-006 | The system shall scale cantrip damage by character level. | M | A |
| FR-SPL-007 | The system shall support spell preparation (Wizard) and known spells (Bard) per class rules. | M | A |
| FR-SPL-008 | The system shall support upcasting where a spell defines scaling. | S | B |

### 3.7 Encounters and combat (FR-ENC)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-ENC-001 | The system shall let a DM create an encounter from campaign characters and monsters. | M | A |
| FR-ENC-002 | When an encounter starts, the system shall roll initiative for each combatant and order the turns, with ties decided by the DM (a d20 roll-off is available) and an option to roll once for a group of identical monsters. | M | A |
| FR-ENC-003 | While it is a combatant's turn, the system shall allow one action, one bonus action, movement up to its speed and one object interaction, and shall track what remains. | M | A |
| FR-ENC-004 | If an action is submitted out of turn or without the required economy, then the system shall reject it with a stable error code. | M | A |
| FR-ENC-005 | When a combatant reaches 0 HP, the system shall apply the unconscious, death-save and death rules for its kind. | M | A |
| FR-ENC-006 | The system shall track conditions with durations and apply their mechanical effects. | M | A |
| FR-ENC-007 | The system shall support temporary hit points and damage resistance, vulnerability and immunity. | M | A |
| FR-ENC-008 | When an encounter ends, the system shall write surviving state back to the characters. | M | A |
| FR-ENC-009 | The system shall let a DM add or remove combatants mid-encounter. | S | A |
| FR-ENC-010 | The system shall let a DM override any numeric state, recording the override in the event log. | S | A |
| FR-ENC-011 | The system shall support surprise at encounter start. | S | B |

### 3.8 Actions and reactions (FR-ACT)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-ACT-001 | When a combatant attacks, the system shall roll a d20 plus modifiers against the target's AC; a natural 20 is a critical hit and a natural 1 misses. | M | A |
| FR-ACT-002 | The system shall apply advantage and disadvantage, cancelling when both apply. | M | A |
| FR-ACT-003 | The system shall resolve saving throws against a DC. | M | A |
| FR-ACT-004 | The system shall support the Dash, Dodge and Disengage actions, and the Help, Hide, Ready, Use an Object, Grapple and Shove actions. | M | A |
| FR-ACT-005 | When an action or event offers a reaction, the system shall suspend resolution, record the pending reaction, and resume when the reacting player or the DM decides or declines. | M | A |
| FR-ACT-006 | The system shall support opportunity attacks when a combatant leaves an enemy's reach. | M | A |
| FR-ACT-007 | The system shall require each state-changing request to carry a client-generated `actionId`, and shall return the stored result for a repeated `actionId`. | M | A |
| FR-ACT-008 | If a request carries an `expectedVersion` that does not match the encounter, then the system shall reject it with `ENCOUNTER_VERSION_CONFLICT`. | M | A |

### 3.9 Rests and resources (FR-RST)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-RST-001 | When a short rest is taken, the system shall allow spending hit dice to heal and shall refresh short-rest resources. | M | A |
| FR-RST-002 | When a long rest is taken, the system shall restore HP, regain hit dice per the rules, and refresh long-rest resources and spell slots. | M | A |
| FR-RST-003 | The system shall refresh dawn- and turn-based resources at their trigger. | S | B |

### 3.10 Grid and movement (FR-GRD)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-GRD-001 | The system shall provide a square-grid battle map per encounter of up to 100 by 100 cells. | M | A |
| FR-GRD-002 | The system shall let a DM place combatants, obstacles and difficult terrain. | M | A |
| FR-GRD-003 | The system shall compute movement paths honoring blocked cells, difficult terrain and occupied squares. | M | A |
| FR-GRD-004 | The system shall highlight the squares reachable with a combatant's remaining movement. | M | A |
| FR-GRD-005 | The system shall provide a configurable distance metric for diagonals (equal-cost by default, true diagonal length, or alternating 5-10-5) using scaled integer costs, and shall always use the eight neighbors for reach and adjacency. | M | A |
| FR-GRD-006 | The system shall use the C++ pathfinder when available and fall back to the Java implementation on timeout, error or an open circuit. | M | A |
| FR-GRD-007 | The system shall allow encounters without a map (theatre-of-mind), skipping range validation. | S | A |
| FR-GRD-008 | The system shall compute area-of-effect cells (sphere, cube, cone, line, cylinder) from an origin. | S | B |
| FR-GRD-009 | The system shall compute line of sight and cover between cells. | C | B |

### 3.11 Content, packs and homebrew (FR-CNT)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-CNT-001 | The system shall ship the SRD 5.1 content as a content pack with the attribution required by its licence. | M | A |
| FR-CNT-002 | The system shall store `pack`, `ruleset`, `source`, `license` and `attribution` on every content record. | M | A |
| FR-CNT-003 | The system shall display attribution for the content packs in use. | M | A |
| FR-CNT-004 | The system shall import a content pack from JSON validated against a published schema, rejecting invalid input, without executing any code. | S | B |
| FR-CNT-005 | The system shall let homebrew features be assembled from validated building blocks (trigger, condition, effect, resource, limits). | S | B |
| FR-CNT-006 | The system shall scope homebrew content to a campaign, require DM approval, and apply numeric caps. | S | B |
| FR-CNT-007 | The system shall label user-created content distinctly. | S | B |
| FR-CNT-008 | The system shall treat damage types and conditions as extensible lists with SRD defaults. | S | B |
| FR-CNT-009 | The system shall provide a homebrew editor UI. | C | B |

### 3.12 Live view (FR-LIV, v1.5)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-LIV-001 | The system shall let campaign players view the live encounter, updated when state changes. | S | B |
| FR-LIV-002 | The system shall exclude DM-only information (monster HP values, hidden stats, DM notes) from player views, showing health descriptors instead. | M | B |
| FR-LIV-003 | When a viewer reconnects, the system shall deliver the current state. | S | B |
| FR-LIV-004 | The system shall let players submit actions for their own character subject to DM approval. | C | B |

### 3.13 Event log (FR-LOG)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-LOG-001 | The system shall append every resolved action to an event log including the dice results. | M | A |
| FR-LOG-002 | The system shall show the log, newest first and paginated, to the DM. | M | A |
| FR-LOG-003 | The system shall support undoing the last action. | C | B |

### 3.14 Install and operate (FR-OPS)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-OPS-001 | The system shall start from a clean machine with `docker compose up` and no manual step beyond optionally editing a `.env` file. | M | A |
| FR-OPS-002 | On first start, the system shall run database migrations and seed the SRD pack automatically. | M | A |
| FR-OPS-003 | The system shall ship no default credentials or secrets. | M | A |
| FR-OPS-004 | The system shall persist data across restarts and upgrades in a named volume, with documented backup and restore. | M | A |
| FR-OPS-005 | The system shall expose liveness and readiness endpoints. | M | A |
| FR-OPS-006 | The system shall run without the pathfinder container. | M | A |
| FR-OPS-007 | The system shall take all configuration from environment variables. | M | A |
| FR-OPS-008 | The project shall publish container images for amd64 and arm64. | S | B |

## 4. Non-functional requirements

Numeric figures are targets to verify, not promises.

| ID | Requirement |
| --- | --- |
| NFR-PERF-001 | Action resolution p95 at most 150 ms excluding the pathfinder and 300 ms including it, on a laptop-class host. |
| NFR-PERF-002 | Pathfinder p95 at most 50 ms for a 50 by 50 grid over loopback. |
| NFR-REL-001 | A pathfinder failure shall not fail any user request; the only effect is added latency. |
| NFR-REL-002 | An action is persisted before it is acknowledged, so a restart loses no acknowledged action. |
| NFR-REL-003 | A duplicate submit shall not duplicate effects (FR-ACT-007). |
| NFR-SEC-001 | Deny-by-default routing, per-campaign authorization at the data layer, CSRF protection, secure headers, input validation and login throttling. |
| NFR-SEC-002 | Imported and homebrew content is data only, validated for schema and size, and rendered escaped. |
| NFR-SEC-003 | No secrets in the repository or logs; dependency, image and secret scans run in CI. |
| NFR-SEC-004 | The pathfinder is reachable only on the internal network and enforces input limits. |
| NFR-USA-001 | A new DM can create a campaign, a character and run a first combat round unaided within 15 minutes (informal test). |
| NFR-USA-002 | Core screens are keyboard-operable and aim for WCAG AA contrast. |
| NFR-MNT-001 | The `engine` package has no framework or I/O dependencies, and abilities are reached only via the accessor; both are enforced by ArchUnit tests in CI. |
| NFR-MNT-002 | Every rule in the ruleset specification has at least one worked-example test. |
| NFR-MNT-003 | Required CI checks pass before merge, and `main` is always deployable. |
| NFR-PRT-001 | Runs on Linux, macOS and Windows through Docker. |
| NFR-OBS-001 | Structured JSON logs with a trace ID, request metrics and no personal data in logs. |
| NFR-LEG-001 | SRD 5.1 attribution is visible in the app and the repository; no non-SRD official text is bundled. |
| NFR-DAT-001 | Schema constraints enforce key invariants (HP bounds, one initiative slot per combatant, one membership per user per campaign). |

## 5. Constraints and external interfaces

- **Technology:** Java 21, Spring Boot, Thymeleaf with HTMX, PostgreSQL with Flyway, a C++17 pathfinder service, Docker Compose. Confirm against the course rubric (see section 10).
- **Licensing:** SRD 5.1 is used under CC-BY-4.0 with the attribution statement given in the SRD itself. The D&D Beyond Basic Rules are not Creative Commons and are not used. Openly licensed third-party packs record their own licence per record; share-alike licences such as ORC need their obligations checked before bundling. This is not legal advice.
- **Pathfinder interface:** a versioned REST/JSON contract (`/v1/path`, `/v1/reach`, later `/v1/area` and `/v1/los`) with enforced limits; defined in `Architecture (v5.0)` section 7.
- **Content pack interface:** a JSON schema for packs and homebrew features, to be published in the repository.
- **Error contract:** stable codes (for example `NOT_YOUR_TURN`, `INVALID_PATH`, `ENCOUNTER_VERSION_CONFLICT`, `VALIDATION_FAILED`), defined in `Architecture (v5.0)` section 9.

## 6. Data requirements

Principle: **a closed vocabulary the engine understands, and open content that references it.** Store choices and current state; derive numbers.

**Kernel (fixed schema)**

| Concept | Core fields |
| --- | --- |
| Actor | id, kind, name, level or CR, six ability scores (via the `Ability` accessor), max HP, current HP, temp HP, speed |
| Stat | a named number the engine computes: AC, initiative, attack bonus, save bonus, skill bonus, spell DC, speed, max HP |
| Modifier | target stat, operation (add, set, min, max, advantage, disadvantage), value, duration, source |
| Resource | key, current, max, recharge (turn, short rest, long rest, dawn, never) |
| Slot pool | slot level, total, used |
| Dice expression | value type with the notation in section 3.3 |
| Action economy | action, bonus action, reaction, movement remaining, object interaction |
| Combatant state | initiative, position, reaction used, concentration, conditions with durations |

**Content (open, replaceable):** classes (hit die, proficiencies, level table), subclasses, features, spells, items, species, backgrounds, monsters, feats, and every name, description and image.

**Stored versus derived** (from the official character sheet's structure, used as a field checklist and not for its layout):

| Sheet area | Stored | Derived |
| --- | --- | --- |
| Identity | name, owner, species, background, alignment, XP, class levels and subclass | proficiency bonus |
| Abilities | six base scores, ability-score choices by level | modifiers, save bonuses |
| Proficiencies | skills, saves, tools, weapons, armor, languages, each with a source; expertise flags | skill totals, passive Perception |
| Combat | HP gained per level, current and temp HP, hit dice spent, death saves, conditions, exhaustion | AC, initiative, speed, max HP, attack bonuses |
| Features | subclass choice, any feats | the feature list, granted by class level and subclass data |
| Resources | uses spent per feature | maximum uses |
| Spellcasting | known and prepared spells, spellbook, slots spent | save DC, spell attack, slots per level |
| Equipment | item, quantity, equipped flag, attunement, currency | weight, attack lines |

## 7. Seams register

Principle D-08: build the simple version now, and leave one narrow seam where the upgrade would go. Add a seam only if the upgrade is plausible, the seam is nearly free, and retrofitting later would be expensive.

| Seam | Simple today | Upgrade it allows | Trigger to revisit |
| --- | --- | --- | --- |
| `Ability` type and accessor | six fixed abilities | custom abilities per ruleset | a campaign needs a different attribute set |
| `Pathfinder` port | Java A\*, optional C++ service | gRPC, other algorithms | benchmark shows transport overhead matters |
| `EncounterNotifier` | in-memory SSE hub | Postgres LISTEN/NOTIFY or Redis pub/sub | more than one app instance |
| `RandomSource` and `Clock` | `SecureRandom`, system clock | seeded replay, simulations | replay or simulator feature |
| `ruleset` and `pack` fields on content | SRD 5.1 only | other editions, licensed packs | a second ruleset is wanted |
| Feature trigger points | built-in hooks for the MVP classes | richer homebrew features | the homebrew editor |
| Diagonal rule setting | 5 ft per diagonal | alternate grid rules | a table asks for a variant |
| Modifier targets | the kernel stats | custom stats and resources | homebrew needs them |
| Feature flags | a few config switches | merge unfinished work safely | live view and player actions |
| Content pack importer | JSON schema and validation | community pack sharing | pack sharing demand |

## 8. Out of scope and deferred

- Virtual tabletop features such as voice and video, plus fog of war and dynamic lighting (vision is a v2 feature; the schema keeps the seam).
- Multiclassing (the schema allows it later).
- Bundling non-SRD official content, or any 2024-rules content.
- Job queue and the encounter-difficulty simulator (revisit after the core is done).
- gRPC, multi-instance deployment, response caching, Kubernetes.
- Mobile app, payments, AI dungeon master.
- Undo and replay (the event log keeps the data, so it stays possible).

## 9. Traceability (summary)

| Goal or quality | Requirements | Architecture section |
| --- | --- | --- |
| Correct, testable rules | FR-DIC, FR-ACT, FR-ENC, NFR-MNT-001, NFR-MNT-002 | 4, 12 |
| Character data model | FR-CHR, section 6 | 6 |
| Spells and features | FR-SPL, FR-CLS | 4 |
| Reactions and interrupts | FR-ACT-005, FR-ACT-006 | 4, 5 |
| Grid and movement | FR-GRD | 7 |
| Extensible content | FR-CNT, section 7 | 4, 15 |
| Resilience and idempotency | FR-ACT-007, FR-ACT-008, NFR-REL | 5, 9 |
| Security | FR-ACC, NFR-SEC, FR-LIV-002 | 10 |
| Install and operate | FR-OPS | 13, 14 |

## 10. Open questions

1. **Course rubric:** resolved. There is no rubric; the stack is free and only Java is required.
2. **Team split:** deferred; the team will decide between themselves.
3. **Milestone A:** confirmed: levels 1 to 5 first, then 5 to 12.
4. **Monsters:** which SRD monsters are in the MVP set?
5. **Spell list:** which spells form the tier 2 set? To be fixed in the ruleset specification.
6. **Rules choices:** diagonal movement is decided (equal cost by default); surprise and how DM overrides are shown to players remain open.
7. **SRD 5.1 verification items:** confirm the feat list (believed to be very small), the single background, the subclass list, the exact spell and monster lists, condition definitions, rest and death-save rules, and the attribution statement text.
8. **Next document:** the ruleset specification with worked examples that become the first tests.

## 11. Release scope

v1 is the MVP (milestone A), v1.5 holds the achievable goals beyond it, and v2 holds the stretch goals. In section 3, milestone A means v1 and milestone B means v1.5 unless the table below places it in v2.

| Release | Scope |
| --- | --- |
| v1 (MVP, milestone A) | DM-driven play for Fighter, Rogue, Paladin, Bard and Wizard at levels 1 to 5; characters with a multiclass-ready schema; dice; combat with reactions; curated tier-2 spells; an SRD monster subset with per-encounter rosters and artwork upload; square grid with the distance-metric setting and movement; rests; sessions, scenes, game clock and audience in the schema with basic UI; LAN accounts with invites; single-step Docker |
| v1.5 (achievable goals) | Live player view with audience filtering; journal and quests; checkpoints; campaign export and import; JSON content-pack import; custom monsters and homebrew spells; levels 6 to 12 as data increments |
| v2 (stretch goals) | Vision phases (radius, then SRD light and senses); players submitting actions; homebrew editor UI; DM-created ability score generation methods; Sorcerer and Warlock; multiclass rules and UI; hex geometry |

The 8-week window is sized for v1 plus the first items of v1.5. Proposed order inside v1.5, so unfinished items cut cleanly: live player view, journal, export and import, checkpoints, pack import, then levels 6 to 12.

## 12. Requirements added in review 2

### 12.1 New decisions

| ID | Decision | Status |
| --- | --- | --- |
| D-12 | Square grid with 8 neighbors; equal-cost diagonals by default; true diagonal length and alternating 5-10-5 are settings; reach and adjacency always use the 8 neighbors; hex later | Decided |
| D-13 | Sessions, scenes, game clock and audience on events are in the MVP schema with basic UI | Decided |
| D-14 | Journal, checkpoints and campaign export are v1.5 | Decided |
| D-15 | Vision is a kernel seam now (sense stats, scene light, cell sight-blocking, a vision model setting); the feature is v2 | Decided (phasing proposed) |
| D-16 | LAN play: the DM is the admin, registration is invite-only after the first account, HTTP on the local network | Decided |
| D-17 | The DM chooses the monster roster per encounter and uploads artwork; only our own placeholders ship | Decided |
| D-18 | Homebrew spells and monsters use the same schema and caps as built-in ones, with caps derived from SRD data | Decided |
| D-19 | Multiclass-ready schema now (hit dice by die size, ability score improvements by class level); single-class UI and rules | Decided |
| D-20 | Release split v1, v1.5 and v2 as in section 11 | Decided |

### 12.2 New functional requirements

| ID | Requirement | Pri | Release |
| --- | --- | --- | --- |
| FR-SES-001 | The system shall organize play into sessions (one real-world play night) within a campaign, with states active, paused and ended. | M | v1 |
| FR-SES-002 | When a session is paused, the system shall preserve the full game state so play resumes exactly where it stopped, including mid-encounter. | M | v1 |
| FR-SES-003 | When a session resumes, the system shall show a recap: current scene, whose turn it is, party HP and resources, and the last events. | S | v1 |
| FR-SCN-001 | The system shall support scenes (a place and time with a map, ambient light and participants) that may or may not contain an encounter. | M | v1 |
| FR-SCN-002 | The system shall let the DM set a scene's ambient light level (bright, dim, dark). | M | v1 |
| FR-CLK-001 | The system shall keep an in-game clock per campaign that advances only when the DM advances it, a rest is taken, or travel is resolved, never with real time. | M | v1 |
| FR-CLK-002 | The system shall measure effect durations in rounds, until end of turn, game time, until a rest, or permanent. | M | v1 |
| FR-AUD-001 | The system shall record an audience (DM only, all, or named players) on every event, note and message, and shall filter on it server-side. | M | v1 |
| FR-AUD-002 | The system shall let the DM make a roll secret so its result is visible only to the DM. | S | v1 |
| FR-AST-001 | The system shall let the DM upload an image for a monster; it shall validate type and size, re-encode, strip metadata, and store the file on the data volume. | M | v1 |
| FR-AST-002 | The system shall extend image upload to characters, scenes and handouts. | S | v1.5 |
| FR-AST-003 | The system shall ship generic placeholder silhouettes by creature type and no official artwork. | M | v1 |
| FR-MON-001 | The DM shall choose the monsters available in each encounter from the enabled packs. | M | v1 |
| FR-MON-002 | The system shall let the DM create custom monsters from a stat-block form, validated like imported content. | S | v1.5 |
| FR-SPL-009 | The system shall let the DM add homebrew spells that follow the same schema and caps as built-in spells, with caps derived from SRD spells of the same level. | S | v1.5 |
| FR-CHR-010 | The schema shall support several classes per character (hit dice by die size, ability score improvements by class and class level, a subclass per class row); multiclass rules and UI are deferred. | M | v1 |
| FR-NET-001 | The system shall be reachable by players on the DM's local network at a displayed address over HTTP. | M | v1 |
| FR-NET-002 | The system shall show a join screen with the address (derived from the DM's browser URL, with a warning for localhost) and an invite code. | S | v1 |
| FR-ACT-009 | The engine shall give the pathfinder a per-mover cost map (blocked, difficult, normal, no-stop) built from the SRD movement rules. | M | v1 |
| FR-JRN-001 | The system shall let the DM write journal entries and quests with an in-game timestamp and an audience. | S | v1.5 |
| FR-JRN-002 | The system shall capture notable events automatically (deaths, level-ups, scene changes, session start and end) as journal candidates. | S | v1.5 |
| FR-CHK-001 | The system shall create checkpoints (full state snapshots) before each encounter, at session end and on demand, and keep the last N. | S | v1.5 |
| FR-CHK-002 | Restoring a checkpoint shall replace the current campaign state atomically and be logged. | S | v1.5 |
| FR-EXP-001 | The system shall export a campaign (state, homebrew packs, assets and a pack manifest) as one file and import it into another instance, validating schema, sizes and versions. | S | v1.5 |
| FR-VIS-001 | The data model shall include sense stats, scene light and cell sight-blocking, and a vision model setting with "full" as the default. | M | v1 |
| FR-VIS-002 | Where a vision model other than "full" is active, the system shall send each player only the cells and combatants their characters can see. | S | v2 |
| FR-CHR-011 | The system shall let the DM set the hit point method per campaign: the player chooses between the rolled value and the fixed average at each level (default), or the DM forces one of them. | S | v1 |
| FR-CHR-012 | The system shall advance characters by milestone by default (the DM triggers a level-up) and shall keep XP and the level thresholds in the data model. | M | v1 |
| FR-CHR-013 | The system shall support XP advancement as an option the DM can select per campaign. | S | v1.5 |
| FR-CHR-014 | When a maximum HP value rises, the system shall raise current HP by the same amount; when it falls below current HP, the system shall set current HP to the new maximum; a creature at 0 HP is not raised. | M | v1 |
| FR-CHR-015 | Before applying an ability improvement that would pass the ability's maximum, the system shall warn the player and show the excess that would be lost. | M | v1 |
| FR-ACT-010 | The system shall provide a campaign setting for automatic success on a natural 20 and automatic failure on a natural 1 for ability checks, saving throws or both (default off). | S | v1 |
| FR-CLK-003 | The system shall let the DM set how far the game clock advances per completed combat round (default 6 seconds; 0 for manual only). | S | v1 |
| FR-ENC-012 | When a combatant joins a running encounter, the system shall let the DM choose join options: initiative roll mode (normal, advantage, disadvantage, fixed), timing (at its initiative or end of round), starting conditions, and a first-turn restriction (none or surprised). | S | v1 |
| FR-ENC-013 | The system shall treat damage absorbed by temporary hit points as damage taken (death save failures at 0 HP, concentration checks) and shall provide a campaign setting for the house rule that fully absorbed damage causes no failure (default off). | S | v1 |
| FR-ENC-014 | The system shall provide an optional campaign setting that lets the DM mark chosen combatants as caught off guard by a mid-combat arrival (default off). | C | v1.5 |

### 12.3 New non-functional requirements

| ID | Requirement |
| --- | --- |
| NFR-REL-004 | Pause, restart and resume (including mid-encounter, with a pending reaction, readied action and active zone) reproduces identical state and next legal actions; a round-trip test enforces it. |
| NFR-REL-005 | An active encounter keeps the content definitions it started with; a pack update does not change it. |
| NFR-SEC-005 | Uploaded images are untrusted: sniffed type, size and dimension caps, re-encoding, content-hash names, authenticated serving. |
| NFR-SEC-006 | Plain HTTP on the LAN is documented as unsuitable for internet exposure without a reverse proxy; invite codes are hashed, expiring and rate-limited. |
| NFR-SEC-007 | Exports and checkpoints contain every campaign secret; only the DM can create, download or restore them. |
| NFR-USA-003 | The layout is responsive so players can use phones or tablets on the local network. |

The seams register is maintained in Architecture (v5.0), section 15.

## 13. Version and revision history

Current version: **1.4**.

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-10-11 | Draft 1: scope, decisions D-01 to D-11, assumptions, use cases, functional and non-functional requirements, kernel data summary, seams, open questions |
| 1.1 | 2026-10-11 | Review 2: square grid and distance metric, LAN accounts with invites, monster rosters and artwork, release scope (section 11), new decisions and requirements (section 12), resolved open questions |
| 1.2 | 2026-10-14 | Ability score generation methods (FR-CHR-002, v2 scope), hit point method, advancement, maximum HP rule, ability improvement warning, natural 20 setting, clock advance (FR-CHR-011 to FR-CHR-015, FR-ACT-010, FR-CLK-003) |
| 1.3 | 2026-10-15 | Join options for mid-combat arrivals, temporary hit points at 0 HP, optional mid-combat surprise (FR-ENC-012 to FR-ENC-014) |
| 1.4 | 2026-10-16 | Version numbering and revision history added; no requirement changes |