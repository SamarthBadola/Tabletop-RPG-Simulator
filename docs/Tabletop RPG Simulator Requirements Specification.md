# Tabletop RPG Simulator: Requirements Specification (v1.20)

Enterprise Web Application (Java) course project. Draft 1, 11 October 2026. Ruleset: D&D System Reference Document 5.1 (2014 rules), CC-BY-4.0. Companion to `the Architecture document`.

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
| A-04 | Spells have automation tiers 0, 1 and 2. All tiers share one cast record and differ only in how the effect is resolved; the tier definitions and the curated tier-2 list are in the Ruleset Specification (10.2 and 10.9). |
| A-05 | The SRD 5.1 monsters ship as seed data, and 18 of them are fully automated (Ruleset Specification 12.10). The DM chooses the roster for each encounter and can add artwork. |
| A-06 | English only; modern desktop browsers; responsive layout (players may use phones or tablets on the local network). |
| A-07 | No official non-SRD rules text is bundled. |

### 1.5 Glossary

**Actor**: a character or monster. **Combatant**: an actor taking part in an encounter. **Kernel**: the fixed vocabulary the engine understands (stats, modifiers, resources, slots, dice). **Content pack**: a versioned set of definitions (classes, spells, items, monsters) with licence and attribution. **Seam**: a narrow interface or accessor that keeps a future upgrade cheap. **Tier**: spell, item or monster automation level (0, 1 or 2; Ruleset Specification 10.2, 11.2 and 12.10).

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
| FR-ACC-004 | The system shall require authentication for every page except registration, login, static assets and the health route (/health), which shows no version and no detail. | M | A |
| FR-ACC-005 | The system shall assign DM and player roles per campaign, not globally. | M | A |
| FR-ACC-006 | If a user requests a campaign, character or encounter they are not a member of, then the system shall respond as if it does not exist. | M | A |
| FR-ACC-007 | The system shall expire idle sessions and let a user sign out. | S | A |

### 3.2 Campaigns (FR-CAM)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-CAM-001 | The system shall let an authenticated user create a campaign and become its DM. | M | A |
| FR-CAM-002 | The system shall let a DM enable or disable content packs for a campaign. | S | A |
| FR-CAM-003 | The system shall let a DM invite users to a campaign as players with a campaign invite code, which a signed-in user enters to join the campaign. | M | A |
| FR-CAM-004 | The system shall let a DM archive a campaign without deleting its data. | C | B |

### 3.3 Dice (FR-DIC)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-DIC-001 | The system shall evaluate dice expressions that are sums of dice groups (NdX, with optional keep or drop, reroll and floor modifiers) and fixed integers, each added or subtracted; the exact notation is in the Ruleset Specification, section 3.10. | M | A |
| FR-DIC-002 | The system shall support keep-highest, keep-lowest, drop-highest and drop-lowest (for example `2d20kh1`, `4d6dl1`). | M | A |
| FR-DIC-003 | The system shall accept any die size from 2 to 1000 and any dice count from 1 to 100 per group, with at most 200 dice, 20 terms and 200 characters per expression. | M | A |
| FR-DIC-004 | If an expression is malformed or exceeds the limits, then the system shall reject it with `VALIDATION_FAILED` and shall not roll. | M | A |
| FR-DIC-005 | The system shall draw each die uniformly from its range using an injectable random source. | M | A |
| FR-DIC-006 | The system shall return every face rolled and whether it was kept, with each result. | M | A |
| FR-DIC-007 | The system shall support reroll-once and minimum-value (floor) modifiers on dice groups, as needed by Great Weapon Fighting and similar features. | M | A |
| FR-DIC-008 | The system shall be able to compute the exact outcome distribution of an expression for previews. | C | B |

### 3.4 Characters (FR-CHR)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-CHR-001 | The system shall let a user create a character by choosing class, species, background (where available), ability scores and starting equipment (FR-EQP-018). | M | A |
| FR-CHR-002 | The system shall let the DM select, per campaign, from a built-in list of ability score generation methods (standard array, point buy, roll 4d6 drop lowest, roll 3d6, manual entry by the DM); DM-created methods are v2. A rolled method is rolled by the server with the dice engine and kept as a rolled set for the player in the campaign until the character is created, so leaving the creation steps and coming back gives the same values and no re-roll; the player assigns the values to the abilities freely (Ruleset Specification, RS-ABL-007). | S | A |
| FR-CHR-003 | The system shall store, per character: six base ability scores; class levels and subclass; proficiencies with their source; HP gained per level; current and temporary HP; hit dice spent; death save counts; conditions; resource uses; spell slots used; known and prepared spells; inventory and currency (FR-EQP-001, FR-EQP-014). | M | A |
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
| FR-SPL-002 | The system shall mark each spell with an automation tier (0, 1 or 2; Ruleset Specification 10.2). | M | A |
| FR-SPL-003 | The system shall implement tier 2 for a curated set covering damage, healing, saving-throw, condition and buff spells (the list is in Ruleset Specification 10.9). | M | A |
| FR-SPL-004 | When a character casts a spell with a slot, the system shall verify and spend a slot of sufficient level (Ruleset Specification 10.4, E-70). | M | A |
| FR-SPL-005 | While a character is concentrating, if it casts another concentration spell or fails a concentration check, then the system shall end the first spell (Ruleset Specification 10.5, E-72, E-73). | M | A |
| FR-SPL-006 | The system shall scale cantrip damage by character level (Ruleset Specification 10.2, E-81). | M | A |
| FR-SPL-007 | The system shall support spell preparation (Wizard) and known spells (Bard) per class rules (Ruleset Specification 10.3). | M | A |
| FR-SPL-008 | The system shall support upcasting where a spell defines scaling. | S | B |
| FR-SPL-009 | The system shall record every cast in one cast record that all automation tiers share; the tiers shall differ only in how the effect is resolved (Ruleset Specification 10.2 to 10.4, E-69 to E-71, E-89). | M | A |
| FR-SPL-010 | When an area spell is cast, the system shall choose the affected squares by integer geometry and the coverage rule of Ruleset Specification 10.6 (E-74 to E-76, E-90), with no floating-point calculation. | M | A |
| FR-SPL-011 | When a creature could counter or otherwise react to a spell being cast, the system shall offer the reaction through the reaction window (FR-RXN-001) and shall limit what the players learn about the cast as the Counterspell information setting says (Ruleset Specification 10.8, E-77, E-91). | M | A |
| FR-SPL-012 | The system shall let the DM choose the spell settings of Ruleset Specification 10.11 through the settings registry (FR-SET-001 to FR-SET-008) and, where 10.11 says so, ask for them before they are first used. | M | A |
| FR-SPL-013 | Outside an encounter, the system shall let a spellcaster prepare spells once after each long rest and cast a ritual without spending a slot (Ruleset Specification 10.3, E-78, E-80). | M | A |
| FR-SPL-014 | When a spell has verbal or costed components, the system shall check them as the component enforcement setting says (Ruleset Specification 10.11, E-79). | S | A |

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
| FR-ENC-010 | The system shall let a DM override any numeric state, recording the override in the event log. The players who may see the changed value shall be shown the override as one standard sentence without the old value or the reason, and the DM may make an override quiet only when no player can see that value. | S | A |
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

