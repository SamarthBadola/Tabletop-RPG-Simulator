# Tabletop RPG Simulator: Content Schema Specification (v1.2)

Nov 21, 2026 · @Samarth

## 1. Purpose, scope and status

This document fixes the exact format of game content: how a content pack is laid out on disk, how each definition is written, which engine pieces a definition may name, and the small grammar used for formulas. The Ruleset Specification says what the rules do; this document says how data refers to them. Current version 1.2. Version 1.0 was accepted by the owner on 2026-11-21. Its decisions are listed in section 18, and the revision history is at the end.

| This document owns | Where the rest lives |
| --- | --- |
| The pack folder, the manifest, keys, hashes and overrides | Rule mechanics and their numbers: Ruleset Specification |
| The schema of every definition kind | Which spells, items and monsters are curated and at which tier: Ruleset Specification, Parts E, F and G |
| The vocabulary registry and the expression grammar | Storage seams outside content, packages and the build order: Architecture and AI Build Brief |
| Validation, limits, error codes and the lifecycle of a pack | Screens, endpoints and the web threat model: the UX and API Specification |

The Ruleset stays the single source of truth. A number such as a saving throw DC, a die or a cost appears in a definition because the SRD or the Ruleset says so, and this document states none of them. Examples show the shape of a record only, and their values are not rules.

Companion documents: the Ruleset Specification (sections 9 to 13 name the fields encoded here), the Requirements Specification (section 3.11 and the content-related requirements of 3.15 to 3.17), the Architecture (the content pack decisions and seams), the Decision Log, the AI Build Brief (Step 5, the content pack skeleton) and the Grid and Pathfinder Specification. References carry no version numbers; each document keeps its own version in its header.

| Id | Meaning |
| --- | --- |
| CS-xxx-nnn | A content schema rule, grouped by area (PKG, KEY, OVR, ENV, VOC, EXP, DEF, VAL, LIF, STO, SRD, HBR, VIS, SEC, REL) |
| CD-n | A content decision, with its status |
| T-101 onward | Tests, continuing the numbering of the Ruleset (T-100 is the last there) |
| V-10 | Verification debt of this document |

Who decided what is recorded in section 18. The owner has decided that a pack is a folder with a manifest (CD-1), that overrides are allowed (CD-2), that formulas use a small declarative grammar (CD-3) and that attribution is one simple Creative Commons statement (CD-4). The owner accepted CD-7 to CD-16 as proposed on 2026-11-21.

## 2. Principles and the shape of a pack

Eight principles hold the design together. Each has an id so later sections and tests can cite it.

| Id | Principle |
| --- | --- |
| CS-PKG-001 | A pack is data only. It holds JSON and text files. It never holds code, scripts, calls out to the host, or references to files outside its own folder. |
| CS-PKG-002 | The engine owns the vocabulary. Every effect type, trigger point, condition, damage type and variable a definition can name is listed in one registry in Java (section 7). A pack combines those names and cannot create one. Adding a mechanic means a Java handler and a registry entry, with no change to the pack format. |
| CS-PKG-003 | The hardcoded part stays small. Whatever can be written as data and a formula is data and a formula. Java holds the handlers for registry names and the grammar evaluator, and nothing else about content. |
| CS-PKG-004 | SRD prose is verbatim. Player-facing description text from the SRD is stored unedited and checked against a normalised extraction of the source. Our own structured mechanics sit in separate fields, so a correction never touches the SRD wording. |
| CS-PKG-005 | Reject whole. A pack loads completely or not at all. One invalid definition, one unknown vocabulary name or one broken reference rejects the pack with `CONTENT_INVALID` and a list of every problem found, not only the first. |
| CS-PKG-006 | Immutable after load. A loaded definition never changes. A change is a new pack version, and the old one stays for every open action and checkpoint that pins it. |
| CS-PKG-007 | Derived identity. A pack's hash is computed from its files. Nothing in the manifest has to be kept in step with the folder contents. |
| CS-PKG-008 | One loader for every pack. The seeded SRD pack, campaign homebrew and an uploaded third-party pack pass through the same loader and the same schema checks. The only difference is the severity of the Ruleset consistency checks, which fail the build of a seeded pack and warn for homebrew, as the Ruleset decides (E-116). |

### What a pack contains

A pack is a folder. Its parts, in the order the loader reads them:

| Part | Purpose | Required |
| --- | --- | --- |
| `manifest.json` | Pack id, version, ruleset, schema and vocabulary requirements, licence, attribution, dependencies (section 3) | Yes |
| Definition files | One file per definition, grouped by kind in subfolders (section 3, and the schema of each kind in section 9) | At least one |
| `NOTICE` | The attribution text a licence requires, shown wherever the pack's content is shown | When the licence requires attribution |
| Text and table assets | Long text kept out of definition files when a definition points at it | No |

A pack depends on three things, and the manifest names all three: one ruleset (CS-PKG-009), a version of the vocabulary registry (section 7) and, optionally, other packs it builds on (section 5). It does not depend on the engine's code version; the vocabulary version is the contract between them.

Three kinds of pack exist, and they differ only in where they come from and who approves them: the SRD pack seeded at first start, homebrew written for one campaign, and a third-party pack imported as a zip of the same folder. Section 17 says which of these ships in which release.

## 3. Pack folder layout and manifest

### Layout

The layout is fixed. The loader reads the tree it finds, and every path has to match one of the patterns below.

```
srd-5.1/
  manifest.json
  NOTICE
  classes/barbarian.json
  subclasses/path-of-the-berserker.json
  features/rage.json
  feats/grappler.json
  species/dwarf.json
  subspecies/hill-dwarf.json
  backgrounds/acolyte.json
  resources/rage-uses.json
  tables/hit-die-by-size.json
  spells/fireball.json
  items/longsword.json
  monsters/goblin.json
  conditions/prone.json
  hazards/pit-trap.json
  poisons/basic-poison.json
  afflictions/cackle-fever.json
  affliction-tables/short-term-madness.json
  text/spells/fireball.md
```

The names above show the shape only. Section 9 fixes the list of kind folders and the schema of each kind.

| Id | Rule |
| --- | --- |
| CS-PKG-009 | A pack names exactly one ruleset in its manifest. Every definition in the pack belongs to that ruleset, and the stored `ruleset` column is copied from the manifest. |
| CS-PKG-010 | A definition is one JSON file at `<kind folder>/<local key>.json`, where the local key is the part of the definition's key after the colon (section 4). A file whose name disagrees with its key is rejected. |
| CS-PKG-011 | A file outside the patterns is rejected: an unknown folder, an unknown extension, a second definition in one file, a file at the pack root other than `manifest.json` and `NOTICE`. A rejected stray file is a likely typo, and silently ignoring it would load a pack that is smaller than its author thinks. |
| CS-PKG-012 | The loader ignores a fixed list of operating-system junk (`.DS_Store`, `Thumbs.db`, a top-level `__MACOSX` folder) and leaves it out of the hash. Nothing else is ignored. |
| CS-PKG-013 | Paths use lowercase ASCII letters, digits, hyphen, underscore and dot, with `/` as the separator. Files are UTF-8 without a byte order mark. |
| CS-PKG-014 | `text/` holds long prose as Markdown files that a definition points at (section 6). The folder mirrors the kind folders, so a spell's text sits at `text/spells/<local key>.md`. A text file that no definition points at is rejected. |
| CS-PKG-015 | The manifest does not list files. The loader walks the tree, computes the pack hash (section 4) and builds the file list itself, so the manifest cannot drift from the contents. |
| CS-PKG-016 | An uploaded pack is a zip of the same folder, with either the files at the zip root or one top-level folder holding them. The limits and checks for a zip are in section 16. |

### Manifest

The manifest is one JSON object. Unknown fields are rejected, so a pack written for a newer format fails clearly on an older engine and is never half-read.

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | Yes | The content schema version the pack is written for. The engine lists the versions it reads and rejects any other. |
| `id` | string | Yes | The pack id and the namespace of its keys. Lowercase letters, digits, hyphen and dot, starting with a letter or digit, 2 to 40 characters. Ids that start with `srd-` are reserved for packs the application seeds. |
| `name` | string | Yes | Display name. |
| `version` | string | Yes | The pack's own content version, three numbers separated by dots. |
| `ruleset` | string | Yes | The ruleset the pack is written for. Version 1 of the application reads one ruleset, identified `srd-5.1`. |
| `vocabulary` | integer | Yes | The lowest vocabulary version (section 7) that contains every name the pack uses. |
| `license` | object | Yes | `id` (an SPDX identifier, or `custom`), `url`, and `attributionRequired` (true or false). |
| `attribution` | string | When `attributionRequired` is true | The attribution text. The loader stores it and the application shows it wherever the pack's content appears. |
| `source` | string | No | Where the content comes from, for example the name of the document. |
| `author` | string | No | Who wrote the pack. |
| `description` | string | No | One paragraph for the pack list. |
| `dependencies` | array | No | Packs this one builds on. Each entry has `id` and `minVersion`; the major version must match (section 5). |
| `notes` | string | No | Free text for the author. The engine never reads it. |

| Id | Rule |
| --- | --- |
| CS-PKG-017 | When `attributionRequired` is true, `attribution` has to be non-empty and a `NOTICE` file has to exist in the pack. |
| CS-PKG-018 | A pack that names a `schema` or `vocabulary` the engine does not know is rejected with the version it needs and the version the engine has, so the message says what to upgrade. |
| CS-PKG-019 | A pack whose `id` is already loaded with a different hash and the same `version` is rejected. A changed pack has a new version (section 11). |

An illustrative manifest follows. It shows the shape; the values are not rules.

```json
{
  "schema": 1,
  "id": "srd-5.1",
  "name": "System Reference Document 5.1",
  "version": "1.0.0",
  "ruleset": "srd-5.1",
  "vocabulary": 1,
  "license": {
    "id": "CC-BY-4.0",
    "url": "https://creativecommons.org/licenses/by/4.0/legalcode",
    "attributionRequired": true
  },
  "attribution": "See NOTICE.",
  "source": "System Reference Document 5.1, Wizards of the Coast LLC"
}
```

## 4. Keys, identity, hashes and versions

Three things identify content: a key says which definition, a hash says which exact content, and three version numbers say what the content was written for and how it has changed.

### Keys

A key is `pack:local`, such as `srd-5.1:fireball`. The pack part is the pack id from the manifest. The local part is lowercase letters, digits and single hyphens, up to 64 characters, and never starts or ends with a hyphen.

| Id | Rule |
| --- | --- |
| CS-KEY-001 | A key is unique within its pack and kind. A spell and an item may share a local key, because the kind is known from the folder and from the field that holds the reference. |
| CS-KEY-002 | The pack part of a definition's key has to equal the pack id of the pack it sits in. A pack cannot define a key in another pack's namespace. Taking over another pack's definition is done with `overrides` (section 5), never by claiming its key. |
| CS-KEY-003 | Every reference to another definition is a full key. There is no short form that means "this pack". A full key is longer to type, and in exchange a search for one key finds every use of it, and moving a definition between packs never silently changes what a reference means. |
| CS-KEY-004 | A reference field names the kind it expects. A `spell` field accepts only a spell key, and the validator checks the kind as well as the existence of the target. |
| CS-KEY-005 | In the seeded SRD pack the local key is the slug of the SRD name: lowercase, apostrophes removed, every other run of non-alphanumeric characters turned into one hyphen. The seed build checks this. Other packs choose their own local keys. |
| CS-KEY-006 | A key is permanent. A later version of a pack may drop a definition, and section 11 says what happens to anything that pointed at it. A key is never reused for a different thing; this is an authoring rule and is not mechanically checked. |

### Hashes

A hash must not change when someone reformats a file, saves it with different line endings or reorders the folder listing, and it must change when any content changes. So hashes are computed from parsed content, not from file bytes.

| Id | Rule |
| --- | --- |
| CS-KEY-007 | The parser rejects a JSON object with a duplicate member name, a number that is not an integer, and an integer outside the range of a signed 53-bit value. Fractions are written in integer units (the Ruleset states the unit of each field) or as a string from a fixed enumeration, as with challenge ratings below one. |
| CS-KEY-008 | A definition's hash is SHA-256 over its canonical form: the JSON Canonicalization Scheme (RFC 8785) applied to the definition after every `text/` file it points at has been read in. Text is normalised first: line endings become LF, and trailing whitespace at the end of each line and at the end of the file is removed. The hash is written `sha256:` followed by 64 lowercase hex digits. |
| CS-KEY-009 | A pack's hash is SHA-256 over this input, in order: the canonical form of the manifest; the normalised text of `NOTICE`; then, for each definition sorted by path in byte order, the path, a newline, the definition hash and a newline. The pack hash does not depend on file order, file formatting or timestamps. |
| CS-KEY-010 | The loader stores the pack hash and every definition hash. An open action pins definition hashes, and a checkpoint records each enabled pack with its version and hash (Architecture). |