The rest, clock and recharge requirements of Ruleset Specification Part C are FR-RST-001 to FR-RST-008, listed in section 12.2 with the other requirements added with the rules parts; FR-RST-009 is below.

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-RST-009 | The system shall refresh turn-based resources at the trigger that their recharge rule names (recharge on rests and at dawn is FR-RST-005). | S | B |

### 3.10 Grid and movement (FR-GRD)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-GRD-001 | The system shall provide a square-grid battle map per encounter of up to 200 by 200 cells. | M | A |
| FR-GRD-002 | The system shall let a DM place combatants, obstacles and difficult terrain. | M | A |
| FR-GRD-003 | The system shall compute movement paths honoring blocked cells, difficult terrain and occupied squares. | M | A |
| FR-GRD-004 | The system shall highlight the squares reachable with a combatant's remaining movement. | M | A |
| FR-GRD-005 | The system shall provide a configurable distance metric for diagonals (equal-cost by default, true diagonal length, or alternating 5-10-5) using scaled integer costs, and shall always use the eight neighbors for reach and adjacency. | M | A |
| FR-GRD-006 | The system shall use the C++ pathfinder when available and fall back to the Java implementation on timeout, error or an open circuit. | M | A |
| FR-GRD-007 | The system shall allow encounters without a map (theatre-of-mind), skipping range validation. | S | A |
| FR-GRD-008 | The system shall compute area-of-effect cells (sphere, cube, cone, line, cylinder) from an origin. | S | B |
| FR-GRD-009 | The system shall compute line of sight and cover between creatures from the map by the corner rules of Ruleset Specification 13.4, with the method chosen by the cover method setting (RS-ENV-019 to RS-ENV-024, E-138, E-139). | C | B |
| FR-GRD-010 | The system shall let the DM upload a map image (PNG, JPEG or WebP) and save it as a map: a grid of square cells set by square size and offset, with the image as a background. Rules, movement and pathfinding read only the grid, and the grid stays within the limit of FR-GRD-001. | S | B |
| FR-GRD-011 | The system shall suggest obstacle squares from an uploaded map image by a detection step that runs on the server inside the request, with no job queue, and that does not depend on the pathfinder. Suggested squares shall be shown apart from confirmed squares and block nothing until the DM saves. The DM shall be able to mark obstacle, difficult terrain and clear squares by hand, and a failed or empty detection shall not prevent saving a map. | S | B |
| FR-GRD-012 | The system shall provide a full-screen display mode of the map in which the side panel is hidden and the acting creature's statistics and the turn order appear in an overlay that opens by pointer, keyboard or touch. | C | B |
| FR-GRD-013 | The system shall let the DM place creatures on the map before an encounter starts, for each group of monsters: by hand, at random on any eligible square, or at random on eligible squares inside an area the DM draws. An eligible square is not an obstacle and not occupied, and for a creature larger than Medium every square of its footprint must be eligible. The server shall choose random positions, so every screen shows one layout, and a preview shall bind nothing until the encounter starts. An area with fewer eligible squares than creatures shall be refused. | S | B |
| FR-GRD-014 | The system shall let the DM draw a start area for the player characters, inside which each player places their own token, and shall let the DM place any character by hand, with or without a start area. | S | B |
| FR-GRD-015 | If the DM starts an encounter while a player character has no position, then the system shall warn the DM and name those characters. If the DM starts anyway, the system shall place each unplaced character on the nearest free eligible square inside the start area, or on the nearest eligible square when no area is drawn. | S | B |

### 3.11 Content, packs and homebrew (FR-CNT)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-CNT-001 | The system shall ship the SRD 5.1 content as a content pack with the attribution required by its licence. | M | A |
| FR-CNT-002 | The system shall store `pack`, `ruleset`, `source`, `license` and `attribution` on every content record. | M | A |
| FR-CNT-003 | The system shall display attribution for the content packs in use. | M | A |
| FR-CNT-004 | The system shall import a content pack, a folder with a manifest and JSON definitions, validated against a published schema and the engine's vocabulary, rejecting the whole pack when any definition is invalid or names something the engine does not define, without executing any code. | S | B |
| FR-CNT-005 | The system shall let homebrew features be assembled from validated building blocks (trigger, condition, effect, resource, limits). | S | B |
| FR-CNT-006 | The system shall scope homebrew content to a campaign, require DM approval, and warn when a definition goes beyond the SRD range of a quantity, the DM confirming the warnings when enabling the pack (Content Schema Specification, CS-HBR-006 to CS-HBR-008). | S | B |
| FR-CNT-007 | The system shall label user-created content distinctly. | S | B |
| FR-CNT-008 | The system shall treat damage types and conditions as extensible lists with SRD defaults. | S | B |
| FR-CNT-009 | The system shall provide a homebrew editor UI. | C | B |
| FR-CNT-010 | The system shall identify every content definition by a key of the form pack:local, so that two packs can use the same local name without a clash. | M | A |
| FR-CNT-011 | The system shall let a pack replace a definition of another pack only by naming its key explicitly, and only in the campaigns that enable that pack. | S | B |
| FR-CNT-012 | The system shall have the database refuse a change to a loaded content definition and refuse two versions of one pack in a campaign. | M | A |
| FR-CNT-013 | The system shall check that the SRD pack reproduces the SRD source text word for word, and shall ship the pack with the Creative Commons attribution notice. | M | A |

### 3.12 Live view (FR-LIV, v1.5)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-LIV-001 | The system shall let campaign players view the live encounter, updated when state changes. | S | B |
| FR-LIV-002 | The system shall exclude DM-only information (monster HP values, hidden stats, DM notes) from player views, showing health descriptors instead. | M | B |
| FR-LIV-003 | When a viewer reconnects, the system shall deliver the current state. | S | B |
| FR-LIV-004 | The system shall let players submit actions for their own character subject to DM approval. | C | v2 |

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