Canonical form is cheap to compute and has a published specification with test vectors, so the conformance tests (section 19) can fix known hashes for known input.

### Versions

| Number | Where | Says | Changes when |
| --- | --- | --- | --- |
| Content schema version | `schema` in the manifest | Which format revision the pack is written in | This document makes an incompatible change to the format |
| Vocabulary version | `vocabulary` in the manifest | The lowest registry version that has every name the pack uses | The engine gains a mechanic; names are only ever added within a content schema version |
| Pack version | `version` in the manifest | Which edition of the pack's content this is | The author changes any definition |

| Id | Rule |
| --- | --- |
| CS-KEY-011 | The vocabulary version is an integer that rises with every addition to the registry. Within one content schema version no name is removed and no name changes meaning. An engine at vocabulary version M accepts a pack that requires N when N is not greater than M. |
| CS-KEY-012 | The declared vocabulary version gives a clear message when it is too high. It never lets a pack through: every name is checked against the registry whatever the manifest says (CS-VOC-004). |
| CS-KEY-013 | Pack versions have three numbers. The engine does not judge whether a change deserves a major, minor or patch bump. It enforces two things only: a version is never reused with different content (CS-PKG-019), and a dependency's major version has to match (section 5). |
| CS-KEY-014 | Pinning uses hashes, not version numbers, so a checkpoint reproduces exactly the content it was made with even if a pack author mislabels a version. |

## 5. Resolution, dependencies and overrides

This section says how a campaign turns a list of enabled packs into one set of definitions, and how a pack replaces something another pack defines. Nothing is replaced by accident. A definition takes over another only when it says so in an `overrides` field, and the replacement is of the whole definition, never of single fields. Both are decisions of the owner (CD-2).

### Dependencies

| Id | Rule |
| --- | --- |
| CS-OVR-001 | A pack lists in its manifest every other pack whose keys it references or overrides. Dependencies form no cycle; a cycle rejects the pack. |
| CS-OVR-002 | A definition may reference keys of its own pack and of the packs in its direct `dependencies`, and no others. A reference that reaches a pack it did not declare is rejected, even when that pack happens to be loaded. |
| CS-OVR-003 | A dependency is satisfied by a loaded version of that pack with the same major version as the entry's `minVersion` and a version not lower than it. |
| CS-OVR-004 | A campaign enables one version of a pack, and enabling a pack requires its dependencies to be enabled already and earlier in the campaign's pack order. Enabling two versions of the same pack id is refused. |
| CS-OVR-005 | A pack is validated when it is loaded, against the exact loaded versions of its dependencies, independently of any campaign. When a dependency gains a new version, the packs that depend on it are checked again before a campaign can move to it (section 11). |

### Overrides

A definition gets an optional `overrides` field holding the full key of the definition it replaces. The overriding definition has its own key in its own pack. It is a complete, ordinary definition: it passes the same schema checks as any other and carries no reference to the one it replaces except this field.

| Id | Rule |
| --- | --- |
| CS-OVR-006 | The target of `overrides` has to exist in a direct dependency of the overriding pack, and its kind has to equal the kind of the overriding definition. An override of the pack's own definitions is rejected, because the author can edit those directly. |
| CS-OVR-007 | Replacement is of the whole definition. There is no patch syntax, no merge and no inheritance from the replaced definition. Anything the replacement should keep, it states again. This keeps every definition readable on its own and keeps the hash of a definition a complete statement of its content. |
| CS-OVR-008 | A key that a pack claims in another pack's namespace is an error (CS-KEY-002). A takeover is only possible through `overrides`. |
| CS-OVR-009 | Definitions with the same kind and name in different packs coexist, each under its own key. The pack list shows a warning that lists them, and the pickers show the pack next to the name. This is not an error, because two unrelated packs may both define a common item. |

### Resolution

Resolution happens when a campaign's content set is built, and it never alters a stored definition. The result is an index from every key in the enabled packs to the definition that currently answers for it.

1. Take the enabled packs in the campaign's pack order.
2. For each key, collect the definitions in enabled packs whose `overrides` names that key.
3. If there are none, the key answers for itself.
4. If there are several, the one from the pack latest in the pack order wins, and the pack list shows the conflict so the owner knows a definition was shadowed.
5. Repeat from step 2 on the winner's key, so that a chain of overrides ends at the last one enabled. Dependencies are acyclic and a pack comes after its dependencies, so the chain terminates.

| Id | Rule |
| --- | --- |
| CS-OVR-010 | Every reference to a key, whether in a definition, in a character, in an item instance or in a monster entry, goes through the resolution index when it is read. The stored reference keeps the original key, so removing the overriding pack restores the original behaviour with nothing to repair. |
| CS-OVR-011 | Because the replacement has the same kind, a reference that was well typed before resolution is well typed after it. The validator does not need to re-check kinds when the index changes. |
| CS-OVR-012 | A change to the enabled packs, their order or their versions applies from the next action, never inside an open action (the open action pins definition hashes, CS-KEY-010). The change is logged like a setting, with who made it and what the resolution changed. |
| CS-OVR-013 | A campaign has no switch for a single override. To drop one, the owner disables the pack that holds it, or splits the pack. A per-definition switch would add a second place where resolution can differ from what the pack list says. |

What the owner sees when enabling a pack is part of the UX and API Specification. This document fixes what the engine can report: the list of overrides the pack would add, the keys that would be shadowed, the same-name warnings, and the packs that would be affected by a dependency change.

## 6. The definition envelope and text

Every definition file is one JSON object. Some fields are the same for every kind and form the envelope. The rest are the kind's own fields (section 9). Both sit at the top level of the object, in one flat record, so an author reads a single file and sees everything about the definition.

### Envelope fields

| Field | Authored | Required | Meaning |
| --- | --- | --- | --- |
| `$schema` | Yes | No | A link to the published schema of the kind, for editor completion. The engine ignores it. |
| `kind` | Yes | Yes | The kind of definition. It has to equal the kind folder the file sits in. |
| `key` | Yes | Yes | The full key (section 4). |
| `name` | Yes | Yes | The display name, 1 to 120 characters. In the SRD pack it is the SRD name, unedited. |
| `overrides` | Yes | No | The full key of the definition this one replaces (section 5). |
| `source` | Yes | No | Where in the pack's source this definition comes from, such as a section or page reference. When absent, the manifest `source` applies. |
| `notes` | Yes | No | A list of short texts from the pack author, shown beside the definition and never merged into its text. |
| `pack`, `packVersion`, `ruleset`, `license`, `attribution`, `hash` | No | No | Set by the loader from the manifest and from the computed hash. A file that contains any of them is rejected. |

The loader-set fields are what the Architecture means by saying every definition carries its pack, ruleset, source, license and attribution. An author cannot set them, because a definition must not claim a licence its manifest does not give.

| Id | Rule |
| --- | --- |
| CS-ENV-001 | Each kind has one JSON Schema (2020-12) that combines the envelope schema with the kind's own fields and forbids every other field (`unevaluatedProperties: false`). An unknown field rejects the pack. A typo in a field name must never turn into a definition that quietly lacks a feature. |
| CS-ENV-002 | A kind's required fields are required because the engine cannot run the definition without them. A field with a sensible empty meaning (no resistances, no components) is optional and its absence means the empty value, written down in section 9. |
| CS-ENV-003 | Names of fields are lower camel case. Effect types, condition parts and enumeration values are lower snake case, so a reader can tell a field from a value at a glance. Trigger points, duration kinds and modifier operations keep the capitals the Ruleset gives them (section 7). |
| CS-ENV-004 | The engine never reads `name`, `notes` or any text field to decide a rule. Behaviour comes from structured fields only, as the Ruleset requires of items (section 11.2). |

### Common value types

The kind schemas build on a short list of types, defined once.

| Type | Written as | Rule |
| --- | --- | --- |
| `Key` | string | A full key (section 4). |
| `Ref<kind>` | string | A full key whose target has to be of that kind and has to be reachable under CS-OVR-002. |
| `Text` | string or `{ "file": "text/..." }` | Prose, as described below. |
| `Dice` | string | A dice expression in the form the engine's dice parser reads. The grammar of section 8 extends it. |
| `Expr` | string | A formula in the expression grammar of section 8. |
| `Enum<name>` | string | A value from a named vocabulary list (section 7). |
| `Int` | integer | A whole number, with the range the kind schema states. |
| Quantities | integer | In the unit the Ruleset states for the field. Item cost is in copper pieces and item weight in hundredths of a pound, for example. A field never holds a fraction (CS-KEY-007). |

### Text

| Id | Rule |
| --- | --- |
| CS-ENV-005 | A `Text` field holds prose either inline, as a JSON string, or as `{ "file": "text/<kind folder>/<local key>.md" }`. Long SRD descriptions go in files, because a JSON string with escaped line breaks is hard to review and compare. The loader reads the file in and the definition hash is computed on the result (CS-KEY-008). |
| CS-ENV-006 | Text is plain paragraphs plus a small set of markup: emphasis, bold, bulleted and numbered lists, and tables written with pipes. Raw HTML, images, links and headings are not part of the subset. The application renders text with its own small converter for exactly this subset, escapes everything else, and so no text can put markup or script into a page. |
| CS-ENV-007 | Text fields have a length limit (section 16) and may not contain control characters other than line feed and tab. |
| CS-ENV-008 | In the SRD pack, a text field holds the SRD wording unedited (CS-PKG-004). Structured fields next to it hold the mechanics, written by us. A correction or a clarification goes in `notes`, so the SRD text stays checkable against its source and the change is visible as ours, as the licence requires. |
| CS-ENV-009 | Homebrew and third-party packs may word their text as they like. The rules above on markup and length still hold. |

### An overriding definition

An illustration of the envelope of a homebrew spell that replaces an SRD spell. The kind's own fields (section 9) complete the object. The values are not rules.

```json
{
  "kind": "spell",
  "key": "homebrew-aria:fireball",
  "name": "Fireball",
  "overrides": "srd-5.1:fireball",
  "notes": ["Reduced to a 15-foot radius for this campaign."]
}
```

## 7. The vocabulary registry

The vocabulary is the list of names a definition may use to talk to the engine: effect types, trigger points, durations, modifier operations, stat keys, variables and the fixed enumerations. It lives in one registry in Java. Every other place that needs the list, the JSON Schema included, reads a file generated from that registry (section 10). A name that is not in the registry cannot appear in a pack. This is the whole of the hardcoded surface that content touches (CS-PKG-002, CS-PKG-003).

### Rules

| Id | Rule |
| --- | --- |
| CS-VOC-001 | The registry holds entries of the form category, name, the vocabulary version that introduced it, the schema of its parameters, and the Java handler or evaluator that gives it meaning. An entry without a handler cannot be registered. |
| CS-VOC-002 | Adding a mechanic is a Java handler, a registry entry and a conformance test (section 19). It needs no change to the pack format, to the loader or to any existing pack. |
| CS-VOC-003 | Names keep the spelling the Ruleset already gives them. Trigger points, duration kinds and modifier operations are in capitals (`ON_CAST`, `UNTIL_TURN_END`, `MULTIPLY`). Effect types, condition parts, enumeration values and flags are lower snake case (`spell_attack`, `piercing`). Stat keys and variables use the colon forms of section 8. |
| CS-VOC-004 | A name that is not in the registry rejects the whole pack with `CONTENT_INVALID`, and the error names every unknown name and the file it was found in. The manifest's `vocabulary` number never excuses an unknown name (CS-KEY-012). This is the owner's decision (CD-6). |
| CS-VOC-005 | The older aliases of trigger points (`ON_ATTACK_ROLL`, `ON_DAMAGE_ROLL`, `ON_SAVE`, `ON_MOVE`, `ON_DAMAGE_TAKEN`) are not in the registry. Content uses the canonical names only, as the Ruleset requires (E-68). |
| CS-VOC-006 | A name is never removed or given a new meaning within one content schema version (CS-KEY-011). A mistake is corrected by adding a new name and marking the old one deprecated. A deprecated name still loads and the pack list shows a warning. |
| CS-VOC-007 | The registry contains no content. It names mechanics, never spells, items or monsters. |
| CS-VOC-008 | Each effect type declares the schema of its parameters in the registry, and the generated export carries it. A definition that passes a parameter the type does not declare, or omits a required one, is invalid. |

### Effect types