### 3.15 Equipment and items (FR-EQP)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-EQP-001 | The system shall keep each item as a definition in content with instances held by an owner, and shall mark each item with an automation tier (0, 1 or 2); the engine shall act on structured fields and effect entries only and never on item names (Ruleset Specification 11.2, E-92, E-93). | M | A |
| FR-EQP-002 | The system shall provide the SRD 5.1 armor, weapons, adventuring gear, mounts and services as reference data, and shall resolve the curated list of magic items of Ruleset Specification 11.6; every other magic item is tier 0 (Ruleset Specification 11.3 to 11.7, E-110). | M | A |
| FR-EQP-003 | The system shall compute armor class from the current state of the character and shall not store it; the highest eligible base wins and the breakdown is kept for display (Ruleset Specification 11.3, RS-EQP-001 to RS-EQP-010, E-94, E-95). | M | A |
| FR-EQP-004 | The system shall derive whether a character is proficient with the worn armor and shield from the proficiency list, and shall apply the consequences that Ruleset Specification 11.3 states (E-96). | M | A |
| FR-EQP-005 | When armor is donned or doffed outside an encounter, the system shall advance the game clock by the time Ruleset Specification 11.3 states; during an encounter, the system shall block the change or allow a DM override as the armor change setting says (E-97). | M | A |
| FR-EQP-006 | The system shall model two hands and shall let a character hold, stow and draw items within the limits of Ruleset Specification 11.4 (RS-EQP-011 to RS-EQP-017, E-98, E-99); the free object interaction follows the object interaction setting, and a spell with a somatic component needs a free hand (RS-EQP-016). | M | A |
| FR-EQP-007 | The system shall generate the attack options of a combatant from held weapons, carried throwable weapons, unarmed strikes and improvised objects, and shall resolve the chosen option through the attack rules of Ruleset Specification Part B (Ruleset Specification 11.5, E-100, E-103). | M | A |
| FR-EQP-008 | The system shall represent weapon properties as enumerated data values, each with one engine rule (Ruleset Specification 11.5, RS-EQP-019 to RS-EQP-028, E-101). | M | A |
| FR-EQP-009 | When a character fires ammunition, the system shall reduce the ammunition stack by one; after the battle, the system shall recover ammunition as the ammunition recovery setting says (Ruleset Specification 11.7, RS-EQP-044, E-102). | M | A |
| FR-EQP-010 | The system shall apply item bonuses through the effect entries of Ruleset Specification 11.2, shall add bonuses from different items and shall count the same definition once (E-104). | S | A |
| FR-EQP-011 | When a character attunes to an item, the system shall enforce the limit, the time and the ending conditions of Ruleset Specification 11.6 (RS-EQP-032, E-105), and shall end the attunement by itself when the state shows an ending condition. | S | A |
| FR-EQP-012 | The system shall track the charges of an item and shall recharge them at dawn as the item states (Ruleset Specification 11.6, E-106). | S | A |
| FR-EQP-013 | The system shall keep the properties of an unidentified item from every player-facing view, filtering them on the server as the unidentified items setting says (Ruleset Specification 11.6, E-109). | S | A |
| FR-EQP-014 | The system shall store currency as whole coins per denomination, express prices in copper and make change by the rule of Ruleset Specification 11.7 (RS-EQP-038, E-107). | M | A |
| FR-EQP-015 | Where the DM has turned on encumbrance, the system shall track carried weight by the chosen rule (Ruleset Specification 11.7, E-108); encumbrance is off by default. | S | A |
| FR-EQP-016 | The system shall apply the equipment slot limits of Ruleset Specification 11.4 as an engine convention that the DM can override (RS-EQP-013, E-111). | S | A |
| FR-EQP-017 | The system shall mark magical and silvered items with flags that the engine reads for damage resistance (Ruleset Specification 11.5, E-112). | S | A |
| FR-EQP-018 | When a character is created, the system shall resolve the starting equipment of the class and background to content ids and give the items to the character; the player marks what is worn or held (Ruleset Specification 11.9, E-113). | M | A |
| FR-EQP-019 | The system shall let the DM choose the equipment settings of Ruleset Specification 11.9 through the settings registry (FR-SET-001 to FR-SET-008). | M | A |

### 3.16 Monsters and encounters (FR-MON)

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-MON-001 | The system shall keep each monster as a definition in content with the fields of Ruleset Specification 12.2, shall spawn it into a combatant with its own hit points, and shall act on structured fields only and never on the monster's name (RS-MON-001, E-114). | M | A |
| FR-MON-002 | The system shall ship the SRD 5.1 monsters as reference data and shall resolve the roster of Ruleset Specification 12.10; every other SRD stat block is tier 0 and a monster that is not in the SRD is not shipped (RS-MON-049 to RS-MON-052, E-130). | M | A |
| FR-MON-003 | The system shall check each shipped stat block against the challenge rating table of Ruleset Specification 12.3, failing the pack build on a mismatch and warning on a homebrew monster (RS-MON-004, RS-MON-005, E-116). | M | A |
| FR-MON-004 | The system shall start monster hit points at the printed average and, where the monster hit points setting says so, roll them once at spawn and record the roll (RS-MON-012, E-115). | M | A |
| FR-MON-005 | The system shall run the ordered handlers of the zero-hit-point hook between damage and the death step, so that traits such as Undead Fortitude, Regeneration and source riders apply (Ruleset Specification 12.4, RS-MON-013 to RS-MON-016, E-117). | M | A |
| FR-MON-006 | The system shall resolve the special traits of the roster as the engine mechanics of Ruleset Specification 12.5 (RS-MON-017 to RS-MON-029), shall roll recharge at the start of the monster's own turn (RS-MON-009, E-123), and shall make a monster's opportunity attack one melee attack (RS-MON-010, E-133). | M | A |
| FR-MON-007 | The system shall compute whether an ally is within 5 feet from the engine's grid, without the pathfinder service, and shall ask the DM to confirm when there is no grid (RS-MON-018, E-122). | S | A |
| FR-MON-008 | The system shall offer legendary actions in a window at the end of another creature's turn, with a points pool that refills at the start of the creature's own turn (RS-MON-030, RS-MON-031, E-119). | S | A |
| FR-MON-009 | The system shall offer Legendary Resistance after a failed saving throw, and the DM shall be able to choose whether it also overrides an automatic failure; by default it does (RS-MON-032, RS-MON-033, E-118). | S | A |
| FR-MON-010 | The system shall run lair actions as an entry in the initiative order that is not a combatant, when the DM marks the fight as taking place in the lair (RS-MON-034, E-120). | S | A |
| FR-MON-011 | The system shall let a spell or feature create creatures, shall record who controls them, and shall remove them without death effects when the creating effect ends (Ruleset Specification 12.8, RS-MON-037 to RS-MON-042, E-126, E-131). | S | A |
| FR-MON-012 | The system shall show the experience points of each monster and the total for the encounter, and shall not award them automatically (RS-MON-044 to RS-MON-046, E-128). | M | A |
| FR-MON-013 | The system shall show the difficulty of an encounter by the budget method of Ruleset Specification 12.9 once the encounter simulator exists (RS-MON-047, RS-MON-048, E-128, E-129). | S | B |
| FR-MON-014 | The system shall let the DM choose the monster settings of Ruleset Specification 12.12 through the settings registry (FR-SET-001 to FR-SET-008). | M | A |