One list serves spells, features, items, conditions and hazards, so that the same behaviour has one name wherever it appears. The first group is the tier-2 spell primitives of the Ruleset with the Part H additions. The second group is the feature and item types the Ruleset names. The third group gives the provisional item names of the Ruleset their final form. The Architecture's feature type names `add_modifier`, `apply_condition` and `grant_reaction` describe the same behaviours as `modifier`, `condition` and `reaction_option`. The registry holds one name for each behaviour, the spell primitive name, and does not register the other three (CD-11). The same holds for four labels of the Part D catalog: `attack_bonus`, `ac_bonus`, `damage_bonus` and `crit_threshold` are plain stat changes, and the registry stores them as `modifier` on the stat keys `attack:<kind>`, `ac`, `damage:<kind>` and `crit_threshold`. The rest of the catalog keeps its Ruleset names.

| Group | Names |
| --- | --- |
| Spell primitives | `spell_attack`, `save`, `damage`, `heal`, `condition`, `modifier`, `hp_pool`, `temp_hp`, `forced_movement`, `teleport`, `zone`, `remove_condition`, `revive`, `next_hit_rider`, `dispel`, `counter`, `reaction_option`, `start_fall`, `ends_affliction`, `suppress_affliction` |
| Feature types | `add_damage_dice`, `damage_die_reroll`, `grant_action`, `reduce_damage`, `roll_bonus_die`, `roll_penalty_die`, `aura`, `condition_immunity`, `turned`, `grant_advantage`, `grant_resistance`, `proficiency_fraction`, `roll_floor`, `save_outcome`, `rest_option`, `spend_resource` |
| Hazard and affliction types | The hazard and affliction records of Part H are built from the types above and from the trigger kinds below. They add no type of their own. |

The Ruleset gives the provisional names of items in capitals. They become these entries.

| Provisional name | Becomes |
| --- | --- |
| `AC_MODIFIER` | `modifier` on the stat key `ac`, operation `ADD` |
| `ATTACK_BONUS`, `DAMAGE_BONUS`, `SAVE_BONUS` | `modifier` on the stat key `attack:<kind>`, `damage:<kind>` or `save:<ability>`, operation `ADD` |
| `EXTRA_DAMAGE_DICE` | `add_damage_dice` |
| `ABILITY_FLOOR` | `modifier` on an ability stat key, operation `MAX` (the Ruleset treats a score set only when higher as a maximum, RS-MOD-003) |
| `HEAL_DICE` | `heal` |
| `CHARGES` | The item field `charges` (maximum formula and recharge rule) together with `spend_resource` |
| `CONTAINER_WEIGHT` | The item field `container`, which is data about the item and not an effect |

The condition table of the Ruleset (RS-CND-003) makes conditions data: bundles of modifiers, advantage sources, forced outcomes, action restrictions and flags. Their building blocks are condition parts.

| Condition part | Meaning |
| --- | --- |
| `flag` | Sets a named flag: `incapacitated`, `cannot_move`, `cannot_speak`, `unaware` and the like |
| `advantage_source` | Advantage or disadvantage on a roll kind, with a direction (the holder rolls, or is rolled against), a distance predicate and a line-of-sight predicate |
| `forced_outcome` | An automatic failure of a save or check kind, or an automatic critical hit from an attacker within a distance |
| `restriction` | A limit on actions, reactions, movement or targeting |
| `modifier` | A stat modifier, as above |
| `grants_resistance` | Resistance to named damage types or to all of them |
| `drops_items`, `applies_condition` | Side effects when the condition starts |

### Trigger points

| Kind | Names |
| --- | --- |
| Reaction window points (RS-REA-001) | `ON_MOVE_STEP`, `BEFORE_ATTACK_ROLL`, `AFTER_ATTACK_ROLL`, `AFTER_HIT`, `AFTER_DAMAGE_ROLL`, `AFTER_SAVE_ROLL`, `AFTER_SAVE_FAIL`, `AFTER_CHECK_ROLL`, `ON_CAST`, `AFTER_DAMAGE_TAKEN`, `ON_READIED_TRIGGER`, `ON_TURN_END` |
| Other feature triggers | `ON_DAWN` (RS-CLK-006) and `START_OF_OWN_TURN`, the start of a creature's own turn (PB-G6; Part D calls it ON\_TURN\_START in RS-FTR-008, and the Ruleset is corrected to the one name). A rest has no trigger point. It offers choices through the rest command, and the registry names them `rest_option` (RS-RST-020, RS-RST-021) |
| Part H | `ON_FALL`, available from version 1.5 |
| Hazard trigger kinds | `STEP_ON`, `ENTER_AREA`, `START_TURN_IN_AREA`, `ACTION`, `DM` |
| Recharge triggers | `SHORT_REST`, `LONG_REST`, `DAWN`, `ROLL_INITIATIVE`, `START_OF_OWN_TURN` (the monster recharge adds a die and a threshold, PB-G6) and `MANUAL` (RS-RES-002) |

### Durations, operations and rounding

| Category | Names |
| --- | --- |
| Duration kinds | `INSTANT`, `ROUNDS(amount)`, `CLOCK(amount, seconds)`, `CONCENTRATION(max)`, `UNTIL_TURN_START`, `UNTIL_TURN_END` (both for a named creature), `UNTIL_DISPELLED`, `UNTIL_SHORT_OR_LONG_REST`, `UNTIL_LONG_REST`, `UNTIL_USED`, `PERMANENT`, `WHILE_EQUIPPED`, `WHILE_ATTUNED` (RS-MOD-004). Store absolute ends or end conditions, never counters; `INSTANT` is never stored as active. |
| Modifier operations | `SET`, `ADD`, `MULTIPLY`, `MIN`, `MAX`; resolve same-key stacking first, then the newest `SET` across keys, followed by `ADD`, exact `MULTIPLY` with one `floorDiv`, `MIN`, `MAX` (RS-MOD-002, RS-MOD-003) |
| Rounding modes | `down` (the default, RS-CNV-001), `nearest_half_up` (used for ammunition recovery, E-102) |

### Enumerations and stat keys

The fixed lists below are closed because the engine's rules refer to them by name. A pack picks from them and cannot extend them.

| List | Source in the Ruleset |
| --- | --- |
| Damage types: acid, bludgeoning, cold, fire, force, lightning, necrotic, piercing, poison, psychic, radiant, slashing, thunder | The damage rules |
| Abilities and skills, with the ability each skill uses | Part A |
| Spell schools, spell components (V, S, M), casting time kinds, range and area shapes | Part E |
| Item categories, weapon properties, armor categories, rarity | Part F |
| Creature sizes, creature types, alignments, speed kinds, senses | Part G |
| Roll kinds: attack, save, check, damage, initiative, death save | Parts A and B |

A stat key names one computed value that a modifier can change. The forms are `ac`, `speed`, `hp_max`, `ability:<ability>`, `skill:<skill>`, `skill:<skill>:prof_multiplier`, `save:<ability>`, `attack:<kind>`, `damage:<kind>`, `crit_threshold`, `attacks_per_action` (the number of attacks of the Attack action, RS-FTR-004) and `jump_distance` (RS-FTR-007). Proficiency effects like expertise target `skill:stealth:prof_multiplier` or the corresponding skill key so they only affect the proficiency component. Integer stats can also have their own `MULTIPLY` operands under RS-MOD-003. The list is closed. A new key is a registry entry with a handler (CS-VOC-002), and a key the registry lacks rejects the pack (CS-VOC-004).

**Modifier values.** A modifier value is a whole number or an `n/d` string parsed as an exact normalized `Ratio` with a positive denominator; floating-point values are invalid. On integer stats, `SET`, `ADD`, `MIN` and `MAX` require a whole-number value. Only `skill:<skill>:prof_multiplier` is ratio-valued and is not rounded; its proficiency component is `floor(proficiency bonus x multiplier)`. A numerator or denominator above 1,000 is rejected when the pack is loaded.

### What this section leaves to the Ruleset

The lists above were compared with the Ruleset section by section before this document was accepted (V-10a). Where the Ruleset leaves a name open, this document gives it. Four labels of the Part D catalog (`attack_bonus`, `ac_bonus`, `damage_bonus`, `crit_threshold`) are stored as `modifier`, the rest of that catalog keeps its names, `START_OF_OWN_TURN` is the one name for the start of a creature's own turn, and a rest has no trigger point. The predicates of Part D are the variables of section 8. A stat key, variable or name that a later rule needs is added by a registry entry and a handler (CS-VOC-002). A seeded definition that uses a name the registry lacks fails the build (CS-VOC-004, T-108).

## 8. The expression grammar

Content needs formulas: a spell's damage that grows with the slot level, a resource's maximum that follows a class level, a monster's average hit points, a condition under which a feature applies. A formula in a pack is a string in one small grammar. It extends the dice grammar of the Ruleset (Part A, the dice notation and the `DiceExpression` parser) so that every dice string already valid keeps its meaning. It adds arithmetic, comparisons, a closed set of variables and four functions. It has no loops, no recursion, no assignment and no user-defined functions, so evaluating it always ends, in a number of steps proportional to its length. This is the owner's decision (CD-3). It keeps the hardcoded part of the simulator to the evaluator and the registry of variables, and nothing about any particular spell or feature.

### Grammar

```
expression  = disjunction ;
disjunction = conjunction { 'or' conjunction } ;
conjunction = negation { 'and' negation } ;
negation    = [ 'not' ] comparison ;
comparison  = sum [ ( '==' | '!=' | '<' | '<=' | '>' | '>=' ) sum ] ;
sum         = product { ( '+' | '-' ) product } ;
product     = unary { ( '*' | '/' ) unary } ;
unary       = [ '-' ] primary ;
primary     = integer | dice-group | variable | call | name | '(' expression ')' ;
call        = function '(' [ expression { ',' expression } ] ')' ;
function    = 'min' | 'max' | 'if' | 'round_div' | 'lookup' ;
dice-group  = [ count ] 'd' size { dice-modifier } ;
count       = integer | variable | '(' expression ')' ;
size        = integer | variable | '(' expression ')' ;
variable    = identifier [ ':' identifier ] ;
name        = "'" identifier "'" ;
```

The dice modifiers (`kh`, `kl`, `dh`, `dl`, `r<=`, `min`) and the validity limits on counts and sizes are those of the dice notation in the Ruleset, unchanged. In a dice group the modifier `min` is followed directly by digits. A call to the function `min` is followed by an opening parenthesis. The two never meet.

### Types

Every expression has one type, found by the validator before the pack loads.

| Type | What it is | Where it may be used |
| --- | --- | --- |
| `int` | A whole number computed without any die | Everywhere, including as the count or size of a dice group |
| `roll` | A sum of dice groups and `int` terms | Fields that roll: damage, healing, a hit point total |
| `bool` | A truth value from a comparison or from `and`, `or`, `not` | Predicate fields such as `activeWhen` |
| `name` | An enumeration value in quotes, such as `'large'` | Compared with a variable of the same enumeration, and as an argument of `lookup` |

| Id | Rule |
| --- | --- |
| CS-EXP-001 | Every string valid in the dice grammar of the Ruleset has the same meaning here, and its canonical form is unchanged. The existing dice tests are run again against this parser (T-103). |
| CS-EXP-002 | A `roll` may combine dice groups only with `+` and `-` and with `int` terms. Multiplying, dividing or comparing a dice group is rejected when the pack loads. A formula such as `8d6 + (slot_level - 3)d6` is valid. `(8d6) * 2` is not, because doubling dice on a critical hit is a rule of the pipeline and not of a formula. |
| CS-EXP-003 | `/` is floor division, as the Ruleset requires for anything that can be negative (RS-CNV-001, RS-CNV-002). `round_div(a, b)` divides to the nearest whole number with a half rounding up, which is the one other mode the Ruleset uses (E-102). It is computed as floor division of `2a + b` by `2b`, and `b` must be greater than zero. No formula has an implicit rounding mode: a reader sees which of the two applies from the operator or the function. |
| CS-EXP-004 | The functions are exactly `min(a, b, ...)`, `max(a, b, ...)`, `if(condition, then, else)`, `round_div(a, b)` and `lookup(table, index[, column])`. A new function is a registry entry and a handler (CS-VOC-002), never a feature of a pack. |
| CS-EXP-005 | `lookup` reads a rule table (section 9). Its first argument is a quoted full key of a `ruleTable`, checked like any reference (CS-KEY-004, CS-OVR-002). Its second argument is an `int` or a `name` that selects a row, and an optional third argument names a column. Without it the table must have one column. A table value is an integer, or dice text where the table says so. |
| CS-EXP-006 | A variable is a name from the registry (section 7), with an optional qualifier after a colon taken from an enumeration of the registry, such as `mod:str` or `weapon:finesse`. The registry states for each variable its type and the contexts in which it exists. A variable used where its context does not provide it, or one that is not registered, rejects the pack (CS-VOC-004). |
| CS-EXP-007 | Arithmetic is exact integer arithmetic. An overflow, a division by zero or a `round_div` by a value that is not positive stops the action with the stable error `EXPRESSION_ERROR` and an entry in the log. There is no floating point anywhere. |
| CS-EXP-008 | A formula may be at most 256 characters, nested at most 16 deep, and call at most 8 functions. The limits are constants in one place (section 16). |
| CS-EXP-009 | A `roll` is evaluated by rolling its dice groups from left to right through the injected `RandomSource`, so a seeded run reproduces. An `int` or `bool` expression never rolls. The operators `and` and `or` stop early, and since a `bool` contains no dice, stopping early cannot change the sequence of random numbers. |
| CS-EXP-010 | The loader evaluates every formula once over a small grid of sample contexts (levels, slot levels and ability scores across their legal ranges) and rejects the pack if any sample raises `EXPRESSION_ERROR`. This finds a division by a variable that can be zero before play does. |
| CS-EXP-011 | The evaluator and the registry of variables are pure Java with no dependency beyond the JDK, inside the pure-Java boundary of the engine that test T-100 enforces. |