### 3.17 Environment and afflictions (FR-ENV, FR-MAD)

The rules are in section 13 of the Ruleset Specification. Each requirement cites the rule ids that define it and the decision ids that fix the choice, and none of them repeats a value. In the Ms column, A is version 1, B is version 1.5 and v2 is version 2.

| ID | Requirement | Pri | Ms |
| --- | --- | --- | --- |
| FR-ENV-001 | The system shall derive the light level of a cell and never store it on a creature, shall give the scene an ambient light and a sunlit flag, and shall let the DM override the light of any creature (Ruleset Specification 13.2, RS-ENV-001, RS-ENV-002, RS-ENV-006, RS-ENV-008, E-134). | M | A |
| FR-ENV-002 | The system shall light a cell from emitters on items, spell effects and zones, with magical darkness as a zone flag, and shall take the best level that reaches the cell (RS-ENV-003 to RS-ENV-005, RS-ENV-007, E-135, E-136). | S | B |
| FR-ENV-003 | The system shall keep the senses of a creature as data with a range in feet, shall show them on the sheet and the stat block, and shall use them in the engine from version 1.5 (Ruleset Specification 13.3, RS-ENV-009, RS-ENV-010). | M | A |
| FR-ENV-004 | The system shall answer what a creature can see through one function, the sight resolver, shall let every rule that needs sight read it and nothing else, and shall let the DM override the answer for any pair of creatures (RS-ENV-011 to RS-ENV-018, E-137). | S | B |
| FR-ENV-005 | The system shall let the DM choose the cover of any attack or effect, and where a computed value exists shall let the DM override it in both directions; the choice is recorded on the roll and shown to the player (Ruleset Specification 13.4, RS-ENV-027). | M | A |
| FR-ENV-006 | The system shall keep sight blocking and cover as two independent values of a cell, shall let the DM open and close doors, and shall hide a secret door from players until the DM reveals it (RS-ENV-025, RS-ENV-028, E-139). | S | B |
| FR-ENV-007 | The system shall apply cover to the Dexterity saving throw of each creature in an area effect, with the origin of the area as the only corner (RS-ENV-026). | S | B |
| FR-ENV-008 | The system shall check every step of a move against the threatened area of each hostile creature and shall offer the opportunity attack through the reaction window (Ruleset Specification 13.5, RS-ENV-029 to RS-ENV-033, E-141). | S | B |
| FR-ENV-009 | The system shall offer flanking as an optional rule that is off by default (RS-ENV-034, E-140). | C | B |
| FR-ENV-010 | The system shall calculate fall damage from a distance that the DM enters, shall round the distance down to a multiple of 10 feet, and shall land the creature prone unless it avoids the damage (Ruleset Specification 13.6, RS-ENV-037 to RS-ENV-039, E-144). | M | A |
| FR-ENV-011 | The system shall record a fall as an event with its own states, shall open the ON\_FALL reaction window before any damage so that a reaction such as Feather Fall can be cast, and shall let a long fall descend over several rounds as the fall descent setting says (RS-ENV-035, RS-ENV-036, RS-ENV-040 to RS-ENV-042, E-142, E-144). | S | B |
| FR-ENV-012 | The system shall take the distance of a fall from the creature's elevation once elevation exists, through the fall distance provider, and shall resolve the end of Fly and Levitate itself (RS-ENV-043, E-143). | S | v2 |
| FR-ENV-013 | The system shall keep hazards as content with a trigger, a way to detect, a way to disable and effects, shall ship the sample traps of the SRD as seed data, and shall let the DM trigger a hazard (Ruleset Specification 13.7, RS-ENV-044, RS-ENV-048, RS-ENV-050, RS-ENV-051, RS-ENV-054, E-145). | M | A |
| FR-ENV-014 | The system shall run a complex trap as a TRAP entry in the initiative order that is not a combatant, and from version 1.5 shall trigger a step-on or enter-area hazard by itself when a creature's step enters the cell (RS-ENV-049, RS-ENV-056, E-146). | S | B |
| FR-ENV-015 | The system shall track holding breath, choking and suffocation, and shall apply the rules for underwater combat (RS-ENV-045 to RS-ENV-047). | M | A |
| FR-ENV-016 | The system shall keep poisons as records with a delivery, a saving throw, effects and a duration on the game clock, and shall ship the sample poisons of the SRD as seed data (RS-ENV-052). | M | A |
| FR-ENV-017 | The system shall send each player only what the player may see, and shall leave hidden hazards, secret doors, unseen creatures and DM-only fields out of what it sends, by a projection made on the server (RS-ENV-055, E-150). | M | A |
| FR-ENV-018 | The system shall let the DM choose the environment settings of Ruleset Specification 13.12.1 through the settings registry (FR-SET-001 to FR-SET-008). | M | A |
| FR-ENV-019 | The system shall make a fall, a trap resolution and an affliction action safe to repeat, so that a request sent twice with the same key changes nothing the second time (FR-ACT-007, FR-ACT-008, E-152). | M | A |
| FR-ENV-020 | The environment rules shall run in pure Java engine packages with no framework, database or network access, and shall meet the budgets of Ruleset Specification 13.9 (E-151, E-153, T-99, T-100). | S | B |
| FR-MAD-001 | The system shall track an affliction as an instance on one creature, with a kind (short-term, long-term or indefinite madness, or a disease), a source, a status and an end time on the game clock, and shall end the instance when the clock passes that time (Ruleset Specification 13.8, RS-MAD-001, RS-MAD-003, RS-MAD-007, RS-MAD-011, E-147). | M | A |
| FR-MAD-002 | The system shall let the DM inflict madness by naming the creature, the kind and the source, shall make the saving throw through the ordinary saving throw path, and shall roll the result on the madness tables of the content pack and record the roll (RS-MAD-002, RS-MAD-004 to RS-MAD-006, E-148). | M | A |
| FR-MAD-003 | The system shall apply the mechanical part of a result as conditions, modifiers and triggers, shall show every other part as an instruction to the DM and the player, and shall not play the character (RS-MAD-008, RS-MAD-009, RS-MAD-013). | M | A |
| FR-MAD-004 | The system shall suppress, end or cure an affliction by the spells and effects that the rules name, and shall keep the timer running while the instance is suppressed (RS-MAD-010, E-149). | M | A |
| FR-MAD-005 | The system shall track a disease as an affliction with a time before symptoms, a saving throw, a counter of progress and a result, and shall ship the sample diseases of the SRD as seed data (RS-MAD-012, RS-ENV-053). | M | A |
| FR-MAD-006 | The system shall measure the distance for the lucky charm result of long-term madness from the map and apply its disadvantage itself (RS-MAD-005). | S | B |

## 4. Non-functional requirements

Numeric figures are targets to verify, not promises.

| ID | Requirement |
| --- | --- |
| NFR-PERF-001 | Action resolution p95 at most 150 ms excluding the pathfinder and 300 ms including it, on a laptop-class host. |
| NFR-PERF-002 | Pathfinder p95 at most 50 ms for a 50 by 50 grid over loopback. |
| NFR-PERF-003 | Obstacle detection on a map image runs inside its request, with no job queue, and finishes or ends with a failure answer within 60 seconds. A slow or failed detection can delay the DM but never prevents saving the map. |
| NFR-REL-001 | A pathfinder failure shall not fail any user request; the only effect is added latency. |
| NFR-REL-002 | An action is persisted before it is acknowledged, so a restart loses no acknowledged action. |
| NFR-REL-003 | A duplicate submit shall not duplicate effects (FR-ACT-007). |
| NFR-SEC-001 | Deny-by-default routing, per-campaign authorization at the data layer, CSRF protection, secure headers, input validation and login throttling. |
| NFR-SEC-002 | Imported and homebrew content is data only, validated for schema and size, and rendered escaped. |
| NFR-SEC-003 | No secrets in the repository or logs; dependency, image and secret scans run in CI. |
| NFR-SEC-004 | The pathfinder is reachable only on the internal network and enforces input limits. |
| NFR-USA-001 | A new DM can create a campaign, a character and run a first combat round unaided within 15 minutes (informal test). |
| NFR-USA-002 | Every screen meets WCAG 2.2 level AA, including keyboard operation, contrast and target size. The UX and API Specification says how each criterion is met. |
| NFR-MNT-001 | The `engine` package has no framework or I/O dependencies, and abilities are reached only via the accessor; both are enforced by ArchUnit tests in CI. |
| NFR-MNT-002 | Every rule in the ruleset specification has at least one worked-example test. |
| NFR-MNT-003 | Required CI checks pass before merge, and `main` is always deployable. |
| NFR-PRT-001 | Runs on Linux, macOS and Windows through Docker. |
| NFR-OBS-001 | Structured JSON logs with a trace ID, request metrics and no personal data in logs. |
| NFR-LEG-001 | SRD 5.1 attribution is visible in the app and the repository; no non-SRD official text is bundled. |
| NFR-DAT-001 | Schema constraints enforce key invariants (HP bounds, one initiative slot per combatant, one membership per user per campaign). |

## 5. Constraints and external interfaces

- **Technology:** Java 25, Spring Boot 4.1.1, Thymeleaf with HTMX, PostgreSQL with Flyway, a C++17 pathfinder service, Docker Compose. Confirm against the course rubric (see section 10).
- **Licensing:** SRD 5.1 is used under CC-BY-4.0 with the attribution statement given in the SRD itself. The D&D Beyond Basic Rules are not Creative Commons and are not used. Openly licensed third-party packs record their own licence per record; share-alike licences such as ORC need their obligations checked before bundling. This is not legal advice.
- **Pathfinder interface:** a versioned REST/JSON contract (`/v1/path`, `/v1/reach`, later `/v1/``los`) with enforced limits; defined in `the Grid and Pathfinder Specification (summarized in the Architecture document, section 7)`.
- **Content pack interface:** a JSON schema for packs and homebrew features, to be published in the repository.
- **Error contract:** stable codes (for example `NOT_YOUR_TURN`, `INVALID_PATH`, `ENCOUNTER_VERSION_CONFLICT`, `VALIDATION_FAILED`), defined in `the Architecture document,` section 9.

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
| Equipment | item, quantity, hand or slot position, identified flag, silvered flag, attunement, charges, ground location, currency by denomination | carried weight, attack options |

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

- Virtual tabletop features such as voice and video, plus fog of war (a v2 feature that reads the sight rules of Ruleset Specification 13.3; the schema keeps the seam).
- Multiclassing (the schema allows it later).
- Bundling non-SRD official content, or any 2024-rules content.
- Job queue and the encounter-difficulty simulator (revisit after the core is done).
- gRPC, multi-instance deployment, response caching, Kubernetes.
- Mobile app, payments, AI dungeon master.
- Full replay, and undo beyond the last action (undoing the last action is a v1.5 could-have, FR-LOG-003; the event log keeps the data, so replay stays possible).

## 9. Traceability (summary)

| Goal or quality | Requirements | Architecture section |
| --- | --- | --- |
| Correct, testable rules | FR-DIC, FR-ACT, FR-ENC, NFR-MNT-001, NFR-MNT-002 | 4, 12 |
| Character data model | FR-CHR, section 6 | 6 |
| Spells and features | FR-SPL, FR-CLS | 4 |
| Equipment and items | FR-EQP | 4, 6 |
| Monsters and encounters | FR-MON | 4, 6 |
| Environment and afflictions | FR-ENV, FR-MAD | 4, 12 |
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
4. **Monsters:** which SRD monsters are in the MVP set? Closed: every SRD stat block ships as seed data, and the 18 that resolve fully are listed in Ruleset Specification 12.10.
5. **Spell list:** which spells form the tier 2 set? Closed: the list is in Ruleset Specification 10.9.
6. **Rules choices:** diagonal movement and surprise are decided (equal cost by default; the DM confirms surprise, Ruleset Specification E-14); how DM overrides are shown to players remains open.
7. **SRD 5.1 verification items:** confirm the feat list (believed to be very small), the single background, the subclass list, the exact spell and monster lists, condition definitions, rest and death-save rules, and the attribution statement text.
8. **Next document:** resolved: the Ruleset Specification exists (see its revision history for the current version). The Content Schema Specification and the Grid and Pathfinder Specification exist too (see their revision histories for the current versions). The next document is the UX and API specification with the threat model.

## 11. Release scope

v1 is the MVP (milestone A), v1.5 holds the achievable goals beyond it, and v2 holds the stretch goals. In section 3, milestone A means v1 and milestone B means v1.5 unless the table below places it in v2.