### Variables

The initial variables follow the concepts the Ruleset already names. The registry is the authority and this table is a reading aid.

| Variable | Type | Context | Meaning |
| --- | --- | --- | --- |
| `level` | int | Character | Character level |
| `class_level` | int | A class or subclass feature | Level in the class that owns the feature |
| `prof` | int | Character | Proficiency bonus |
| `score:<ability>`, `mod:<ability>` | int | Character and monster | An ability score and its modifier |
| `spell_mod`, `spell_dc`, `spell_attack_bonus` | int | Spell | Values of the caster |
| `slot_level`, `spell_level` | int | Spell | The level of the slot used, and the spell's own level |
| `hp`, `hp_max` | int | Character and monster | Current and maximum hit points |
| `resource:<key>` | int | Feature | Current amount of a resource |
| `weapon:<property>` | bool | Attack | Whether the weapon in use has the property |
| `attack_is_ranged`, `has_advantage` | bool | Attack | Facts about the roll in progress |
| `size`, `creature_type`, `damage_type` | name | Monster, effect | The enumeration value in play |

The predicates of Part D (9.3 of the Ruleset) add these variables. `attack_is_melee` and `attack_is_weapon` are bool in an attack (a spell attack is not a weapon attack, RS-FTR-006). `has_disadvantage` is bool in an attack (RS-ROG-002). `weapon_hands` is an int, the number of hands holding the weapon, and `other_weapon_held`, `shield_wielded` and `armor_worn` are bool (RS-FTR-001). `enemy_of_target_adjacent` is bool, true when another enemy of the target stands within 5 feet of it and is not incapacitated (RS-ROG-002). `turn_token:<key>` is bool, true while a once-per-turn use is unspent (E-50). `moved_at_most_half_speed` is bool (RS-ROG-008). In an aura, `source_conscious` is bool and `distance_to_source` is an int in feet (RS-PAL-011). `creature_type` also exists for the target of an attack (RS-PAL-005).

### Illustrations

These show how the grammar reads. The tables and numbers they name are not rules.

| Use | Formula |
| --- | --- |
| Spell damage that grows by slot level | `8d6 + (slot_level - 3)d6` |
| A resource maximum from a class table | `lookup('srd-5.1:rage-uses', class_level)` |
| Cantrip dice that grow with level | `lookup('srd-5.1:cantrip-scaling', level)d10` |
| A monster's printed average hit points | `(3 * (8 + 1)) / 2` for `3d8`, rounded down as the SRD prints it |
| Half of the ammunition expended | `round_div(expended, 2)` |
| A feature that needs a finesse weapon or a ranged attack | `weapon:finesse or attack_is_ranged` |

## 9. Definition schemas by kind

This section lists the fields of each kind. What a field means in play is the Ruleset's business, and the section that states it is named in each table. Here only the shape is fixed: the name, the type, whether it is required. Every kind also has the envelope of section 6. The field names were accepted with this document.

### Kinds and folders

| Kind | Folder | What it defines | Ruleset |
| --- | --- | --- | --- |
| `class` | `classes/` | A class, with its level table | 9.1 |
| `subclass` | `subclasses/` | A subclass of one class | 9.1 |
| `species` | `species/` | A species with its traits | 3.1 (score bonuses only) |
| `subspecies` | `subspecies/` | A variant of one species | 3.1 (score bonuses only) |
| `background` | `backgrounds/` | A background | 3.2 (duplicate proficiencies, E-22) |
| `feature` | `features/` | One named capability: a class feature, a trait, a magic item power | 9.3 |
| `feat` | `feats/` | A feat, with prerequisite and choices | DEC-073 |
| `resource` | `resources/` | A counted resource with a maximum and recharge | 8.3 (RS-RES-001) |
| `ruleTable` | `tables/` | A lookup table indexed by level or by name | 9.2, 12.3 |
| `condition` | `conditions/` | A condition as a bundle of parts | 7.8 |
| `spell` | `spells/` | A spell | 10.2 |
| `item` | `items/` | An item definition | 11.2 |
| `monster` | `monsters/` | A stat block | 12.2 |
| `hazard` | `hazards/` | A trap or environmental hazard | 13.7 |
| `poison` | `poisons/` | A poison | 13.7 |
| `affliction` | `afflictions/` | A madness effect, a disease or a lasting poison | 13.8 |
| `afflictionTable` | `affliction-tables/` | A d100 table of afflictions | 13.8 |

| Id | Rule |
| --- | --- |
| CS-DEF-001 | The kinds above are the complete list for version 1 of the schema. A new kind is a change to this document and a new content schema version. |
| CS-DEF-002 | Packs hold definitions only. Characters, encounters, item instances, the clock, the log and every other piece of play state are database state and are never in a pack. The instance fields of the Ruleset (owner, location, quantity, equipped, attuned, identified and the rest) are not content. |
| CS-DEF-003 | An `Effect` is an object with a `type` from the effect types of section 7 and the parameters that the type declares. Every list called `effects` below holds such objects, and the registry validates each one (CS-VOC-008). |
| CS-DEF-004 | A required list is non-empty. An optional list that is absent means the empty list. |

### Shared structures

| Structure | Fields |
| --- | --- |
| `Trigger` | `point` (a trigger point), `when` (an optional `bool` formula) |
| `Cost` | `action` (`action`, `bonus_action`, `reaction` or `none`), `resource` (optional `Ref<resource>`), `amount` (an `int` formula, default 1) |
| `Limit` | `per` (`turn`, `round`, `short_rest` or `long_rest`), `uses` (an `int` formula, default 1) |
| `Duration` | `kind`; `amount` for `ROUNDS` and `CLOCK`; `seconds` as the `CLOCK` unit; `max` for `CONCENTRATION`; the named creature for turn-end kinds. The runtime resolves round and clock inputs to absolute ends, and turn kinds to an absolute end round plus creature. |
| `Save` | `ability`, `dc` (an `int` formula), `onSuccess` (`none` or `half`) |
| `Area` | `shape` (an area shape), `size` (feet) |
| `Choice` | `id`, `kind` (`ability`, `skill`, `tool`, `language`, `feature`), `count`, `from` (a list of values or keys) |

### Character kinds

| Kind | Field | Type | Required | Meaning |
| --- | --- | --- | --- | --- |
| `feature` | `text` | `Text` | Yes | The description |
|  | `trigger` | `Trigger` | No | When it runs. Absent for a passive feature |
|  | `activeWhen` | `bool` formula | No | Whether it applies at all |
|  | `effects` | `Effect[]` | Yes | What it does |
|  | `cost` | `Cost` | No | What using it costs |
|  | `limit` | `Limit` | No | How often it can run |
|  | `choices` | `Choice[]` | No | Picks the character makes when it is gained |
| `class` | `text` | `Text` | Yes | The description |
|  | `hitDie` | `Enum<hit_die>` | Yes | The class hit die |
|  | `savingThrows` | `Enum<ability>[]` | Yes | Proficient saves |
|  | `proficiencies` | object | No | Armor, weapons, tools and `skillChoice` (`count`, `from`) |
|  | `spellcasting` | object | No | `ability`, `progression` (`full`, `half`, `third`, `pact`, `none`), `preparation` (`known` or `prepared`), `slots` (`Ref<ruleTable>`), `ritualCasting` |
|  | `resources` | `Ref<resource>[]` | No | Resources the class owns |
|  | `advancement` | `Ref<ruleTable>` | No | A table of per-level values the class features read |
|  | `levels` | `Level[]` | Yes | One entry per level, level 1 upward with no gaps. Each has `level`, `features` (`Ref<feature>[]`), and `grants` (`asi`, `subclass`) |
| `subclass` | `text`, `levels` | as `class` | Yes | `levels` lists only the levels at which the subclass adds something |
|  | `class` | `Ref<class>` | Yes | The class it belongs to |
|  | `spellcasting` | object | No | As in `class` |
| `species` | `text` | `Text` | Yes | The description |
|  | `size` | `Enum<size>` | Yes |  |
|  | `speed` | `Int` | Yes | In feet |
|  | `abilityBonuses` | list | No | `{ability, amount}` entries, or a `Choice` |
|  | `languages`, `features` | list | No | Languages and `Ref<feature>[]` |
| `subspecies` | `species` | `Ref<species>` | Yes | The species it varies |
|  | `text`, `abilityBonuses`, `features` | as `species` |  |  |
| `background` | `text` | `Text` | Yes |  |
|  | `skills`, `tools`, `languages` | lists and `Choice` | No | Proficiencies granted |
|  | `equipment` | `Text` | No |  |
|  | `features` | `Ref<feature>[]` | No |  |
| `feat` | `text` | `Text` | Yes |  |
|  | `prerequisite` | `bool` formula | No | Over character variables, such as `score:str >= 13` (DEC-073) |
|  | `choices` | `Choice[]` | No |  |
|  | `features`, `effects` | `Ref<feature>[]`, `Effect[]` | No | At least one of the two |

### Rules, resources and conditions

| Kind | Field | Type | Required | Meaning |
| --- | --- | --- | --- | --- |
| `resource` | `max` | `int` formula | Yes | The maximum |
|  | `recharge` | list | No | Entries of `on` (a recharge trigger) and `restore` (an `int` formula, or `all`) |
|  | `text` | `Text` | No |  |
| `ruleTable` | `index` | `level` or `name` | Yes | What selects a row |
|  | `columns` | list | Yes | Entries of `name` and `type` (`int`, `dice`, `name`) |
|  | `rows` | list | Yes | Each row has the index value and one value per column |
| `condition` | `text` | `Text` | Yes |  |
|  | `parts` | list | Yes | Condition parts of section 7, each with its parameters |
|  | `levels` | integer | No | For a condition that has levels, as exhaustion does (RS-CND-010) |

### Spell

Fields follow the Ruleset (10.2).

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `text` | `Text` | Yes | The description |
| `higherLevel` | `Text` | No | The text for higher slot levels |
| `level` | `Int` | Yes | 0 for a cantrip |
| `school` | `Enum<school>` | Yes |  |
| `castingTime` | object | Yes | `kind` (`action`, `bonus_action`, `reaction`, `minutes`, `hours`), `amount`, `reactionTrigger` (`Text`) |
| `ritual` | bool | No |  |
| `range` | object | Yes | `kind` (`self`, `touch`, `ranged`, `sight`, `unlimited`) and `feet` |
| `area` | `Area` | No |  |
| `components` | object | Yes | `verbal`, `somatic`, `material` (`text`, `cost` in copper pieces, `consumed`) |
| `duration` | `Duration` | Yes | With a `concentration` flag |
| `classes` | `Ref<class>[]` | Yes | The classes that can cast it |
| `tier` | `0`, `1` or `2` | Yes | How much the engine resolves |
| `rolls` | list | Tier 1 | Entries of `label` and a `roll` formula |
| `effects` | `Effect[]` | Tier 2 | The effect list |
| `reaction` | object | No | `point`, `cost` (`Cost`), `filter` (a `bool` formula), for a spell offered in a reaction window (10.8) |

### Item

Fields follow the definition half of the Ruleset (11.2).

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `text` | `Text` | Yes |  |
| `category` | `Enum<item_category>` | Yes |  |
| `cost` | `Int` | Yes | In copper pieces |
| `weight` | `Int` | Yes | In hundredths of a pound |
| `tier` | `0`, `1` or `2` | Yes |  |
| `rarity` | `Enum<rarity>` | No |  |
| `requiresAttunement` | bool | No |  |
| `weaponData` | object | Weapons | `damage` (a `roll` formula), `damageType`, `properties`, `range`, `versatileDamage` |
| `armorData` | object | Armor | `baseAc`, `dexterity` (`full`, `capped` or `none`), `stealthDisadvantage`, `strengthRequired` |
| `effects` | `Effect[]` | Tier 2 | Each with `appliesWhen` (`carried`, `equipped` or `attuned`) |
| `charges` | object | No | `max` (an `int` formula) and `recharge`, as for a resource |
| `container` | object | No | `capacity` in hundredths of a pound |

An item may also carry `unidentified`, an optional object with a `name` and a `text`. It is what a player sees before the item is identified (section 15).

### Monster

Fields follow the stat block of the Ruleset (12.2).

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `size`, `type`, `alignment` | enumerations | Yes | `tags` is an optional list |
| `ac` | object | Yes | `value` and `source` (`Text`) |
| `hp` | object | Yes | `dice` (a `roll` formula) and `average` (`Int`, as the stat block prints it) |
| `speeds` | object | Yes | `walk`, `fly`, `swim`, `climb`, `burrow` in feet, and `hover` |
| `abilities` | object | Yes | The six scores |
| `saves`, `skills` | objects | No | Bonuses, as printed |
| `damageModifiers` | list | No | Entries of `kind` (`vulnerability`, `resistance`, `immunity`), `types`, and an optional `unless` (`magical`, `silvered`, `adamantine`) |
| `conditionImmunities` | `Ref<condition>[]` | No |  |
| `senses` | object | No | Each sense in feet, and `passivePerception` |
| `languages` | `Text` | No |  |
| `challenge` | object | Yes | `cr` (a value from the challenge rating list), `xp`, `proficiencyBonus` |
| `traits`, `actions`, `bonusActions`, `reactions` | `ActionDef[]` | No |  |
| `legendaryActions` | object | No | `uses` and `entries` (`ActionDef[]`, each with a `cost`) |
| `lairActions`, `regionalEffects` | `Text` | No | Text only in version 1 |

An `ActionDef` has `name`, `text` (the printed description), and the structured part: `attack` (`kind`, `bonus`, `reach` or `range`, and `hits`, a list of `roll` and `damageType`), `save` (`Save`), `effects` (`Effect[]`), `recharge` (`on`, `die`, `threshold`), `limit` (`Limit`), `cost` (legendary action points) and, for a multiattack, `attacks` and `replacements`.

Monster images. A monster definition has no image field, because a definition is immutable once loaded. A DM maps an uploaded image to a monster by its content key, the same way for an SRD monster and a monster from a pack. The mapping is campaign data kept by the application outside the pack, and one image serves every copy of the monster. Where no image is mapped, the application shows the placeholder silhouette and the initials of the creature (the UX and API Specification, UX-032).

Map assets. A map is an asset and carries no rules. Its fields are name, image (the content hash of the stored image), squareSizePx and offsetX, offsetY in pixels, columns and rows, and the squares that are obstacles and the squares that are difficult terrain, each square written as a column and row. A map belongs either to a campaign, which is the case in version 1.5 where the DM creates it in the application, or to a pack. The pack case is a seam and not a feature: this document does not add a maps folder to the pack layout, and a pack that carries maps would hold confirmed squares only, because the detected or confirmed state of a square is campaign data and never part of a pack.

### Hazards, poisons and afflictions

Fields follow Part H (13.7, 13.8).

| Kind | Field | Type | Required | Meaning |
| --- | --- | --- | --- | --- |
| `hazard` | `text` | `Text` | Yes |  |
|  | `area` | `Area` | Yes |  |
|  | `trigger` | hazard trigger kind | Yes |  |
|  | `detection`, `disabling` | object | No | A `check` (skill or ability) and a `dc` |
|  | `effects` | `Effect[]` | Yes |  |
|  | `visibility` | `hidden` or `visible` | No | Default `hidden` (section 15) |
|  | `resets` | bool | No |  |
| `poison` | `delivery` | `ingested`, `inhaled`, `injury` or `contact` | Yes |  |
|  | `save`, `effects`, `duration` | `Save`, `Effect[]`, `Duration` | Yes | The duration runs on the game clock |
| `affliction` | `afflictionKind` | `MADNESS_SHORT`, `MADNESS_LONG`, `MADNESS_INDEFINITE`, `DISEASE` or `POISON` | Yes |  |
|  | `text`, `effects`, `duration` |  | Yes | DISEASE and POISON also require `save` for resisting them |
| `afflictionTable` | `afflictionKind` | as above | Yes |  |
|  | `die` | `Dice` | Yes | The roll that picks a row, such as a d100 |
|  | `rows` | list | Yes | Entries of `from`, `to` and `affliction` (`Ref<affliction>`), covering every face of the die once |

## 10. Validation

A pack is validated once, when it is loaded, and the result decides whether it exists. Validation lives in the `content` package and nowhere else. The engine never sees JSON; it receives immutable definitions that the loader built from a pack that passed every check below (Architecture, content decisions).

### Stages

The stages run in this order. Each stage collects every problem it finds and the run continues to the end of the stage, so the author sees the whole list at once. A stage that found errors stops the run before the next stage, because later stages assume the earlier ones held.

| Stage | What it checks | Rules |
| --- | --- | --- |
| 1. Intake | Size, file count and zip safety, before anything is parsed | Section 16 |
| 2. Parse | UTF-8, strict JSON (no duplicate member names, integers only), text files | CS-KEY-007, CS-PKG-013 |
| 3. Layout and manifest | Paths, kind folders, file names against keys, manifest fields and dependencies | CS-PKG-009 to CS-PKG-019, CS-OVR-001 |
| 4. Schema | Each definition against the JSON Schema of its kind, with unknown fields forbidden | CS-ENV-001, CS-ENV-002 |
| 5. Vocabulary | Every name against the registry, parameters of each effect against its declared schema | CS-VOC-004, CS-VOC-008 |
| 6. Expressions | Parse, type, context and sample evaluation of every formula | CS-EXP-001 to CS-EXP-011 |
| 7. References | Existence, kind and reachability of every key, and the targets of `overrides` | CS-KEY-003, CS-KEY-004, CS-OVR-002, CS-OVR-006 |
| 8. Ruleset consistency | Checks that tie a definition to the Ruleset's numbers | CS-VAL-004 |
| 9. Hashing | Definition hashes and the pack hash | CS-KEY-008, CS-KEY-009 |

| Id | Rule |
| --- | --- |
| CS-VAL-001 | A pack with any error is rejected whole with `CONTENT_INVALID` (CS-PKG-005). The response carries every problem as an object with the file, the JSON pointer of the field, the id of the rule that was broken and a message. The rule id is the stable code a tool or a test can match on. |
| CS-VAL-002 | A warning does not reject the pack. It is stored with the pack and shown in the pack list. Warnings are: definitions with the same kind and name in different packs (CS-OVR-009), a deprecated vocabulary name (CS-VOC-006), a feature that no definition grants, and the consistency findings that are warnings for homebrew (CS-VAL-004). |
| CS-VAL-003 | Validation is deterministic. The same pack, the same registry and the same dependency versions give the same result and the same hashes, on any machine. |
| CS-VAL-004 | The consistency checks are those the Ruleset states, and the pack never decides them. A monster's printed average hit points equal the average of its dice plus the modifier, rounded down (RS-MON-004, RS-CNV-001). Its printed proficiency bonus and XP follow from its challenge rating (RS-MON-005). Its hit die follows from its size (RS-MON-003). In a seeded pack a failed check is an error. In homebrew it is a warning, as the Ruleset decides (E-116). |
| CS-VAL-005 | The references that grant things (a class level to a feature, a choice to its options, a feat to its features, a species to its traits) form no cycle. A cycle is an error, because granting would never end. |
| CS-VAL-006 | A spell of tier 2 has a non-empty `effects` list, a spell of tier 1 has a non-empty `rolls` list, and a spell of tier 0 has neither. The same holds for the `effects` of an item. The check enforces that the declared tier matches what the engine will actually do. Which spells are curated at which tier is the Ruleset's list, and the pack carries the tier it gives. |
| CS-VAL-007 | Validation never reaches the network. Every `$ref` in a schema is a local file, and a pack cannot supply a schema. |

### Where the schema comes from

The owner asked for the choice that is best for consistency and for tracking changes (CD-7). Two designs were open: derive the schema from Java classes, or write it by hand and keep the Java in step.

The decision is a split. The shape of each kind (fields, types, which are required) is a hand-written JSON Schema, draft 2020-12, in a folder of the repository. A person reads it, a pull request shows exactly what changed, and an editor can use it through `$schema`. Every list of names (effect types, trigger points, enumerations) is generated from the Java registry into one file, `vocabulary.json`, which the schemas reference. Nothing in a schema repeats a name by hand.

| Id | Rule |
| --- | --- |
| CS-VAL-008 | `vocabulary.json` is generated from the registry by the build and is checked into the repository. A build in CI regenerates it and fails when the result differs from the checked-in file, so a name cannot be added in Java without the schema export changing in the same commit. |
| CS-VAL-009 | Each kind has fixtures: valid definitions and invalid ones that each break one rule. A test loads every fixture against the schema and against the Java validator and fails if they disagree. A second test fails if a registry entry has no valid fixture that uses it. These conformance tests are what keep the hand-written schema and the Java from drifting (T-107, T-106). |
| CS-VAL-010 | The schema files are published with the application for editor use. Their location is `app/src/main/resources/content-schema/`, which the repository layout of the AI Build Brief names. |
| CS-VAL-011 | The JSON Schema validator has to support draft 2020-12, including `unevaluatedProperties`, and has to resolve references from local files only. The library and its version are recorded in the Architecture. The candidate is the networknt json-schema-validator. It supports drafts 4, 6, 7, 2019-09 and 2020-12. Its 3.x release line is built on Jackson 3 and needs Java 17 or later, and its 2.x line is built on Jackson 2. Spring Boot 4.1 uses Jackson 3 by default and keeps Jackson 2 only as a deprecated aid to migration, so the build uses the 3.x line (V-10c). |

## 11. Lifecycle

A pack is loaded once, and after that it never changes. What changes over time is which packs a campaign uses and at which version. This section says what the engine does at each step and what it refuses to do.

### States

A pack version is in one of four states. A campaign relates to a loaded pack version separately, as enabled or not.

&#91;embedded content: pack states · 4 states, 1 decision\]

A pack is stored only after every validation stage passes. From there a DM enables it per campaign, and an archived version can be deleted only when nothing holds it.

| Id | Rule |
| --- | --- |
| CS-LIF-001 | A pack version is Received while it is being read, Rejected if any validation stage fails, Loaded once all stages pass, and Archived when it is hidden from new use. Only Loaded and Archived versions are stored. A Rejected pack leaves a report and nothing else. |
| CS-LIF-002 | A Loaded version is immutable (CS-PKG-006). Nothing edits it, and no operation rewrites its hash. |

### Loading

| Case | Outcome |
| --- | --- |
| A pack id not yet loaded | Validated and stored as Loaded |
| The same id, version and hash as one already loaded | Nothing is done and the existing version is returned. Loading twice is harmless |
| The same id and version with a different hash | Rejected (CS-PKG-019) |
| The same id with a new version | Validated and stored next to the old version, which stays Loaded |
| A pack whose dependency is not loaded in an acceptable version | Rejected, with the dependency named (CS-OVR-003) |

| Id | Rule |
| --- | --- |
| CS-LIF-003 | At first start the application loads the SRD pack that ships with it through the same loader. At every later start it compares the hash of the shipped pack with the stored hash for that version. A mismatch stops the start with a clear error, because it means the release was built with changed content under an unchanged version. |
| CS-LIF-004 | Loading is independent of any campaign. A loaded pack version can be enabled in any number of campaigns. |
| CS-LIF-005 | A loaded version is never edited in place, including homebrew. Changing homebrew means loading a new version (a new zip in version 1.5, an editor that writes a new version in version 2). |

### In a campaign

| Id | Rule |
| --- | --- |
| CS-LIF-006 | Enabling a pack in a campaign is a DM action. It requires the pack's dependencies to be enabled and earlier in the order (CS-OVR-004), and it shows the report of section 5: the overrides added, the keys shadowed and the same-name warnings. It applies from the next action (CS-OVR-012). |
| CS-LIF-007 | Disabling a pack is refused while campaign state refers to a definition that only that pack provides: a character who has the class, a monster in an encounter, an item instance. The refusal lists each reference. The DM removes or replaces them first. A key that an override replaced does not block, because the original resolves again. |
| CS-LIF-008 | Moving a campaign from one version of a pack to another is an upgrade. The engine runs an upgrade check before it applies: keys the new version drops that campaign state refers to (blocking), definitions whose hash changed (shown), overrides gained or lost, and the revalidation of every enabled pack that depends on this one against the new version (CS-OVR-005). A blocking finding refuses the upgrade. The result of the check is shown to the DM before they confirm. |
| CS-LIF-009 | Enabling, disabling, reordering and upgrading are logged like a setting, with who did it and what resolution changed (CS-OVR-012). |