| Release | Scope |
| --- | --- |
| v1 (MVP, milestone A) | DM-driven play for Fighter, Rogue, Paladin, Bard and Wizard at levels 1 to 5; characters with a multiclass-ready schema; dice; combat with reactions; curated tier-2 spells; equipment with computed armor class, hands, attack options, currency and a curated list of magic items; the SRD 5.1 monsters as seed data, 18 of them fully automated, with per-encounter rosters and artwork upload; square grid with the distance-metric setting and movement; scene light, senses as data, cover chosen by the DM, fall damage, hazards with the SRD sample traps, breath and underwater rules, poisons, and the madness and disease tracker; rests; sessions, scenes, game clock and audience in the schema with basic UI; LAN accounts with invites; single-step Docker |
| v1.5 (achievable goals) | Live player view with audience filtering; journal and quests; checkpoints; campaign export and import with the access data by username; map upload with grid and obstacle editing, the full-screen map and creature placement before an encounter; JSON content-pack import; custom monsters and homebrew spells; the encounter difficulty readout with the simulator; light sources and zones; line of sight and cover computed from the map; reach and opportunity attacks checked on every step; the fall event with its reaction window; hazards that trigger on movement; the optional flanking rule; levels 6 to 12 as data increments |
| v2 (stretch goals) | Vision phases (radius, then SRD light and senses); elevation, with the fall distance calculated from it; players submitting actions; homebrew editor UI; DM-created ability score generation methods; Sorcerer and Warlock; multiclass rules and UI; hex geometry; mounted combat; Polymorph and other stat block swaps |

The 8-week window is sized for v1 plus the first items of v1.5. Proposed order inside v1.5, so unfinished items cut cleanly: live player view, journal, export and import, checkpoints, pack import, then levels 6 to 12.

## 12. Requirements added in review 2

### 12.1 New decisions

| ID | Decision | Status |
| --- | --- | --- |
| D-12 | Square grid with 8 neighbors; equal-cost diagonals by default; true diagonal length and alternating 5-10-5 are settings; reach and adjacency always use the 8 neighbors; hex later | Decided |
| D-13 | Sessions, scenes, game clock and audience on events are in the MVP schema with basic UI | Decided |
| D-14 | Journal, checkpoints and campaign export are v1.5 | Decided |
| D-15 | Vision is a kernel seam now (sense stats, scene light, cell sight blocking and cover, a vision model setting); the engine computes light and sight from v1.5 (Ruleset Specification 13.3) and fog of war is v2 | Decided (phasing proposed) |
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
| FR-AST-004 | The system shall let the DM map an uploaded image to a monster of any enabled pack, so that one image serves every copy of that monster, and the map token shall show the creature's initials until an image is mapped. | S | v1 |
| FR-MON-015 | The DM shall choose the monsters available in each encounter from the enabled packs. | M | v1 |
| FR-MON-016 | The system shall let the DM create custom monsters from a stat-block form, validated like imported content. | S | v1.5 |
| FR-SPL-015 | The system shall let the DM add homebrew spells that follow the same schema and caps as built-in spells, with caps derived from SRD spells of the same level. | S | v1.5 |
| FR-CHR-010 | The schema shall support several classes per character (hit dice by die size, ability score improvements by class and class level, a subclass per class row); multiclass rules and UI are deferred. | M | v1 |
| FR-NET-001 | The system shall be reachable by players on the DM's local network at a displayed address over HTTP. | M | v1 |
| FR-NET-002 | The system shall show a join screen with the address (derived from the DM's browser URL, with a warning for localhost) and an invite code. | S | v1 |
| FR-ACT-009 | The engine shall give the pathfinder a per-mover cost map (blocked, difficult, normal, no-stop) built from the SRD movement rules. | M | v1 |
| FR-JRN-001 | The system shall let the DM write journal entries and quests with an in-game timestamp and an audience. | S | v1.5 |
| FR-JRN-002 | The system shall capture notable events automatically (deaths, level-ups, scene changes, session start and end) as journal candidates. | S | v1.5 |
| FR-CHK-001 | The system shall create checkpoints (full state snapshots) before each encounter, at session end and on demand, and keep the last N. | S | v1.5 |
| FR-CHK-002 | When a checkpoint is restored, the system shall first save a checkpoint of the current campaign state, so the restore can be undone by restoring that checkpoint, then replace the current campaign state atomically and log the restore. | S | v1.5 |
| FR-EXP-001 | The system shall export a campaign (state, homebrew packs, assets and a pack manifest) as one file and import it into another instance, validating schema, sizes and versions. The export also holds the campaign's access data: members by username with their roles, character ownership by username and invite state. It never holds a password hash, a session, an account id or an invite code. An import is owned by the importing DM and matches usernames to the accounts on the instance, and matched members take their roles and characters (FR-CHR-017 covers a username with no account). | S | v1.5 |
| FR-VIS-001 | The data model shall include sense stats, scene light, and cell sight blocking and cover, and a vision model setting with "full" as the default (RS-ENV-010, RS-ENV-025, E-134). | M | v1 |
| FR-VIS-002 | Where a vision model other than "full" is active, the system shall send each player only the cells and combatants their characters can see, as the sight resolver answers (RS-ENV-015, E-137, E-150). | S | v2 |
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
| FR-CHR-016 | When a character would gain the same skill or tool proficiency from two sources, the system shall ask the player to choose a different proficiency of the same kind. A campaign setting lets the DM narrow this to overlaps with the background, or turn it off (default: any two sources). | S | v1 |
| FR-CHR-017 | When an imported campaign names a member whose username has no account on the instance, the system shall hold that member's characters as account missing and show them to the DM. A held character passes to that person when they join with that username, and the DM can discard it instead. | S | v1.5 |
| FR-ENC-015 | The system shall support damage resistance, vulnerability and immunity per damage type, including resistance and immunity that apply only to nonmagical damage, and immunity to conditions; every damage instance records whether it is magical. | S | v1 |
| FR-RST-001 | The DM shall be able to run a short rest for chosen characters. At its end each participant may spend Hit Dice, up to the unspent ones, rolling each die plus the Constitution modifier, either a chosen number of dice or one at a time until hit points reach a target; hit points regained are capped at the maximum, and spent Hit Dice stay spent until a long rest. | M | v1 |
| FR-RST-002 | A long rest shall restore all hit points, half the character's Hit Dice (minimum 1, the player choosing which sizes), all spell slots and the features that recharge on a long rest, end temporary hit points, and reduce exhaustion by 1 when the DM confirms the character ate and drank. It gives benefit at most once per 24 hours, measured end to end, and only if the character had at least 1 hit point at the start. | M | v1 |
| FR-RST-003 | A rest shall advance the game clock by its actual duration; an interrupted rest gives no benefit and advances the clock by the time that passed. A rest cannot start while an encounter is active. A dead creature, or one at 0 hit points that is not stable, cannot take part, and a second long rest inside the interval completes without benefit after a warning. A rest is applied all at once and safely repeatable: submitting it twice has the effect of once. | M | v1 |
| FR-RST-004 | The DM shall be able to set the rest variant (Standard, Epic Heroism, Gritty Realism or custom lengths) and the long rest interval, and the house rules that an interrupted long rest counts as a short rest after at least 1 hour and that Hit Dice healing uses the fixed average. | S | v1 |
| FR-RST-005 | Limited-use features shall recharge on a short rest, a long rest, at dawn or when initiative is rolled according to data-defined recharge rules, for the MVP classes (Fighter, Rogue, Paladin, Bard, Wizard); the recharge model shall also allow a rule that returns one use on a short rest and all on a long rest. | M | v1 |
| FR-RST-006 | The rest dialog shall offer the wizard's Arcane Recovery at the end of a short rest, and the bard's Song of Rest for creatures that spend Hit Dice. | S | v1 |
| FR-CLK-004 | The system shall derive time of day and dawn from the game clock; effects with clock durations shall expire when the clock advances, in order, and effects measured in rounds shall continue on the clock (6 seconds per remaining round) when an encounter ends. | M | v1 |
| FR-RST-007 | Rests and clock changes shall be recorded in a campaign event log with the audience, the game time and every die rolled. | M | v1 |
| FR-RST-008 | Players shall be able to complete their part of a rest (spending Hit Dice) on their own screens. | C | v1.5 |
| FR-SET-001 | Every campaign setting shall be defined once in a registry with a key, type, allowed values, default, tier and a plain-language explanation of each option; a setting the DM has not decided shall use its default. | M | v1 |
| FR-SET-002 | A House rules panel shall list every setting with its state (default or decided), who decided it and when, and let the DM change it or return it to the default. | M | v1 |
| FR-SET-003 | Creating a campaign shall offer a short setup wizard with presets for the structural settings (ruleset and packs, distance metric, rest variant, advancement, hit point method). | S | v1 |
| FR-SET-004 | Every setting decision shall be recorded in the campaign event log (key, old and new value, who, when, how it was decided); a change never alters earlier events, and every action shall record the settings it used. | M | v1 |
| FR-SET-005 | Only the DM of a campaign shall be able to decide or change its settings; values shall be validated against the registry. | M | v1 |
| FR-SET-006 | Each campaign shall have a settings mode: configure now, ask as needed, or defaults only (version 1 offers configure now and defaults only). | S | v1 |
| FR-SET-007 | In ask-as-needed mode, when an action needs an undecided setting, the system shall ask the DM before the action is resolved (never after a die is rolled), showing the options, the default and an explanation, and then carry on; the DM can use the default for now or always use defaults. An action spawned by a reaction that needs an undecided setting after dice were rolled shall use the default for that action and leave the setting undecided, and nothing shall ever be asked after a die has been rolled. | S | v1.5 |
| FR-SET-008 | Players shall be able to see the campaign's house rules. | S | v1.5 |
| FR-ENC-016 | A campaign setting shall choose how critical hits roll damage dice that have keep or drop modifiers: roll the group again and add (default), add one extra die only, or reject such groups when content is defined. | S | v1 |
| FR-ENC-017 | The system shall support features that let a weapon's damage dice be rolled again with either total used (for example the Savage Attacker feat), with campaign settings for whether extra damage dice are included and how critical dice are handled. | S | v1.5 |
| FR-RXN-001 | When an action reaches a point where a creature has an eligible reaction or feature option (when a creature leaves reach, when it is attacked, after a roll, after a hit, after damage is rolled, after a failed save, when a spell is cast), the system shall pause the action, offer the option to whoever decides (the DM in version 1), and then resume it. If nothing is eligible there shall be no pause. | M | v1 |
| FR-RXN-002 | Options shall respect the reaction economy: a creature that has used its reaction (or cannot act) is offered only options that cost no reaction, and taking a reaction option withdraws that creature's other reaction options. | M | v1 |
| FR-RXN-003 | Options shall be offered in a fixed order (the acting side first, then the defending side, then damage-changing options), and the roll and its outcome shall be recomputed after each option. | M | v1 |
| FR-RXN-004 | The pause shall work in an encounter and in a scene (ability checks and saves outside combat); an open window shall survive a restart and shall block other actions in its encounter or scene. | M | v1 |
| FR-RXN-005 | Reactions shall nest to a depth of 6; beyond that no further reactions shall be offered and the limit shall be logged, not silent. | M | v1 |
| FR-RXN-006 | The options a window offers shall never reveal hidden information (for example a DM-only roll or armor class) to the person deciding. | M | v1 |
| FR-RXN-007 | The DM shall be able to cancel an open window; the cancelled action's effects shall not apply, and the dice already rolled shall remain in the log. | M | v1 |
| FR-RXN-008 | Players shall answer for the creatures they control on their own screens, the DM can answer for any creature, and a player can choose 'always pass' for a feature on their creature so they are not asked each time. | S | v1.5 |
| FR-RXN-009 | While a reaction window is open, a level-up, rest, clock advance, DM edit or other action that would change a creature in it shall be rejected until the window closes (answers and cancel still work); the open action shall keep the settings it has read and the content versions it started with. | M | v1 |
| FR-RXN-010 | An option in a reaction window shall be able to require a follow-up choice by the same decider (for example which of two totals to keep after a damage reroll), which cannot be passed. | M | v1 |
| FR-RXN-011 | A campaign setting shall choose what a decider sees at a reaction window and when after-roll options are offered: the total (default), the natural die only, or the outcome (then the options are offered after it and may flip it); whatever is revealed shall be logged with the decider as its audience. | S | v1 |

### 12.3 New non-functional requirements

| ID | Requirement |
| --- | --- |
| NFR-REL-004 | Pause, restart and resume (including mid-encounter, with a pending reaction, readied action and active zone) reproduces identical state and next legal actions; a round-trip test enforces it. |
| NFR-REL-005 | An active encounter keeps the content definitions it started with; a pack update does not change it. |
| NFR-SEC-005 | Uploaded images (monster images and maps) are untrusted: sniffed type, size and dimension caps, re-encoding, content-hash names, authenticated serving. |
| NFR-SEC-006 | Plain HTTP on the LAN is documented as unsuitable for internet exposure without a reverse proxy; invite codes are hashed, expiring and rate-limited. |
| NFR-SEC-007 | Exports and checkpoints contain every campaign secret; only the DM can create, download or restore them. |
| NFR-USA-003 | The layout is responsive so players can use phones or tablets on the local network. |

The seams register is maintained in the Architecture document, section 15.

## 13. Version and revision history

Current version: **1.20**.

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-10-11 | Draft 1: scope, decisions D-01 to D-11, assumptions, use cases, functional and non-functional requirements, kernel data summary, seams, open questions |
| 1.1 | 2026-10-11 | Review 2: square grid and distance metric, LAN accounts with invites, monster rosters and artwork, release scope (section 11), new decisions and requirements (section 12), resolved open questions |
| 1.2 | 2026-10-14 | Ability score generation methods (FR-CHR-002, v2 scope), hit point method, advancement, maximum HP rule, ability improvement warning, natural 20 setting, clock advance (FR-CHR-011 to FR-CHR-015, FR-ACT-010, FR-CLK-003) |
| 1.3 | 2026-10-15 | Join options for mid-combat arrivals, temporary hit points at 0 HP, optional mid-combat surprise (FR-ENC-012 to FR-ENC-014) |
| 1.4 | 2026-10-16 | Version numbering and revision history added; no requirement changes |
| 1.5 | 2026-10-21 | Reconciliation: the technology baseline now matches the repository (Java 25, Spring Boot 4.1.1); dice requirements aligned with the Ruleset Specification (FR-DIC-001 and FR-DIC-003 describe sums of terms and the concrete limits; FR-DIC-007 reroll-once and floor raised to Must in v1, because Great Weapon Fighting is a level 1 Fighter option) |
| 1.5.1 | 2026-10-30 | Corrections after the cross-document review: open questions no longer list the Ruleset Specification as the next document or surprise as open; the out-of-scope line on undo and replay now agrees with FR-LOG-003 |
| 1.6 | 2026-11-14 | New requirement FR-CHR-016: replacement of duplicate skill and tool proficiencies, with a campaign setting (decision E-22) |
| 1.7 | 2026-11-14 | New requirement FR-ENC-015: damage resistance, vulnerability and immunity with the nonmagical qualifier and condition immunity (verified wording, Ruleset Specification 0.12) |
| 1.7.1 | 2026-11-15 | Correction: the pathfinder interface no longer lists /v1/area (area shapes are computed in the engine) and points to the new Grid and Pathfinder Specification |
| 1.8 | 2026-11-16 | Rests, the clock and recharge (Ruleset Specification Part C): FR-RST-001 to FR-RST-008 and FR-CLK-004 |
| 1.9 | 2026-11-16 | Settings registry, House rules panel, setup wizard, ask-before-use and settings modes (FR-SET-001 to FR-SET-008); critical dice mode (FR-ENC-016); reroll-and-choose damage features (FR-ENC-017) |
| 1.10 | 2026-11-16 | Part C edge cases applied to FR-RST-001 (a spend plan: a count, or one die at a time until hit points reach a target) and FR-RST-003 (dying and dead participants, a second long rest inside the interval) |
| 1.11 | 2026-11-16 | The reaction window (FR-RXN-001 to FR-RXN-008): one pause mechanism for reactions and for choices after a roll, a hit or a failed save |
| 1.12 | 2026-11-16 | Reaction window rectifications: FR-RXN-009 (frozen state and pins), FR-RXN-010 (follow-up choices), FR-RXN-011 (roll information setting); FR-SET-007 (settings during a reaction chain) |
| 1.13 | 2026-11-18 | Spells (Ruleset Specification Part E): FR-SPL-009 to FR-SPL-014 added, references to the Ruleset Specification added to FR-SPL-002 to FR-SPL-007, the tier wording, the glossary entry and the closed spell-list question updated. References to the Architecture document no longer carry a version number, so a version is stated in one place only. |
| 1.14 | 2026-11-19 | Equipment and items (Ruleset Specification Part F): new section 3.15 with FR-EQP-001 to FR-EQP-019; references added to the character creation and storage requirements; the equipment row of the data table, the glossary entry for tier, the release scope and the traceability table updated. |
| 1.15 | 2026-11-19 | Monsters and encounters (Ruleset Specification Part G): new section 3.16 with FR-MON-001 to FR-MON-014; assumption A-05, the open question about monsters, the glossary entry for tier, the release scope (v1, v1.5 and v2 rows) and the traceability table updated. |
| 1.16 | 2026-11-19 | Environment and afflictions (Ruleset Specification Part H): new section 3.17 with FR-ENV-001 to FR-ENV-020 and FR-MAD-001 to FR-MAD-006, where the madness tracker is a version 1 requirement. FR-GRD-009 now points to RS-ENV-019 to RS-ENV-024 and keeps its priority and milestone. FR-GRD-001 corrected to the 200 by 200 grid cap, which the Grid and Pathfinder Specification and the Architecture already state. FR-VIS-001, FR-VIS-002, the vision seam row, the out-of-scope entry, the release scope (v1, v1.5 and v2 rows) and the traceability table updated. |
| 1.17 | 2026-11-21 | Content Schema Specification, accepted by the owner on 2026-11-21, applied by reference. FR-CNT-004 now describes a pack as a folder with a manifest that is rejected whole when invalid or when it names something the engine does not define. FR-CNT-006 replaces the numeric caps with warnings against the SRD range that the DM confirms (CS-HBR-006 to CS-HBR-008). New FR-CNT-010 (namespaced keys), FR-CNT-011 (explicit overrides), FR-CNT-012 (database enforced immutability and one version of a pack per campaign) and FR-CNT-013 (verbatim SRD text and the Creative Commons notice). The next-document list is updated. |
| 1.18 | 2026-11-21 | UX and API Specification, accepted by the owner on 2026-11-21, applied by reference. FR-ACC-004 names the health route. NFR-USA-002 raises the target to WCAG 2.2 AA. FR-CAM-003 becomes a Must in milestone A: a signed-in user joins with a campaign invite code. New FR-GRD-010 to FR-GRD-015 (map upload, obstacle detection inside the request, full-screen map, placing creatures, start area, unplaced characters), FR-AST-004 (monster image mapping), FR-CHR-017 (held characters) and NFR-PERF-003. FR-CHR-002 (rolled set kept until the character is created), FR-CHK-002 (a restore saves a checkpoint first), FR-EXP-001 (access data by username) and NFR-SEC-005 (maps) reworded. FR-LIV-004 moved to v2. Release scope updated. |
| 1.19 | 2026-11-22 | Duplicate requirement identifiers removed after a review. The monster, spell and rest requirements that predate the rules parts kept the numbers that the new sections then reused. The older FR-MON-001 (the DM chooses the monsters of an encounter from the enabled packs) and FR-MON-002 (custom monsters from a stat-block form) in section 12.2 are now FR-MON-015 and FR-MON-016; the older FR-SPL-009 (homebrew spells) in section 12.2 is now FR-SPL-015. FR-MON-001 to FR-MON-014 in section 3.16 and FR-SPL-009 to FR-SPL-014 in section 3.6 are unchanged. In section 3.9 the original FR-RST-001 and FR-RST-002 are removed because the fuller FR-RST-001 to FR-RST-008 in section 12.2 replace them, and the original FR-RST-003 (refresh at dawn and by turn) is now FR-RST-009 and keeps only the turn-based part, since FR-RST-005 already covers dawn. Priorities and milestones are unchanged. Every identifier is now defined once. |
| 1.20 | 2026-11-22 | FR-ENC-010 now says how an override is shown: the players who may see the changed value read it as one standard sentence without the old value or the reason, and the DM may make an override quiet only when no player can see that value. Applies UXD-11 and UXQ-24, accepted by the owner. No other requirement changed. |