### Pinning, checkpoints and deletion

| Id | Rule |
| --- | --- |
| CS-LIF-010 | An open action pins the hashes of the definitions it uses (CS-KEY-010). A change to the enabled packs while it is open waits until it closes. |
| CS-LIF-011 | A checkpoint records every enabled pack with its id, version, hash and position in the order. Restoring a checkpoint needs those exact hashes loaded. When one is missing, the restore is refused and the message names the pack and version to load. |
| CS-LIF-012 | A loaded version can be archived at any time. Archiving hides it from the lists of packs a campaign can newly enable, and it keeps working in every campaign that already enables it. |
| CS-LIF-013 | A loaded version can be deleted only when no campaign enables it, no checkpoint records it and no other loaded pack depends on it. Otherwise the refusal lists what holds it. |

## 12. Storage mapping

The Architecture decided that definitions are stored as JSONB with a few relational columns beside them (pack, key, ruleset, name, license). This section fixes which tables carry content and what the database refuses, so that the invariants of this document hold even if application code is wrong. The DDL itself belongs to the migrations of the AI Build Brief; the column lists here are the contract those migrations satisfy.

### Tables

| Table | Holds | Columns that matter |
| --- | --- | --- |
| `content_pack` | One row per loaded pack version | `id`, `pack_key` (the manifest id), `version`, `hash`, `ruleset`, `manifest` (JSONB), `notice` (text), `warnings` (JSONB), `state` (`loaded` or `archived`), `loaded_at`, `loaded_by` |
| `content_pack_dependency` | The dependencies a pack was resolved against at load | `pack_id`, `dependency_id` |
| `content_definition` | One row per definition | `id`, `pack_id`, `kind`, `key`, `name`, `ruleset`, `license`, `hash`, `overrides` (the key it replaces, or null), `body` (JSONB) |
| `campaign_pack` | The packs a campaign enables, in order | `campaign_id`, `pack_id`, `pack_key`, `position`, `enabled_at`, `enabled_by` |
| `content_import_log` | Every attempt to load a pack, accepted or not | `id`, `at`, `by`, `pack_key`, `version`, `hash`, `outcome`, `report` (JSONB) |

The application keeps no file copy of an uploaded pack. The stored definitions are the pack, and a later export, which this document does not specify for version 1, would write canonical files from them.

| Id | Rule |
| --- | --- |
| CS-STO-001 | `content_definition.body` holds the definition as authored, with every `text/` file read into it. The loader-set fields of section 6 are not in the body. They are columns, copied from the manifest, so that the licence and attribution of a definition cannot differ from the pack row that owns it. The definition hash covers the authored body only. |
| CS-STO-002 | `(pack_id, kind, key)` is unique in `content_definition`, and `(pack_key, version)` and `hash` are unique in `content_pack`. A `kind` column has a CHECK constraint that lists the kinds of section 9. |
| CS-STO-003 | `content_definition.pack_id` is a foreign key to `content_pack` with an explicit `ON DELETE` choice. `campaign_pack.pack_id` is a foreign key with `ON DELETE RESTRICT`, so a pack that a campaign enables cannot be deleted. `content_pack_dependency.dependency_id` is also `RESTRICT`, so a pack that another depends on cannot be deleted (CS-LIF-013). |
| CS-STO-004 | `campaign_pack` carries `pack_key` next to `pack_id`, tied by a composite foreign key to `(id, pack_key)` of `content_pack`, and `(campaign_id, pack_key)` is unique. The database therefore refuses two versions of one pack in a campaign (CS-OVR-004). The pair `(campaign_id, position)` is unique, with the constraint deferrable so that a reorder can swap two positions in one transaction. |
| CS-STO-005 | Content rows are insert-only. A trigger rejects any UPDATE of a `content_definition` row, and of every column of `content_pack` except `state`. The application role has no other write path. The immutability of CS-PKG-006 is enforced by the database and does not depend on the loader behaving. |
| CS-STO-006 | Characters, encounters, item instances and the log store content references as keys in text columns, never as row ids and without a foreign key. References are resolved through the campaign's resolution index (CS-OVR-010), which a foreign key cannot express. The check that no reference is left dangling is the application's, at disable, upgrade and delete time (CS-LIF-007, CS-LIF-008). |
| CS-STO-007 | Every table records when and by whom a row was written, and times are in `timestamptz`. |

### Reading

| Id | Rule |
| --- | --- |
| CS-STO-008 | When a campaign opens, the `content` package builds one immutable content set: the definitions of the enabled packs, turned into the engine's immutable definition objects, with the resolution index of section 5. The engine receives that object and never a row or a JSON tree. |
| CS-STO-009 | The content set is cached under a key made of the ordered list of enabled pack hashes. A change to the list gives a new key, so nothing has to be invalidated, and an open action keeps the set it started with (CS-LIF-010). |
| CS-STO-010 | Indexes are created in the migration that creates the table: `(kind, key)` on `content_definition`, `(pack_id)` on each foreign key, and a lower-cased `(kind, name)` index for the pickers. There is no index on `body` in version 1, because nothing queries inside it. |
| CS-STO-011 | Loading the SRD content set when a campaign opens is measured in a benchmark (T-112) and the result is recorded. It is reported and does not gate a build, because the target hardware varies. |

## 13. The SRD pack

The SRD pack is the content the application ships with and seeds at first start. It is a pack like any other (CS-PKG-008): the same loader, the same schema, the same overrides. What is special about it is how it is built and how its text is kept faithful to the source.

### Why verbatim

The description text of the SRD is stored word for word and kept apart from the mechanics (CD-8). Three reasons decided it. A paraphrase cannot be checked by a machine, and a person would have to re-read every entry; verbatim text can be compared with the source automatically (CS-SRD-004). A paraphrase invites small rule changes that nobody notices, while the mechanics here are written separately as structured data and checked against the Ruleset. And the licence allows verbatim use with attribution. Where the SRD prints a flaw or an ambiguity, the correction goes in `notes` beside the text and never into it (CS-ENV-008).

### Pipeline

The pack is built by tooling in the repository, not by hand, in five steps.

1. **Extract.** The SRD PDF is read with a column-aware extractor. Running headers, footers and page numbers are removed, and a hyphenated line break is joined. The output is one normalised text file with a recorded SHA-256 of the PDF it came from.
2. **Cut.** For each definition, a tool cuts the description text out of the extraction by its heading and writes it to the definition's `text` field or file. Nobody retypes it.
3. **Author.** The structured fields (mechanics, references, tier, formulas) are written by us from the Ruleset, in the schema of section 9. The tier of each spell and item is the Ruleset's.
4. **Verify.** The checks of CS-SRD-004 to CS-SRD-007 run, and then the ordinary validation of section 10, with the Ruleset consistency checks as errors.
5. **Hash and ship.** The loader computes the pack hash. The build embeds it in the application for the startup comparison of CS-LIF-003.

| Id | Rule |
| --- | --- |
| CS-SRD-001 | The pack's manifest has the id `srd-5.1`, the ruleset `srd-5.1`, the licence `CC-BY-4.0` with `attributionRequired` true, and a `NOTICE` file. Its version changes whenever any definition changes. |
| CS-SRD-002 | The pack is the checked-in folder of seed data named in the repository layout of the AI Build Brief, and the application ships exactly that folder. |
| CS-SRD-003 | A text field of the SRD pack comes from the cutting tool. A text field that a person edited by hand fails the check below. |
| CS-SRD-004 | The verbatim check compares each text field with the normalised extraction. Both sides are compared after the markup of the text subset (CS-ENV-006) is removed, runs of whitespace are collapsed, the hyphen and minus glyphs that the PDF font uses are treated as one (a hyphen that the font draws as several glyphs counts once), tabs, carriage returns and no-break spaces count as whitespace, and straight and curly quotation marks and apostrophes are treated as one. The extraction drops the running header and footer of each page by their position on the page and not by their words (V-10f). Anything else that differs fails the check. A tool reports the field and the first differing words (T-109). |
| CS-SRD-005 | A checked-in source index records, for every text field, where its words sit in the extraction and a hash of that span. The check fails if a field does not match its recorded span, so a change of extractor or a drifting heading is found, not absorbed. |
| CS-SRD-006 | The coverage report lists every entry the extraction finds under the headings of the kinds in section 9, and whether the pack contains it. An entry the Ruleset covers and the pack lacks fails the build. An entry the Ruleset does not yet cover is present at tier 0 where the kind has a tier, and is otherwise listed in the report as not yet in the pack. |
| CS-SRD-007 | Mechanics come from the Ruleset and are never inferred from the text. A mechanics field that disagrees with the Ruleset's consistency rules (CS-VAL-004) fails the build for this pack. |
| CS-SRD-008 | The verbatim check shows the text came from the source. It does not show that the right span was cut for the right definition. A person reads a sample of each kind once when the pack is first built, and again for any entry whose index changes. This is verification debt V-10. |
| CS-SRD-009 | Nothing in the engine or the loader treats `srd-5.1` specially, apart from the reserved `srd-` prefix of pack ids (section 3). A campaign can override an SRD definition and cannot edit it. |

### Attribution

The SRD is available under the Creative Commons Attribution 4.0 International licence. The owner asked for one simple attribution statement (CD-4). The pack's `NOTICE` holds it, and the application shows it wherever SRD content is shown.

```
This work includes material taken from the System Reference Document 5.1
("SRD 5.1") by Wizards of the Coast LLC and available at
https://dnd.wizards.com/resources/systems-reference-document.
The SRD 5.1 is licensed under the Creative Commons Attribution 4.0
International License available at
https://creativecommons.org/licenses/by/4.0/legalcode.

The text has been reformatted and combined with structured game data written
for this application. The wording of the SRD text is unchanged.
```

| Id | Rule |
| --- | --- |
| CS-SRD-010 | The licence asks for attribution and for a note of any change. The statement above gives both: the first paragraph is the attribution, and the second says what was changed. Where the application shows SRD text, a viewer can reach this statement from the same screen. The statement is on the help page (SCR-ACC-005 of the UX and API Specification), which every signed-in page links to from its footer, and every screen that shows rules text, a stat block, a spell or an item carries a source label with the pack name that links to the statement for SRD 5.1 content. |
| CS-SRD-011 | The application does not imply that Wizards of the Coast made or endorses it, and uses none of their product marks as its own name. A statement that the application works with fifth edition rules follows the terms on Wizards' SRD page. |
| CS-SRD-012 | The repository's top-level `NOTICE` carries the same statement. The AI Build Brief describes that file as copied verbatim from the SRD. The PDF in hand is the OGL edition and has no Creative Commons wording, so the Brief's description changes to the Creative Commons statement above, which the SRD 5.1 preamble requires. |

The wording was checked on 2026-11-21 (V-10b). Wizards' own Creative Commons edition, which the check could read in its German translation, carries the same two sentences in the same order, and an English republication of that edition gives the English text above. The PDF supplied for this project is the OGL edition of SRD 5.1. The game content of the two editions is taken as the same, and the source index of CS-SRD-005 is what would show a difference if it is not. The first full build uses the Creative Commons edition as its source, so that the pack comes from the edition its attribution names.

## 14. Homebrew

Homebrew is content a DM writes or imports for their own table. The Architecture and the Requirements already fix its nature: declarative, data only, built from the engine's effect types, scoped to a campaign, approved by the DM, with numeric limits derived from SRD data. This section says how the schema carries those decisions. The editor that would make writing homebrew comfortable is later work (version 2). In versions 1.5 and later a DM writes the JSON files and imports them.

| Id | Rule |
| --- | --- |
| CS-HBR-001 | Homebrew is an ordinary pack. It has a manifest, its own pack id, its own namespace and its own hash, and it goes through the loader and the nine validation stages like every other pack. There is no second, looser format. |
| CS-HBR-002 | A homebrew pack affects a campaign only where the DM enables it (CS-LIF-006). It never touches another campaign, and it never changes an SRD definition. It replaces one only through an explicit `overrides` field, and only in the campaigns that enable it (CS-OVR-007). |
| CS-HBR-003 | Only the DM role can load, enable, upgrade, disable or delete a pack. Players cannot. The pack screen is SCR-DM-005 of the UX and API Specification, where the pack routes and their checks are defined, and a player who calls one is refused with FORBIDDEN. |
| CS-HBR-004 | Homebrew can combine anything in the vocabulary and cannot add to it. A mechanic the vocabulary lacks fails validation with the unknown name in the message (CS-VOC-004). The way forward is a request for a registry entry (CS-VOC-002), which any pack can then use. |
| CS-HBR-005 | Consistency findings that tie a definition to the Ruleset (CS-VAL-004) are warnings for homebrew, as the Ruleset decides (E-116). Structural and vocabulary errors are errors for every pack. |

### Limits derived from SRD data

Homebrew can be balanced badly by accident. The limits warn the DM when a definition is beyond what the SRD itself contains, and they never block, since a DM may want a monster stronger than any in the book.

| Id | Rule |
| --- | --- |
| CS-HBR-006 | The limits are computed from the SRD pack and are not typed in by hand. The SRD build writes them to a small file shipped with the application, so they change only when the SRD pack does. |
| CS-HBR-007 | The first set covers the quantities that the Ruleset's own tables relate to a level or a challenge rating: the average damage and the average healing of a spell by spell level, the armor class, hit points, attack bonus and damage per round of a monster by challenge rating, and the largest bonus an item gives by rarity. The registry names the measured quantities, and a new one is added there. |
| CS-HBR-008 | A definition beyond the SRD range of a quantity gets a warning that names the quantity and the largest SRD value. The pack list shows the warnings, and the DM confirms them when enabling the pack. The confirmation is logged with the setting that enabled the pack (CS-LIF-009). |

### Authoring

| Id | Rule |
| --- | --- |
| CS-HBR-009 | The validator is available outside the application as a command-line check that runs stages 1 to 9 on a folder or a zip and prints the problems. It is the same code the loader runs. The SRD build uses it as well, so an author sees what the loader will say before importing. |
| CS-HBR-010 | The published schemas (CS-VAL-010) let an editor complete and check field names while an author types. The repository holds one small valid example of each kind that authors can copy. |
| CS-HBR-011 | Every save of a homebrew pack is a new version (CS-LIF-005). The older version stays loaded until it is deleted, so a DM can move a campaign back. |

## 15. Visibility and hidden information

Some content is secret until the table finds it out: a trap, an unidentified item, a monster behind a door, the effects of a madness. The player view must show none of it early. This section fixes how a definition says what a player may see, so that the answer lives in the schema and a screen cannot get it wrong by reading the body directly.

| Id | Rule |
| --- | --- |
| CS-VIS-001 | Every field in a kind schema carries a visibility class: `public`, which anyone viewing the campaign may see; `dm`, which only the DM may see; or `revealed`, which is public once a stated state is true, such as an item being identified, a hazard being detected or a monster being revealed. The class sits in the hand-written schema. A pack cannot change it for its own definitions. |
| CS-VIS-002 | A field without a class is `dm`. A new field is exposed to players only when someone decides it should be. The conformance suite fails if a field in a schema has no class (T-114). |
| CS-VIS-003 | The `notes` of the envelope are `dm`. The structured mechanics of every kind (`effects`, `rolls`, `parts`, `reaction`, `tier`, formulas) are `dm`: the engine uses them and no screen needs to show them to a player. |
| CS-VIS-004 | Player-facing views are built from definitions by one component of the `content` package that reads the classes from the schema and returns view models. The templates receive view models and never a definition body, so a template cannot show a `dm` field by mistake. |

### What a player sees

| Kind | Public | Revealed |
| --- | --- | --- |
| Spell, feature, feat, class, subclass, species, subspecies, background, condition | Name and text, and for a spell its level, school, casting time, range, components and duration |  |
| Item | The `unidentified` name and text, when the item has them | The name, text, category and properties, once the item instance is identified (Ruleset 11.2) |
| Monster |  | Name, size, type and the text of its traits and actions, once the monster is revealed. Numbers stay `dm` |
| Hazard | Name, text and area when `visibility` is `visible` | The same, once the hazard is detected or triggered. A hazard with `visibility` `hidden` is `dm` until then |
| Affliction and poison |  | Name and text, once applied to a creature the player controls. The effects stay `dm` |

An item can carry an optional `unidentified` object with a `name` and a `text`. It is what a player sees before identification, for example a plain potion. An item without it appears to players as its category until it is identified.

| Id | Rule |
| --- | --- |
| CS-VIS-005 | A key such as `srd-5.1:mimic` names what a thing is. A player-facing identifier, URL or message never contains the key of a definition that is not public or revealed. Such views use opaque ids of the instance. |
| CS-VIS-006 | A definition hash, a pack list entry or an error message shown to a player never reveals a hidden definition. The pack list is a DM view. |
| CS-VIS-007 | The reveal state belongs to the encounter or the item instance and is play state (CS-DEF-002). A definition says what becomes visible, and the state says when. |

## 16. Security of imported content

A pack is a file from outside the application, and from version 1.5 a DM can upload one. The content part of the threat model is short because a pack is data only (CS-PKG-001): nothing in it runs. The controls below cover what a hostile or careless file can still do, which is to exhaust resources, escape its folder, plant markup in a page, or leak what should stay hidden. The web side of uploading (authentication, request forgery, rate limits at the endpoint) belongs to the UX and API Specification and is named here only where the content code depends on it.

| Threat | Control | Rule |
| --- | --- | --- |
| Code or script inside a pack | None can run. Packs are JSON and text, formulas have no loops or calls out, and nothing is deserialised into arbitrary classes | CS-PKG-001, CS-SEC-005 |
| Zip bomb, huge pack | Limits on compressed size, expanded size, entry count and ratio, enforced while streaming | CS-SEC-001 to CS-SEC-003 |
| Path traversal, links and special files in a zip | Entries are never written to disk, and entries with unsafe names are refused | CS-SEC-002 |
| Deep or large JSON | Limits on file size, nesting depth and array length | CS-SEC-001 |
| Script or markup in text | A small converter renders a fixed subset and escapes everything else | CS-ENV-006 |
| Misleading text through control characters | Format and bidirectional control characters are refused | CS-SEC-006 |
| Runaway formulas | Length, depth and call limits, no loops, exact arithmetic, a bounded sample evaluation | CS-EXP-007 to CS-EXP-010 |
| Network access during validation | None. Schema references are local files | CS-VAL-007 |
| Leaking hidden content | Visibility classes in the schema, view models, opaque ids | Section 15 |
| A pack that replaces SRD behaviour unseen | Overrides are explicit, listed and logged | CS-OVR-007, CS-OVR-012 |

### Limits

The values are starting points that the owner accepted (V-10d). They are constants in one place of the `content` package, so changing one is a one-line change that this document then records. They are generous for the SRD pack and tight for a hostile file.

| Limit | Value |
| --- | --- |
| Uploaded zip, compressed | 20 MiB |
| All files in a pack, expanded | 100 MiB |
| Number of files in a pack | 10,000 |
| Compression ratio of one entry | 100 to 1 |
| One definition file | 256 KiB |
| One text file under `text/`, or one inline text string | 64 KiB, and 16 KiB inline |
| `manifest.json` | 64 KiB |
| Path length | 200 characters |
| JSON nesting depth | 32 |
| Length of one array | 2,000 entries |
| Dependencies of one pack, and the longest dependency chain | 16 and 8 |
| A formula | 256 characters, nested 16 deep, 8 calls (CS-EXP-008) |

| Id | Rule |
| --- | --- |
| CS-SEC-001 | Every limit above is checked before the data it limits is parsed or expanded, and a pack over any limit is rejected at stage 1 or 2 with the limit named. A declared size in a zip header is never trusted. The loader counts the bytes it actually reads and stops at the limit. |
| CS-SEC-002 | The loader never writes an entry of a pack to the file system. It reads entries from the archive into bounded memory buffers. Entries are refused when the name is absolute or contains `..` or a backslash, when it is a link or a special file, when it is encrypted, when it is an archive inside the archive, or when two names are equal after case folding. |
| CS-SEC-003 | Only one pack import runs at a time, and the import of a pack uses a bounded amount of memory, so a large upload cannot starve the application during play. |
| CS-SEC-004 | Error reports quote at most 80 characters of any value, and are escaped for the place where they are shown. A report never echoes a whole definition. |
| CS-SEC-005 | Packs are read into a JSON tree and checked against the schema. Objects of the engine are then built by code that reads that tree field by field. The JSON library runs with polymorphic and default typing off, so no class name in a pack can select a class to instantiate. |
| CS-SEC-006 | Text, names and notes may not contain Unicode bidirectional controls (U+202A to U+202E and U+2066 to U+2069), a byte order mark inside text, or other format and control characters apart from line feed and tab. Keys, pack ids and file names are ASCII (CS-KEY, CS-PKG-013), so a key cannot be spoofed by a look-alike letter. |
| CS-SEC-007 | The patterns used inside the hand-written schemas are written by us, are anchored and are of a linear-time form, and run only after the length limit of the field. A pack cannot supply a schema or a pattern. |
| CS-SEC-008 | The import log keeps the outcome, the hash and the report of each attempt, and not the body of the pack. A rejected pack is not kept. |
| CS-SEC-009 | Authorisation is by the DM role (CS-HBR-003). The content code assumes the caller was checked and does not check roles itself, so the check exists in one place, at the endpoint. |

## 17. Release phasing

The Requirements place the SRD seed and the loader in version 1, JSON pack import with custom monsters and homebrew spells in version 1.5, and the homebrew editor in version 2. This section says which parts of this document each release needs. The schema itself is one design from the start, because a later change to the format would be a change to every pack.

| Part | Version 1 | Version 1.5 | Version 2 |
| --- | --- | --- | --- |
| Content schema, one version for all kinds (section 9) | Complete | Unchanged | Unchanged, or a new content schema version if one is needed |
| Vocabulary registry and expression grammar (sections 7, 8) | The names the v1 engine implements | Names added for the new mechanics, such as `ON_FALL` | Further additions |
| Pack folder, manifest, keys, hashes (sections 3, 4) | Complete | Unchanged | Unchanged |
| Loader and the nine validation stages, from a folder | Complete | Unchanged | Unchanged |
| SRD pack, verbatim, with the Ruleset's tiers (section 13) | Seeded at first start | New SRD versions as tiers rise | Further versions |
| Resolution index, overrides and dependencies in the engine (section 5) | Built, because every reference goes through the index (CS-OVR-010). Only the SRD pack is enabled | Used by imported packs | Used by the editor |
| Storage tables and database guards (section 12) | Complete, with only the SRD pack in them | Unchanged | Unchanged |
| Visibility classes and view models (section 15) | Complete | Unchanged | Unchanged |
| Zip upload, the import log, the limits of section 16 | Not present | Yes | Yes |
| Enable, disable, upgrade and delete with their checks (section 11) | The SRD pack is enabled at campaign creation | Complete for any pack | Complete |
| Limits derived from SRD data, the command-line check (section 14) | Not present | Yes | Yes |
| Homebrew monsters and spells | Not present | Yes, as the Requirements state | Yes |
| Other homebrew kinds (items, features, classes, hazards) | Not present | The loader accepts them, since the schema covers all kinds. They are not part of the committed scope | Supported in the editor |
| A homebrew editor and pack export | Not present | Not present | Yes |

| Id | Rule |
| --- | --- |
| CS-REL-001 | The content schema version does not change between versions 1, 1.5 and 2 unless this document says so. New releases add vocabulary names and nothing else (CS-KEY-011), so a pack written for an earlier release keeps loading. |
| CS-REL-002 | Each release has a vocabulary version. A pack that uses a name from a later one declares that version in its manifest and is refused by an earlier application with a message naming the version it needs (CS-PKG-018). |
| CS-REL-003 | The content package skeleton is built in Build Step 5 of the AI Build Brief: the registry, the schema files, the manifest and layout checks, the expression evaluator on top of the dice parser, reference checks, hashing and the storage tables, tested with a small sample pack and not with the SRD. Each later step that teaches the engine a new kind adds that kind's registry entries, fixtures and SRD files in the same step, so no kind is ever in the engine without its data. |

## 18. Decisions, conflicts and edge cases

### Decisions

All sixteen decisions are the owner's. CD-1 to CD-6 were made first, and CD-7 to CD-16 were accepted as proposed. Both happened on 2026-11-21.

| Id | Decision | Status |
| --- | --- | --- |
| CD-1 | A pack is a folder with a manifest | Accepted |
| CD-2 | Overrides are allowed. A definition replaces another only through an explicit `overrides` field, and the replacement is of the whole definition | Accepted |
| CD-3 | Formulas use a small declarative grammar that extends the dice grammar, so the hardcoded part of the simulator stays as small as possible | Accepted |
| CD-4 | Attribution is one simple Creative Commons statement in `NOTICE` | Accepted |
| CD-5 | Keys are namespaced by pack, in the form `pack:local` | Accepted |
| CD-6 | A name the engine does not know rejects the whole pack | Accepted |
| CD-7 | The shape of each kind is a hand-written JSON Schema. The lists of names are generated from the Java registry, and conformance tests keep the two in step (section 10) | Accepted |
| CD-8 | SRD description text is stored verbatim and checked against the source. Corrections go in `notes` (section 13) | Accepted |
| CD-9 | The manifest does not list files. The loader reads the tree and computes the hash, so the manifest cannot drift (CS-PKG-015) | Accepted |
| CD-10 | References are full keys. There is no short form for the same pack (CS-KEY-003) | Accepted |
| CD-11 | One name per behaviour in the registry. The provisional item names of the Ruleset, three feature type names of the Architecture and four labels of the Part D catalog are folded into one registry name each (section 7) | Accepted |
| CD-12 | Conditions are data (a `condition` kind with parts), as the Ruleset says (RS-CND-003) | Accepted |
| CD-13 | Homebrew limits derived from SRD data warn and never block (CS-HBR-008) | Accepted |
| CD-14 | The database enforces immutability and one version of a pack per campaign (CS-STO-004, CS-STO-005) | Accepted |
| CD-15 | Lookup tables are a `ruleTable` kind read through `lookup`, so class tables and the hit die by size are data (section 9) | Accepted |
| CD-16 | Species and subspecies are kinds beside class and subclass, so character creation reads all four from content | Accepted |

### What changes in other documents when this one is accepted

| Document | Change |
| --- | --- |
| Ruleset Specification | The three rows that said the content schema was to be written (class definition format, spell exact format, provisional effect primitive names) now point here. The provisional item primitive names became the registry names of section 7. The Ruleset roadmap lists the parts of the Ruleset only, so it is not changed |
| Architecture | The effect type list of the feature trigger table uses the registry names. The content pack importer and schema seams row points here. The content decisions gain the folder, manifest, key and override decisions |
| Requirements Specification | The content requirements gain overrides, namespaced keys, attribution and the limits of section 16. The next-document list drops this document |
| Decision Log | CD-1 to CD-16 are recorded with their status |
| AI Build Brief | The content pack step and the paragraph that stops work until this document exists are updated. The `NOTICE` description changes (CS-SRD-012). The repository layout names the schema folder (CS-VAL-010). The companion versions list gains this document |

### Edge cases

| Id | Case | Result |
| --- | --- | --- |
| EC-1 | Two packs define a spell named Fireball | Both load under their own keys, a warning lists them, and pickers show the pack (CS-OVR-009) |
| EC-2 | Two enabled packs override the same key | The pack later in the campaign order answers for the key, and the pack list shows the shadowed one (section 5) |
| EC-3 | An override pack is disabled while a character holds the overriding definition | Refused while state refers to it. A reference to the replaced key is fine, because the original resolves again (CS-LIF-007) |
| EC-4 | A pack claims a key in another pack's namespace | Rejected (CS-KEY-002) |
| EC-5 | A pack references a key of a loaded pack it did not list as a dependency | Rejected (CS-OVR-002) |
| EC-6 | A new version of a dependency breaks a pack that depends on it | The upgrade check reruns validation of the dependents and refuses the upgrade (CS-LIF-008) |
| EC-7 | The same pack is uploaded twice | The second load does nothing (section 11) |
| EC-8 | A pack changes content and keeps its version | Rejected (CS-PKG-019). For the shipped SRD pack the application stops at start (CS-LIF-003) |
| EC-9 | The manifest declares a vocabulary version that is too low for a name the pack uses | Rejected as an unknown name (CS-KEY-012) |
| EC-10 | A formula divides by a variable that is zero at some level | The sample evaluation rejects the pack (CS-EXP-010) |
| EC-11 | A text file is present and nothing points at it, or a pointer has no file | Rejected (CS-PKG-014) |
| EC-12 | `attributionRequired` is true and `NOTICE` is missing | Rejected (CS-PKG-017) |
| EC-13 | A zip made on macOS contains `__MACOSX` | Ignored and left out of the hash (CS-PKG-012) |
| EC-14 | A number does not fit a signed 53-bit integer, or is a fraction | Rejected (CS-KEY-007) |
| EC-15 | A checkpoint needs a pack version that has been deleted | The restore is refused and names the pack. Deletion is refused while a checkpoint records the version, so this arises only after the data was damaged (CS-LIF-011, CS-LIF-013) |
| EC-16 | A player asks for a URL built from a monster's key | No such URL exists. Player views use opaque ids (CS-VIS-005) |

## 19. Tests and verification debt

Test ids continue the numbering of the Ruleset, whose last is T-100. The pure-Java boundary test T-100 covers the expression evaluator and the registry of variables (CS-EXP-011). Every test below was accepted with the document.

| Id | What it checks | Rules | Release |
| --- | --- | --- | --- |
| T-101 | A valid sample pack loads, and each layout and manifest violation is rejected with its rule id | CS-PKG-009 to CS-PKG-019 | 1 |
| T-102 | Known input gives the known canonical form and the known definition and pack hashes. Reformatting, CRLF line endings and file order do not change a hash, and any change of content does | CS-KEY-007 to CS-KEY-010 | 1 |
| T-103 | Every dice test vector of the Ruleset's dice engine parses in the extended grammar with the same meaning and the same canonical form | CS-EXP-001 | 1 |
| T-104 | Types, floor division with negative values, `round_div` on halves and negatives, `if`, `lookup`, overflow, division by zero, the limits, the sample evaluation and the order of rolls under a seed | CS-EXP-002 to CS-EXP-010 | 1 |
| T-105 | Dependency checks, cycles, override targets, resolution with chains and shadowing, same-name warnings, and the index after a pack is disabled | CS-OVR-001 to CS-OVR-013 | 1 for the index, 1.5 for enabling |
| T-106 | The generated `vocabulary.json` equals the checked-in file. Every registry entry has a handler and a valid fixture that uses it | CS-VOC-001, CS-VAL-008, CS-VAL-009 | 1 |
| T-107 | Valid and invalid fixtures of every kind agree between the JSON Schema and the Java validator | CS-ENV-001, CS-ENV-002, CS-VAL-009 | 1 |
| T-108 | An unknown effect type, a trigger alias, a variable or an enumeration value rejects the whole pack, and the report names every unknown name and its file | CS-VOC-004, CS-VOC-005 | 1 |
| T-109 | The SRD verbatim check passes on the pack, fails when one word is changed, tolerates the whitespace and glyph differences of CS-SRD-004, and finds a drifting span | CS-SRD-003 to CS-SRD-006 | 1 |
| T-110 | Ruleset consistency checks are errors in a seeded pack and warnings in homebrew. A spell's tier matches its effects and rolls | CS-VAL-004, CS-VAL-006, CS-HBR-005 | 1 |
| T-111 | A zip bomb, a path traversal name, a link, an encrypted entry, duplicate names after case folding and an oversize file are rejected, nothing is written to disk, and memory stays bounded | CS-SEC-001 to CS-SEC-003 | 1.5 |
| T-112 | The database refuses an update of a definition, a second version of a pack in a campaign and the delete of a held pack. A pinned action and a checkpoint reproduce their content. The load time of the SRD content set is recorded | CS-STO-003 to CS-STO-005, CS-STO-011, CS-LIF-010, CS-LIF-011 | 1 |
| T-113 | Raw HTML, script, images and links in text are escaped, the subset renders, and control and bidirectional characters are rejected | CS-ENV-006, CS-SEC-006 | 1 |
| T-114 | Every schema field has a visibility class. Player view models hold no `dm` field and no key of a hidden definition | CS-VIS-001 to CS-VIS-006 | 1 |
| T-115 | Each case of the loading table, the startup hash check, the upgrade check and the delete refusals | CS-LIF-003 to CS-LIF-013 | 1 for the startup check, 1.5 for the rest |
| T-116 | Limits derived from SRD data, their warnings, and the command-line check giving the same output as the loader | CS-HBR-006 to CS-HBR-009 | 1.5 |

### Verification debt V-10

These were the points of the draft that rested on something not yet confirmed. Each was cleared before the document was accepted, or is recorded here as an accepted risk.

| Item | What was open | How it was cleared |
| --- | --- | --- |
| V-10a | The registry lists of section 7 follow the Ruleset as written. The feature predicates of Part D, the complete stat key list, the recharge triggers other than the monster recharge, and the exact enumeration names are taken from concepts and not from a single table | Cleared 2026-11-21 by reading the Ruleset: Part C (rests, recharge events, ON\_DAWN, duration kinds), 9.3 (the whole feature catalog and its predicates), Part G (START\_OF\_OWN\_TURN) and RS-REA-001. Section 7 was corrected as described there. The enumerations are the SRD's own lists, and a difference found when the registry is first generated is corrected by a registry entry (CS-VOC-002). |
| V-10b | The second sentence of the attribution statement is written from memory of the Creative Commons release, and the PDF in hand is the OGL edition | Cleared 2026-11-21. Wizards' Creative Commons edition (read in its German translation) has the same two sentences, and an English republication gives the English text of section 13. The OGL and Creative Commons editions are taken as having the same game content, and the first full build uses the Creative Commons edition. |
| V-10c | A JSON Schema library for draft 2020-12 must pair with the JSON library of the Spring Boot version in use | Cleared 2026-11-21. The networknt json-schema-validator supports 2020-12. Its 3.x line is built on Jackson 3 and Spring Boot 4.1 uses Jackson 3 by default, so the build uses the 3.x line (3.0.8 of 2026-09-30 was the newest when checked). Build Step 5 confirms the exact release. |
| V-10d | The limits of section 16 are starting values | Cleared 2026-11-21 against the SRD text. The whole SRD is about 1.4 million characters, the longest spell entry about 4,000 and a large stat block (the aboleth) about 3,300, so the 16 KiB inline limit has several times the room. About 1,500 definition files are expected against a limit of 10,000, and the expanded pack about 5 MB against 100 MiB. The limits stay as starting values in one place. T-111 measures the hostile cases at the first build. |
| V-10e | The Ruleset section named in the first table of section 9 for species, background and the lookup tables is approximate | Cleared 2026-11-21 against the Ruleset headings. Section 9 now names 3.1 and 3.2 for species, subspecies and background, 9.2 and 12.3 for the lookup tables, 8.3 for resources and 9.3 for features. The Ruleset has no chapter that defines species traits or backgrounds, so those three kinds fix their shape only until one is written. |
| V-10f | One person reads a sample of each kind of SRD text against the PDF | The mechanism was cleared on 2026-11-21 with a prototype on three entries (Fireball, Second Wind, the aboleth's traits) cut from the PDF in hand. The check passed the exact text, tolerated changed whitespace and hyphen glyphs, failed one changed word and named the first differing words, and failed a drifting span. It found three extraction details, now in CS-SRD-004. The human sample read of CS-SRD-008 stays an accepted risk: the owner reads one entry of each kind at the first full build. |
| V-10g | The Ruleset and decision ids this document cites were read during drafting and are not yet cross-checked one by one | Cleared 2026-11-21 by reading every cited id in the Ruleset. One correction: RS-MON-005 covers the proficiency bonus and XP of a challenge rating and not attack and save numbers. The hit point average is RS-MON-004 and the hit die by size is RS-MON-003 (section 9, monster). |

## 20. Revision history

| Version | Date | Change |
| --- | --- | --- |
| 0.1 | 2026-11-21 | First draft. Pack folder and manifest, keys and hashes, dependencies and overrides, the definition envelope and text, the vocabulary registry, the expression grammar, the schema of every kind, validation, lifecycle, storage, the SRD pack and its attribution, homebrew, visibility, security of imports and release phasing. Decisions CD-1 to CD-16 (CD-1 to CD-6 accepted by the owner, the rest Proposed), tests T-101 to T-116, verification debt V-10 |
| 1.0 | 2026-11-21 | Accepted by the owner. Decisions CD-7 to CD-16 accepted as proposed. Verification debt V-10 cleared: the registry lists corrected against the Ruleset (the rest duration kind, recharge events, ON\_DAWN and START\_OF\_OWN\_TURN, the Part D feature catalog and its predicates, four Part D labels stored as modifier), the Ruleset references of section 9 corrected, the monster consistency ids corrected, the attribution wording and the JSON Schema library pairing confirmed, the limits checked against the SRD text and the verbatim check prototyped. Accepted risks: the human sample read of the first SRD build, the enumerations at first generation and the hostile-file measurements of T-111. On propagation the schema folder was named (CS-VAL-010) and the Ruleset, Architecture, Requirements, Decision Log and AI Build Brief were updated by reference |
| 1.1 | 2026-11-21 | UX and API Specification, accepted by the owner on 2026-11-21, applied by reference. The scope table now points to that document. CS-SRD-010 is closed: the attribution statement is on the help page and every rules screen links to it. CS-HBR-003 names the pack screen and the refusal a player gets. Section 9 gains the rule for monster images (a mapping outside the pack, not a field of the definition) and the shape of a map asset, which belongs to a campaign or, as a seam, to a pack. |
| 1.2 | 2026-11-22 | E-155: the modifier operand representation and pack limits are exact; section 7 now lists all 13 duration names and the end-time/end-condition rules, including modifier value validation. |
