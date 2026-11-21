# Tabletop RPG Simulator: Ruleset Specification (v1.2)

Oct 15, 2026 · @Samarth

The source of truth for the D&D mechanics the engine implements, based on SRD 5.1 (CC-BY-4.0). Rules are paraphrased with SRD section references. Part A (foundations) is written; the other parts are listed in section 1 and gate the later build steps.

## 0. How to use this specification

- **Rule IDs** look like `RS-ABL-002`. Every rule has a paraphrase, an SRD reference, an engine mapping, and, where useful, worked examples.
- **Worked examples** use **forced dice** (the dice results are stated), so tests need no random seed. Format: Given, When, Then. Test names should quote the rule ID.
- **Status** per rule: Specified, Needs decision, or Not yet written.
- **Engine decision** marks a place where the SRD is silent or leaves it to the DM, so we chose a default. Each is listed in section 5 for confirmation.
- **Sources read for Part A:** the SRD 5.1 chapters Using Ability Scores and Beyond 1st Level, read in full from an online rendition of the SRD text, plus the Combat chapter for the round length. Verified on 2026-10-30 against SRD text (renditions of the SRD and the Basic Rules text, not the official PDF): Inspiration, Backgrounds, Multiclassing, the Rogue, Fighter and Bard entries, spellcasting basics and the belt and manual entries. Two small residuals are in V-3. Where Part A cross-references a later chapter, the rule says so and is marked for verification.
- The SRD attribution notice is copied verbatim into the repository `NOTICE` file; this document paraphrases.
- **Attribution text**, verified word for word on 2026-11-22 against the SRD 5.1 Creative Commons PDF that Wizards of the Coast publishes at media.wizards.com/2023/downloads/dnd/SRD\_CC\_v5.1.pdf. The `NOTICE` file and the attribution page carry exactly this and nothing else is needed: “This work includes material taken from the System Reference Document 5.1 (“SRD 5.1”) by Wizards of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.” The same PDF says that no other attribution regarding Wizards is required and that a work may call itself “compatible with fifth edition” or “5E compatible”.
- **SRD list check (Decision Log OQ-07), 2026-11-22.** All 40 tier-2 spells of 10.9 exist in SRD 5.1 under the names used here. The SRD drops possessive names, so the entries are Hideous Laughter and Acid Arrow. The class letters of 10.9 match the SRD lists for the bard (read in the official PDF, including Faerie Fire and Hideous Laughter on the bard list), the paladin and the wizard. All 20 monsters of 12.10 exist in SRD 5.1. Werewolf is one entry (challenge 3, with Shapechanger) and the Adult Red Dragon (challenge 17) has Actions and Legendary Actions but no Lair Actions section, as 12.10 says. The beholder, the mind flayer and the displacer beast are not in it, and the gelatinous cube is. The conditions are the 15 of the SRD: blinded, charmed, deafened, exhaustion, frightened, grappled, incapacitated, invisible, paralyzed, petrified, poisoned, prone, restrained, stunned and unconscious. The spell and monster names, the paladin and wizard lists and the condition list were checked against the community SRD 5.1 database (dnd5eapi.co); the bard list, the two monster entries and the attribution text were read in the official PDF.

## 1. Document map and status

| Part | Contents | Status | Gates |
| --- | --- | --- | --- |
| A. Foundations | Ability scores, proficiency, d20 tests, advantage, checks, saves, levels and hit points, time | Specified (revised 2026-11-14; verification debt cleared (V-1 to V-3)) | Build Steps 2 and 3 |
| B. Combat | Initiative, turns, action economy, movement, attacks, critical hits, damage, resistance, death, cover, conditions | Specified (draft; all Part B engine decisions decided) | Combat core |
| C. Rest and resources | Short and long rests, hit dice, recharge | Specified (draft; all Part C engine decisions decided, including the edge cases E-32 to E-44; E-45 decided) | Rests |
| D. Classes | The five MVP classes, tables and features, levels 1 to 5 then to 12 | Specified (draft 2026-11-16; section 9; engine decisions E-46 to E-68 decided; V-5 cleared) | Class features |
| E. Spellcasting | Slots, preparation, concentration, areas of effect, the curated tier-2 list | Specified (first draft 2026-11-16; section 10; engine decisions E-69 to E-91 decided; V-6 cleared 2026-11-16 (Xanathar's pages and the Mearls answer not found)) | Spells |
| F. Equipment and armor class | Armor, weapons, weapon properties | E-92 to E-113 decided (section 11) | Characters |
| G. Monsters and encounters | Stat block fields, CR and XP, level bands | Written as section 12 and accepted on 2026-11-19: rules RS-MON-001 to RS-MON-052, decisions E-114 to E-133 | Monster roster |
| H. Environment | Light, vision and obscurement, line of sight and cover, reach and opportunity attacks, falling, hazards, madness and diseases | Accepted as section 13 on 2026-11-19 | Phases in 13: version 1 data and DM flags, version 1.5 automatic, version 2 elevation |

## 2. Conventions

**RS-CNV-001 Rounding.** Fractions round down unless a rule says otherwise. The SRD states this for ability modifiers and for halved proficiency; the fixed hit point value in a class entry is the one stated exception (an average rounded up). Status: Specified (verify the general rule in the Introduction).

**RS-CNV-002 Integer math.** Use floor division for anything that can be negative. A truncating division gives the wrong ability modifier for scores below 10 (see RS-ABL-002).

**RS-CNV-003 Computed, never stored.** Modifiers, proficiency bonus, skill and save totals, passive scores and maximum HP are computed from stored choices and state.

## 3. Part A: Foundations

### 3.1 Ability scores and modifiers (SRD: Using Ability Scores)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-ABL-001 | Every creature has six abilities: Strength, Dexterity, Constitution, Intelligence, Wisdom, Charisma, each with a score from 1 to 30. 10 or 11 is the human average; 18 is about the most a person usually reaches; adventurers can reach 20; monsters and divine beings can reach 30. Monster stat blocks give their scores directly. | `Ability` enum; scores stored as `pc_ability` rows, 1 to 30 |
| RS-ABL-002 | The modifier is the score minus 10, divided by 2, rounded down. It runs from -5 (score 1) to +10 (score 30). | `modifier = Math.floorDiv(score - 10, 2)` |
| RS-ABL-003 | The spellcasting ability is Intelligence for wizards; Wisdom for clerics, druids and rangers; Charisma for bards, paladins, sorcerers and warlocks. Fighters and rogues have none in SRD 5.1. | Class definition data: `spellcasting_ability` |
| RS-ABL-004 | A level-up ability score improvement (and a feat's increase) cannot raise a score above that ability's maximum, which is 20 unless something raises it. Some effects raise a score and its maximum together (the Manual of Bodily Health raises both by 2). The SRD treats 30 as the top of the scale. The score the engine uses is the base plus the ability stat's modifiers, clamped to a ruleset ceiling (default 30, a setting) and floored at 0. | Validation on level-up choices; \`AbilityScores.score\` applies the ability stat's modifiers ; \`maxScore(ability)\` caps improvements and a ceiling setting clamps the result (decision DEC-074, decided) |
| RS-ABL-005 | Before an improvement is applied, the player is warned if it would take a score above its maximum, told how much would be lost, and can reallocate. If they proceed anyway, the improvement is applied up to the maximum and the excess is lost (engine decision E-8, decided). | Pre-apply validation returns a warning with the excess that would be lost; the clamp applies only on confirmation |
| RS-ABL-006 | Effects can reduce a score to 0. What happens at 0 is defined by the effect that reduced it (for example a drain that kills), so the engine records an ability-at-zero event and lets that effect decide. The modifier formula still applies at 0 (-5). | Ability-at-zero event; consequence defined by the effect |
| RS-ABL-007 | Ability scores at character creation come from a generation method chosen by the DM for the campaign from a built-in list of common-practice methods: standard array (15, 14, 13, 12, 10, 8); point buy (27 points, base scores 8 to 15 before bonuses, costing 0, 1, 2, 3, 4, 5, 7 and 9 points for 8 to 15); roll 4d6 and drop the lowest, six times, assigned freely; roll 3d6, six times, assigned freely; or manual entry by the DM. Species bonuses apply afterwards. For example 15, 14, 13, 12, 10, 8 costs exactly 27 points. These are common practice, not SRD text (verify); DM-created methods are version 2. | Registry of built-in method definitions (key, kind, parameters); rolled values come from the dice engine and are logged |

Reference: score to modifier. 1 gives -5; 2-3 gives -4; 4-5 gives -3; 6-7 gives -2; 8-9 gives -1; 10-11 gives 0; 12-13 gives +1; 14-15 gives +2; 16-17 gives +3; 18-19 gives +4; 20-21 gives +5; each further pair adds 1, up to 30 giving +10.

**Examples (RS-ABL-002)**

- Score 9 gives -1. Truncating division gives 0, which is wrong; floor division gives -1.
- Score 8 gives -1; 10 gives 0; 15 gives +2; 20 gives +5; 1 gives -5; 30 gives +10.

**More examples (RS-ABL-004 to RS-ABL-006)**

- Strength 19, level-up improvement of +2 with a maximum of 20: the score becomes 20 and the extra point is lost; the UI warns.
- Constitution 20 with a maximum of 20; a Manual of Bodily Health raises both by 2: score 22, maximum 22. A later +2 improvement can then reach 22.
- A drain effect reduces Strength 14 to 0: the engine records ability-at-zero, and the effect decides the consequence (a Shadow's drain kills).
- Base 20 with +12 from modifiers and a ceiling of 30: the computed score is 30.

### 3.2 Proficiency bonus and its multiplier (SRD: Using Ability Scores, Beyond 1st Level)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-PRF-001 | A character's proficiency bonus depends on total character level across all classes (Multiclassing, verified: your proficiency bonus is always based on your total character level): +2 at levels 1 to 4, +3 at 5 to 8, +4 at 9 to 12, +5 at 13 to 16, +6 at 17 to 20. Monsters have the bonus built into their stat block: the engine reads their attack, save and skill bonuses and spell DCs from the stat block instead of recomputing them. | `prof_bonus = floor((level - 1) / 4) + 2`; monsters read the stat block field |
| RS-PRF-002 | The bonus is added to a given roll or number at most once, even if several rules grant it. | One proficiency component per roll; duplicates collapse |
| RS-PRF-003 | The bonus can be multiplied or divided before it is applied (doubled for expertise, halved for the bard's Jack of All Trades), but only once, and only the bonus is multiplied, never the whole check. Halves round down. | `component = floor(prof_bonus x multiplier)`, multiplier in 0, 1/2, 1, 2 |
| RS-PRF-004 | If you lack proficiency, an effect that multiplies the bonus gives nothing: 0 times anything is 0. | Multiplier 0 stays 0 under `MULTIPLY` |
| RS-PRF-005 | Attack rolls and saving throws do not normally multiply the bonus. Half proficiency (the bard's Jack of All Trades) applies to ability checks only (verified in the Bard entry: half the proficiency bonus, rounded down, on any ability check that does not already include the bonus). | Default multiplier 1 for proficient attacks and saves |
| RS-PRF-006 | Proficiencies come in kinds: skills, tools, saving throws, weapons, armor and languages. Each is stored with its source. The same proficiency from two sources counts once; where character creation would grant a duplicate, the character chooses a replacement of the same kind (Backgrounds, verified; the SRD states it for skill and tool proficiencies, E-22). | pc\_proficiency rows with kind, key and source; duplicates collapse; the replacement prompt follows a campaign setting (E-22) |

**Kernel impact.** A skill, save or attack total is a formula with a separate proficiency-multiplier component. The `MULTIPLY` modifier operation targets that multiplier (for example `skill:stealth:prof_multiplier`), never the final total, or expertise would double the ability modifier as well.

**Examples**

- Level 5, proficient, multiplier 1: +3. Expertise, multiplier 2: +6.
- Level 1, Jack of All Trades on a non-proficient skill, multiplier 1/2: floor(2 x 1/2) = +1.
- Not proficient, expertise granted: multiplier 0 x 2 = 0, so +0.

### 3.3 d20 tests and advantage (SRD: Using Ability Scores)

The three main rolls are ability checks, saving throws and attack rolls. Each rolls a d20, adds modifiers, and compares the total to a target number.

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-D20-001 | A test is d20 plus modifiers compared to a target: a Difficulty Class for checks and saves, Armor Class for attacks (Part B). A check meets its DC when the total equals or exceeds it. | `D20Test(target)`; success when `total >= target` |
| RS-D20-002 | The SRD gives no automatic success or failure on a natural 20 or 1 for ability checks and saving throws. The automatic hit and miss on 20 and 1 belongs to attack rolls (Part B). | Setting `autoOutcomeOnNat20Checks`, default off; the DM can turn on automatic success on a natural 20 and automatic failure on a natural 1 for checks, saves or both (engine decision E-1, decided) |
| RS-ADV-001 | With advantage you roll a second d20 and use the higher; with disadvantage, the lower. | Roll `2d20kh1` or `2d20kl1` |
| RS-ADV-002 | Several sources of advantage do not stack: you never roll more than one extra die. The same holds for disadvantage. | Collect sources, then reduce to one mode |
| RS-ADV-003 | If a roll has both advantage and disadvantage, you have neither and roll one die, even when one side has several sources and the other has one. | `mode = NORMAL` if both lists are non-empty |
| RS-ADV-004 | When a rule lets you reroll or replace a d20 and you rolled two, you replace only one of them, your choice. | Reroll targets a single die; result recomputed |
| RS-ADV-005 | The DM may grant advantage or impose disadvantage for circumstances. | DM action with a source label recorded in the log |
| RS-ADV-006 | Every source of advantage or disadvantage has a scope: the kind of roll (attack, ability check, saving throw), optionally one ability, and any conditions (for example disadvantage on attack rolls while frightened). Only sources whose scope matches the roll count. | AdvantageSource with a scope predicate and a label |
| RS-ADV-007 | Inspiration (awarded by the DM): a character has it or does not and cannot stockpile it. Spending it gives advantage on one attack roll, saving throw or ability check. Verified in the SRD Inspiration text: spending it gives advantage on one attack roll, saving throw or ability check, and a character can instead give its inspiration to another player as a reward for good roleplaying. | Boolean on the character; creates one advantage source when spent |

The roll result records the mode, every source of advantage and disadvantage by name, both dice, and which die was kept.

**Examples (forced dice)**

- Disadvantage, dice 17 and 5: use 5. Advantage, same dice: use 17.
- Two advantage sources, no disadvantage: still two dice, mode advantage.
- Two advantage sources and one disadvantage source: one die, mode normal.
- Advantage, dice 1 and 13; the roller rerolls the 1 and gets 9: the dice are now 9 and 13, keep 13.

### 3.4 Ability checks, contests, passive checks (SRD: Using Ability Scores)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-CHK-001 | The DM picks the relevant ability and a DC. Typical DCs: 5 very easy, 10 easy, 15 medium, 20 hard, 25 very hard, 30 nearly impossible. | DC presets in the UI; any integer allowed |
| RS-CHK-002 | Check total = d20 + ability modifier + proficiency component (if proficient in the relevant skill or tool) + other bonuses and penalties. | `skill:<name>` stat formula |
| RS-CHK-003 | Each skill is tied to an ability. Constitution has no skills. | Skill table below |
| RS-CHK-004 | Variant: the DM may pair a skill with a different ability for a particular check (for example Constitution with Athletics for a long swim). | Check request has an optional ability override |
| RS-CHK-005 | Contest: both sides make checks; the higher total wins. A tie leaves the situation as it was before the contest. | `ContestResult` is `WIN_A`, `WIN_B` or `TIE` |
| RS-CHK-006 | Passive check: 10 plus all modifiers that normally apply. Add 5 for advantage, subtract 5 for disadvantage. The result is called a score. | Computed without dice |
| RS-CHK-007 | Working together: the helper's effort gives the leader advantage. The helper must be able to attempt the task alone, and the DM decides when help is productive. In combat this is the Help action (Part B). | Help creates an advantage source for one check |
| RS-CHK-008 | Group check: everyone rolls; if at least half the group succeeds, the whole group succeeds. | `successes x 2 >= members` |
| RS-CHK-009 | Initiative is a Dexterity check, so any effect that applies to ability checks or Dexterity checks also applies to it (details in Part B). | Part B |
| RS-CHK-010 | A check request names the ability, an optional proficiency key (a skill or a tool), and a DC or an opponent. Tools are not skills: a tool proficiency can pair with any ability the DM calls for (for example thieves' tools with Dexterity). | Check request: ability, optional proficiency key, DC or contest |

Skills by ability: **Strength** Athletics. **Dexterity** Acrobatics, Sleight of Hand, Stealth. **Intelligence** Arcana, History, Investigation, Nature, Religion. **Wisdom** Animal Handling, Insight, Medicine, Perception, Survival. **Charisma** Deception, Intimidation, Performance, Persuasion. (18 skills.)

**Examples (forced dice)**

- Strength 16 (+3), proficient in Athletics at level 1 (+2), d20 = 11, DC 15: total 16, success.
- Contest: A rolls total 14, B rolls total 14: tie, nothing changes. A 17 against B 12: A wins.
- Passive Perception: Wisdom 15 (+2), proficient at level 1 (+2): 10 + 4 = 14. With advantage 19; with disadvantage 9.
- Group check: 4 of 8 succeed: success (8 >= 8). 3 of 8: fail. 3 of 5: success. 2 of 5: fail.

**More examples (checks)**

- A group of one succeeds only if that member succeeds.
- A DC 30 check with a total bonus of +0 cannot be met: there is no automatic success on a natural 20 (RS-D20-002), so the DM's view should say that no result can succeed.
- A Dexterity check with thieves' tools uses the Dexterity modifier plus the tool proficiency component (RS-CHK-010), and no skill is involved.

### 3.5 Saving throws (SRD: Using Ability Scores; spell DC to be verified in Spellcasting)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-SAV-001 | A save is d20 + ability modifier + proficiency component if proficient in that ability's saves + other modifiers. It can be affected by advantage and disadvantage. | `save:<ability>` stat formula |
| RS-SAV-002 | Every class grants proficiency in at least two saves; some monsters have save proficiencies. | Class and monster data |
| RS-SAV-003 | The DC comes from the effect. For a spell it is determined by the caster's spellcasting ability and proficiency bonus (verified: spell save DC = 8 + proficiency bonus + spellcasting ability modifier, and the spell attack modifier = proficiency bonus + spellcasting ability modifier). | Verified (E-6 decided) |
| RS-SAV-004 | What success and failure mean is defined by the effect: often no harm or reduced harm on success. | Effect definitions carry `onSuccess` and `onFailure` outcomes |
| RS-SAV-005 | An effect can force a save to succeed or fail automatically (for example some conditions make a creature automatically fail Strength and Dexterity saves; Part B). No d20 is rolled and the log records the override and its source. | SaveOverride with a source; result flagged AUTO\_FAIL or AUTO\_SUCCESS |
| RS-SAV-006 | Death saving throws and concentration checks are saves with their own rules (Parts B and E). | Parts B and E |

**Example (forced dice).** Target has Dexterity 14 (+2), not proficient. Effect DC 13. d20 = 10: total 12, fails.

### 3.6 Levels, experience and hit points (SRD: Beyond 1st Level, Using Ability Scores)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-LVL-001 | A character gains a level on reaching the experience total: level 2 at 300, 3 at 900, 4 at 2,700, 5 at 6,500, 6 at 14,000, 7 at 23,000, 8 at 34,000, 9 at 48,000, 10 at 64,000, 11 at 85,000, 12 at 100,000 (full table to level 20 in data). One award can cross several thresholds; level-up choices are then made one level at a time. | `class_level_def` or a shared advancement table |
| RS-LVL-002 | Advancement is by milestone by default: the DM decides when characters gain a level. XP advancement is an option the DM can select (planned for v1.5); the thresholds stay in the data. The MVP content cap is level 12; XP beyond it is kept but grants no level. | Campaign setting `advancement = XP or MILESTONE` ; milestone is the default and XP is a later option (engine decision E-4, decided) |
| RS-LVL-003 | Each level gives one more Hit Die. The hit point gain is the Hit Die roll plus the Constitution modifier (minimum 1), or the fixed value in the class entry (the die's average, rounded up: d6 gives 4, d8 gives 5, d10 gives 6, d12 gives 7), with the Constitution modifier added in both cases (verified in the class entries: Rogue d8 with 5, Fighter d10 with 6, Bard d8 with 5, Wizard d6 with 4, Paladin d10 with 6). | Store the raw die result (or the fixed value) per level; campaign setting for the hit point method: player's choice (default), fixed average only, or rolled only |
| RS-LVL-004 | If the Constitution modifier changes, the hit point maximum changes as though the new modifier had applied from level 1: it moves by the modifier difference times the character's level. | `maxHp = sum over levels of max(1, raw_i + conMod)` |
| RS-LVL-005 | Where a class grants an ability score improvement you raise one score by 2 or two scores by 1, never above 20. Feats are an optional rule: at an improvement a character may take a feat instead. A character can hold any number of feats, limited only by how many are granted (the DM can grant extras). The SRD ships one feat (Grappler); others come from packs and homebrew. A feat is content: its effects, prerequisites and any choices (such as which ability or skill) live in its definition. | `pc_asi` rows by class and class level |
| RS-LVL-006 | Level-one hit points use the class's rules (maximum of the Hit Die plus Constitution modifier); verified in the Rogue entry (E-7 decided). | Part D |
| RS-LVL-007 | Hit point maximum is a modifiable stat: the derived base (the sum of per-level gains) plus modifiers from effects, such as spells and features that raise it and drain effects that lower it. When the maximum rises, current hit points rise by the same amount; when it falls, current hit points are clamped to the new maximum if they exceed it (E-5). When a temporary increase ends, that counts as a fall: clamp only. A maximum reduced to 0 is resolved by the effect that did it. | maxHp stat with modifiers; current HP clamped to it |
| RS-LVL-008 | Proficiency bonus uses total character level. Class features that scale with level use the class level unless stated otherwise (verified in Multiclassing: proficiency bonus by total level; hit points from the higher-level entry after level 1; hit dice pooled by die size and tracked separately for different sizes; features gained per class level; same-named features such as Extra Attack, Channel Divinity and Unarmored Defense do not stack; spell slots from combined levels, with paladin and ranger levels counting half, rounded down). Hit dice are tracked per die size. | Class level rows; hit dice per die size |

**Examples (Fighter, d10, forced values)**

- Level 3, Constitution 14 (+2). Raw gains: level 1 is 10 (the maximum die, per Part D), level 2 is 6, level 3 is 4. Gains: 12, 8, 6. Maximum HP 26.
- Constitution rises to 16 (+3): gains become 13, 9, 7, so 29. That is +1 per level, three levels, +3, matching RS-LVL-004.
- Minimum 1: a d6 hit die with Constitution 6 (-2) and a raw roll of 1 gives max(1, -1) = 1.

**More examples (RS-LVL-007, E-5)**

- Maximum 20, current 20. A +5 maximum effect (like Aid) applies: 25 of 25. The character takes 10 damage: 15 of 25. The effect ends: maximum 20, current 15 (unchanged, since it does not exceed the maximum).
- Maximum 20, current 20. A drain effect lowers the maximum by 8: maximum 12, current 12.
- Constitution rises from 14 to 16 at level 3: the maximum rises by 3 (one per level), so current HP rises by 3.
- A character at 0 HP and dying when the maximum rises: current stays 0 under the exception; the DM heals normally.

### 3.7 Time (SRD: Combat, Adventuring; to be confirmed in Part B)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-TIM-001 | A combat round is about 6 seconds of game time, so 10 rounds make a minute. | `GameClock` in seconds; durations convert at 6 seconds per round |
| RS-TIM-002 | One minute is 10 rounds and one hour is 600 rounds; this is a fixed rules constant (a round is about 6 seconds). Effects whose duration is given in rounds, or in minutes while in combat, are counted in rounds; time-based durations count on the game clock. In combat, each completed round advances the game clock by a DM-set amount, default 6 seconds (engine decision E-9, decided). | Round and clock durations are separate kinds; a setting sets the clock advance per round |

### 3.8 Dice and roll composition (for Build Step 2)

The dice engine must support these rules. Rules that come from a class, spell or item are marked for verification in their own part.

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-DIE-001 | A roll is an ordered list of terms. A term is a dice group (dice of one size, with optional keep, drop, reroll or floor modifiers) or a fixed whole number, added or subtracted. Examples: d20 + 3 + 2; d8 + 3d6 + 3 (weapon die, extra damage dice, modifier); d20 + 3 - d4. | `RollResult` holds a list of `RollTerm`s, each with a label, a source, its dice and a signed subtotal; the total is their sum |
| RS-DIE-002 | Each term keeps its own label and source (for example the Dexterity modifier, proficiency, Bless) so the log can show where every number came from. | Terms are never merged before logging |
| RS-DIE-003 | The natural roll is the face of the d20 before any modifier; with advantage or disadvantage it is the kept die. Natural results drive critical hits and automatic misses (Part B). | `naturalD20` on the result |
| RS-DIE-004 | When a rule lets you reroll a die, the new result replaces the old one and you must use it, unless the rule says you may choose. A given die is rerolled at most once per use of the rule. Great Weapon Fighting works this way in SRD 5.1 (reroll a 1 or 2 on a damage die for a two-handed melee attack and use the new roll); the halfling's Lucky trait rerolls a natural 1 on a d20. Both verified: Great Weapon Fighting is a Fighter fighting style that lets you reroll a 1 or 2 on a damage die for a two-handed melee attack and use the new roll, and the halfling's Lucky trait rerolls a 1 on a d20 attack roll, ability check or saving throw and uses the new roll. | Reroll modifier: threshold, once per die, replace |
| RS-DIE-005 | A rule can treat a low d20 result as a minimum value (the rogue's Reliable Talent treats a roll of 9 or lower as 10 on checks that add the proficiency bonus). With advantage or disadvantage the floor applies to each d20 before the kept die is chosen. Verified (Rogue, Reliable Talent at level 11): whenever you make an ability check that lets you add your proficiency bonus, you can treat a d20 roll of 9 or lower as a 10. | Floor modifier applied per die |
| RS-DIE-006 | A critical hit doubles the number of damage dice, not the modifiers. Extra damage dice from features such as Sneak Attack double as well (Part B). | Each dice term carries a `doublesOnCrit` flag |
| RS-DIE-007 | Spells and features can add bonus dice to a specific roll (Bless adds 1d4 to attack rolls and saving throws; Bardic Inspiration adds a die to a check, attack or save; Guidance adds 1d4 to a check) and penalty dice (Bane subtracts 1d4). These are roll bonuses (section 3.9), added as extra terms. | Extra labeled terms, positive or negative |
| RS-DIE-008 | Whether a damage or healing total can drop below 0 is settled in Part B. A check or save total can be any integer. | Clamp only where a rule says so |
| RS-DIE-009 | Dice limits are an engine safety measure, not a game rule: die size 2 to 1,000, 1 to 100 dice per group, and caps on total dice and expression length. | Reject with `VALIDATION_FAILED` |
| RS-DIE-010 | Any roll can be secret (DM only); the result is recorded with an audience. | `Audience` on the roll event |

**Examples (forced dice)**

- Attack with Bless: d20 = 12, ability modifier +3, proficiency +2, Bless d4 = 3. Terms 12, +3, +2, +3; total 20; natural roll 12.
- Attack with Bane: d20 = 12, +5, Bane d4 = 2. Total 12 + 5 - 2 = 15.
- Damage: d8 = 5, extra 3d6 = 2, 4, 6, modifier +3. Total 5 + 12 + 3 = 20.
- Advantage with dice 3 and 18 and a +5 modifier: natural roll 18, total 23.
- Reliable Talent with advantage: dice 7 and 15 become 10 and 15, keep 15. Dice 7 and 4 both become 10, keep 10.
- Great Weapon Fighting: damage die shows 2, the reroll shows 1: use 1, with no further reroll.

### 3.9 Modifiers and stacking (for Build Step 3)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MOD-001 | Two kinds of modifier. A stat modifier is a persistent change to a computed stat (armor class, speed, an ability score, maximum HP, a skill total) and is part of computing it. A roll bonus applies only when a particular roll is made; it can be a fixed number or dice and can be consumed. | `Modifier` (stat) and `RollBonus` (roll) are separate types |
| RS-MOD-002 | Stacking: the effects of different spells or features add together while they overlap; the same spell or feature affecting the same target more than once does not stack with itself. Only the most potent instance applies, or the most recent if they are equal. (Spellcasting, combining magical effects, verified: the effects of different spells add; the same spell cast several times does not combine, and the most potent effect applies, as with two clerics casting Bless on one target. When castings are equally potent, the most recent applies (verified). Suppressed instances are kept, and an earlier one resumes if the applying one ends first. The DMG errata extends the rule to same-named game features (class features, items), so it applies to every modifier source with the same name.) | Every modifier carries a `stackKey` (its source identity) and a comparable potency |
| RS-MOD-003 | Order for stat modifiers: set, then add, then multiply (round down), then minimum and maximum, as decided in DEC-021. An effect that sets a score only if it is higher (the giant-strength belts, verified: the score changes to the belt's score, and the belt has no effect if the wearer's score is already equal or higher; it requires attunement) is a maximum operation. | Fixed pipeline order |
| RS-MOD-004 | Lifetime: a modifier is permanent (with a recorded source, such as a species trait or a consumed item), lasts while an item is worn or attuned, lasts for a duration (rounds, until the end of a turn, game time, until a rest), or lasts until used. Ending the source ends the modifier. A permanent effect is never undone retroactively by a later change elsewhere. | Duration kinds from the architecture |
| RS-MOD-005 | The proficiency bonus is never stacked: it is one component (RS-PRF-002) however many modifiers mention it. | One component per roll |
| RS-MOD-006 | The DM can add a labeled one-off modifier, roll bonus, advantage or disadvantage to any roll; it is recorded in the log with its source. | DM override action |

**Examples**

- Two clerics each cast Bless on the same fighter: one +1d4 applies. Bless and Bardic Inspiration on the same attack: both apply, a d4 and a Bardic die.
- Armor class 15 from armor, +1 from a ring and +2 from a spell: 18, because different sources add. The same spell cast twice still adds +2 once.
- Strength 14 and an effect that sets the score to 19 only if higher: 19. A character with 21 stays at 21.

### 3.10 Dice notation, limits and the roll API (for Build Step 2)

This section answers the open implementation questions for the dice engine. The decisions in it are **decided (D-1 to D-6 in 3.11, accepted 2026-10-21, and D-7 to D-13, accepted 2026-10-30); changing one later** only changes parser constants or the error table, not the design.

**Two layers.** (1) The **parser**, `DiceExpression.parse(String)`, turns text into an expression of unlabeled terms and can print its canonical form. It knows nothing about labels or sources. (2) The **composition API**, `RollPlan`, lets the kernel and engine assemble a roll from labeled terms (a d20, an ability modifier, a Bless die, a damage die) and evaluate it. Rolling a bare expression typed by a DM (`2d6+3`) uses the same machinery: each term is labeled with its own canonical text and has no source.

**Grammar** (letters are case-insensitive; the canonical form is lowercase)

```
expression = [ ws ] [ sign ] term { ws sign ws term } [ ws ] ;
sign       = '+' | '-' ;
term       = dice-group | integer ;
integer    = digit { digit } ;
dice-group = [ count ] 'd' size { modifier } ;
count      = digit { digit } ;          (omitted means 1)
size       = digit { digit } ;
modifier   = keep-drop | reroll | floor ;
keep-drop  = ( 'kh' | 'kl' | 'dh' | 'dl' ) number ;
reroll     = 'r<=' number ;
floor      = 'min' number ;
ws         = { ' ' | tab } ;
```

**Modifier meanings**

| Token | Meaning | Example |
| --- | --- | --- |
| `khN` | keep the N highest dice | `2d20kh1` (advantage) |
| `klN` | keep the N lowest dice | `2d20kl1` (disadvantage) |
| `dhN` | drop the N highest dice | `4d6dh1` |
| `dlN` | drop the N lowest dice | `4d6dl1` (ability scores) |
| `r<=N` | reroll once any die showing N or less; the new result is final | `1d8r<=2` (Great Weapon Fighting), `1d20r<=1` (halfling Lucky) |
| `minN` | any die showing less than N counts as N | `1d20min10` (Reliable Talent) |

**Evaluation order for one dice group** (fixed, whatever the order the modifiers were typed in): roll all the dice, in order; reroll pass; floor pass; keep or drop selection; sum the kept values. When several dice tie for selection, the earlier die is the one selected: `kh` keeps the earlier of equal highest dice, `dh` drops the earlier of equal highest dice, and likewise for the lowest.

**Validity rules**

| Part | Valid range or rule |
| --- | --- |
| Count | 1 to 100 (0 is invalid) |
| Die size | 2 to 1000 |
| `kh` and `kl` number | 1 to the count |
| `dh` and `dl` number | 1 to the count minus 1 (at least one die must remain) |
| `r<=` number | 1 to the size minus 1 |
| `min` number | 2 to the size |
| Modifiers | each kind (keep-drop, reroll, floor) at most once per group; at most one keep-drop token per group (so `kh1dl1` and `kh1kh1` are conflicts); modifiers are only allowed on dice groups |
| Integer literal | 0 to 1,000,000 |
| First term | may carry a leading sign (`-d4`, `+3`); a sign with no term after it, or two signs in a row, is a syntax error |
| Operators | only `+` and `-`; no multiplication, division, parentheses or percentile dice (`d%` is a syntax error) |

**Limits** (a `DiceLimits` record with these defaults; one place to change them)

| Limit | Default |
| --- | --- |
| Die size | 2 to 1000 |
| Dice per group | 100 |
| Total dice per expression (all groups, before keep and drop) | 200 |
| Total dice after critical doubling | 400 |
| Terms per expression | 20 |
| Expression length | 200 characters |
| Integer literal | 1,000,000 |

Within these limits the largest possible magnitude is about 20.2 million, far below the 32-bit integer limit, so overflow cannot happen; arithmetic still uses exact operations (`addExact`) and throws if it ever did.

**Whitespace, case and characters.** ASCII only. Spaces and tabs are allowed at both ends and around signs, never inside a term: `2 d6`, `d 6` and `kh 1` are syntax errors. Letters in any case (`2D20KH1` equals `2d20kh1`). Leading zeros in numbers are accepted (`007` is 7). Non-ASCII digits and look-alike characters are syntax errors. An empty or blank input is its own error.

**Canonical form** (`toString()`): lowercase, no whitespace, an explicit count, modifiers in the order keep-drop, reroll, floor, and no leading `+`. `D8 + 3D6 + 3` prints as `1d8+3d6+3`, and parsing a canonical form gives an equal expression (a round-trip property).

**Errors.** The parser throws `DiceParseException` (an unchecked exception in the pure engine, extending `IllegalArgumentException`) carrying a stable code, the 0-based position in the input, the offending token, and for limit errors the limit's name and value. The web layer maps it to `VALIDATION_FAILED` (HTTP 400, a caller fault, never logged at ERROR) with the dice error code in the details.

| Code | When |
| --- | --- |
| `DICE_EMPTY` | the input is empty or only whitespace |
| `DICE_SYNTAX` | an unexpected character or token: whitespace inside a term, doubled or trailing sign, unknown modifier, non-ASCII character, `d%` |
| `DICE_VALUE_OUT_OF_RANGE` | a number outside its allowed range; the detail names the field (count, size, keep-drop number, reroll threshold, floor, literal) |
| `DICE_MODIFIER_CONFLICT` | a modifier kind repeated, or more than one keep-drop token on a group |
| `DICE_LIMIT_EXCEEDED` | a total limit exceeded; the detail names it (total dice, terms, length, dice after doubling) |

Evaluating a valid expression cannot fail. A random source that returns a value outside its bound is a programming error (`IllegalStateException`).

**Randomness and draw order**

- `RandomSource.nextInt(bound)` returns 0 to bound minus 1. A die face is the draw plus 1.
- Terms are evaluated left to right. Within a dice group all initial dice are drawn first, in order, then the rerolls for the eligible dice, in die order, one draw each.
- Tests use `ForcedDice`: it takes die faces (1 to the die size) and returns face minus 1, fails loudly if a face is out of range for the requested size, and the test asserts that every forced value was consumed. Statistical tests use `SeededRandom(seed)`. Production uses a `SecureRandom`-backed source.

**Result model**

```java
public record DieRoll(int rolled, Integer rerollFace, int value, boolean kept, boolean rerolled, boolean floored, boolean critical) {}
// rolled = the first draw; rerollFace = the replacement face if rerolled, else null; value = the effective value after reroll and floor; kept = counted in the subtotal; critical = added by critical doubling (D-15)

public record RollTerm(String label, String source, int sign, String expression,
                       List<DieRoll> dice, int fixed, int subtotal, boolean doublesOnCrit) {}
// subtotal = sign x sum of kept values (dice term) or sign x fixed (fixed term)

public enum RollMode { NORMAL, ADVANTAGE, DISADVANTAGE }
public record RollResult(List<RollTerm> terms, int total, Integer naturalD20, RollMode mode) {}
// total = sum of subtotals; naturalD20 = the effective value (after reroll and floor) of the kept d20 test die, if the plan has one
```

**Composition API**

```java
RollPlan plan = RollPlan.builder()
    .d20(RollMode.ADVANTAGE)                       // 2d20kh1; DISADVANTAGE gives 2d20kl1; NORMAL gives 1d20
    .fixed("Dexterity modifier", "ability:DEX", 3)
    .fixed("Proficiency", "proficiency", 2)
    .bonusDice("Bless", "spell:bless", DiceExpression.parse("1d4"))
    .penaltyDice("Bane", "spell:bane", DiceExpression.parse("1d4"))
    .build();
RollResult result = plan.roll(rng);
```

- `d20(mode)` adds the d20 test die term and marks it, so the result carries `naturalD20`. It accepts an optional floor setting for that die. It does not offer a group reroll: replacing one die of an advantage or disadvantage pair is the separate replaceD20 operation (D-8).
- `dice(label, source, expression, doublesOnCrit)` adds one labeled dice group (an expression with exactly one dice group and no fixed terms; a composite expression is rejected, D-9); `fixed` adds a signed fixed number (the term keeps the sign and the magnitude separately, D-11); `bonusDice` and `penaltyDice` add a positive or negative dice term.
- `doubleDiceForCritical()` returns a new plan `for the chosen critical dice mode (D-14). By default each flagged term is rolled again and added: a plain group has its count doubled, and a group with a keep or drop token gets an independent copy placed right after it. The limit on dice after doubling is re-checked`.
- `DiceExpression.roll(rng)` is the standalone path described above.
- **Not part of Step 2:** the d20 test comparison (hit, miss, critical, DC), contests, advantage source resolution, probability distributions (FR-DIC-008), and anything from combat. Step 2 stops at producing a correct `RollResult`.

**Test contract (minimum)**

| Case | Expected |
| --- | --- |
| `d20` | valid; canonical `1d20` |
| `2D20KH1` | valid; canonical `2d20kh1` |
| `4d6dl1` | valid |
| `d8+3d6+3` | valid; canonical `1d8+3d6+3` |
| `d20+3-d4` | valid; the `d4` term is negative |
| `  -d4 ` | valid; canonical `-1d4` |
| `1d20min10` | valid |
| `2d6r<=2` | valid |
| `100d1000` | valid (the boundary) |
| empty string and `   ` | `DICE_EMPTY` |
| `0d6`, `1d1`, `1d1001`, `101d6` | `DICE_VALUE_OUT_OF_RANGE` |
| `4d6kh5`, `4d6dl4`, `1d20r<=20`, `1d20min21`, `1000001` | `DICE_VALUE_OUT_OF_RANGE` |
| `4d6kh1dl1`, `4d6kh1kh1` | `DICE_MODIFIER_CONFLICT` |
| `2 d6`, `d`, `1d`, `d%`, `2d6++3`, `2d6+` | `DICE_SYNTAX` |
| an Arabic-Indic digit in a count | `DICE_SYNTAX` |
| `100d6+100d6+1d6` | `DICE_LIMIT_EXCEEDED` (total dice) |
| 21 terms, or 201 characters | `DICE_LIMIT_EXCEEDED` |

**Worked examples with forced dice** (faces in draw order)

| Example | Faces | Expected |
| --- | --- | --- |
| Attack with Bless: d20, +3, +2, bonus 1d4 | 12, 3 | terms 12, +3, +2, +3; total 20; natural 12 |
| Attack with Bane: d20, +5, penalty 1d4 | 12, 2 | total 15 |
| Damage `1d8+3d6+3` | 5, 2, 4, 6 | 5 + 12 + 3 = 20 |
| Advantage `2d20kh1` with +5 | 3, 18 | natural 18; total 23; the second die is kept |
| Reliable Talent `2d20kh1min10` | 7, 15 | values 10 and 15; keep 15 |
| Reliable Talent, both low | 7, 4 | values 10 and 10; the earlier die is kept; total 10 |
| Great Weapon Fighting `1d8r<=2` | 2, 1 | rerolled once; the value is 1 and is used |
| Reroll once only `1d8r<=2` | 2, 2 | rerolled once; the value stays 2 |
| Draw order `2d6r<=2` | 1, 5, 6 | initial dice 1 and 5; die 1 rerolls and draws 6; values 6 and 5; total 11 |
| Tie selection `2d20kh1` | 10, 10 | the first die is kept; the second is dropped |
| Critical doubling of `1d8+3d6+3` with the dice flagged | counts 1 and 3 become 2 and 6 | the +3 is unchanged |
| Reroll then floor, 1d20r<=9min10 | 2, 7 | rolled 2, rerollFace 7, value 10; naturalD20 is 10; all three faces are kept in the history |
| Replace one d20 of a pair: advantage 2d20kh1, then replaceD20 on die 0 (a rule such as a reroll of a natural 1) | 1, 14, then 9 | die 0 becomes rolled 1, rerollFace 9, value 9; die 1 (14) stays kept; naturalD20 is 14 |
| Signed fixed term, fixed(label, source, -2) | none | sign -1, magnitude 2, subtotal -2 |

Statistical check (seeded): 200,000 rolls of a d20; each face appears between 9,500 and 10,500 times (the expected value is 10,000, so this is about five standard deviations).

### 3.11 Dice engine decisions (decided)

| ID | Question | Decision |
| --- | --- | --- |
| D-1 | Reroll and floor syntax | `r<=N` (reroll once) and `minN` (floor), with keep and drop as `khN`, `klN`, `dhN`, `dlN`; fixed evaluation order; each kind at most once per group |
| D-2 | Concrete safety limits | 200 dice, 20 terms and 200 characters per expression; 400 dice after doubling; literals up to 1,000,000 |
| D-3 | Where labels and sources come from | The parser is label-free; the `RollPlan` composition API attaches labels and sources; a bare expression is labeled by its own term text |
| D-4 | Whitespace, case, repeated or conflicting modifiers, keep and drop counts, overflow | As in section 3.10: ASCII only, whitespace around signs only, case-insensitive, strict ranges, conflicts rejected, overflow impossible within limits |
| D-5 | Error contract | One `DiceParseException` with five stable codes, mapped to `VALIDATION_FAILED` at the web boundary |
| D-6 | Draw order and forced-dice testing | Initial dice then rerolls per group, left to right; `ForcedDice` takes faces and asserts that all were consumed |
| D-7 | Reroll history in the result | DieRoll gains a nullable rerollFace. rolled is the first face, rerollFace the replacement, value the effective value after reroll and floor, so all faces survive |
| D-8 | Rerolling one d20 of an advantage or disadvantage pair (RS-ADV-004) | The group reroll r<=N applies to every eligible die in a dice group. For d20 tests, RollPlan.d20() offers only the floor, and RollResult.replaceD20(index, rng) replaces exactly one die (the new face is final and the die is marked rerolled); the caller decides eligibility and who chooses |
| D-9 | What RollPlan.dice() accepts | Exactly one dice group: no fixed terms and no sums. A composite expression is rejected; a fromExpression helper expands a typed expression into separate terms |
| D-10 | Limits across a whole plan | The 20-term, 200-dice and 400-dice-after-doubling limits count across the entire plan; at most one d20 test die per plan |
| D-11 | Signed fixed terms | fixed(label, source, int) takes a signed integer; the term stores the sign and the magnitude separately; the subtotal is sign times magnitude |
| D-12 | What naturalD20 is after a reroll and a floor | The effective value of the kept d20 test die, after any reroll and floor. A rule that says to treat a roll as a 10 makes it a 10 for later rules; the history keeps every face. Research: Reliable Talent says to treat a roll of 9 or lower as a 10, official guidance is that a 1 or 20 has no special effect on ability checks, and community consensus reads the treated roll as the roll. The Rogue chapter wording is verified (DEC-087) |
| D-13 | Implementation checks | As listed under the table |
| D-14 | Critical doubling for a dice group that has a keep or drop token | Decided: a campaign setting for the critical dice mode. ROLL\_AGAIN\_ADD (the default, the official reading 'roll the dice an additional time'): a flagged plain group has its count doubled, and a flagged group with a keep or drop token is rolled again as an independent copy placed right after it, so each copy keeps its own rule. EXTRA\_DIE\_ONLY (a homebrew style): a flagged group gains one extra die with the same keep or drop numbers. REJECT: a flagged group with a keep or drop token is refused when content is defined (CONTENT\_INVALID); if one is met at run time it is not doubled and the log records critical\_not\_applied. Reroll and floor modifiers carry over in every mode |
| D-15 | Recording which dice came from critical doubling | Decided: DieRoll gains a boolean critical, true for dice added by critical doubling, so features that reroll or choose between the base roll and the critical dice (such as Savage Attacker, RS-DMG-018) work without changing the result model later. It is cheap to add during Step 2 and costly afterwards |

**Implementation checks (D-13)**

- **Numeric tokens:** a run of more than 9 digits is rejected before parsing with `DICE_VALUE_OUT_OF_RANGE`; a `NumberFormatException` must never escape.
- **Error precedence:** the empty check comes first, then the length limit; scanning then goes left to right and the first syntax, range or conflict error wins; the term-count limit triggers at the 21st term; the total-dice limit is checked after the whole expression is parsed.
- **Whitespace:** only space and tab; newline, carriage return, other control characters and non-breaking spaces are `DICE_SYNTAX`.
- **Immutability:** expressions, terms and results are immutable, and every collection is a defensive copy (`List.copyOf`).
- **Builder validation:** an empty plan is rejected at build time; a label must be non-blank, at most 64 characters and free of control characters; a source may be null but follows the same rule when present; API misuse throws `IllegalArgumentException` or `IllegalStateException`, never `DiceParseException`.
- **Canonicalization:** leading zeros normalize (`007` prints as `7`).
- **Tests to add:** tie selection for `kh`, `kl`, `dh` and `dl`; reroll and floor preserving all faces and the `naturalD20` rule; `replaceD20`; plan-wide limits including `100d6+100d6+1d6`; oversized numeric tokens; newline-only input.

## 4. What Part A means for the engine (Build Steps 2 and 3)

| Area | Requirement |
| --- | --- |
| Dice (Step 2) | Roll results record mode, sources, every die and which was kept; advantage is `2d20kh1`, disadvantage `2d20kl1` |
| Kernel (Step 3) | `modifier` uses floor division; proficiency is a separate component with a multiplier stat; `MULTIPLY` targets the multiplier, not the total |
| Kernel (Step 3) | `maxHp` is derived from stored raw hit die results and the current Constitution modifier |
| Kernel (Step 3) | Passive scores, contests and group checks are pure functions with no dice |
| Tests | Every rule above has at least one worked example as a test; the negative-modifier case is mandatory |
| Dice (Step 2) | A roll is a list of labeled, signed terms (dice groups or fixed numbers), not one expression plus one modifier; results record every term, the natural d20, reroll once per die, a d20 floor, a crit-doubling flag per dice term, bonus and penalty dice, an audience, and enforced limits (section 3.8). |
| Kernel (Step 3) | Two modifier types (stat modifiers and roll bonuses); stacking by source with the most-potent rule; fixed order set, add, multiply, min and max; durations and lifetimes (section 3.9). |
| Kernel (Step 3) | Ability score model (DEC-074): base plus modifiers, per-ability maximum (default 20), ceiling setting (default 30), floor 0, ability-at-zero event, improvements clamped at the maximum. |
| Kernel (Step 3) | Advantage and disadvantage sources carry a scope (roll kind, ability, conditions); saves can be forced to succeed or fail; checks name an optional proficiency key (skill or tool). |
| Kernel (Step 3) | Proficiency kinds (skills, tools, saves, weapons, armor, languages) with sources and duplicate collapse; monster stat blocks supply final numbers instead of recomputation. |
| Kernel (Step 3) | Maximum HP is a stat with modifiers (derived base plus effects); current HP is clamped to it; hit dice are tracked per die size; durations are stored in game seconds. |

## 5. Engine decisions and open points to confirm

| ID | Question | Decision |
| --- | --- | --- |
| E-1 | Automatic success or failure on natural 20 or 1 for checks and saves | Decided: off by default, as the SRD gives none; a campaign setting lets the DM enable it for ability checks, saving throws or both |
| E-2 | Feats (Grappler is the only one in the SRD) | Decided (DEC-073): supported, several per character, content-driven; campaign setting feats.enabled, default on. Prerequisite and choice details go in Part D and the content schema. |
| E-3 | Fixed or rolled hit points per level | Decided (player chooses): the player chooses per level between the rolled value and the fixed average (the rulebook's own default), and the DM can force either through a campaign setting. In one forum poll of 121 groups, about 44% let players choose, 26% force the average and 9% force rolls (21% other). Variants such as rolling with a minimum of half the die are common but not built in for version 1. |
| E-4 | Level-up by XP or milestone | Decided: milestone is the default; XP advancement is a DM-selectable option planned for v1.5, with the XP column and thresholds kept in the data model from v1 |
| E-5 | Current HP when maximum changes | Decided: when the maximum rises, current HP rises by the same amount; when it falls, current HP becomes the new maximum only if it was higher; when a temporary increase ends, clamp only. Exception (decided): a creature at 0 HP is not raised, so a stat change cannot silently end a dying state |
| E-6 | Spell save DC formula and spell attack bonus | Decided: DC = 8 + proficiency bonus + spellcasting ability modifier; spell attack modifier = proficiency bonus + spellcasting ability modifier (verified in the SRD spellcasting text) |
| E-7 | Level-one hit points | Decided: the maximum of the hit die plus the Constitution modifier (verified in the Rogue entry) |
| E-8 | An ability improvement that would pass the maximum | Decided: warn the player before the improvement is applied (the choice screen shows that the score would pass the maximum and what would be lost) so they can reallocate; if they proceed, apply up to the maximum and lose the excess |
| E-9 | Game clock during combat | Decided: two separate values. The rules constant stays 6 seconds per round (10 rounds per minute) because every SRD duration is calibrated to it. The clock advance per completed round is a DM setting, default 6 seconds, including 0 for manual only. Each clock advance runs ExpireAfflictions (RS-MAD-007), and the end of a long rest offers the saving throws of diseases (RS-MAD-012). Neither adds a rest rule. |
| E-10 | Ability score generation methods (standard array, point buy, rolling 4d6 and dropping the lowest) | Decided: version 1 ships a built-in list of common-practice methods (standard array, point buy, roll 4d6 drop lowest, roll 3d6, manual entry by the DM) from which the DM selects per campaign; DM-created methods are version 2. Not found in the SRD text read so far; verify |
| E-11 | Roll bonuses (including dice) as a separate type from stat modifiers | Decided: two types (RS-MOD-001) |
| E-12 | Stacking rule for repeated effects | Decided: the same source does not stack with itself (most potent, then most recent); different sources add (RS-MOD-002) |
| E-13 | A d20 floor effect combined with advantage or disadvantage | Decided: apply to each die before choosing the kept one (RS-DIE-005) |
| V-1 | Cleared 2026-10-30: Inspiration, Multiclassing (proficiency by total level, spell slots), Backgrounds, the Rogue, Fighter and Bard entries, Spellcasting (spell DC, spell attack, combining effects) and the belt and manual wording were verified against SRD text | Done; two small residuals are tracked in V-3 |
| E-22 | Scope of the duplicate proficiency replacement rule (RS-PRF-006) | Decided (broad reading by default): apply the SRD rule to skill and tool proficiencies from any two sources (the character picks a different proficiency of the same kind). Some tables apply it only to overlaps with the background, so a campaign setting offers broad (default), background only, or off |
| V-3 | Small residual verification items after clearing V-1 (cleared 2026-11-14) | Cleared: (1) the tie-break is verified: the most recent effect applies when castings are equally potent; (2) multiclass hit dice pooling by die size and features by class level are verified; (3) the Bard (d8, Dexterity and Charisma saves), Wizard (d6, Intelligence and Wisdom saves) and Paladin (d10, Wisdom and Charisma saves) entries are verified, with the 1st-level and fixed hit point values following the stated rule |

## 6. Next

Part B (combat) is drafted in section 7 and gates the combat core. Part D (classes) and Part E (spellcasting) follow; their rules for the five MVP classes depend on Parts A to C.

## 7. Part B: Combat (SRD: Combat, Conditions)

Part B covers the order of combat, turns, movement, actions, attacks, cover, damage, healing, death and conditions. It was written from the full SRD Combat and Conditions chapters. It reuses Part A (checks, saves, advantage, modifiers, dice) by rule ID. Mounted and underwater combat are deferred to v2.

### 7.1 Order of combat

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-CMB-001 | Combat runs in rounds of about 6 seconds; in each round every participant takes one turn in initiative order. The steps: determine surprise, establish positions, roll initiative, take turns, repeat rounds until the fighting stops. | Encounter state machine (architecture 4.7); round counter; clock advance per RS-TIM-002 |
| RS-CMB-002 | Surprise: the DM decides who might be surprised. If neither side tries to be stealthy, they notice each other. Otherwise the Stealth checks of those hiding are compared with each opposing creature's passive Perception, and a creature that does not notice a threat is surprised. Surprise is per creature: some members of a group can be surprised and others not. | `surprised` flag per combatant; the engine suggests the comparison and the DM confirms; on a tie the DM decides whether the hider is noticed (E-14, decided) |
| RS-CMB-003 | A surprised creature still rolls initiative, but it cannot move or take an action on its first turn and cannot take a reaction until that turn ends. | Flag cleared at the end of its first turn |
| RS-CMB-004 | Initiative: every participant makes a Dexterity check. The DM may make one roll for a whole group of identical creatures, which then act together. Combatants act from the highest total to the lowest and the order stays the same every round. A creature with lair actions also has a LAIR entry at count 20 that belongs to no combatant (RS-MON-034). | `initiative` stat (Dexterity check formula, RS-CHK-009); optional group roll |
| RS-CMB-005 | Ties: the DM decides among tied DM-controlled creatures; players decide among their own tied characters; the DM decides between a monster and a player character. Optionally the tied combatants each roll a d20 and the highest goes first. A LAIR entry loses every tie (RS-MON-034). | Tie prompt with an optional roll-off (DEC-026) |
| RS-CMB-006 | A combatant who joins a running combat is not covered by the SRD. Community practice is to roll its initiative and weave it into the order; some DMs have it join at the end of the round and act from the next round. The engine does the first by default, and the DM can choose join options (RS-CMB-007) (E-15). | Insert with a tie prompt; it acts when its place next comes up |
| RS-CMB-007 | Join options chosen by the DM when a combatant joins a running combat: the initiative roll mode (normal, advantage, disadvantage, or a fixed value); the timing (at its initiative, acting when that place next comes up, or at the end of the current round with its first turn in the next); starting conditions (for example prone for a creature thrown into the fight); and a first-turn restriction (none, or surprised as in RS-CMB-003, for a creature that enters unaware). A joiner never acts twice in one round. | JoinOptions on the join action; default is a normal roll, at its initiative, no conditions, no restriction |
| RS-CMB-008 | Surprise belongs to the start of combat. A creature that arrives later and attacks from hiding is a hidden attacker with advantage against creatures that cannot see it (RS-ATK-005), not a surprise. As an optional house rule the DM can mark chosen combatants as caught off guard by a mid-combat arrival, so they cannot act or react until their next turn (RS-CMB-003 applied to them). | Hidden state from the Stealth check; optional campaign setting, default off |
| RS-CMB-009 | Reinforcements arriving together roll as a group (RS-CMB-004). Summoned or conjured creatures follow their spell's own initiative text (Part E). A combatant that leaves and returns keeps its original place in the order unless the DM resets it. | Group join; the spell text decides for summons |

### 7.2 Your turn

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-CMB-010 | On its turn a creature may move up to its speed and take one action, in either order, and may do less or nothing. | `TurnBudget` |
| RS-CMB-011 | A bonus action exists only when a feature, spell or ability grants one. At most one bonus action per turn, taken when the creature chooses unless the ability says otherwise. Anything that stops a creature from taking actions also stops bonus actions. | `bonusActionAvailable`, granted by features |
| RS-CMB-012 | Free activity: brief communication, and one interaction with an object or feature of the environment during the move or the action (open a door, draw a weapon as part of an attack). A second object interaction takes the Use an Object action. The DM may require an action for awkward interactions. | `objectInteractionAvailable` |
| RS-CMB-013 | A reaction is an instant response to a trigger, on any creature's turn. A creature has one reaction per round: after taking one it cannot take another until the start of its next turn. A reaction that interrupts another creature's turn lets that creature continue its turn afterwards. | `reactionAvailable`, reset at the start of the creature's own turn; reaction window (architecture 4.7, 5.4) |
| RS-CMB-014 | A creature that cannot take actions or reactions (incapacitated, surprised on its first turn) has those removed from its turn budget. | Budget built from the creature's conditions |
| RS-REA-001 | The reaction window is the engine's one mechanism for pausing an action to let someone choose. It opens at defined points of an action: when a creature leaves a reach; when a creature attacks (before the roll); after an attack roll, before the outcome; after a hit is confirmed, before damage; after damage is rolled, before it is applied; after a saving throw roll and after a failed save; after an ability check roll; when a spell is cast; after damage is taken; when a readied trigger occurs; and at the end of any creature's turn (legendary actions, RS-MON-031). It opens only if at least one decider has an eligible option; otherwise the action resolves with no pause. There is no second kind of window (decision E-46). The canonical point names are ON\_MOVE\_STEP, BEFORE\_ATTACK\_ROLL (W0), AFTER\_ATTACK\_ROLL (W1), AFTER\_HIT (W2), AFTER\_DAMAGE\_ROLL (W3), AFTER\_SAVE\_ROLL (WS1), AFTER\_SAVE\_FAIL (WS2), AFTER\_CHECK\_ROLL (WC), ON\_CAST, AFTER\_DAMAGE\_TAKEN and ON\_READIED\_TRIGGER and ON\_TURN\_END, and from version 1.5 ON\_FALL; the older feature trigger names are aliases (ON\_ATTACK\_ROLL is AFTER\_ATTACK\_ROLL, ON\_DAMAGE\_ROLL is AFTER\_DAMAGE\_ROLL, ON\_SAVE is AFTER\_SAVE\_ROLL, ON\_MOVE is ON\_MOVE\_STEP, ON\_DAMAGE\_TAKEN is AFTER\_DAMAGE\_TAKEN). The database check and the engine use only the canonical names (E-68). | \`AwaitingReaction(pending, events)\`; the points of Ruleset section 9.4 plus the movement, casting and readied points |
| RS-REA-002 | Each option has a cost and an effect. Cost: the creature's reaction, a resource (a spell slot, a Bardic Inspiration use), legendary action points (RS-MON-030), or nothing. Effect: change the roll or the damage, start a new action (an opportunity attack, a reaction spell), or cancel the action. A creature that has used its reaction this round is offered only options that cost no reaction; when a creature takes an option that costs its reaction, its other reaction options are withdrawn from the window. Free options are not limited by the reaction, and neither are options that cost legendary action points. | \`ReactionOption(cost, effect)\`; \`reactionAvailable\` on the combatant |
| RS-REA-003 | Several creatures can have options in one window (the attacker, the defender, a bystander within range). Each decides for itself and may pass; the window closes when every decider has chosen or passed. In version 1 the DM answers for all of them; from v1.5 each player answers for the creatures they control. | A list of deciders, each with its offered options, in the pending record |
| RS-REA-004 | Order inside a window (E-62): a window proceeds in four ordered steps. Step 1: options of the acting side that raise its result. Step 2: options of the defending side that lower the result or change the outcome. Step 3: damage changes by the acting side. Step 4: damage changes by the defending side. A decider is asked at every step where it has an eligible option, and passing at one step does not forfeit a later one. Deciders at the same step go in initiative order (ties by id). After any option the roll and the outcome are recomputed and the remaining options are re-evaluated; if the outcome changes (a hit becomes a miss) the later points that depended on it are skipped. | A fixed comparator on options; recompute after each answer |
| RS-REA-005 | Nesting (E-64): an option that starts a new action resolves that action completely, including its own windows, before the original action continues. Windows nest to a depth of 6. At depth 6 no further reactions are offered, the action resolves without them, and the log records \`reaction\_depth\_limit\`; it is never silent. | A stack of frames in the continuation; \`depth\` between 1 and 6 |
| RS-REA-006 | Information: the options a window offers depend only on what the decider knows (its own resources, reaction, position, and what the campaign's roll information setting lets it see). A window never reveals, through the options it offers, whether an option would help (for example it does not stop offering Shield because the roll was a natural 20, and does not depend on a DM-only AC). Whatever is revealed to a decider for a decision is logged with that decider as its audience (E-47). | Eligibility is computed from the decider's view; tested metamorphically (T-25) |
| RS-REA-007 | Scope (E-61): a window opens in an encounter or in a scene. In a scene it covers ability checks and saving throws made outside combat (for example Bardic Inspiration on a Stealth check). Outside combat there are no rounds, so a creature's reaction is available unless the DM says otherwise. | Scope \`ENCOUNTER\` or \`SCENE\` on the pending record |
| RS-REA-008 | Cancel (E-63): the DM can cancel an open window. The action's effects are not applied, but the dice already rolled stay in the log, so cancelling cannot be used to reroll. A cancel is idempotent. | \`ActionCancelled\` event carrying the draws |
| RS-REA-009 | Persistence and resume: the open window, the original action, the dice already rolled and the decisions so far are persisted, so a restart loses nothing. The engine resumes by re-running the action from its pre-action state with the recorded dice and decisions; the action completes atomically when the last window closes. | Continuation = action + draws + decisions; a replaying \`RandomSource\` |
| RS-REA-010 | While a window is open, other actions in the same encounter or scene are rejected. Only the DM, or the player who controls the decider, can answer for it; the DM can pass for everyone. In v1.5 a player can set a creature's feature to 'always pass' so a bard is not asked about Cutting Words on every roll; passing never reveals information. | \`REACTION\_PENDING\`; \`REACTION\_NOT\_PERMITTED\`; per-creature pass policy (v1.5) |
| RS-REA-011 | Follow-up choice (E-66): an option can require a follow-up choice from the same decider, for example which of two totals to keep after Savage Attacker's reroll. The follow-up is part of the same step, cannot be passed, is recorded in the continuation, and is answered before the window moves on. | A follow-up choice carried by the chosen option |
| RS-REA-012 | Frozen state and pins (E-65): while a window is open, a command that would change a creature in it (a level-up, a rest, a clock advance, a DM edit of hit points or the sheet, or any other action) is rejected until the window closes; answers and cancel are allowed. The open action also pins the settings it has read and the content definition versions it started with; a setting or pack change made meanwhile applies from the next action. This keeps the replay valid. | \`REACTION\_PENDING\`; pins stored in the continuation |
| RS-REA-013 | Order of a saving throw: roll (bonuses such as an aura are applied, and the aura source's state is read at roll time); WS1 (Bardic Inspiration); outcome; if a rolled save failed, WS2 (Indomitable; the reroll reads the aura again); final outcome; then adjustments (Evasion, Potent Cantrip, Sculpt Spells) and effects. An automatic failure has no roll, so WS1 and WS2 do not open for it, except that a creature with Legendary Resistance gets a window at WS2 that offers only that option when the setting legendaryResistanceOverridesAutoFail is on (RS-MON-033). After a rolled failure, WS2 also offers Legendary Resistance (RS-MON-032). Order of an ability check: roll; WC (Bardic Inspiration, Cutting Words); outcome. | Fixed pipeline for saves and checks |
| RS-REA-014 | Settings during a chain (E-67): an action spawned by an option (for example a paladin's opportunity attack) that needs an undecided setting after dice were rolled uses the default for that action. The setting stays undecided and is asked before the next action that needs it. Nothing is ever asked after a die has been rolled. | Default applied, not recorded as decided (Architecture 4.15) |

**Reaction window edge cases.** Each has a test (Architecture section 12.1); a test that would still pass with the feature removed does not count.

| ID | Case | Handling | Tests |
| --- | --- | --- | --- |
| RS-REA-R1 | A creature already used its reaction this round | Its reaction-cost options are not offered; free options (Smite, Inspiration) still are | T-26 |
| RS-REA-R2 | A creature is incapacitated, unconscious, or surprised on its first turn | It has no reaction; options costing one are not offered | T-26 |
| RS-REA-R3 | The decider falls unconscious or dies before its point (for example damage from the same action) | Options that need it conscious are withdrawn when the point is evaluated | T-22 |
| RS-REA-R4 | Two creatures have options at one point (a mover leaves two enemies' reaches) | Offered one at a time in initiative order; the first result is applied before the second is offered | T-22, T-23 |
| RS-REA-R5 | An earlier option removes the trigger (an opportunity attack kills the mover) | The remaining options tied to that trigger are withdrawn | T-22 |
| RS-REA-R6 | An option stops the original action (Counterspell, or Shield turning a hit into a miss) | The action ends as fizzled or missed; later points are skipped; costs already paid stay paid | T-22 |
| RS-REA-R7 | Two options need the same resource | The second is re-evaluated after the first and withdrawn if the resource is gone | T-26 |
| RS-REA-R8 | One decider has several options, some free and some costing the reaction | Any number of free options, at most one reaction option | T-26 |
| RS-REA-R9 | A concentration check after damage while a window is open | Not nested: it belongs to applying damage and opens its own points after the attack's last point | T-22 |
| RS-REA-R10 | Dice rolled by an option (a Bardic Inspiration die) | Drawn at the moment of the decision, recorded, and returned unchanged on replay | T-23, T-36 |
| RS-REA-R11 | A stale answer (the window was cancelled or already answered) | Refused with `REACTION_OPTION_INVALID` or `ENCOUNTER_VERSION_CONFLICT`; nothing changes | T-30 |
| RS-REA-R12 | The DM cancels while a player is answering | The first write wins by version; the loser is refused | T-30 |
| RS-REA-R13 | No option is eligible | No window record and no prompt | T-24 |
| RS-REA-R14 | A reaction outside combat | No rounds; the reaction is available unless the DM says otherwise | T-34 |
| RS-REA-R15 | A player is offline | The window waits; the DM can answer for the creature, logged as answered by the DM | T-31 |
| RS-REA-R16 | A chain reaches the depth cap | The next reaction is not offered; `reaction_depth_limit` is logged; the action resolves | T-27 |
| RS-REA-R17 | Hidden information (a DM-only monster AC) | The options offered never depend on it; the prompt shows only what the roll information setting allows | T-25 |
| RS-REA-R18 | A readied action's trigger occurs | Opens a window for the readier: use the reaction now or ignore it | T-22 |
| RS-REA-R19 | A level-up, rest, clock advance, DM edit or sheet edit touching a creature in an open window | Rejected with REACTION\_PENDING until the window closes; answers and cancel still work | T-32, T-38 |
| RS-REA-R20 | A setting is changed while a window is open | The open action keeps the settings it read; the new value applies from the next action | T-37 |
| RS-REA-R21 | A content pack is updated while a window is open | The open action keeps the definition versions it started with | T-37 |
| RS-REA-R22 | An action spawned by an option needs an undecided setting after dice were rolled | The default is used for that action; the setting stays undecided and is asked before the next action that needs it | T-39 |
| RS-REA-R23 | An aura source falls unconscious before a reroll (for example before Indomitable's new roll) | The reroll reads the aura again and gets no bonus | T-22 |
| RS-REA-R24 | An automatic failure (paralyzed, stunned) on a saving throw | No roll, so WS1 and WS2 are not offered, except that a creature with Legendary Resistance gets a WS2 window offering only that option when legendaryResistanceOverridesAutoFail is on (the default, T-71); Evasion then applies to the final outcome | T-22 |
| RS-REA-R25 | A turned creature, or any effect that bars reactions | Reaction-cost options are not offered to it; free options are | T-26 |
| RS-REA-R26 | An option with a follow-up choice (Savage Attacker's choice of total) | The follow-up is mandatory, in the same step, recorded, and replayed unchanged | T-22, T-23 |
| RS-REA-R27 | A checkpoint is taken while a window is open (v1.5) | The pending window is part of the saved state and restores atomically with it | T-17 |
| RS-REA-R28 | A once-per-turn feature used in an attack made as a reaction | It counts toward the turn in which it happens and is spent exactly once when the action completes, although the action is replayed | T-23 |
| RS-REA-R29 | The roll information setting is 'outcome shown' | After-roll options are offered after the outcome is revealed and may flip it | T-25 |

The turn budget holds: action, bonus action, movement remaining (in cost units), object interaction, reaction.

### 7.3 Movement and position

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MOV-001 | A creature may move up to its speed, using as much or as little as it likes. Walking, jumping, climbing and swimming can be combined, and the distance of each part is deducted from the speed. | `movementRemaining` in cost units |
| RS-MOV-002 | Movement can be split around the action and, when one Attack action makes several attacks (for example with Extra Attack), between those attacks. | Moves and attacks interleave within a turn |
| RS-MOV-003 | With several speeds (walking, flying), switching to a new speed subtracts the distance already moved from it; if the result is 0 or less, the new speed cannot be used in this move. | Per-mode speed |
| RS-MOV-004 | Difficult terrain: each foot of movement costs 1 extra foot, even if several things in the space count as difficult terrain. The space of another creature, hostile or not, counts as difficult terrain. This holds for a swarm's space too (RS-MON-023). | Cost map: difficult and occupied cells cost double (architecture 7.3) |
| RS-MOV-005 | Prone: dropping prone costs no movement. Standing up costs movement equal to half the speed, and is impossible if that much movement is not left or if the speed is 0. A prone creature can only crawl (or use magic such as teleportation); each foot crawled costs 1 extra foot, so one foot of crawling in difficult terrain costs 3. | Stand-up cost; crawl cost; extra costs add (E-16) |
| RS-MOV-006 | A creature can move through a non-hostile creature's space, and through a hostile creature's space only if that creature is at least two sizes larger or smaller. Nobody can willingly end a move in another creature's space, friend or foe. The one exception is a swarm (RS-MON-023): it can occupy another creature's space and another creature can occupy its space, so the limits above do not apply to it. A swarm's reach is 0, so a creature that leaves its space leaves its reach. Leaving a hostile creature's reach provokes an opportunity attack (RS-ATK-008). | `noStop` cells; hostile cells blocked unless the size difference is 2 or more |
| RS-MOV-007 | A flying creature that is knocked prone, has its speed reduced to 0 or otherwise cannot move falls, unless it can hover or magic holds it aloft. Fall damage (verified): at the end of a fall a creature takes 1d6 bludgeoning damage per 10 feet fallen, to a maximum of 20d6, and lands prone unless it avoids taking damage from the fall. | Fall trigger; 13.6 (RS-ENV-035, RS-ENV-040) |
| RS-MOV-008 | Size and space: Tiny 2.5 by 2.5 ft; Small and Medium 5 by 5; Large 10 by 10; Huge 15 by 15; Gargantuan 20 by 20 or more. Space is the area a creature controls in combat, not its physical size. Eight Medium creatures fit around one Medium creature, and as many as 20 around a Gargantuan one. A swarm of Tiny creatures takes the space of a Medium creature (RS-MON-023). | `footprint(anchor, size)`; Tiny occupies a whole cell in version 1 (E-20) |
| RS-MOV-009 | Squeezing: a creature can squeeze through a space large enough for a creature one size smaller. While squeezing it spends 1 extra foot per foot, has disadvantage on attack rolls and Dexterity saving throws, and attack rolls against it have advantage. | Squeeze flag from the footprint check; modifiers while in the space |

### 7.4 Actions in combat

| ID | Action and rule | Engine mapping |
| --- | --- | --- |
| RS-ACT-001 | **Attack:** one melee or ranged attack. Features such as Extra Attack allow more attacks with this action. | `AttackAction`; attack count from features |
| RS-ACT-002 | **Cast a Spell:** the casting time says whether it takes an action, a reaction, minutes or hours; most take 1 action (Part E). | `CastSpellAction` |
| RS-ACT-003 | **Dash:** gain extra movement equal to the speed after modifiers; a change to speed changes the extra movement by the same amount. | Adds the current speed to `movementRemaining` |
| RS-ACT-004 | **Disengage:** movement does not provoke opportunity attacks for the rest of the turn. | Turn flag |
| RS-ACT-005 | **Dodge:** until the start of its next turn, attack rolls against the creature have disadvantage if it can see the attacker, and its Dexterity saves have advantage. Lost if it becomes incapacitated or its speed drops to 0. | Timed effect with advantage sources scoped by direction (against it) and by roll kind (Dexterity saves) |
| RS-ACT-006 | **Help:** the helper gives a creature advantage on its next ability check for a task, if made before the start of the helper's next turn; or, for an attack, gives an ally advantage on its first attack roll against a creature within 5 feet of the helper, if the ally attacks before the helper's next turn. | One-shot advantage source with an expiry |
| RS-ACT-007 | **Hide:** a Dexterity (Stealth) check; success gives the benefits in RS-ATK-005. Verified: until discovered, the check is contested by the Wisdom (Perception) of any creature that actively searches, and other creatures notice you by comparing the Stealth check with their passive Wisdom (Perception), which is 10 plus the Wisdom modifier and other bonuses or penalties (plus 5 with advantage, minus 5 with disadvantage). The SRD gives no tie rule, so the DM decides (E-14). You cannot hide from a creature that can see you clearly. From version 1.5, hiding reads obscurement, cover and the sight state (RS-ENV-016), and the DM can still override the result. | Check request |
| RS-ACT-008 | **Ready:** choose a perceivable trigger and a response (an action, or moving up to the speed). When the trigger occurs the creature can use its reaction right after it, or ignore it; one reaction per round. A readied spell is cast normally (its slot is spent) but held: it needs a casting time of 1 action and concentration, and fizzles without effect if concentration breaks. Concentration is on one spell at a time, so readying a spell while concentrating on another ends the first (verified, RS-ACT-012). | `ReadiedAction` entity; the DM confirms the trigger; held spell with concentration |
| RS-ACT-009 | **Search:** devote attention to finding something; the DM calls for a Wisdom (Perception) or Intelligence (Investigation) check. | Check request |
| RS-ACT-010 | **Use an Object:** an action when an object requires it or when a second object is used in the turn. | `UseObjectAction` |
| RS-ACT-011 | An improvised action: the DM decides whether it is possible and what roll it needs. | `ImprovisedAction` resolved by a DM check or save request |
| RS-ACT-012 | Concentration (Spellcasting, verified): normal activity such as moving and attacking does not interfere. It ends if the creature casts another spell that requires concentration (no roll), takes damage and fails a Constitution saving throw (DC 10 or half the damage taken, whichever is higher, with a separate save for each source of damage), or is incapacitated or dies. The DM may require a DC 10 Constitution save for environmental effects such as a wave. A creature can end it at any time, with no action. Damage that temporary hit points absorb still counts as damage taken (E-18). | One concentration slot per creature; the damage-taken event triggers a Constitution save request whose roll bonuses and advantage apply; casting a second concentration spell ends the first |

### 7.5 Making an attack

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-ATK-001 | An attack has three steps: choose a target within range (a creature, an object or a location); determine modifiers (cover, advantage or disadvantage, bonuses and penalties); resolve it by rolling, and on a hit roll damage unless the attack says otherwise. If a creature is making an attack roll, it is making an attack. | Attack resolution pipeline |
| RS-ATK-002 | An attack roll is a d20 plus modifiers. If the total equals or exceeds the target's Armor Class, the attack hits. A character's AC is computed from worn armor, a shield, class features and effects (Part F, 11.3); a monster's AC is in its stat block. | `D20Test(target = AC)` |
| RS-ATK-003 | Modifiers: for characters the ability modifier (Strength for melee weapons, Dexterity for ranged; finesse and thrown weapons break this rule; a spell attack uses the spellcasting ability) plus the proficiency bonus when proficient with the weapon, and always for spell attacks. Monsters use the modifier in the stat block. | Attack bonus stat with a proficiency component (RS-PRF-003) |
| RS-ATK-004 | A natural 20 hits regardless of modifiers or AC and is a critical hit; a natural 1 misses regardless. This applies to attack rolls only (checks and saves follow E-1). | `naturalD20` drives hit and miss |
| RS-ATK-005 | Unseen attackers and targets: attacking a target you cannot see gives disadvantage, whether you guess its location or hear it; if it is not where you aimed, you automatically miss (the DM typically just says you missed). When a creature cannot see you, you have advantage against it. A hidden attacker gives away its position when the attack hits or misses. | Visibility predicate (vision seam); `hidden` state ends on attack |
| RS-ATK-006 | Ranged attacks: a single-range attack cannot target beyond that range. A two-range attack has a normal and a long range: disadvantage beyond normal range, and no attacks beyond long range. A ranged attack made within 5 feet of a hostile creature that can see you and is not incapacitated has disadvantage. | Distance by the campaign metric; adjacency for the 5-foot test |
| RS-ATK-007 | Melee attacks reach 5 feet unless a creature's description says more (typically creatures larger than Medium). An unarmed strike is a melee attack dealing bludgeoning damage equal to 1 plus the Strength modifier; the creature is proficient with it. | Reach counts steps (8 neighbors); unarmed strike profile |
| RS-ATK-008 | Opportunity attack: when a hostile creature you can see moves out of your reach, you can use your reaction to make one melee attack against it, right before it leaves your reach. Disengage prevents it, and so does teleporting or being moved without using the creature's own movement, action or reaction (an explosion, a fall). | Reaction window on a move trigger |
| RS-ATK-009 | Two-weapon fighting: after taking the Attack action with a light melee weapon held in one hand, you can use a bonus action to attack with a different light melee weapon in the other hand. The bonus attack does not add the ability modifier to damage unless it is negative. A thrown weapon can be thrown instead. | Bonus action attack; damage modifier rule |
| RS-ATK-010 | Grapple: a special melee attack made with the Attack action, replacing one attack. The target must be no more than one size larger and within reach. It is a Strength (Athletics) check contested by the target's Strength (Athletics) or Dexterity (Acrobatics), the target's choice; it succeeds automatically against an incapacitated target. On success the target is grappled. A grappled creature can use its action to escape with a contested check. Moving a grappled creature halves your speed unless it is two or more sizes smaller. You can release at will. | `ContestResult` (RS-CHK-005; a tie means the grapple fails); Grappled condition |
| RS-ATK-011 | Shove: a special melee attack replacing one attack, with the same size limit, contest and automatic success as a grapple. On success the target is knocked prone or pushed 5 feet away. | Contest; prone condition or a 5-foot displacement |

**Examples (forced dice)**

- Attack: d20 = 15 with +5 against AC 20 totals 20 and hits. d20 = 20 hits even against AC 30 and is a critical hit. d20 = 1 with +15 against AC 10 misses.
- Movement: speed 30, standing up from prone costs 15, leaving 15. Crawling 5 feet in difficult terrain costs 15.
- Different speeds: walk 30 and fly 60. Fly 20, walk 10 (30 used), then switch to flying: 60 minus 30 leaves 30 more feet.
- Dash: speed 30 gives 60 feet of movement. If the speed is reduced to 15, a dash gives 30.
- Opportunity attack: a goblin moves out of a fighter's reach and the fighter uses its reaction to attack right before it leaves. If the goblin took Disengage first, there is no attack.
- Grapple: attacker's Athletics total 14 against the target's 14: a tie, so the grapple fails. If the target is incapacitated, it succeeds with no roll.
- Ranged in melee: an archer within 5 feet of a hostile that can see it shoots with disadvantage; the same shot at 10 feet does not.

### 7.6 Cover

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-COV-001 | Cover protects a target only when the attack or effect comes from the opposite side of the obstacle. | Cover is evaluated per attacker and target |
| RS-COV-002 | Half cover (an obstacle blocks at least half of the body: a low wall, large furniture, a narrow trunk, or a creature, friend or foe): +2 to AC and Dexterity saves. Three-quarters cover (a portcullis, an arrow slit, a thick trunk): +5. Total cover (completely concealed): cannot be targeted directly by an attack or spell, though some area effects can include it. From version 1.5 the cover degree is computed (13.4, RS-ENV-024), and the DM can still override it. | Cover becomes a roll bonus or a targeting restriction for that attack |
| RS-COV-003 | Several sources of cover do not add; only the most protective degree applies. | Take the maximum |
| RS-COV-004 | In version 1 the DM sets the cover level for each attack (default none) and the engine suggests half cover when a creature stands between attacker and target. Automatic computation from the grid is planned (FR-GRD-009, v1.5). | Manual selection with a suggestion (E-17) |

### 7.7 Damage, healing and death

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-DMG-001 | Current hit points run from the maximum down to 0. Damage is subtracted. Losing hit points has no effect on capabilities until a creature drops to 0. | `hpCurrent` on the character or combatant |
| RS-DMG-002 | A damage roll is the damage dice plus modifiers. A weapon attack adds the same ability modifier as its attack roll; a spell states its dice and modifiers. Penalties can reduce damage to 0 but never below 0. Damage dealt to several targets at once (a fireball) is rolled once for all of them. | Damage total clamped at 0; one roll, applied per target |
| RS-DMG-003 | Critical hit: roll all of the attack's damage dice twice and add them, then add the modifiers as normal. Extra damage dice from features such as Sneak Attack are rolled twice as well. How a dice group with a keep or drop token is rolled twice, and the optional variants, follow the critical dice mode setting (D-14). | Dice terms with the crit-doubling flag (RS-DIE-006) |
| RS-DMG-004 | Damage types: acid, bludgeoning, cold, fire, force, lightning, necrotic, piercing, poison, psychic, radiant, slashing, thunder. Types have no rules of their own; other rules such as resistance rely on them. | Damage type registry with these defaults (extensible, FR-CNT-008) |
| RS-DMG-005 | Resistance to a damage type halves that damage; vulnerability doubles it. Both apply after all other modifiers to damage, resistance first. Several instances of resistance or vulnerability to the same type count as one. Halves round down. | One flag per type; applied in the damage pipeline |
| RS-DMG-006 | Immunity (Monsters chapter, verified): some creatures are immune to certain damage types, and some are resistant or immune to damage from nonmagical attacks (a magical attack is delivered by a spell, a magic item or another magical source). Some creatures are also immune to certain conditions. Immunity means no damage of that type, or no effect of that condition. Objects are immune to poison and psychic damage. | Immunity and resistance flags per damage type with an optional nonmagical qualifier (stat blocks may add 'not silvered or adamantine'); every damage instance carries a magical flag; a condition immunity list per creature |
| RS-DMG-007 | Healing adds hit points to the current total, capped at the maximum; the excess is lost. A dead creature cannot regain hit points until magic such as Revivify restores it to life. | Heal effect clamps to `maxHp` |
| RS-DMG-008 | Temporary hit points are a separate buffer: lost first, leftover damage carries over to real hit points. They can exceed the maximum, cannot be restored by healing, and do not add together: the creature keeps the old amount or takes the new one. At 0 hit points they do not restore consciousness or stabilize but still absorb damage. They last until used up or a long rest unless the effect gives a duration. | `hpTemp` field with a replace-not-add rule |
| RS-DMG-009 | A creature reduced to 0 hit points either dies outright or falls unconscious, unless a zero-hit-point handler changes the outcome first (RS-MON-013). | Death state machine (below) |
| RS-DMG-010 | Instant death: when damage reduces a creature to 0 and damage remains, it dies if the remaining damage equals or exceeds its hit point maximum. | Remaining damage computed after resistance and temporary hit points |
| RS-DMG-011 | Otherwise it falls unconscious, which ends if it regains any hit points. | Unconscious condition applied and removed |
| RS-DMG-012 | Death saving throws: at the start of each of its turns at 0 hit points, the creature rolls a d20 that is not tied to any ability. 10 or higher is a success, otherwise a failure. Three successes make it stable; three failures kill it. They need not be consecutive, and both counts reset when it regains any hit points or becomes stable. A natural 1 counts as two failures; a natural 20 regains 1 hit point. Only features that improve saving throws help. | `deathSaves {successes, failures}`; roll bonuses and advantage apply; no ability modifier |
| RS-DMG-013 | Taking any damage at 0 hit points is one death save failure, even when temporary hit points absorb it (the official clarification is that absorbed damage is still damage taken; a campaign setting can change this, E-18); a critical hit is two; damage equal to or above the hit point maximum is instant death. | Applied after temporary hit points (E-18) |
| RS-DMG-014 | Stabilizing: an action to give first aid to an unconscious creature, a DC 10 Wisdom (Medicine) check. A stable creature makes no death saves but stays unconscious; it stops being stable if it takes any damage; if not healed it regains 1 hit point after 1d4 hours. A suffocating creature has the marker of RS-ENV-046, which blocks healing and stabilizing until it can breathe. | `STABLE` state with a recovery timer on the game clock |
| RS-DMG-015 | Most DMs have monsters die the moment they reach 0 hit points; major villains and special NPCs can follow the player rules. | Per-combatant `deathMode` (E-19) |
| RS-DMG-016 | Knocking out: when a melee attack reduces a creature to 0 hit points, the attacker can choose, at that instant, to knock it out: it falls unconscious and is stable. | Choice offered on a melee drop to 0 |
| RS-DMG-017 | Order of applying damage (engine): damage roll, critical doubling of dice, other flat modifiers, a halving feature such as Uncanny Dodge (RS-ROG-005), resistance, vulnerability, temporary hit points, hit points, then the 0 hit points outcome: the instant death check, then the zero-hit-point handlers (RS-MON-013), then dead, unconscious, or a death save failure. | Fixed pipeline; each step logged |
| RS-DMG-018 | Reroll-and-choose damage features, for example the Savage Attacker feat (not in the SRD, but a player can take it from a pack): once per turn, when the attacker rolls weapon damage, it may roll the weapon's damage dice again and use either total. The community disagrees on two points, so each is a campaign setting. Scope: only the weapon's own damage dice (the default, following the feat's wording) or every damage die of the attack, including Sneak Attack and smite dice. Critical dice: reroll the whole weapon roll including the critical dice (the default), or let the player choose to reroll either the base dice or the critical dice. The player chooses which total to use; the interface highlights the higher one. | Effect type reroll\_damage\_choose, usable once per turn; damage terms carry a category (weapon or extra, from the term's source) and the critical flag on each die (D-15); RollResult.rerollTerms(selector, rng) returns the second roll and the caller offers both totals (E-45) |

Death states: alive, dying (at 0 with success and failure counts), stable (unconscious, no saves), dead. A knocked-out creature is stable. A creature kept at 0 hit points by a handler (the troll under RS-MON-015) is unconscious and makes no death saves; it stays that way until the handler resolves it.

### 7.8 Conditions (SRD: Conditions)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-CND-001 | A creature either has a condition or does not. Several effects can impose the same condition, each instance with its own duration, but the condition's effects never get worse (except exhaustion). | Condition set; instances kept per source; effects applied once |
| RS-CND-002 | A condition lasts until countered (prone ends by standing up) or for the duration of the effect that imposed it. | Duration kinds from the architecture |
| RS-CND-003 | Conditions are data: bundles of modifiers, advantage sources, forced outcomes, action restrictions and flags, plus the source creature where a rule refers to one (frightened, charmed, grappled). | `condition_def` content; instances carry a `source` |

| Condition | Effects (paraphrased) | Engine mapping |
| --- | --- | --- |
| Blinded | Cannot see and automatically fails ability checks that need sight. Attack rolls against it have advantage; its own attack rolls have disadvantage. | Check override (needs sight); advantage sources by direction |
| Charmed | Cannot attack the charmer or target it with harmful abilities or magical effects. The charmer has advantage on social ability checks against it. | Targeting restriction; advantage source with the charmer as source |
| Deafened | Cannot hear and automatically fails ability checks that need hearing. | Check override (needs hearing) |
| Frightened | Disadvantage on ability checks and attack rolls while the source of its fear is in line of sight. It cannot willingly move closer to that source. | Scoped sources with a line-of-sight predicate; movement restriction |
| Grappled | Speed becomes 0 and it gets no bonus to speed. Ends if the grappler is incapacitated, or if an effect removes it from the grappler's reach. | `speed` capped at 0 at the end of the pipeline; end triggers |
| Incapacitated | Cannot take actions or reactions. | Turn budget flags |
| Invisible | Impossible to see without magic or a special sense; counts as heavily obscured for hiding; its location can be found by noise or tracks. Attack rolls against it have disadvantage; its attack rolls have advantage. | Visibility rule; advantage sources by direction |
| Paralyzed | Incapacitated, cannot move or speak. Automatically fails Strength and Dexterity saves. Attack rolls against it have advantage. Any hit from an attacker within 5 feet is a critical hit. | Flags; save override; automatic critical when the attacker is adjacent |
| Petrified | Turned with its worn and carried nonmagical items into a solid inanimate substance; weight times ten; stops aging. Incapacitated, cannot move or speak, unaware of its surroundings. Attack rolls against it have advantage. Automatically fails Strength and Dexterity saves. Resistance to all damage. Immune to poison and disease (existing ones are suspended, not neutralized). | Flags; resistance to every type; poison immunity |
| Poisoned | Disadvantage on attack rolls and ability checks. | Advantage sources by roll kind |
| Prone | Its only movement option is to crawl unless it stands up, which ends the condition. Disadvantage on its attack rolls. Attack rolls against it have advantage if the attacker is within 5 feet, otherwise disadvantage. | Crawl cost; advantage source with a distance predicate |
| Restrained | Speed becomes 0 with no bonus to speed. Attack rolls against it have advantage; its attack rolls have disadvantage. Disadvantage on Dexterity saves. | `speed` capped at 0; scoped sources |
| Stunned | Incapacitated, cannot move, can speak only falteringly. Automatically fails Strength and Dexterity saves. Attack rolls against it have advantage. | Flags; save override |
| Unconscious | Incapacitated, cannot move or speak, unaware of its surroundings. Drops what it holds and falls prone. Automatically fails Strength and Dexterity saves. Attack rolls against it have advantage. Any hit from an attacker within 5 feet is a critical hit. | Flags; drops items; applies prone; automatic critical when adjacent |

**Exhaustion** is measured in six levels, and a creature suffers its current level and all lower ones: 1 disadvantage on ability checks; 2 speed halved; 3 disadvantage on attack rolls and saving throws; 4 hit point maximum halved; 5 speed reduced to 0; 6 death. Further exhaustion adds levels. An effect that removes exhaustion lowers the level, and all effects end below level 1. A long rest reduces the level by 1 if the creature has also eaten and drunk, and being raised from the dead reduces it by 1.

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-CND-010 | Exhaustion is a level from 0 to 6 on the creature, producing cumulative modifiers per level. | `exhaustion` counter; level effects are modifiers (a halved maximum is a `MULTIPLY` of one half on maximum HP, then the current HP clamp of E-5 applies) |

### 7.9 Deferred to v2

| ID | Rule | Note |
| --- | --- | --- |
| RS-CMB-090 | Mounted combat: mounting or dismounting costs half the speed; a controlled mount shares the rider's initiative and may only Dash, Disengage or Dodge; an independent mount keeps its own initiative; a DC 10 Dexterity save avoids falling if the mount is moved against its will or knocked prone. Underwater combat: melee attacks have disadvantage without a swim speed unless the weapon is a dagger, javelin, shortsword, spear or trident; ranged attacks automatically miss beyond normal range and have disadvantage unless the weapon is a crossbow, a net or a thrown weapon; full immersion gives resistance to fire. | Not in v1 or v1.5 |

### 7.10 What Part B means for the engine

| Area | Requirement |
| --- | --- |
| Turns | `TurnBudget`, a per-combatant surprise flag, optional group initiative, tie prompts with an optional d20 roll-off, and the persisted reaction window |
| Conditions | Data-defined bundles with instances that carry a source and a duration; effects apply once however many instances; exhaustion as a level counter |
| Advantage sources | The scope from RS-ADV-006 needs a direction (rolls made by the creature, or rolls made against it) and optional predicates: attacker distance (prone, paralyzed), line of sight (frightened), and the source creature |
| Forced outcomes | Save overrides (automatic failure of Strength and Dexterity saves), check overrides (needs sight or hearing), and an automatic critical hit when the attacker is adjacent |
| Damage | The fixed pipeline in RS-DMG-017, which emits a damage-taken event (even when temporary hit points absorb everything; it drives death save failures, concentration checks and 'when you take damage' triggers) and a hit-points-lost event; damage types, resistance, vulnerability and immunity as flags per type, each counted once; temporary hit points as a replace-not-add field |
| Death | The state machine alive, dying, stable, dead with death save counts; knocked-out creatures are stable; a per-combatant death mode |
| Movement | Speed capped at 0 by conditions; extra costs add; the cost map from the architecture; stand-up cost and crawl cost |
| Contests | Grapple and shove reuse `ContestResult`, with automatic success against an incapacitated target |
| Cover | A per-attack cover level chosen by the DM in version 1 |

### 7.11 Engine decisions and open points (Part B)

| ID | Question | Decision |
| --- | --- | --- |
| E-14 | How surprise is determined | Decided: the engine suggests the comparison of Stealth against passive Perception and the DM confirms; on a tie the DM decides whether the hider is noticed |
| E-15 | A combatant joining mid-combat | Decided: roll initiative and weave the joiner into the order by default, with DM-chosen join options (RS-CMB-007) for arrivals that are thrown in, unaware or ambushing; end-of-round joining is an option; no mid-combat surprise unless the optional house rule is on (RS-CMB-008) |
| E-16 | Extra movement costs that overlap (difficult terrain, crawling, squeezing) | Decided: they add, one extra foot each per foot (the SRD's own example of crawling in difficult terrain costs 3) |
| E-17 | Cover in version 1 | Decided: the DM sets it per attack, with a suggestion when a creature is in the way; automatic from the grid in v1.5 |
| E-18 | Damage at 0 HP that temporary hit points absorb completely | Decided (after research): follow the official clarification. Damage absorbed by temporary hit points is still damage taken, so at 0 HP it causes a death save failure (two for a critical hit) and requires concentration checks; temporary hit points still reduce the damage that counts toward instant death. A campaign setting lets the DM use the house rule that fully absorbed damage causes no failure (default off). When temporary hit points expire nothing happens to real hit points. |
| E-19 | Monsters reduced to 0 HP | Decided: they die, unless the DM marks a combatant to use the player rules or to be knocked out |
| E-20 | Footprint of Tiny creatures | Decided: one whole cell in version 1 |
| E-21 | What counts as 'can see' (Dodge, unseen attackers, ranged attacks in melee) | Decided: true unless the viewer is blinded, the target is invisible, or the DM marks obscuring conditions, in version 1. From version 1.5 the sight state of RS-ENV-015 decides (RS-ENV-016) |
| V-2 | Verification debt for Part B (cleared 2026-11-14) | Cleared: immunity wording (Monsters chapter), hiding and passive Perception (Using Ability Scores and Combat), concentration including readied spells (Spellcasting and Combat) and falling damage (Adventuring, The Environment) are verified against SRD text and now stated in RS-DMG-006, RS-ACT-007, RS-ACT-008, RS-ACT-012 and RS-MOV-007 |
| E-45 | How a reroll-and-choose feature such as Savage Attacker treats the weapon's dice, extra dice and critical dice | Decided: two campaign settings. Scope: the weapon's damage dice only (default), or all damage dice of the attack. Critical dice: reroll the whole weapon roll including the critical dice (default), or let the player choose to reroll either the base dice or the critical dice. The feature is once per turn and the player picks either total as a follow-up choice in the window (RS-REA-011); scope is the shared weaponDiceScope setting of E-52 |

**Examples (forced dice)**

- Critical damage: dagger 1d4 + 3 plus Sneak Attack 2d6. Normal: 1d4 + 2d6 + 3. Critical: 2d4 + 4d6 + 3. With d4 dice 3 and 2 and d6 dice 6, 1, 4 and 5: 5 + 16 + 3 = 24.
- Resistance: 25 bludgeoning damage, a magical aura reducing damage by 5, and resistance: (25 - 5) / 2 = 10. Damage of 7 with resistance: 3. Resistance and vulnerability to the same type on 20 damage: 10, then 20.
- Instant death: maximum 12, current 6, 18 damage: reduced to 0 with 12 remaining, so the creature dies. With 17 damage, 11 remains and it falls unconscious.
- Death saves: dice 14, 6, 1 give a success, a failure, then two failures (natural 1): three failures and the creature dies. A roll of 20 regains 1 hit point and resets both counts.
- Temporary hit points: 5 temporary hit points and 7 damage leaves 0 temporary and 2 damage to hit points. Receiving 12 temporary hit points while holding 10: keep 10 or take 12, never 22.
- Healing: maximum 20, current 14, healing 8: current 20 (6 regained).
- Prone: an attacker within 5 feet has advantage against a prone target; from 10 feet it has disadvantage.
- Paralyzed: a hit from within 5 feet is a critical hit; a bow hit from 30 feet is a normal hit.

**More examples (E-15, E-18)**

- Joining: a fighter arrives at initiative 14 while the current turn is at 10. Its place has already passed this round, so its first turn is next round. If the DM picks end-of-round joining, the same applies whatever it rolled.
- Thrown in: a character is hurled into the arena. The DM picks disadvantage on its initiative roll and the prone condition; it acts when its place comes up and spends half its speed to stand.
- Unaware arrival: townsfolk walk into a fight. The DM marks them surprised: they cannot move or act on their first turn and cannot react until it ends.
- Ambush: a rogue arrives hidden and wins its Stealth check against passive Perception. Its first attack has advantage because the target cannot see it; no creature is surprised unless the optional house rule is on.
- Temporary hit points at 0 HP: a creature at 0 HP has 5 temporary hit points and takes 3 damage. Temporary hit points drop to 2, hit points stay 0, and it suffers one death save failure. When the temporary hit points later expire nothing happens to it: it is still at 0 HP, still dying, and still rolling death saves.

## 8. Part C: Rests, time and resources (SRD: Resting, class features)

Part C covers short and long rests, Hit Dice, how a rest moves the game clock, how limited-use features recharge, and the time rules that depend on them. The rest rules and the recharge rules of the Fighter, Paladin, Bard and Wizard were verified against SRD text on 2026-11-16 (SRD renditions and Basic Rules text, not the official PDF); the Rogue has no limited-use features in levels 1 to 12. A rest is a **campaign-level action**, not an encounter action: it happens in a scene with no active encounter.

One note on terms. In SRD 5.1 the Fighter's Second Wind is a single use that returns after a short rest or a long rest. The pattern "one use returns on a short rest, all uses on a long rest" belongs to the 2024 rules, which this project does not use; the recharge model below supports both patterns as data, so a later pack could use it.

### 8.1 Rest rules

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-RST-001 | Short rest: a period of downtime of at least 1 hour in which a character does nothing more strenuous than eating, drinking, reading and tending wounds. | `RestKind.SHORT`; the minimum comes from the campaign settings (RS-RST-008) |
| RS-RST-002 | At the end of a short rest a character can spend one or more Hit Dice, up to its unspent Hit Dice (the maximum equals its level). For each die the player rolls it, adds the Constitution modifier, and the character regains that many hit points. The SRD text gives no minimum; the Player's Handbook says minimum 0, which the engine applies, so a negative total heals nothing. The player may decide after each roll whether to spend another. Spent Hit Dice return only after a long rest. | A `RollPlan` with a labeled Hit Die term and a Constitution term; healing is capped at the maximum (RS-DMG-007); `pc_hit_dice` keeps the spent count per die size |
| RS-RST-003 | Long rest: a period of downtime of at least 8 hours, spent sleeping or in light activity (reading, talking, eating, standing watch) for no more than 2 hours. A period of strenuous activity of at least 1 hour (walking, fighting, casting spells or similar) interrupts the rest, and the characters must begin it again to gain any benefit. | The DM marks a rest complete or interrupted (RS-RST-007) |
| RS-RST-004 | Long rest benefits: the character regains all lost hit points; regains spent Hit Dice up to half the total number of Hit Dice (minimum 1; a character with 8 Hit Dice regains 4); regains the uses of every feature that recharges on a long rest, and all spell slots; loses temporary hit points (RS-DMG-008); and loses 1 level of exhaustion if it also ate and drank (RS-CND-010). | `RestResolver` applies these in a fixed order and logs each |
| RS-RST-005 | A character cannot benefit from more than one long rest in a 24-hour period, and must have at least 1 hit point at the start of the rest. A short rest has no limit. | `lastLongRestEnd` on the character; the 24 hours are measured end to end (E-23) |
| RS-RST-006 | When a character has Hit Dice of several sizes, the player chooses which spent dice return, up to the limit (default suggestion: the largest first). | A choice in the rest command (E-30) |
| RS-RST-007 | An interrupted rest gives no benefit. The clock advances by the time that actually passed. The characters must begin the rest again. | `InterruptRest(elapsed)`; a campaign setting can give short-rest benefits to a long rest interrupted after at least 1 hour (E-25) |
| RS-RST-008 | Rest lengths come from a campaign setting. Standard: a 1 hour short rest and an 8 hour long rest. The Dungeon Master's Guide variants: Epic Heroism, a 5 minute short rest and a 1 hour long rest; Gritty Realism, an 8 hour short rest and a 7 day long rest. A DM may also set custom lengths, and may let a particular rest run longer than the minimum. The variant text sets no separate limit on long rests per period, so the presets set the interval to the long rest's own length (E-29). For Epic Heroism the DMG also suggests limiting spellcasters to half their spell slots (rounded down, 5th level or lower) after a 1 hour long rest, with a full 8 hour rest restoring everything; that optional tweak is not built in for version 1. | `restVariant` plus `shortRestMinutes`, `longRestMinutes` and `longRestIntervalHours` (E-29) |
| RS-RST-009 | A rest cannot start while an encounter is active. | Error `ENCOUNTER_ACTIVE`; the DM ends the encounter first |
| RS-RST-010 | The DM chooses which characters take part. Each participant's rest is resolved on its own, and an absent character is untouched. Monsters and NPCs are not tracked; the DM resets them by hand. | Participant list on the rest command |
| RS-RST-011 | For the exhaustion reduction the DM confirms, for each participant, that the character ate and drank (default yes). Food and water are not tracked in version 1. | A per-character flag on the long rest command (E-28) |

### 8.2 Time and the game clock

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-CLK-001 | The clock advances only when the DM advances it, a rest completes or is interrupted, or travel is resolved. A completed rest advances it by the rest's actual duration, at least the minimum; an interrupted rest by the time elapsed. | `GameClock.advance(reason, seconds)` returns the crossing events below |
| RS-CLK-002 | Time of day is derived from the clock: the seconds since midnight are the campaign's start time of day plus the clock, modulo 86,400. A day is 24 hours in version 1. The campaign sets a start time of day (default 08:00) and a dawn time (default 06:00). | Settings `startTimeOfDay` and `dawnTime`; calendars with weeks and months are deferred (E-27) |
| RS-CLK-003 | When the clock advances, every effect with a clock duration whose end time has been reached ends, in the order of end time, and each expiry is logged. For example a Bardic Inspiration die lasts 10 minutes and is gone after a short rest. | Effects store an absolute `endsAt` in seconds |
| RS-CLK-004 | Effects that last until a rest ends at the matching completion: an effect that lasts until a short or long rest ends when either rest completes; an effect that lasts until a long rest ends only when a long rest completes. | Duration kinds `UNTIL_SHORT_OR_LONG_REST` and `UNTIL_LONG_REST` |
| RS-CLK-005 | When an encounter ends, an effect measured in rounds keeps running on the clock: its remaining rounds become game seconds at 6 seconds per round. A spell with one minute (10 rounds) left after 3 rounds of combat has 42 seconds remaining. | Conversion at encounter end (E-24) |
| RS-CLK-006 | Dawn: when the clock reaches or passes the next dawn time, an `ON_DAWN` trigger fires once for each dawn crossed. It is used by items that recharge at dawn (Part F). | Dawn crossings returned by `advance` |
| RS-CLK-007 | A timer on the clock (for example a stable creature's 1d4 hours before it regains 1 hit point, RS-DMG-014) completes during a rest if the rest lasts long enough. | Timers share the effect-expiry sweep |
| RS-CLK-008 | A character that starts a long rest loses concentration, because sleep makes it unconscious. A short rest does not end concentration. The DM can override. | Concentration slot cleared at the start of a long rest (E-30) |

### 8.3 Limited-use features and the recharge model

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-RES-001 | A limited-use feature is a resource with a key, current uses, a maximum (a number, or a formula of level and ability modifiers), and one or more recharge rules. | `Resource` in the kernel; `resource_def` in content |
| RS-RES-002 | A recharge rule is an event plus an amount. Events: short rest, long rest, dawn, rolling initiative, the start of the creature's own turn, and manual. Handlers of the start-of-turn event run in a fixed order (RS-MON-036). Amounts: all, a number, or a formula. A long rest does not count as a short rest, so a feature that recharges on a short or long rest has two rules. | `RechargeRule(event, amount)` |
| RS-RES-003 | When a maximum rises, the current uses rise by the same amount; when it falls, the current uses are clamped to it (the same rule as hit points, E-5). | Applied when level or an ability score changes (E-31) |
| RS-RES-004 | Spell slots are a pool with the long rest as the event and all as the amount; features such as Arcane Recovery restore slots through an explicit choice at a short rest. | `SlotPool` |
| RS-RES-005 | A feature that can be used 'once per day' recharges on a long rest. | Same rule as RS-RES-002 (E-31) |

The features of the five MVP classes (verified):

| Class | Feature | Uses | Recharge |
| --- | --- | --- | --- |
| Fighter | Second Wind | 1 | all, on a short or long rest |
| Fighter | Action Surge | 1 (2 at level 17) | all, on a short or long rest |
| Fighter | Indomitable (level 9) | 1 (2 at level 13, 3 at level 17) | all, on a long rest |
| Rogue | none in levels 1 to 12 |  |  |
| Paladin | Divine Sense | 1 + Charisma modifier | all, on a long rest |
| Paladin | Lay on Hands | a pool of 5 x paladin level hit points | all, on a long rest |
| Paladin | Channel Divinity (level 3) | 1 | all, on a short or long rest |
| Bard | Bardic Inspiration | Charisma modifier (minimum 1) | all, on a long rest; from level 5 (Font of Inspiration) on a short or long rest |
| Wizard | Arcane Recovery | 1 per day | on a long rest; used at the end of a short rest (RS-RST-020) |
| All casters | Spell slots | by the class table | all, on a long rest |

Features that unlock beyond the level 12 cap (Cleansing Touch, Superior Inspiration, Overchannel) follow the same model and are added with the levels 13 to 20 data.

### 8.4 Class effects that happen at a rest

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-RST-020 | Arcane Recovery (Wizard, level 1): once per day, when finishing a short rest, the wizard chooses expended spell slots to recover. Their combined level must be at most half the wizard level, rounded up, and none can be 6th level or higher. A level 4 wizard can recover one 2nd-level slot or two 1st-level slots. | A choice in the short rest command; checked against the limit; spends the `arcane_recovery` resource |
| RS-RST-021 | Song of Rest (Bard, level 2): during a short rest the bard performs, and each friendly creature that can hear it and spends one or more Hit Dice at the end of the rest regains an extra die of hit points once: 1d6, 1d8 from level 9, 1d10 from level 13, 1d12 from level 17. The bard counts if it spends Hit Dice. | The rest command can name the performing bard and the creatures that heard |
| RS-RST-022 | Wizards and paladins can change their list of prepared spells when they finish a long rest, which takes at least 1 minute per spell level for each spell on the list (this time is part of the rest). | Preparation choice in the long rest command (Part E) |

### 8.5 Worked examples (forced dice)

- **Hit Dice:** a level 5 fighter with Constitution +2 spends 2 Hit Dice and rolls 6 and 3. It regains (6 + 2) + (3 + 2) = 13 hit points, capped at its maximum.
- **Long rest and Hit Dice:** a character with 8 Hit Dice, 6 of them spent, regains 4 and keeps 2 spent. If it also has 2 levels of exhaustion and ate and drank, it drops to 1.
- **Arcane Recovery:** a level 5 wizard (half of 5, rounded up, is 3) can recover one 3rd-level slot, or a 2nd and a 1st, or three 1st-level slots, but not a 6th-level slot.
- **24-hour limit:** a long rest ended at 06:00 on day 1. A second long rest that ends at 22:00 on day 1 gives no benefit; one that ends at 06:00 on day 2 does.
- **Clock and dawn:** a long rest from 22:00 that lasts 8 hours moves the clock to 06:00 and fires dawn once. A Bardic Inspiration die given at 21:55 ended at 22:05, during the rest.
- **Rounds to clock:** Bless is cast with 1 minute (10 rounds) of duration, and combat ends after 3 rounds. The effect then has 42 seconds left on the clock.
- **Interrupted rest:** a long rest is ambushed after 5 hours. There is no benefit, the clock has moved 5 hours, and the party must start again.
- **Epic Heroism:** with a 5 minute short rest, Second Wind and Action Surge return after 5 minutes of game time.

### 8.6 What Part C means for the engine

| Area | Requirement |
| --- | --- |
| Rest resolver | A pure `RestResolver` in `engine.rest`: it takes the party state, the rest request, the settings and a `RandomSource`, and returns events. One rest is one write transaction in the application layer, idempotent by `actionId`. In version 1 the DM enters everything in one dialog; a pending rest that players complete on their own screens comes with player actions (v1.5 and v2). |
| Game clock | Add time of day, dawn crossings and `advance(reason, seconds)`; effects store an absolute end time; an ordered expiry sweep. |
| Effect durations | Rounds, until end of turn, game time (with an end time), until a short or long rest, until a long rest, permanent; the conversion at encounter end. |
| Resources | `resource_def` with a maximum formula and recharge rules in the content schema; `pc_resource` stores the spent uses; the rest resolver applies recharge rules by event. |
| Data | `pc_rest_state` (the end of the last long rest); a campaign-level event log for rests and clock changes with an audience (the existing combat log belongs to encounters); settings `restVariant`, `shortRestMinutes`, `longRestMinutes`, `longRestIntervalHours`, `interruptedLongRestGivesShortRest`, `hitDiceMethod`, `startTimeOfDay`, `dawnTime`. |
| Errors | `ENCOUNTER_ACTIVE`, `REST_TOO_SHORT`, `HIT_DICE_UNAVAILABLE`, `REST_NOT_ALLOWED` (for example no hit points at the start of a long rest). A second long rest within 24 hours is not an error: it completes and logs that no benefit applied. |
| Events | `RestStarted`, `HitDieSpent`, `HpRegained`, `HitDiceRecovered`, `ResourceRecharged`, `ExhaustionReduced`, `EffectExpired`, `ClockAdvanced`, `RestInterrupted`, `RestCompleted`, all with audience ALL. |

### 8.7 Engine decisions and open points (Part C)

| ID | Question | Decision |
| --- | --- | --- |
| E-23 | How are the 24 hours of the long rest limit measured? | Decided: end to end, so a long rest gives its benefits only if it ends at least 24 hours after the previous long rest ended, so a nightly rest works |
| E-24 | What happens to round-based effects when combat ends? | Decided: they continue on the clock at 6 seconds per remaining round |
| E-25 | An interrupted long rest | Decided: no benefit by default (the SRD rule); a campaign setting gives short-rest benefits if at least 1 hour had passed (default off) |
| E-26 | How are Hit Dice healing rolls made? | Decided: rolled, as the SRD says; a campaign setting allows the fixed average instead (default off) |
| E-27 | Time of day | Decided: a 24 hour day, a start time of day (default 08:00) and a dawn time (default 06:00); calendars are deferred |
| E-28 | Food and water for exhaustion | Decided: the DM confirms per character per long rest (default yes); no ration tracking in version 1 |
| E-29 | Rest variants | Decided: presets Standard, Epic Heroism and Gritty Realism, plus custom lengths; the long rest interval is a separate setting, and the presets set it to 24 hours, 1 hour and 7 days respectively, because the variant text sets no separate limit and a 24 hour limit would defeat Epic Heroism |
| E-30 | Concentration and rests | Decided: a long rest ends concentration when it starts; a short rest does not; the DM can override. Hit Dice recovery is chosen by the player |
| E-31 | Resource maximum changes, and 'once per day' | Decided: a rising maximum raises current uses by the same amount, a falling one clamps; 'once per day' recharges on a long rest |
| V-4 | Verification debt for Part C (cleared 2026-11-16) | Cleared: the DMG variant text sets no separate long rest limit, so the presets set the interval (E-29); the SRD 5.1 text says a long rest is spent sleeping or in light activity for no more than 2 hours (the 6 hours of sleep appears only in the Player's Handbook), which RS-RST-003 follows; the SRD gives no minimum for Hit Dice healing, so the Player's Handbook's minimum of 0 is applied (RS-RST-002) |

### 8.8 Edge cases and their handling (Part C)

These cases came out of a design review of Part C. Each has a handling; decisions E-32 to E-44 were accepted on 2026-11-16.

| ID | Case | Handling |
| --- | --- | --- |
| E-32 | Hit Dice spending and the rule that the player decides after each roll, in a rest entered in one dialog | The command carries a spend plan per participant: either a count, or 'until current hit points reach a target, or no dice remain'. The resolver rolls one die at a time and stops by that rule, so the decision after each roll is honored; free per-roll choices come with the pending rest (v1.5) |
| E-33 | Order of steps inside a rest | Fixed: check preconditions; advance the clock (effects expire by end time, then by creation order; then dawns); then for each participant in the listed order: exhaustion reduction (long rest), hit points restored to the maximum as it stands after that reduction, Hit Dice spending or recovery, spell slot and resource recharge, temporary hit points end, concentration ends; then the log |
| E-34 | Dying, stable and dead participants | A dead creature, or one at 0 hit points that is not stable, cannot rest, and the whole command is rejected with the list of such participants (REST\_NOT\_ALLOWED). A stable creature at 0 hit points can pass the time but gets no long rest benefit, because it needs 1 hit point at the start; its recovery timer still completes |
| E-35 | The participant list | Not empty, no duplicates, at most 30, all in the campaign; an unknown id is NOT\_FOUND |
| E-36 | The size of a clock advance | One advance is at most 366 days; a manual advance of 0 or less is rejected |
| E-37 | Changing settings in the middle of a campaign | A changed start time of day or dawn time affects only later derived values and later dawn crossings, never earlier events; changed rest lengths apply to later rests |
| E-38 | Several dawns in one advance (for example a 7 day long rest) | One trigger per dawn crossed, in order; a recharge is capped by its maximum |
| E-39 | Things that happen at the same instant | Effects ending at or before the new time expire first; then dawn triggers fire |
| E-40 | What the idempotency request hash covers | A canonical form of the request (stable key order, normalized numbers); the participant order is kept because it fixes the order of rolls. Only a different hash conflicts |
| E-41 | A second long rest inside the interval | It completes and advances the clock but applies no benefit; the dialog warns first and the log records `long_rest_no_benefit` |
| E-42 | Rounds converted to seconds at the end of an encounter | Whole rounds remaining, with the current round counted as elapsed; effects that last until the end of a turn end with the encounter |
| E-43 | Song of Rest from several bards | Only the highest die among the performing bards applies, once per creature (same-name features do not stack) |
| E-44 | Characters who do not take part in a rest | The clock is campaign-wide, so their effects expire normally, but they get no rest benefits; a manual clock advance never gives rest benefits |

Cases checked and already covered: a rest shorter than the minimum (REST\_TOO\_SHORT); spending more Hit Dice than are unspent (HIT\_DICE\_UNAVAILABLE); two rests at once (CAMPAIGN\_VERSION\_CONFLICT); a repeated submit (idempotency); a level-up between rests (E-31); Lay on Hands as a pool resource; Arcane Recovery limits (RS-RST-020); multiclass Hit Dice (pooled by die size); an interrupted rest that still lets effects expire; pausing a session (no clock change).

## 9. Part D: Classes (levels 1 to 12; Fighter, Rogue, Paladin, Bard, Wizard)

Part D specifies the five MVP classes through level 12, with each class's single SRD subclass: Fighter (Champion), Rogue (Thief), Paladin (Oath of Devotion), Bard (College of Lore) and Wizard (School of Evocation). The class text was read in full from the SRD 5.1 pages (Fighter, Paladin and Bard from the SRD edition at dnd-srd-sphinx.readthedocs.io, Wizard from the Free Basic Rules rendition, Rogue from several SRD renditions) on 2026-11-16. The SRD text of some classes still mentions subclasses that are not part of the SRD (Battle Master, Eldritch Knight, Oath of the Ancients, Oath of Vengeance, College of Valor); they are not specified here and can arrive as content packs. Spell rules, spell lists and the spells themselves are Part E; equipment items are Part F. Part D keeps the class data, the features and the engine support they need.

The research also found community disagreements, rules that depend on the order of events (decisions made after a roll or a hit), and features whose official ruling changed over time. Section 9.5 lists each with a default and, where it is a real dispute, a campaign setting; section 9.6 records the engine decisions that follow (E-46 to E-60, decided).

### 9.1 How a class is represented

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-CLS-001 | A class definition holds: the hit die; the saving throw proficiencies; armor, weapon and tool proficiencies; the number of skills to choose and the list; starting equipment as choices (or starting gold); the level table; the levels at which a subclass is chosen and its feature levels; the ability score improvement levels; the spellcasting description if any. | Class data in content (Part E of the Architecture, content schema); one class definition per class id |
| RS-CLS-002 | Hit points: the class hit die; at level 1 the maximum of the die plus Constitution; at higher levels a roll or the fixed value plus Constitution (E-3). Fixed values: Fighter and Paladin 6, Bard and Rogue 5, Wizard 4. Spent and recovered Hit Dice are tracked per die size (Part C). | `pc_class_level` with the class, the level and the subclass; the hit die belongs to the class |
| RS-CLS-003 | Ability score improvement: one ability score by 2, or two by 1, never above 20. Levels: Fighter 4, 6, 8, 12, 14, 16, 19; Rogue 4, 8, 10, 12, 16, 19; Paladin, Bard and Wizard 4, 8, 12, 16, 19. Feats instead of improvements arrive with content packs (the SRD has one feat). | Level-up choice of kind `ASI` (the overflow pre-warning of Part A applies) |
| RS-CLS-004 | Subclass: chosen at level 3 by the Fighter, Rogue, Paladin and Bard, and at level 2 by the Wizard. Feature levels after that: Fighter 7, 10, 15, 18; Rogue 9, 13, 17; Paladin 7, 15, 20; Bard 6, 14; Wizard 6, 10, 14. | `subclass` on `pc_class_level`; the subclass adds its own features at its levels |
| RS-CLS-005 | Level-up is one campaign action. It grants the class's automatic features for the new level and asks for the choices that level needs (hit point method, ability score improvement, subclass, fighting style, expertise skills, spells known or added to the spellbook). The action is idempotent and applies all at once, and the choices are recorded. | A `LevelUpPlan` computed by the engine from the class data (what is granted, what must be chosen), then `ApplyLevelUp(choices)`; validated against the plan |
| RS-CLS-006 | The data model keeps a list of class levels so multiclassing (verified in Part A, RS-MUL) fits later, but version 1 characters have one class. Features that scale with a class (Second Wind, Arcane Recovery, Lay on Hands, spell preparation) use that class's level, not the character level. | `pc_class_level` rows; features read their own class level |
| RS-CLS-007 | Starting data per class is in the table below. A new character takes either the equipment choices or starting gold: Fighter, Paladin and Bard 5d4 x 10 gp; Rogue and Wizard 4d4 x 10 gp. | A creation choice; the items resolve in Part F |

### 9.2 Class tables (verified against the SRD)

**Starting data**

| Class | Hit die | Saving throws | Armor and weapons | Skills and tools |
| --- | --- | --- | --- | --- |
| Fighter | d10 | Strength, Constitution | all armor and shields; simple and martial weapons | choose 2 of Acrobatics, Animal Handling, Athletics, History, Insight, Intimidation, Perception, Survival; no tools |
| Rogue | d8 | Dexterity, Intelligence | light armor; simple weapons, hand crossbows, longswords, rapiers, shortswords | choose 4 of Acrobatics, Athletics, Deception, Insight, Intimidation, Investigation, Perception, Performance, Persuasion, Sleight of Hand, Stealth; thieves' tools |
| Paladin | d10 | Wisdom, Charisma | all armor and shields; simple and martial weapons | choose 2 of Athletics, Insight, Intimidation, Medicine, Persuasion, Religion; no tools |
| Bard | d8 | Dexterity, Charisma | light armor; simple weapons, hand crossbows, longswords, rapiers, shortswords | any 3 skills; 3 musical instruments of choice |
| Wizard | d6 | Intelligence, Wisdom | no armor; daggers, darts, slings, quarterstaffs, light crossbows | choose 2 of Arcana, History, Insight, Investigation, Medicine, Religion; no tools |

**Starting equipment choices**

| Class | Choices (in addition to the background's equipment) |
| --- | --- |
| Fighter | chain mail, or leather armor with a longbow and 20 arrows; a martial weapon and a shield, or two martial weapons; a light crossbow and 20 bolts, or two handaxes; a dungeoneer's pack or an explorer's pack |
| Rogue | a rapier or a shortsword; a shortbow and a quiver of 20 arrows, or a shortsword; a burglar's, dungeoneer's or explorer's pack; leather armor, two daggers and thieves' tools |
| Paladin | a martial weapon and a shield, or two martial weapons; five javelins or any simple melee weapon; a priest's pack or an explorer's pack; chain mail and a holy symbol |
| Bard | a rapier, a longsword or any simple weapon; a diplomat's pack or an entertainer's pack; a lute or any other musical instrument; leather armor and a dagger |
| Wizard | a quarterstaff or a dagger; a component pouch or an arcane focus; a scholar's pack or an explorer's pack; a spellbook |

**Fighter, levels 1 to 12**

| Level | Features gained |
| --- | --- |
| 1 | Fighting Style, Second Wind |
| 2 | Action Surge (one use) |
| 3 | Martial Archetype: Champion (Improved Critical) |
| 4 | Ability Score Improvement |
| 5 | Extra Attack |
| 6 | Ability Score Improvement |
| 7 | Champion: Remarkable Athlete |
| 8 | Ability Score Improvement |
| 9 | Indomitable (one use) |
| 10 | Champion: Additional Fighting Style |
| 11 | Extra Attack (two extra attacks, three attacks in all) |
| 12 | Ability Score Improvement |

**Rogue, levels 1 to 12** (Sneak Attack is one d6 per two levels, rounded up)

| Level | Sneak Attack | Features gained |
| --- | --- | --- |
| 1 | 1d6 | Expertise, Sneak Attack, Thieves' Cant |
| 2 | 1d6 | Cunning Action |
| 3 | 2d6 | Roguish Archetype: Thief (Fast Hands, Second-Story Work) |
| 4 | 2d6 | Ability Score Improvement |
| 5 | 3d6 | Uncanny Dodge |
| 6 | 3d6 | Expertise |
| 7 | 4d6 | Evasion |
| 8 | 4d6 | Ability Score Improvement |
| 9 | 5d6 | Thief: Supreme Sneak |
| 10 | 5d6 | Ability Score Improvement |
| 11 | 6d6 | Reliable Talent |
| 12 | 6d6 | Ability Score Improvement |

**Paladin, levels 1 to 12** (spell slots by level in the last columns)

| Level | Features gained | 1st | 2nd | 3rd |
| --- | --- | --- | --- | --- |
| 1 | Divine Sense, Lay on Hands |  |  |  |
| 2 | Fighting Style, Spellcasting, Divine Smite | 2 |  |  |
| 3 | Divine Health, Sacred Oath: Devotion (oath spells, Channel Divinity: Sacred Weapon and Turn the Unholy) | 3 |  |  |
| 4 | Ability Score Improvement | 3 |  |  |
| 5 | Extra Attack | 4 | 2 |  |
| 6 | Aura of Protection | 4 | 2 |  |
| 7 | Devotion: Aura of Devotion | 4 | 3 |  |
| 8 | Ability Score Improvement | 4 | 3 |  |
| 9 | none | 4 | 3 | 2 |
| 10 | Aura of Courage | 4 | 3 | 2 |
| 11 | Improved Divine Smite | 4 | 3 | 3 |
| 12 | Ability Score Improvement | 4 | 3 | 3 |

Oath of Devotion spells (always prepared, not counted against the prepared total): level 3 protection from evil and good, sanctuary; level 5 lesser restoration, zone of truth; level 9 beacon of hope, dispel magic. (The table of the SRD page also lists level 13 and 17 oath spells beyond the level 12 cap.)

**Bard, levels 1 to 12** (the SRD page's column headings for cantrips and spells known are swapped; the text says two cantrips and four 1st-level spells at level 1)

| Level | Cantrips | Spells known | Features gained | 1st | 2nd | 3rd | 4th | 5th | 6th |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 2 | 4 | Spellcasting, Bardic Inspiration (d6) | 2 |  |  |  |  |  |
| 2 | 2 | 5 | Jack of All Trades, Song of Rest (d6) | 3 |  |  |  |  |  |
| 3 | 2 | 6 | Bard College: Lore (Bonus Proficiencies, Cutting Words), Expertise | 4 | 2 |  |  |  |  |
| 4 | 3 | 7 | Ability Score Improvement | 4 | 3 |  |  |  |  |
| 5 | 3 | 8 | Bardic Inspiration (d8), Font of Inspiration | 4 | 3 | 2 |  |  |  |
| 6 | 3 | 9 | Countercharm, Lore: Additional Magical Secrets | 4 | 3 | 3 |  |  |  |
| 7 | 3 | 10 | none | 4 | 3 | 3 | 1 |  |  |
| 8 | 3 | 11 | Ability Score Improvement | 4 | 3 | 3 | 2 |  |  |
| 9 | 3 | 12 | Song of Rest (d8) | 4 | 3 | 3 | 3 | 1 |  |
| 10 | 4 | 14 | Bardic Inspiration (d10), Expertise, Magical Secrets | 4 | 3 | 3 | 3 | 2 |  |
| 11 | 4 | 15 | none | 4 | 3 | 3 | 3 | 2 | 1 |
| 12 | 4 | 15 | Ability Score Improvement | 4 | 3 | 3 | 3 | 2 | 1 |

**Wizard, levels 1 to 12**

| Level | Cantrips | Features gained | 1st | 2nd | 3rd | 4th | 5th | 6th |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 3 | Spellcasting, Arcane Recovery | 2 |  |  |  |  |  |
| 2 | 3 | Arcane Tradition: Evocation (Evocation Savant, Sculpt Spells) | 3 |  |  |  |  |  |
| 3 | 3 | none | 4 | 2 |  |  |  |  |
| 4 | 4 | Ability Score Improvement | 4 | 3 |  |  |  |  |
| 5 | 4 | none | 4 | 3 | 2 |  |  |  |
| 6 | 4 | Evocation: Potent Cantrip | 4 | 3 | 3 |  |  |  |
| 7 | 4 | none | 4 | 3 | 3 | 1 |  |  |
| 8 | 4 | Ability Score Improvement | 4 | 3 | 3 | 2 |  |  |
| 9 | 4 | none | 4 | 3 | 3 | 3 | 1 |  |
| 10 | 5 | Evocation: Empowered Evocation | 4 | 3 | 3 | 3 | 2 |  |
| 11 | 5 | none | 4 | 3 | 3 | 3 | 2 | 1 |
| 12 | 5 | Ability Score Improvement | 4 | 3 | 3 | 3 | 2 | 1 |

The wizard's spellbook starts with six 1st-level spells and gains two spells of a level the wizard has slots for at each wizard level; the prepared list is Intelligence modifier plus wizard level (minimum one). The paladin prepares Charisma modifier plus half the paladin level, rounded down (minimum one); the bard knows spells instead of preparing them. These preparation rules belong to Part E.

### 9.3 Features and how the engine supports them

Each row paraphrases the SRD rule (checked against the text) and names the engine support: an effect type, a trigger, a resource, a reaction window point (section 9.4) or a campaign setting (section 9.5). 'Window' in a row means the reaction window opens at that point and the creature may choose after seeing a roll or a hit.

**Fighter**

| ID | Feature and rule | Engine support |
| --- | --- | --- |
| RS-FTR-001 | Fighting Style (level 1, again for the Champion at 10; the same option cannot be taken twice): Archery +2 to attack rolls with ranged weapons; Defense +1 AC while wearing armor; Dueling +2 damage with a melee weapon held in one hand and no other weapons; Great Weapon Fighting rerolls a 1 or 2 on a damage die (once, the new roll stands) for a melee weapon held in two hands that is two-handed or versatile; Protection lets the fighter use its reaction to impose disadvantage on an attack against an adjacent creature it can see, while wielding a shield; Two-Weapon Fighting adds the ability modifier to the second attack's damage | Effect types `attack_bonus`, `ac_bonus`, `damage_bonus`, `damage_die_reroll`, a reaction window for Protection; settings `archeryThrown` and `weaponDiceScope` (E-51, E-52) |
| RS-FTR-002 | Second Wind: on its turn, a bonus action regains 1d10 plus the fighter level in hit points; once used, a short or long rest is needed | Resource (1 use, recharge short or long rest); `RollPlan` 1d10 + class level; healing capped at the maximum |
| RS-FTR-003 | Action Surge (level 2): on its turn the fighter takes one additional action on top of its action and any bonus action; one use per rest, two uses from level 17 but only once per turn | Effect `grant_action` (a counter for extra actions this turn); resource recharge short or long rest; the extra action can be any action, including Attack |
| RS-FTR-004 | Extra Attack: two attacks with the Attack action from level 5, three from level 11 | Attack action produces N attacks; N is a stat from the feature level |
| RS-FTR-005 | Indomitable (level 9): after failing a saving throw, the fighter can reroll it and must use the new roll; one use per long rest, two from level 13 | Window after a failed save; resource recharge long rest; replaces the save result |
| RS-FTR-006 | Champion, Improved Critical (level 3): weapon attacks critically hit on a roll of 19 or 20 (18 to 20 from level 15, Superior Critical) | `crit_threshold` stat on weapon attacks; the lowest threshold applies, thresholds do not add; spell attacks are not weapon attacks |
| RS-FTR-007 | Champion, Remarkable Athlete (level 7): adds half the proficiency bonus, rounded up, to Strength, Dexterity and Constitution checks that do not already use it; a running long jump covers extra feet equal to the Strength modifier | Proficiency-fraction modifier (E-55); jump distance stat |
| RS-FTR-008 | Champion, Survivor (level 18, beyond the level 12 cap): regains 5 + Constitution modifier hit points at the start of each turn at half hit points or fewer and above 0 | Trigger `START_OF_OWN_TURN`; included in the model, active in the levels 13 to 20 data |

**Rogue**

| ID | Feature and rule | Engine support |
| --- | --- | --- |
| RS-ROG-001 | Expertise (levels 1 and 6): choose two skill proficiencies, or one skill and thieves' tools; the proficiency bonus is doubled for checks using them | Proficiency level `EXPERTISE` per skill or tool; a level-up choice |
| RS-ROG-002 | Sneak Attack: once per turn, one creature hit with an attack takes extra damage (1d6 per two rogue levels, rounded up) if the attack uses a finesse or ranged weapon and either the attacker has advantage or another enemy of the target is within 5 feet of it, that enemy is not incapacitated, and the attacker has no disadvantage. The extra dice are doubled on a critical hit | Effect `add_damage_dice` with a once-per-turn token (E-50); the eligibility test uses the attack's advantage state (advantage and disadvantage cancel, Part A) and positions; dice flagged for critical doubling (D-14) |
| RS-ROG-003 | Thieves' Cant: a secret mix of dialect and code for hidden messages | Flavor language entry; no engine effect |
| RS-ROG-004 | Cunning Action (level 2): a bonus action on each of the rogue's turns to Dash, Disengage or Hide | Grants those actions as bonus actions |
| RS-ROG-005 | Uncanny Dodge (level 5): when an attacker the rogue can see hits it with an attack, a reaction halves that attack's damage (rounded down) | Window after a hit (E-46); `reduce_damage` applied after the other flat modifiers and before resistance and vulnerability, and it stacks with resistance (E-54) |
| RS-ROG-006 | Evasion (level 7): for an effect that allows a Dexterity save to take half damage, the rogue takes no damage on a success and half on a failure | Save modifier for Dexterity saves that deal half on success (E-53) |
| RS-ROG-007 | Reliable Talent (level 11): on an ability check that adds the proficiency bonus, a d20 roll of 9 or lower is treated as 10 | The per-die floor of Part A (E-13), only when the full or doubled bonus applies (E-55) |
| RS-ROG-008 | Thief, Fast Hands (3): the Cunning Action bonus action can also be a Sleight of Hand check, using thieves' tools to disarm a trap or open a lock, or the Use an Object action. Second-Story Work (3): climbing costs no extra movement and a running jump adds the Dexterity modifier in feet. Supreme Sneak (9): advantage on a Stealth check when moving no more than half speed | Added options for the bonus action; movement cost rule; advantage on Stealth under a movement condition |

**Paladin**

| ID | Feature and rule | Engine support |
| --- | --- | --- |
| RS-PAL-001 | Divine Sense: as an action, until the end of the next turn the paladin knows the location and type (celestial, fiend or undead) of such creatures within 60 feet not behind total cover, and any consecrated or desecrated place or object; uses equal 1 + Charisma modifier per long rest | Resource; an informational action with a distance query for the DM; recharge long rest |
| RS-PAL-002 | Lay on Hands: a pool of 5 x paladin level hit points per long rest; as an action touch a creature and spend any amount from the pool to heal it, or spend 5 points to end one disease or poison (several in one use); no effect on undead and constructs | Numeric resource pool; the amount chosen at use time; the target filter |
| RS-PAL-003 | Fighting Style (level 2): Defense, Dueling, Great Weapon Fighting or Protection | As RS-FTR-001 |
| RS-PAL-004 | Spellcasting (level 2): Charisma; spells are prepared (Charisma modifier plus half the level, rounded down, minimum one) from the paladin list; slots recover on a long rest; a holy symbol is the focus; the prepared list can change after a long rest | Part E; slot table above |
| RS-PAL-005 | Divine Smite (level 2): when a melee weapon attack hits, expend one spell slot to add radiant damage: 2d8 for a 1st-level slot plus 1d8 per slot level above 1st, at most 5d8, plus 1d8 against an undead or fiend (at most 6d8). The extra dice double on a critical hit | Window after a hit (E-46, E-48); `add_damage_dice` paid with a spell slot; flagged for critical doubling |
| RS-PAL-006 | Divine Health (level 3): immune to disease | `condition_immunity` for the disease effect type |
| RS-PAL-007 | Sacred Oath (level 3): Oath of Devotion grants oath spells (always prepared, not counted), Channel Divinity, then Aura of Devotion (7). Channel Divinity: one use per short or long rest; save DCs use the paladin's spell save DC | Subclass; resource; the DC uses Part E's spell save DC |
| RS-PAL-008 | Sacred Weapon (Channel Divinity): as an action, for 1 minute a held weapon adds the Charisma modifier (minimum +1) to attack rolls, sheds bright light (20 feet) and dim light (20 feet more) and counts as magical; it can end on the paladin's turn as part of another action, and it ends if the weapon is no longer held or the paladin falls unconscious | Timed effect (1 minute, E-24 conversion); `attack_bonus`; light source on the item; end conditions |
| RS-PAL-009 | Turn the Unholy (Channel Divinity): as an action each fiend or undead within 30 feet that can see or hear the paladin makes a Wisdom save, and on a failure is turned for 1 minute or until it takes damage: it must move away, cannot willingly end within 30 feet, cannot take reactions, and may only Dash or try to escape what holds it (or Dodge if it cannot move) | Effect `turned`: the engine blocks reactions and ends it on damage; the DM plays the movement (E-58) |
| RS-PAL-010 | Extra Attack (level 5): two attacks with the Attack action | As RS-FTR-004 |
| RS-PAL-011 | Aura of Protection (level 6): the paladin and friendly creatures within 10 feet add the paladin's Charisma modifier (minimum +1) to saving throws; the paladin must be conscious; 30 feet from level 18 | `aura` effect following the source; bonus to saves; the stacking rule of E-49 |
| RS-PAL-012 | Aura of Devotion (7) and Aura of Courage (10): the paladin and friendly creatures within 10 feet cannot be charmed (frightened) while the paladin is conscious; 30 feet from level 18 | `aura` with `condition_immunity` |
| RS-PAL-013 | Improved Divine Smite (level 11): every melee weapon hit deals an extra 1d8 radiant damage, added to Divine Smite's | `add_damage_dice` on melee weapon hits; flagged for critical doubling |

**Bard**

| ID | Feature and rule | Engine support |
| --- | --- | --- |
| RS-BRD-001 | Spellcasting: Charisma; spells known (not prepared) from the bard list; a spell can be swapped for another when gaining a level; ritual casting of known ritual spells; a musical instrument is the focus | Part E; tables above |
| RS-BRD-002 | Bardic Inspiration: as a bonus action, one creature other than the bard within 60 feet that can hear it gains a die (d6, d8 at 5, d10 at 10, d12 at 15) for 10 minutes; once, the creature may roll it and add the result to one ability check, attack roll or saving throw, deciding after it rolls the d20 but before the DM says whether the roll succeeds; a creature holds only one die at a time; uses equal Charisma modifier (minimum 1) per long rest, or per short or long rest from level 5 | Resource; effect `roll_bonus_die` on the target with a clock duration (10 minutes) and one use; window after the roll (E-46, E-47) |
| RS-BRD-003 | Jack of All Trades (level 2): half the proficiency bonus, rounded down, on ability checks that do not already include it | Proficiency-fraction modifier (E-55) |
| RS-BRD-004 | Song of Rest (level 2): see RS-RST-021 | Part C |
| RS-BRD-005 | Expertise (levels 3 and 10): two more skill proficiencies each time, bonus doubled | As RS-ROG-001 (skills only) |
| RS-BRD-006 | Font of Inspiration (level 5): all Bardic Inspiration uses return on a short or long rest | Recharge rule (Part C) |
| RS-BRD-007 | Countercharm (level 6): as an action start a performance that lasts until the end of the bard's next turn; the bard and friendly creatures within 30 feet who can hear it have advantage on saves against being frightened or charmed; it ends early if the bard is incapacitated or silenced or ends it | Timed `aura` giving conditional advantage; end conditions |
| RS-BRD-008 | Magical Secrets (10): two spells from any class's list (a level the bard can cast, or a cantrip) count as bard spells; two more at 14 and 18 | Part E; needs the spell lists of the other classes in the content pack |
| RS-BRD-009 | College of Lore: three bonus skill proficiencies (3); Cutting Words (3): a reaction that spends a Bardic Inspiration use, rolls the die and subtracts it from an attack roll, ability check or damage roll of a creature the bard can see within 60 feet, used after the roll but before the outcome is known or before the damage is dealt; the creature is immune if it cannot hear the bard or is immune to charm; Additional Magical Secrets (6): two spells from any class that do not count against the spells known | Window after a roll or before damage (E-46, E-47); `roll_penalty_die`; resource; content check |

**Wizard**

| ID | Feature and rule | Engine support |
| --- | --- | --- |
| RS-WIZ-001 | Spellcasting and spellbook: Intelligence; three cantrips at level 1 (four at 4, five at 10); a spellbook of six 1st-level spells plus two per level; the prepared list is Intelligence modifier plus wizard level (minimum one) and can change after a long rest; ritual casting of book spells without preparing them; an arcane focus; copying a spell into the book takes 2 hours and 50 gp per spell level | Part E |
| RS-WIZ-002 | Arcane Recovery | RS-RST-020 (Part C) |
| RS-WIZ-003 | School of Evocation: Evocation Savant (2) halves the cost and time to copy evocation spells; Sculpt Spells (2): when casting an evocation spell that affects other creatures it can see, it chooses 1 + the spell's level of them, who automatically succeed on the save and take no damage when they would take half | Part E; a choice of creatures at cast time; save override |
| RS-WIZ-004 | Potent Cantrip (6): when a creature succeeds on a save against a damaging cantrip it takes half the cantrip's damage but suffers no other effect | Save outcome override; interacts with Evasion (E-53) |
| RS-WIZ-005 | Empowered Evocation (10): add the Intelligence modifier to one damage roll of a wizard evocation spell | `damage_bonus` once per cast on one damage roll; for spells with several damage rolls the caster chooses |

### 9.4 The reaction window and the order of an attack

The class features expose a design problem that the earlier parts did not need to solve. Many features are chosen after an event, not before: Bardic Inspiration and Cutting Words after a roll but before the outcome is known, Divine Smite and Uncanny Dodge after a hit but before damage, Indomitable after a failed save, Savage Attacker-style rerolls after damage is rolled. The reaction window of Part B already pauses an action for a choice. After review on 2026-11-16 there is **one** mechanism, not two: the reaction window is generalized (decision E-46, rules RS-REA-001 to RS-REA-010 in Part B). Its options have a cost (the creature's reaction, a resource, or nothing) and an effect (change the roll or the damage, start a new action such as an opportunity attack, or cancel), so a free option such as Divine Smite and a reaction option such as Uncanny Dodge sit in the same window and share the one-reaction-per-round budget. The points below are where the window opens.

| Point | Opens | Options it can offer |
| --- | --- | --- |
| W0 | Before an attack roll, when a creature attacks a target other than the defender | Protection (the fighting style's reaction) |
| W1 | After an attack roll, before the outcome is determined | Bardic Inspiration die (attacker), Cutting Words (a bard that can see the attacker), the Shield spell and similar (Part E) |
| W2 | After a hit is confirmed, before damage is rolled | Shield and other outcome-changing reactions first; then Divine Smite (attacker); then Uncanny Dodge (defender) |
| W3 | After damage is rolled, before it is applied | A reroll-and-choose feature such as Savage Attacker (attacker); Cutting Words on a damage roll |
| WS1 | After a saving throw roll, before the outcome | Bardic Inspiration die |
| WS2 | After a saving throw is failed | Indomitable |
| WC | After an ability check roll, before the outcome | Bardic Inspiration die, Cutting Words |

A window at a point proceeds in four ordered steps (E-62): (1) options of the acting side that raise its result; (2) options of the defending side that lower the result or change the outcome; (3) damage changes by the acting side; (4) damage changes by the defending side. A decider is asked at every step where it has an eligible option, and passing at one step does not forfeit a later one. At W2 this gives Shield (step 2), then Divine Smite (step 3), then Uncanny Dodge (step 4); at W3 it gives Savage Attacker (step 3), then Cutting Words on the damage roll (step 4). The orders of a saving throw and of an ability check are RS-REA-013. After any option that changes the roll the engine recomputes the total and the outcome and, if the outcome changed (a hit became a miss), skips the later points that no longer apply. A window opens only if at least one option is eligible (a paladin with an expended slot and a hit; a creature holding an inspiration die), so a table with no such features sees no prompts. In version 1 the DM answers for the players ('Offer Divine Smite?'); in v1.5 the players answer on their own screens. The window is persisted state: it survives a restart, resumes by replaying the action from its recorded dice and decisions, nests to a depth of 6, works in scenes as well as encounters, and the DM can cancel it (the dice stay in the log). The full rules are RS-REA-001 to RS-REA-010 and their edge cases in Part B.

**The order of an attack, in full:** declare the attack; W0; roll the attack (advantage and disadvantage cancel as in Part A); W1; determine the outcome (hit, miss, critical hit); on a hit W2; roll damage (the weapon's dice plus extra dice such as Sneak Attack, Divine Smite and Improved Divine Smite, all flagged for critical doubling, with the Great Weapon Fighting reroll); W3; the damage pipeline of RS-DMG-017 (after the other flat modifiers, Uncanny Dodge's halving; then resistance, vulnerability, temporary hit points and hit points).

### 9.5 Edge cases and community conflicts

Every row was checked against the SRD text, the official Sage Advice compendium where it has an answer, and community discussions. Where the rules are clear or officially ruled, the default follows the ruling and there is no setting. Where the community is genuinely divided, the default is the best-supported reading and a campaign setting offers the alternative (the settings tiers of the Architecture, section 4.15, apply: each is asked on first use).

| ID | Topic | What was found | Default | Setting |
| --- | --- | --- | --- | --- |
| C-D1 | How often Sneak Attack applies | The feature says once per turn, not once per round, and not only on the rogue's turn; the official Sage Advice compendium confirms an opportunity attack or readied attack on another creature's turn can use it again | Once per turn, whoever's turn it is (E-50) | none |
| C-D2 | Sneak Attack without advantage | The condition is another enemy of the target within 5 feet that is not incapacitated, with no disadvantage on the attack; the enemy need not be the rogue's ally (the 2024 text says ally). A finesse or ranged weapon is required; a second attack with an off-hand weapon can carry it | SRD text as written | none |
| C-D3 | When Divine Smite is chosen | The feature says 'when you hit'. Most tables and the rules text read it as after the hit is confirmed but before damage is rolled, so a critical hit doubles the smite dice (the lead designer confirmed a critical doubles all of the attack's damage dice, including smite). A minority rule that smite is declared before the attack (slot spent only on a hit) or only after damage is rolled (no doubling) | After the hit, before damage (E-48) | `divineSmiteTiming`: after hit (default) or declared before the attack |
| C-D4 | Shield or Uncanny Dodge against a smite | Forum discussion agrees a defender must decide on Shield when the attack would hit, before learning of any smite; Uncanny Dodge only halves damage | Window order W2: outcome-changing reactions, then Smite, then Uncanny Dodge (E-46) | none |
| C-D5 | Two paladins' Aura of Protection | In 2015 the lead designer said the auras stack (not a spell). The later DMG errata (Combining Game Effects) says features with the same name apply only once, the most potent; the errata is the current rule, and many tables still stack them or house-rule it | Same-named features do not stack: the highest bonus applies (E-49; consistent with the stacking rule of Part A) | `auraOfProtectionStacking`: highest only (default) or stack |
| C-D6 | Auras and unconscious paladins; death saves | The auras need the paladin conscious. The SRD says a death saving throw is aided by spells and features that improve saving throws, so allies in the aura add the bonus to it (confirmed) | Aura ends while the source is unconscious or dead; death saves get the bonus; the source's state is read when the save is rolled and again on any reroll | none |
| C-D7 | Archery and thrown weapons | The style says 'ranged weapons'. A thrown melee weapon such as a javelin or handaxe is used for a ranged attack, but stays a melee weapon: the Sage Advice Compendium says Archery does not apply to a thrown dagger or handaxe, while a dart, which is a ranged weapon, does qualify; many tables include thrown weapons anyway | Ranged-category weapons only, as the Sage Advice Compendium rules (E-51) | `archeryThrown`: excluded (default) or included |
| C-D8 | What Great Weapon Fighting rerolls | The text says 'a damage die for an attack'; the Sage Advice Compendium says only the weapon's own damage dice, not Sneak Attack or smite dice, and says the same of Savage Attacker. Including all dice is a house rule | Weapon dice only (E-52) | `weaponDiceScope`: weapon dice only (default) or all damage dice of the attack |
| C-D9 | Dueling | 'A melee weapon in one hand and no other weapons': a shield is not a weapon, so it is allowed (the Sage Advice Compendium confirms a shield, a spellcasting focus or a free hand all fit); a versatile weapon used in one hand qualifies | As written | none |
| C-D10 | Fighting styles from feats | The SRD fighting styles are class features; the optional styles of other books are content packs | SRD styles only | none |
| C-D11 | When a bard or player decides, and what they see | 'After the roll, before the DM says whether it succeeds.' Tables differ on whether the player is told the total, only the natural die, or the outcome (the 2024 rules dropped the timing restriction, because it slowed play and blocked revealing ACs) | The total is shown, the outcome is not (E-47) | `rollDecisionInfo`: total shown (default), natural die only, or outcome shown |
| C-D12 | What Cutting Words can reduce | Attack rolls, ability checks and any damage roll, including damage with no attack roll (magic missile); a creature that cannot hear the bard or is immune to charm is unaffected | As written | none |
| C-D13 | Fractions of the proficiency bonus | The official clarification: the bonus (full, doubled or any fraction) is added to a roll only once; Jack of All Trades and Remarkable Athlete never combine (the larger applies); Reliable Talent needs a check that adds the full or doubled bonus, so it does not trigger from Jack of All Trades | Official ruling (E-55) | none |
| C-D14 | Reliable Talent with disadvantage | Each d20 of 9 or lower counts as 10 before the lower is kept; the same as the per-die floor of Part A | Part A rule | none |
| C-D15 | Uncanny Dodge and resistance | The DMG applies resistance and vulnerability after all other modifiers. Uncanny Dodge is not resistance, so it stacks with it; the official clarification is that it reduces the damage before the effects of the damage are applied, and it covers an attack that hits, not damage that follows a saving throw (for example the poison of a bite). Halving rounds down (the general rule of the Player's Handbook) | After the other flat modifiers and before resistance and vulnerability (E-54) | none |
| C-D16 | Evasion | It applies to a Dexterity save that takes half damage on a success; on a failure half damage; an automatic failure (paralyzed, stunned, unconscious) therefore takes half. Whether Evasion covers a cantrip made into a save-for-half by Potent Cantrip is disputed; the community prefers 'yes'. The 2024 text forbids Evasion while incapacitated; the SRD 5.1 text has no such sentence (confirmed) | Evasion applies to Potent Cantrip (E-53) | none |
| C-D17 | Action Surge | The extra action can be any action; it is not a bonus action; Extra Attack applies to each Attack action; a second leveled spell in one turn follows the bonus-action spell rule (Part E) | As written | none |
| C-D18 | Indomitable | The fighter rerolls a failed save and must use the new roll, even if worse; it cannot be used on a success, and it is not offered for an automatic failure, which has no roll | As written | none |
| C-D19 | Lay on Hands | Any amount up to the pool; 5 points per disease or poison; no effect on undead or constructs; it does not revive the dead | As written | none |
| C-D20 | Critical ranges | Improved Critical and Superior Critical set the threshold for weapon attacks only; the lowest threshold applies | As written | none |
| C-D21 | Bardic Inspiration | A creature holds one die at a time (the SRD gives no rule for a second offer; default: it is refused while the creature holds one); the use is spent when the die is given, not when it is rolled; a die lasts 10 minutes of game time; it can be added to an attack roll, ability check or saving throw but not to damage, and only where a roll is made | As written | none |
| C-D22 | Turned creatures | 'Turned' is not an SRD condition; the effect defines the behavior (move away, no reactions, Dash or Dodge only) | Engine enforces reactions and the end on damage; the DM plays the movement (E-58) | none |
| C-D23 | Dueling with a thrown one-handed melee weapon | The style needs a melee weapon held in one hand and no other weapon, and does not require a melee attack; the lead designer said in 2015 that it applies to a thrown melee weapon, but this is not in the compendium and some tables disagree because the weapon leaves the hand | Applies (the weapon was held in one hand when the attack was made) | none |

### 9.6 Engine decisions for Part D (decided)

| ID | Question | Decision |
| --- | --- | --- |
| E-46 | How are choices made after a roll, a hit or a failed save handled? | Decided 2026-11-16: there is one pause mechanism, the reaction window of Part B, and it is generalized. It opens at defined points of an action, offers options to several deciders, each costing the reaction, a resource or nothing, nests to a depth of 6, resumes by replaying the action from its recorded dice and decisions, and works in scenes as well as encounters (RS-REA-001 to RS-REA-010; ADR-31). The name 'reaction window' is kept; there is no separate decision window |
| E-47 | What does a reaction window show, and when are the after-roll options offered? | Decided: a setting with three modes. Total shown (the default): after-roll options are offered before the outcome, and the decider sees the total (the effective natural d20 value, after rerolls and Reliable Talent, plus modifiers) but not whether it hits or succeeds. Natural die only: offered before the outcome; the decider sees only the effective natural d20 value. Outcome shown: the outcome is revealed first and the options are offered after it and may flip it (the 2024-style rule). Whatever is revealed to a decider is logged with that decider as its audience, and the options offered never depend on more than the decider is shown (RS-REA-006) |
| E-48 | When is Divine Smite chosen? | Decided: after a hit is confirmed and before damage is rolled (step 3 of W2). The setting alternative, declared before the attack, makes the slot level part of the attack input; the slot is spent only if the attack still hits after the outcome-changing options of W2 (for example Shield), and a critical hit doubles the smite dice in both modes. The setting is on the pre-flight list for any attack by a paladin with a spell slot |
| E-49 | Do auras from several paladins stack? | Decided: no, the highest bonus applies; a setting offers stacking. An aura works only while its source is conscious; the source's state is read when the save is rolled and again on any reroll (for example an Indomitable reroll), and the bonus applies to death saving throws |
| E-50 | How is 'once per turn' tracked? | Decided: by the identity of the current turn (the creature whose turn it is, and the round). An attack made as a reaction counts toward the turn in which it happens, not the reactor's own turn, so a rogue can use Sneak Attack once on its own turn and once on another creature's turn. Outside encounters there are no turns and once-per-turn features are not limited. The token is spent exactly once, when the action completes, even though the action is replayed |
| E-51 | Does Archery apply to thrown weapons? | Decided: only to weapons in the ranged category (the Sage Advice Compendium rules that a thrown melee weapon stays a melee weapon); a setting offers including thrown weapons |
| E-52 | What do Great Weapon Fighting and reroll-and-choose features cover? | Decided: one shared setting, weaponDiceScope: the weapon's own damage dice (default) or all damage dice of the attack; it replaces the separate Savage Attacker scope setting of E-45. Great Weapon Fighting is automatic and never opens a window (rerolling a 1 or 2 is always at least as good). Savage Attacker is an option at W3 (step 3) with a follow-up choice of which total to keep (RS-REA-011) |
| E-53 | Which saves does Evasion change? | Decided: Evasion acts on the final outcome of a saving throw, after any Bardic Inspiration or Indomitable. It changes Dexterity saves that deal half damage on a success, including a cantrip made into a save-for-half by Potent Cantrip; an automatic failure takes half. An automatic failure has no roll, so no window opens for it (RS-REA-013) |
| E-54 | Where does Uncanny Dodge go in the damage pipeline? | Decided: Uncanny Dodge is a reaction option at W2 step 4, offered to the defender after Smite even if the defender passed Shield at step 2; taking it uses the reaction, and the attacker must be one the defender can see. Its effect is applied in the damage pipeline after the other flat modifiers and before resistance and vulnerability: it halves the attack's rolled damage, rounded down for each damage type; it stacks with resistance; it applies only to the damage of an attack that hits, not to damage that follows a saving throw |
| E-55 | How do fractions of the proficiency bonus combine? | Decided: the bonus is added at most once to a roll (full, doubled or a fraction, the largest applying); Reliable Talent needs the full or doubled bonus |
| E-56 | Which subclasses ship? | Decided: the SRD has one per class (Champion, Thief, Oath of Devotion, College of Lore, School of Evocation); others arrive as content packs with the same class data format |
| E-57 | How is level-up recorded? | Decided: one idempotent action driven by an engine-computed plan; choices are validated and recorded; characters hold a list of class levels, with one class in version 1. The choices are inputs, not window decisions, because they do not depend on a result. A level-up that touches a creature in an open reaction window is rejected until the window closes (RS-REA-012) |
| E-58 | How are effects the engine cannot fully enforce handled (for example Turn the Unholy's movement)? | Decided: the engine applies what it can (for a turned creature, or any effect that bars reactions, reaction-cost options are not offered; the effect ends on damage; a visible marker shows the rules text) and the DM plays the rest |
| E-59 | Where do spell rules for the classes live? | Decided: Part E holds preparation and known-spell rules, the spellbook, Magical Secrets and the spell lists; Part D only holds the tables and the feature hooks. Part E also defines every reaction spell (Counterspell, Shield, Hellish Rebuke and others) as an option with a point, a cost and an effect, and adds no new kind of window |
| E-60 | How are starting equipment and wealth handled? | Decided: the creation choice is recorded now; the items resolve through Part F; E-113 resolves the item names to content ids and leaves wearing and holding to the player |
| V-5 | Verification of Part D (cleared 2026-11-16) | Cleared; see section 9.9 |
| E-61 | Do reaction windows work outside encounters? | Decided: yes. The scope is an encounter or a scene (ability checks and saves outside combat), from version 1; outside combat a creature's reaction is available unless the DM says otherwise |
| E-62 | In what order are options offered in a window? | Decided: a window at a point proceeds in four ordered steps: (1) options of the acting side that raise its result; (2) options of the defending side that lower the result or change the outcome; (3) damage changes by the acting side; (4) damage changes by the defending side. A decider is asked at every step where it has an eligible option, and passing at one step does not forfeit a later one; deciders at a step go in initiative order; after any option the result is recomputed and the remaining options re-evaluated |
| E-63 | What happens to the dice of a cancelled action? | Decided: they stay in the log and the action's effects are not applied, so a cancel cannot be used to reroll |
| E-64 | How deep can reactions nest, and can a player mute a prompt? | Decided: depth 6, after which no further reactions are offered and the log and a metric record it; a per-creature 'always pass' choice for a feature arrives in v1.5 and never reveals information |
| E-65 | What keeps the replay valid while a window is open? | Decided: an open window freezes the creatures in it: a level-up, rest, clock advance, DM edit or other action touching them is rejected until it closes (answers and cancel still work). The open action pins the settings it has read and the content definition versions it started with (RS-REA-012) |
| E-66 | Can an option need a second choice (which total to keep)? | Decided: yes, a mandatory follow-up choice by the same decider, in the same step, recorded in the continuation (RS-REA-011) |
| E-67 | How do ask-before-use settings work inside a reaction chain? | Decided: an action spawned by an option that needs an undecided setting after dice were rolled uses the default for that action; the setting stays undecided and is asked before the next action that needs it (RS-REA-014) |
| E-68 | Which names do the window points have? | Decided: one canonical list (RS-REA-001): ON\_MOVE\_STEP, BEFORE\_ATTACK\_ROLL, AFTER\_ATTACK\_ROLL, AFTER\_HIT, AFTER\_DAMAGE\_ROLL, AFTER\_SAVE\_ROLL, AFTER\_SAVE\_FAIL, AFTER\_CHECK\_ROLL, ON\_CAST, AFTER\_DAMAGE\_TAKEN, ON\_READIED\_TRIGGER; the older trigger names and the W labels are aliases |

### 9.7 What Part D means for the engine

| Area | Requirement |
| --- | --- |
| Class data | A class definition format in content (section 9.1); one file per class and subclass; features declare their effects, triggers, resources and the windows they use; the format is fixed by the Content Schema Specification (the class and subclass kinds, section 9 of that document) |
| Level-up | `LevelUpPlan` (what is granted, what is chosen) and `ApplyLevelUp(choices)`; the plan is a pure function of the class data and the character |
| Feature state | Per-character feature state: spent resources (Part C), chosen options (fighting styles, expertise, subclass), and once-per-turn tokens keyed to the turn identity (E-50) |
| Effects | The catalog in section 9.3: `attack_bonus`, `ac_bonus`, `damage_bonus`, `add_damage_dice`, `damage_die_reroll`, `grant_action`, `crit_threshold`, `reduce_damage`, `roll_bonus_die`, `roll_penalty_die`, `aura`, `condition_immunity`, `turned`, proficiency fractions (`proficiency_fraction`) and the d20 floor (`roll_floor`). In content, `attack_bonus`, `ac_bonus`, `damage_bonus` and `crit_threshold` are labels of this catalog only: each is stored as a `modifier` entry on the matching stat key, and the other names are the registry names of the Content Schema Specification (section 7) |
| Reaction window | The `reaction window of Part B, extended as in section 9.4 and RS-REA-001 to RS-REA-010 (ADR-31); persisted in the pending_reaction` table |
| Auras | An aura resolver that, for each saving throw or condition check, finds the sources in range (distance from the grid, with the adjacency function of RS-MON-018), applies the highest bonus (or all, by setting), and ignores unconscious or dead sources |
| Settings | `divineSmiteTiming`, `auraOfProtectionStacking`, `archeryThrown`, `weaponDiceScope` (replacing the Savage Attacker scope), `rollDecisionInfo` (the registry of Architecture 4.15) |
| Tests | Every feature row of section 9.3 gets a conformance test with a forced-dice scenario; a data-driven test checks each class table (slots, Sneak Attack dice, Channel Divinity uses) against section 9.2; each worked example below is a named test; each point has a test for the order of options and for outcome recomputation, and the reaction window suite (Architecture 12.1, T-22 to T-39) covers resume, nesting, the reaction budget, cancel, information leaks, pinned settings and content, frozen state and settings during a chain |

### 9.8 Worked examples (forced dice)

- **Sneak Attack on a critical hit:** a level 5 rogue (3d6) hits with a dagger (1d4) and a Dexterity modifier of +4. A critical hit doubles all the dice: 2d4 + 6d6, plus 4 once.
- **Divine Smite:** a level 2 paladin spends a 2nd-level slot on a hit against an undead: 3d8 plus 1d8 for the undead, 4d8 in all; on a critical hit 8d8. A 5th-level slot against an undead is capped at 6d8.
- **Aura of Protection:** a paladin with Charisma 18 (+4) and an ally 8 feet away: the ally adds +4 to its saving throw. A second paladin with +3 whose aura also reaches the ally: by default the ally adds +4 (the highest), not +7.
- **Lay on Hands:** a level 6 paladin has a pool of 30. It heals an ally for 12 (18 left), then spends 5 to end a disease (13 left).
- **Bardic Inspiration:** a creature holding a d6 die rolls a natural 14 on an attack for a total of 17 against AC 18. In W1 it spends the die and rolls 3, making 20, which hits. If the die were rolled before it could be used, the total would stay 17.
- **Reliable Talent:** a rogue with Stealth proficiency rolls a 7 on a Stealth check; it is treated as 10. On an unproficient Perception check with Jack of All Trades from a multiclass bard level, the half bonus is added but Reliable Talent does not apply.
- **Second Wind:** a level 5 fighter regains 1d10 + 5 hit points, capped at its maximum.
- **Action Surge:** a level 5 fighter takes the Attack action (two attacks), uses Action Surge and takes the Attack action again: four attacks in the turn.
- **Prepared spells:** a level 5 paladin with Charisma 14 prepares 2 + 2 = 4 spells; a level 3 wizard with Intelligence 16 prepares 3 + 3 = 6.

### 9.9 Verification of Part D (V-5, cleared 2026-11-16)

The class text, tables and most rulings above were checked directly. Six points were left open and have now been checked against the SRD and Basic Rules text and the Sage Advice Compendium (v2.7, 2021, as quoted by sources that cite it); the results are below. The Compendium is the official source for rulings and is still treated as RAW for the 2024 rules wherever they did not change.

- **Evasion (confirmed):** the SRD 5.1 and Basic Rules Rogue text has no sentence about being incapacitated; the 2024 text adds one. The 2014 rule stands: a Dexterity save for half damage gives no damage on a success and half on a failure.
- **Death saves and Aura of Protection (confirmed):** the SRD says a death saving throw is not tied to an ability score and that the creature is 'aided only by spells and features that improve your chances of succeeding on a saving throw', so the aura bonus applies. The aura still needs its source to be conscious.
- **Archery and thrown weapons (confirmed):** the Sage Advice Compendium says Archery does not apply to a melee weapon that is thrown (a dagger or handaxe stays a melee weapon); a dart is a ranged weapon and qualifies. The default stands; including thrown weapons is a house rule. The same compendium confirms Dueling supports a shield, a spellcasting focus or a free hand.
- **Great Weapon Fighting (confirmed):** the compendium says it does not reroll Divine Smite's dice, and the lead designer said it covers only the weapon's own damage dice; the compendium says the same of Savage Attacker. The default stands; including all dice is a house rule.
- **Uncanny Dodge (decided by reading, community split):** the official statements are that it reduces damage before the effects of the damage are applied and that it covers only an attack that hits, and the DMG applies resistance and vulnerability after all other modifiers. The order against flat reductions is not ruled. The default (E-54) halves the attack's rolled damage after the other flat modifiers and before resistance and vulnerability, rounded down for each damage type, and it stacks with resistance. This replaces the earlier draft, which put it last.
- **Starting wealth (confirmed):** Fighter, Paladin and Bard 5d4 x 10 gp; Rogue and Wizard 4d4 x 10 gp. The Basic Rules table covers the Fighter, Rogue and Wizard; the Player's Handbook table covers the Bard and Paladin, and secondary sources agree. The values are recorded as data.
- **Bardic Inspiration, a second die (open by the rules):** the SRD says only that a creature holds one die at a time; it gives no rule for a second offer. The default refuses the offer. The use is spent when the die is given, not when it is rolled.

## 10. Part E: Spellcasting (draft)

Part E specifies how the engine stores, prepares and casts spells. It fixes the order of a cast, concentration, areas of effect on the grid, what a tier-2 spell can do, how reaction spells use the reaction window of Part B, and the curated tier-2 list for the five MVP classes. Status: first draft of 2026-11-16, built from the SRD 5.1 Spellcasting chapter and spell lists, with unofficial sources named wherever a conflict is settled. Engine decisions E-69 to E-91 were decided between 2026-11-16 and 2026-11-18. Part E is propagated by reference to the Architecture, the Requirements Specification and the AI Build Brief (see 10.11); this section is the only place that defines these rules, settings and tests.

### 10.1 Sources, what was checked and what was not

The SRD 5.1 Spellcasting chapter was read in full from two renderings (5thsrd.org and the Sphinx edition) and the rules text is identical. Class spell lists and spell entries come from 5thsrd.org, whose tables are complete. A first read of the Sphinx list page turned out to be incomplete (it left out Vicious Mockery, Animal Friendship, Fire Bolt, Poison Spray, Find Familiar, Counterspell, Compulsion and Branding Smite, among others), so it is not used for anything. SRD 5.1 text is licensed CC BY 4.0 (attribution in section 1). Every SRD rule in this part is paraphrased, not copied.

| Question | What was checked | Finding |
| --- | --- | --- |
| SRD spellcasting rules | SRD 5.1 text, two renderings, read in full | Identical; the basis of 10.3 to 10.6 |
| Class spell lists | 5thsrd.org tables for Bard, Paladin, Wizard and Warlock | Counts below. The only smite spell in the SRD is Branding Smite (paladin, 2nd level); Hellish Rebuke is SRD (warlock, 1st level); Witch Bolt and Ray of Sickness are not SRD |
| Spell entries | 5thsrd.org and other SRD copies: entries read for all 40 curated spells (marked 'read' in 10.9) and for Feather Fall | Text paraphrased in 10.7 to 10.9; every tier-2 spell has now been read (V-6 cleared) |
| Data shape | The dnd5eapi (5e-bits) spell JSON and the Foundry dnd5e activity types | Models for 10.2 and 10.7; neither is a dependency |
| Bonus action spells and reactions | Crawford, 5 December 2016 (reproduced on sageadvice.eu); the Sage Advice Compendium as quoted on forums | A reaction spell cast on the caster's own turn counts against the rule; the order of casting does not matter |
| Counterspell information | SRD text; Crawford (the caster need not know the spell); Xanathar's identification rule (a reaction and an Arcana check), quoted on forums | The SRD has no identification step; the Xanathar rule is outside the SRD |
| Magic Missile and concentration | Mearls (2014: one source) and Crawford (2016: each dart a source), as summarized on forums | A real conflict; C-E5 |
| Areas on a grid | DMG p.251 and Xanathar's p.86, as quoted on forums | Origin at a grid intersection; circular areas cover a square if they cover at least half of it; the template method counts any covered part |

The books themselves were not read, only quotations of them, so everything taken from the DMG, Xanathar's, Sage Advice and tweets is marked unofficial or secondary and sits on the verification list (V-6).

**Spell list counts (SRD 5.1, counted by hand from the 5thsrd.org tables; the pack validation test recounts them).**

| Class | Cantrips | 1st | 2nd | 3rd | 4th | 5th | 6th | 7th | 8th | 9th | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Bard | 9 | 20 | 19 | 15 | 8 | 16 | 7 | 10 | 5 | 3 | 112 |
| Paladin | 0 | 11 | 8 | 6 | 3 | 3 | 0 | 0 | 0 | 0 | 31 |
| Wizard | 14 | 27 | 31 | 28 | 23 | 23 | 19 | 15 | 12 | 12 | 204 |

### 10.2 Spells as data, and the three tiers

A spell is one definition in a content pack. The fields below are what the engine reads; the Content Schema Specification gives the exact format (the spell kind, section 9 of that document).

| Field | Meaning |
| --- | --- |
| id, name, level, school | Stable content id; level 0 to 9 (0 is a cantrip); one of the eight schools |
| castingTime | An action, a bonus action, a reaction (with the point it waits for), or a number of minutes or hours |
| ritual | True if the spell has the ritual tag |
| range | Self (with an area origin), touch, or a distance in feet |
| components | V, S, M, with the material text, a cost in gp if any, and whether it is consumed |
| duration | Instantaneous, a length, 'until dispelled', and a concentration flag |
| classes | The class lists the spell belongs to |
| text, higherLevel | The SRD text and the upcasting text, shown to the DM |
| tier | 0, 1 or 2 |
| effects | Tier 1: the rolls. Tier 2: the effect list of 10.7, scaled by slot level and character level |
| reaction | For reaction spells: the point, a filter and the cost (10.8) |

Architecture 4.9 defines three tiers by what the engine does with a spell. This part refines it (E-69): the bookkeeping of a cast does not depend on the effect, so every tier shares it, and the tiers differ only in how the effect is resolved.

| Tier | Shared by all tiers | Effect resolution | Scope |
| --- | --- | --- | --- |
| 0 | The cast is recorded, the slot is spent, the action economy and the bonus action rule apply, concentration is tracked, V components and costed components are checked | None. The spell text is shown and the DM applies the effect with the manual tools (conditions, modifiers, hit points) | Every SRD spell of levels 0 to 6 by default |
| 1 | Same | The engine rolls the spell's damage or healing dice, scaled by slot or character level, logs them, and offers apply buttons to the DM | Damage and healing spells outside the curated list |
| 2 | Same | The engine resolves the spell: targeting and areas, attack rolls, saves, damage, healing, conditions, modifiers, zones, durations and concentration | The curated list in 10.9 |

A spell is chosen for tier 2 when it meets three tests: one of the five classes casts it at levels 1 to 5 (or it is a key reaction); it can be written with the primitives of 10.7 without a new kind of engine concept; and its effect changes state the engine already tracks. A new primitive costs more than a new spell that uses it, so the list was chosen to exercise every primitive at least once (10.9). Moving a spell up a tier changes data, not engine code, which is the rule of Architecture 4.9.

### 10.3 Slots, known and prepared spells, preparation, rituals

Each row paraphrases the SRD rule and names the engine support. Class numbers (slots, spells known, prepared counts) are in Part D, section 9.2.

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-SPL-001 | A spell has a level from 0 to 9. A caster casts a leveled spell by expending a slot of that level or higher, and the spell takes the slot's level for that casting. A cantrip uses no slot. A long rest restores all slots; some features restore slots otherwise (Part D) | The kernel slot pool derives slots by level from the class tables and stores only the used counts; `CastSpellAction` carries the slot level |
| RS-SPL-002 | A caster has a limited list of spells fixed in mind: a bard knows spells, a wizard and a paladin prepare them. The counts depend on class level | `character_spell` rows with a source (KNOWN, PREPARED, SPELLBOOK, ALWAYS\_PREPARED, GRANTED) and the class that grants them |
| RS-SPL-003 | A wizard prepares Intelligence modifier + wizard level spells (minimum 1) from the spellbook; a paladin prepares Charisma modifier + half the paladin level, rounded down (minimum 1), from the paladin list. Each must be of a level the caster has slots for. Always-prepared spells (oath spells) do not count against the number | The class definition validates the list; the engine computes the allowed count, like the level-up plan (E-57) |
| RS-SPL-004 | The prepared list can change after a long rest, and preparing takes at least 1 minute per spell level for each spell on the list | `PrepareSpellsAction`: allowed once after each completed long rest and never inside an encounter; it advances the clock by the sum of the spell levels in minutes (cantrips are free); the DM can waive the time (E-78) |
| RS-SPL-005 | A spell with the ritual tag can be cast normally or as a ritual. The ritual takes 10 minutes longer, spends no slot and cannot be cast at a higher level. The caster needs a ritual feature and the spell on the list that feature names: a wizard's spellbook even if unprepared; the other classes as their class text says (Part D) | `RitualCastAction`, a campaign action outside encounters; it advances the clock by the casting time plus 10 minutes, and the DM confirms the ritual was not interrupted (E-80) |
| RS-SPL-006 | A caster must be proficient with every armor and shield worn to cast a spell | Precondition `CAST_ARMOR_NOT_PROFICIENT`; the facts are computed from the worn armor and shield (Part F, RS-EQP-008, E-96) |
| RS-SPL-007 | Spell save DC = 8 + proficiency bonus + spellcasting ability modifier (+ special modifiers). Spell attack bonus = proficiency bonus + spellcasting ability modifier. The ability is the one of the class that grants the spell (Bard and Paladin: Charisma; Wizard: Intelligence); a spell gained from another list through Magical Secrets counts as a bard spell | Kernel stats `stat:spell_dc` and `stat:spell_attack` per class; each cast records which class is casting (E-87) |
| RS-SPL-008 | Cantrip damage grows at levels 5, 11 and 17 (Fire Bolt 1d10, 2d10, 3d10, 4d10). The level is the character's total level (PHB; E-81) | Scaling by `characterLevel` in the effect definition |
| RS-SPL-009 | Some spells grow with a higher slot: extra dice, targets or darts | Scaling by slot levels above the base level in the effect definition, checked by the pack validation test against the SRD text |

### 10.4 Casting a spell

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-SPL-010 | Most spells take 1 action; some take a bonus action, a reaction, or much longer | `castingTime` on the definition decides which part of the turn budget the cast uses |
| RS-SPL-011 | Bonus action rule: after a spell cast as a bonus action, the caster cannot cast another spell that turn except a cantrip with a casting time of 1 action. The order does not matter (Sage Advice): a caster that has already cast a spell other than a 1-action cantrip this turn cannot cast a bonus action spell. A reaction spell cast on the caster's own turn counts (Crawford, 2016). A reaction spell cast on someone else's turn is not limited | Per-turn flags `bonusActionSpellCast` and `nonCantripSpellCast`, cleared at the start of the creature's turn; precondition `SPELL_BONUS_ACTION_RULE`; setting `bonusActionSpellRule` (E-71) |
| RS-SPL-012 | A reaction spell is cast in response to the trigger its text names and uses the reaction | A reaction spell is an option in the reaction window (10.8), not an action the player sends |
| RS-SPL-013 | A spell with a casting time longer than 1 action takes the caster's action every turn and needs concentration while it is being cast; if concentration breaks the spell fails and no slot is spent | Version 1: rejected inside an encounter (`CAST_TIME_TOO_LONG`, the DM runs it); outside combat a long casting is a campaign action the DM confirms (E-80) |
| RS-SPL-014 | The target must be within range. A touch spell needs a touched creature. Once cast, a spell's effects are not limited by its range | Range measured from the caster's space with the grid metric; touch means within 5 feet (Grid specification) |
| RS-SPL-015 | Components: a V spell cannot be cast when gagged or in silence; an S spell needs free use of a hand; an M spell needs the object, or a component pouch or spellcasting focus; a costed component must be the specific item; a consumed component is needed for every casting | Version 1 checks V through conditions and zones (`cannot_speak`, Silence), costed or consumed components through inventory items and the focus and component pouch flags, and S through the free-hand rule of Part F (E-79, E-98, RS-EQP-016) |
| RS-SPL-016 | A target needs a clear path, so it cannot be behind total cover. An area placed at a point the caster cannot see behind an obstruction begins on the near side of it | The vision seam (full by default) and the Grid cover seam; precondition `CAST_NO_CLEAR_PATH` |
| RS-SPL-017 | A spell that targets a creature of the caster's choice can target the caster unless it must be hostile or another creature. A caster inside the area of its own spell can be affected | A target filter on the definition |
| RS-SPL-018 | The slot, the action and the cast are committed when casting begins. A spell that is then countered has still spent them | A `SpellCast` event is written before the ON\_CAST window opens (E-70) |

**Order of a cast.**

1. Declare the caster, the spell, the slot level (leveled spells), the targets or the point and direction, and whether it is a ritual.
2. Check preconditions; a failed check rejects the command and changes nothing: the caster can act; the spell is known, prepared or granted; a slot is available; the casting time fits the turn budget and the bonus action rule; components, armor, range, a clear path and target filters; the number of targets the slot allows; the placement of an area.
3. Commit: spend the slot and the action, set the per-turn flags, write `SpellCast`, and end the caster's current concentration if the new spell needs concentration (E-72).
4. Open the ON\_CAST window (10.8). Creatures that can see the caster may counter it; creatures targeted by Magic Missile may use Shield. If the cast is cancelled, nothing else happens and the commitments stand.
5. Resolve the effects in the order of 10.7: attack rolls and saves through their windows (RS-REA-013), then damage through the pipeline of RS-DMG-017, then conditions, modifiers, zones and forced movement.
6. Record what lasts: active effects and zones carry the cast, the caster and, for a concentration spell, the new concentration record.
7. Write the events and the log text. Everything from step 3 on is atomic with the action it belongs to.

### 10.5 Concentration in the engine

Part B already states the SRD concentration rules (RS-ACT-012), that damage absorbed by temporary hit points still counts as damage taken (E-18), and that a long rest ends concentration (E-30). This section adds what the engine needs to run them.

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-SPL-020 | A creature concentrates on at most one spell. Casting another concentration spell ends the first, with no roll. It takes effect when the new cast is committed (E-72) | A `ConcentrationRecord` per combatant: spell instance, caster, slot level, start time, and the effects and zones tied to it |
| RS-SPL-021 | Taking damage while concentrating requires a Constitution saving throw with DC 10 or half the damage taken, whichever is higher (fractions round down) | Triggered by the damage-taken event, which is emitted even when temporary hit points absorb everything; the DC uses the event amount after resistance and vulnerability |
| RS-SPL-022 | A separate save is made for each source of damage. A source is one damage instance: one attack that hits, or one spell effect on one target, with all its damage types added together. Ongoing damage (a zone each turn, a burning effect) is a new instance each time. Magic Missile darts are separate instances by default (E-73) | A `DamageInstance` id on each damage event; setting `missileDamageSources` |
| RS-SPL-023 | Being incapacitated or dying ends concentration, with no roll | Condition hooks of Part B (unconscious includes incapacitated) |
| RS-SPL-024 | A creature can end concentration at any time, with no action. The DM can also end it | `EndConcentrationAction`, free |
| RS-SPL-025 | The DM may call for a DC 10 Constitution save for an environmental event | `RequestConcentrationSave(dc)` among the DM tools |
| RS-SPL-026 | When concentration ends, every effect and zone of that spell ends at once | Cascade: effects with `concentrationOf` are removed, zones deleted, and one `ConcentrationEnded` event is written with its reason (SAVE\_FAILED, NEW\_SPELL, INCAPACITATED, DEAD, VOLUNTARY, DISPELLED, DURATION, REST, DM) |
| RS-SPL-027 | A concentration save is a saving throw, so bonuses and advantage apply (Aura of Protection, Bardic Inspiration, Bless), and the windows WS1 and WS2 open (Indomitable can reroll it). When one save in a batch fails, the later saves of that batch are skipped and logged | The normal save pipeline (RS-REA-013); the batch is the damage events of one resolved action, in order |
| RS-SPL-028 | A duration of 'up to 1 minute' ends the effect at that time on the clock. A readied spell fizzles if concentration breaks (Part B) | An absolute end time on the clock; duration kind CONCENTRATION with a maximum |

### 10.6 Areas of effect on the grid

The SRD defines five shapes and says nothing about grids. The grid rules below are the DMG (p.251, read in Crawford's tweet) and Xanathar's (read second-hand; its page numbers were not found, V-6), made exact so the engine never needs floating point. Area shapes stay in the Java engine (the Grid and Pathfinder Specification has no area endpoint). All distances are in feet; the metric setting only changes how they are displayed.

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-SPL-030 | An area is a cone, cube, cylinder, line or sphere, each with a point of origin. The effect expands in straight lines from the origin; a location is left out if no unblocked straight line reaches it, and only total cover blocks | `AreaShape` in `engine.grid`; a line-of-effect test from the origin to the center of each candidate square against the map's blocking edges. Until the map has blocking edges the DM marks creatures behind total cover as excluded on the cast |
| RS-SPL-031 | On a grid a point-in-space origin is a grid intersection (DMG). An area that starts at the caster has its origin on the boundary of the caster's space, and the caster is never inside its own cone, cube or line unless the spell says so | Coordinates are integers in units of 2.5 feet: intersections have even coordinates and square centers odd ones. The origin is stored as such a pair |
| RS-SPL-032 | Sphere and cylinder: the origin is included. A square is in the area if the area covers at least half of it (DMG). Engine definition: the square's center is within the radius of the origin, measured in a straight line (E-74) | Integer test on squared distance, so no square root |
| RS-SPL-033 | Cone: the width at any point equals its distance from the origin; the spell gives the length. The direction is chosen by a second grid intersection, so any direction is possible. By default (HALF\_ALL) a square is in the area if its center is inside the cone, the proxy for 'at least half covered'; sampled areas for 15, 30 and 60 foot cones along an axis and a diagonal showed no difference (V-6). With areaCoverageRule at DMG\_XGTE, any positive part counts and touching an edge only does not (the template method of Xanathar's). The origin is not included | The test compares squared lengths, so no square root or trigonometry; exact for every direction |
| RS-SPL-034 | Cube: the spell gives the side; the origin lies on a face and the cube is aligned with the grid. From the caster, the cube is placed so that at least one full edge of the caster's space lies on a face of the cube (a 15-foot cube from self has 12 placements). The origin is not included | A list of placements the interface offers; the cast carries the chosen one |
| RS-SPL-035 | Line: from the origin along a direction for its length, with the width the spell gives. By default a square counts if its center is inside the line (sampled areas for a 100-foot line along an axis and a diagonal agree); under DMG\_XGTE any positive overlap counts. The origin is not included | A rectangle test in integers |
| RS-SPL-036 | A creature larger than one square is affected if any square it occupies is in the area | Footprint of the combatant |
| RS-SPL-037 | The setting `areaCoverageRule` chooses between half of a square for every shape, decided by the center test (HALF\_ALL, the default), and the Xanathar's template method for cones, cubes and lines, any positive overlap (DMG\_XGTE) (E-74, E-90) | An integer center-in-shape test for HALF\_ALL; exact overlap by integer geometry for DMG\_XGTE |
| RS-SPL-038 | Version 1 is flat: a cylinder is treated as a circle and elevation is ignored (E-76) | A seam in `GridGeometry` for height in v2 |

**Reference counts for a sphere or cylinder (center test, origin on an intersection).** These are golden values for test T-43, counted by hand.

| Radius | 5 ft | 10 ft | 15 ft | 20 ft | 30 ft |
| --- | --- | --- | --- | --- | --- |
| Squares in the area | 4 | 12 | 32 | 52 | 112 |

The center test is the engine's exact definition of 'covers at least half'. For the five reference radii it gives exactly the squares of the true half-area rule: an exact integer comparison on 2026-11-16 found no square that differs (V-6, repeated by T-43). Other radii are not claimed. For cones, lines and cubes the same center test is the default meaning of 'at least half covered' (HALF\_ALL): a sampled comparison for 15, 30 and 60 foot cones along an axis and a diagonal, 100 and 60 foot lines and a 15-foot cube from self found no square that differs (V-6). Area code may not use floating-point types or `Math.sqrt`, `Math.sin`, `Math.cos`, `Math.atan2` or `Math.hypot` (an addition to the forbidden-API rule of T-6).

### 10.7 What a tier-2 spell can do: the effect primitives

Additions from Part H. Zones carry the flags of RS-ENV-004, RS-ENV-009 and RS-ENV-047. Three effect types are added to the primitives: start\_fall, ends\_affliction and suppress\_affliction. The area code asks lineOfEffect (RS-ENV-026) before an effect reaches a cell. Targeting that needs a target the caster can see calls canSee (RS-ENV-016). Darkness and Daylight use the overlap rule through the spell data (RS-ENV-004). In version 2 Fly and Levitate are resolved by the engine (RS-ENV-043). Before that they stay at tier 0.

A tier-2 effect list is built from the primitives below. The shape follows the dnd5eapi spell record (damage by slot level, a save with a success rule, an area) and the Foundry activity types (attack, save, damage, heal, utility), with the additions this engine needs. The names are final: they are the effect type names of the Content Schema Specification (section 7), which also fixes the fields of each.

| Primitive | What it does | Used by (tier-2 list) |
| --- | --- | --- |
| `spell_attack` | A ranged or melee spell attack roll with the caster's spell attack bonus. A critical hit doubles the damage dice (Part B). A ranged spell attack within 5 feet of a hostile creature that can see the caster and is not incapacitated has disadvantage. Cover applies. The attack goes through the windows W0 to W3 | Fire Bolt, Ray of Frost, Shocking Grasp, Chill Touch, Scorching Ray |
| `save` | The target rolls the ability save against the spell save DC. Outcomes: nothing, half damage, or the effect applies. Repeat saves (at the end of each of its turns, when it takes damage, with or without advantage) are part of the effect. The save goes through WS1 and WS2 | Acid Splash, Vicious Mockery, Burning Hands, Thunderwave, Faerie Fire, Bane, Hold Person, Hideous Laughter, Shatter, Fireball, Lightning Bolt, Slow, Fear, Hypnotic Pattern, Web |
| `damage` | Dice from a formula that scales by slot level, character level, darts or rays; a damage type; applied through the pipeline of RS-DMG-017; half on a successful save when the spell says so | Most attack and save spells |
| `heal` | Dice plus the spellcasting modifier; no effect on undead or constructs; never above the maximum; a creature at 0 hit points regains consciousness and its death saves reset (Part B) | Cure Wounds, Healing Word |
| `condition` | Applies an SRD condition for the duration, with optional repeat saves and end triggers (damage, being shaken awake) | Hold Person (paralyzed), Hideous Laughter (prone, incapacitated), Hypnotic Pattern (charmed, incapacitated, speed 0), Fear (frightened), Sleep (unconscious), Web (restrained) |
| `modifier` | A bundle of stat modifiers, roll bonus or penalty dice, advantage flags or rules as an active effect, with a duration and optional end triggers (the target attacks, the target casts a spell) | Shield, Mage Armor, Shield of Faith, Blur, Bless (d4 on attack rolls and saves), Bane (d4 subtracted), Haste, Slow, Heroism, Invisibility, Aid (maximum hit points), Ray of Frost (speed), Shocking Grasp (no reactions), Chill Touch (no healing) |
| `hp_pool` | Sleep: roll a total; affect creatures in ascending order of current hit points, subtracting each from the total while it still covers the creature's hit points; skip unconscious, undead and charm-immune creatures | Sleep |
| `temp_hp` | Grants temporary hit points (they replace, not add) | Heroism (at the start of each of the target's turns) |
| `forced_movement` | Pushes a creature a number of feet away from the origin; stops at obstacles; provokes nothing | Thunderwave |
| `teleport` | Moves the caster to an unoccupied space it can see within range | Misty Step |
| `zone` | A persistent area with its own rules: difficult terrain, obscurement, a save when a creature enters or starts its turn there, a Strength check to escape; tied to the cast; may end early (Web collapses if not anchored) | Web |
| `remove_condition` | Ends a named condition or disease on a target | Lesser Restoration |
| `revive` | Returns a creature that died within the last minute to 1 hit point; needs the time of death on the clock | Revivify |
| `next_hit_rider` | Adds damage dice to the next weapon hit (Branding Smite, ended by the hit) or to every weapon hit (Divine Favor); doubled on a critical hit | Branding Smite, Divine Favor |
| `dispel` | Ends spell effects on a chosen creature, object or effect: a spell of a level up to the slot's level ends; a higher one needs an ability check against 10 + its level (window WC applies) | Dispel Magic |
| `counter` | Cancels a cast in the ON\_CAST window (10.8) | Counterspell |
| `reaction_option` | Declares that a spell is offered as an option in a window, with its point, cost and filter (10.8) | Shield, Counterspell |

**Resolution inside one cast.** Targets are fixed at declaration. For an area spell the damage is rolled once and applied to every target (a save decides full or half); for attack spells each attack rolls its own damage. For each target in initiative order: attack roll or save (with their windows), then damage through the pipeline, then riders, conditions and modifiers, then zones. Forced movement happens after damage. Nothing is applied to a target that the cast cancelled or that left the area when the spell resolved.

**Scaling and duration.** A formula is written as a base plus a step for each slot level above the spell's level (`8d6 + 1d6 per level`), or by character level for cantrips (levels 5, 11, 17), or as a count (darts, rays, targets). Durations use the kinds of Part C plus those the spells need: INSTANT, ROUNDS (6 seconds each), CLOCK, CONCENTRATION (with a maximum), UNTIL\_TURN\_START and UNTIL\_TURN\_END of a named creature (Shield ends at the start of the caster's next turn), UNTIL\_DISPELLED and UNTIL\_LONG\_REST. Every effect stores an absolute end time or end turn, never a counter (E-82).

**Not a primitive.** Summoning, polymorph, walls, illusions that create images, mirror duplicates and anything that needs vision or elevation stay at tier 0 in version 1 (10.9).

### 10.8 Reaction spells and Counterspell in the reaction window

There is one pause mechanism, the reaction window of Part B (ADR-31). A reaction spell is an option in it with a point, a cost and an effect. It is never a separate kind of action. The point names are the canonical ones of RS-REA-001.

| Spell | Point and step | Cost | Effect | Notes |
| --- | --- | --- | --- | --- |
| Shield | AFTER\_HIT, step 2, when the caster is hit; ON\_CAST, step 2, when the caster is targeted by Magic Missile | The reaction and a 1st-level slot or higher | +5 AC until the start of the caster's next turn, including against the triggering attack, so a hit can become a miss; no damage from Magic Missile for the same period | Needs V and S (not silenced); offered only when the caster can cast it (spell known or prepared, slot, reaction) and, for the first trigger, only on a hit |
| Counterspell | ON\_CAST, step 2, offered to each creature within 60 feet that can see the caster | The reaction and a slot of 3rd level or higher | Cancels the cast. A spell of a level up to the slot's level fails; a higher spell needs an ability check with the caster's spellcasting ability against 10 + the spell's level (window WC applies, so Peerless Skill and Bardic Inspiration can add) | The decider picks the slot level before it learns the spell's level (`counterspellInfo`, E-77). The slot is spent even if the counter fails. The cast being countered has already spent its slot and action |
| Feather Fall | A new point, ON\_FALL (E-84) | The reaction and a 1st-level slot | Up to five falling creatures within 60 feet descend 60 feet per round for 1 minute and take no falling damage when they land | Needs V and a feather; version 1.5 |
| Hellish Rebuke | AFTER\_DAMAGE\_TAKEN, step 2, when the caster is damaged by a creature within 60 feet that it can see | The reaction and a 1st-level slot or higher | The attacker makes a Dexterity save against the caster's spell DC; 2d10 fire damage, half on a success, +1d10 per slot level above 1st | Warlock list; reachable by Magical Secrets (version 1.5). It is a new action, so it opens its own windows (E-64) |
| A readied spell | The readied trigger (Part B) | The reaction; the slot was spent when it was readied | Casts the held spell | Fizzles if concentration breaks |

Rules that apply to all of them:

- A reaction spell is cast, so it commits its slot and reaction first and then opens its own ON\_CAST window. A Counterspell can be countered, and a Shield can be countered, within the nesting depth of 6 (E-64).
- The bonus action rule of RS-SPL-011 applies to a reaction spell cast on the caster's own turn.
- Eligibility depends only on what the decider knows (RS-REA-006). A window never reveals the spell's name or level unless `counterspellInfo` allows it. In version 1 the DM answers for the players and sees the truth.
- Inside step 2, options that cancel the action go before options that only soften it, so that a Counterspell is not preceded by a wasted Shield (E-83). Within those, initiative order applies.
- Content packs can add reaction spells that are not in the SRD (Absorb Elements, Silvery Barbs) with the same `reaction` field. They need no engine change.
- Counterspell needs the caster to be seen. The vision seam answers who can see whom (full vision by default, so every conscious creature within 60 feet qualifies unless the DM says otherwise or a condition such as invisibility applies).

### 10.9 The curated tier-2 list, tier 1 and what stays at tier 0

At level 5 a bard or wizard casts spells up to 3rd level and a paladin up to 2nd. Levels 6 to 12 reach 6th level for bards and wizards and 3rd level for paladins. The list below is the tier-2 set for levels 1 to 5, 40 spells chosen so that every primitive of 10.7 appears at least once and every spell a player at those levels is likely to reach for in a fight is covered. B is Bard, P is Paladin, W is Wizard. 'Text' says whether the SRD entry was read for this draft; all 40 entries were read for V-6, which is cleared (see 10.13).

| Spell | Lv | Class | Primitives | Text |
| --- | --- | --- | --- | --- |
| Fire Bolt | 0 | W | spell\_attack, damage, cantrip scaling | read |
| Ray of Frost | 0 | W | spell\_attack, damage, modifier (speed -10 ft until the caster's next turn) | read |
| Shocking Grasp | 0 | W | melee spell\_attack (advantage against metal armor), damage, modifier (no reactions) | read |
| Acid Splash | 0 | W | save, damage, a second target within 5 feet | read |
| Chill Touch | 0 | W | spell\_attack, damage, modifier (no healing; undead have disadvantage against the caster) | read |
| Vicious Mockery | 0 | B | save, damage, modifier (disadvantage on its next attack roll) | read |
| Magic Missile | 1 | W | damage by darts, scaling by darts | read |
| Shield | 1 | W | reaction\_option, modifier (AC until the start of the caster's turn) | read |
| Burning Hands | 1 | W | cone, save for half | read |
| Thunderwave | 1 | B, W | cube from self, save for half, forced\_movement | read |
| Sleep | 1 | B, W | hp\_pool, condition (unconscious) | read |
| Mage Armor | 1 | W | modifier (base AC 13 + Dexterity, 8 hours) | read |
| Cure Wounds | 1 | B, P | heal, touch | read |
| Healing Word | 1 | B | heal, bonus action, range | read |
| Bless | 1 | P | modifier (roll bonus die), concentration, targets by slot | read |
| Bane | 1 | B | save, modifier (roll penalty die), concentration | read |
| Faerie Fire | 1 | B | cube, save, modifier (advantage against, no invisibility), concentration | read |
| Hideous Laughter | 1 | B, W | save, conditions (prone, incapacitated), repeat save when damaged with advantage | read |
| Heroism | 1 | B, P | temp\_hp each turn, modifier (cannot be frightened), concentration | read |
| Divine Favor | 1 | P | next\_hit\_rider on every hit, bonus action, concentration | read |
| Shield of Faith | 1 | P | modifier (+2 AC), bonus action, concentration | read |
| Scorching Ray | 2 | W | several spell\_attacks, damage per ray, rays by slot | read |
| Misty Step | 2 | W | teleport, bonus action | read |
| Hold Person | 2 | B, W | save, condition (paralyzed), repeat save each turn, humanoid filter, targets by slot | read |
| Web | 2 | W | zone, save, condition (restrained), Strength check to escape | read |
| Aid | 2 | P | modifier (maximum hit points), 8 hours, scaling | read |
| Lesser Restoration | 2 | B, P | remove\_condition | read |
| Branding Smite | 2 | P | next\_hit\_rider (one hit), bonus action, concentration, ends invisibility | read |
| Invisibility | 2 | B, W | modifier (invisible) with end triggers (attacks, casts), concentration | read |
| Shatter | 2 | B, W | sphere, save for half | read |
| Blur | 2 | W | modifier (attackers have disadvantage), concentration | read |
| Fireball | 3 | W | sphere, save for half, scaling | read |
| Lightning Bolt | 3 | W | line, save for half | read |
| Counterspell | 3 | W | reaction\_option, counter, ability check | read |
| Dispel Magic | 3 | B, P, W | dispel, ability check | read |
| Haste | 3 | W | several modifiers, lethargy when it ends, concentration | read |
| Slow | 3 | W | save, several modifiers, repeat save, up to six targets | read |
| Hypnotic Pattern | 3 | B, W | cube, save, conditions, ends on damage or being shaken | read |
| Fear | 3 | B, W | cone, save, condition (frightened), repeat save | read |
| Revivify | 3 | P | revive, a costed consumed component | read |

**Tier 1.** Any other spell whose text has a damage or healing roll that the formula notation can express, for example Poison Spray, Acid Arrow, Vampiric Touch and Color Spray. The pack validation test checks that each such spell's formula matches its SRD text.

**Tier 0 in version 1.** Everything else, grouped by why:

| Group | Examples | Why it waits |
| --- | --- | --- |
| Summoned and conjured creatures | Find Steed (Find Familiar and Animate Dead are tier 2 since Part G, RS-MON-042) | Find Steed needs mounts (version 2); creature creation and control are specified in Part G (RS-MON-037 to RS-MON-041) |
| Shape change | Polymorph, Alter Self | Needs a stat block swap |
| Images and duplicates | Silent Image, Major Image, Mirror Image | Needs perception rules or an attack-redirect hook |
| Walls and shaped terrain | Wall of Fire, Wall of Ice, Wall of Stone | Needs shape editing and blocking edges on the map |
| Anything that needs vision, light or elevation | Darkness, Fog Cloud, Daylight, Fly, Levitate | Waits for the version 2 vision model and elevation |
| Free-form control | Command, Suggestion, Dominate Person | Needs a forced-behavior model |
| Information and travel | Detect Magic, Identify, Clairvoyance, Teleport | The DM adjudicates, usually outside combat |

**Candidates for the version 1.5 tier-2 additions (levels 6 to 12).** Mirror Image, Flaming Sphere, Heat Metal, Silence, Calm Emotions, Sleet Storm, Stinking Cloud, Vampiric Touch, Feather Fall, Cone of Cold, Ice Storm, Greater Invisibility, Confusion, Cloudkill, Chain Lightning, Disintegrate, Mass Cure Wounds and Hold Monster. They are a starting point for refinement, not a decision (E-85).

### 10.10 Edge cases and community conflicts

Each row has a default and, where tables really differ, a campaign setting. Settings are asked on first use, as in Architecture 4.15. Each row has a test (10.13); a test that would still pass with the feature removed does not count.

| ID | Case | What the sources say | Default | Setting |
| --- | --- | --- | --- | --- |
| C-E1 | A bonus action spell, then a reaction spell on the caster's own turn (Shield) | SRD: only a 1-action cantrip may follow. Crawford (2016): reaction spells count | Enforced on the caster's own turn, in both orders; reaction spells on other creatures' turns are free | `bonusActionSpellRule`: OWN\_TURN, ANY\_TURN, REACTION\_EXEMPT or OFF |
| C-E2 | A leveled action spell, then a reaction spell on the same turn | Sage Advice: allowed; only a bonus action spell restricts | Allowed | none |
| C-E3 | Counterspell without knowing the spell | SRD: no identification step. Crawford: not needed. Xanathar's: identifying costs a reaction | The engine decides which creatures could counter the cast (they know the spell, have a free reaction and a slot, are within 60 feet and can see the caster) and alerts only those players that a spell is being cast; the slot level is picked blind and the DM sees the truth | `counterspellInfo`: ALERT, REVEAL or SILENT |
| C-E4 | Counterspelling a Counterspell, a reaction spell or a cantrip | Community and Sage Advice: all allowed while a reaction is free | Allowed, as nested windows to depth 6 | none |
| C-E5 | Magic Missile as one damage source or several (concentration saves, death save failures) | Mearls: one source. Crawford (later): each dart | Each dart is a separate instance, applied in the caster's order; a dart that hits a creature already at 0 hit points causes a failure | `missileDamageSources`: PER\_DART or SINGLE |
| C-E6 | Magic Missile: one damage roll or one per dart | The SRD says a dart deals 1d4 + 1. PHB p.196 says damage dealt to several targets at the same time is rolled once; Crawford's Sage Advice (reported 9 September 2016): one roll is the rules as written, either way is fine at the table | One roll per dart (decided 2026-11-16); the setting can enforce one roll for all darts | missileRoll: PER\_DART or ONE\_ROLL |
| C-E7 | Area damage: one roll for all targets | PHB p.196: damage to more than one target at the same time is rolled once for all of them | One roll per cast, applied to every target (V-6 cleared) | none |
| C-E8 | A concentration spell that is countered | SRD silent; the cast still happened | The old concentration ends when the new cast is committed, even if it is countered | `counteredConcentration`: ENDS\_OLD or KEEP\_OLD |
| C-E9 | Concentration DC when temporary hit points absorb the damage | Decided in E-18: still damage taken | The DC uses the event amount | none |
| C-E10 | One hit with several damage types (weapon, Sneak Attack, Smite) | One attack is one source | One save, DC from the total | none |
| C-E11 | Concentration and the damage that knocks the caster out | Unconscious is incapacitated, which ends it | No save; it ends | none |
| C-E12 | Cantrip scaling by class or character level | PHB: character level | Total character level | none |
| C-E13 | Grid areas: origin and which squares a circular area covers | DMG: origin at an intersection; at least half of a square. Many tables count squares by distance instead | Intersection; the center test | `areaCoverageRule` |
| C-E14 | Grid areas: cones, cubes and lines | DMG p.251 (Crawford, 1 March 2016): a circular area that covers at least half a square affects it. Xanathar's template method, as one community guide reads it: any covered part counts. The 2024 rules: half for every shape | At least half, decided by the center test (HALF\_ALL) | `areaCoverageRule`: HALF\_ALL or DMG\_XGTE |
| C-E15 | Cover against an area spell | Part B: cover adds to Dexterity saves; the reference point of an area is its origin | Cover is computed from the origin to the target; until the map has walls the DM flags it on the cast | none |
| C-E16 | A ranged spell attack next to an enemy | SRD: disadvantage within 5 feet of a hostile creature that can see the caster and is not incapacitated | Applies to ranged spell attacks only | none |
| C-E17 | Healing undead and constructs | SRD: no effect | A creature type check; monsters carry a type | none |
| C-E18 | Sleep: ties, order and what counts | SRD: ascending current hit points, ties unspecified, unconscious creatures ignored, undead and charm-immune skipped | Ascending current hit points, then initiative order, then id; temporary hit points are not counted; a creature wakes on damage or when an ally uses an action to shake it | none |
| C-E19 | Haste lethargy | SRD: when the spell ends the target cannot move or act until after its next turn | A lethargy condition (incapacitated, speed 0) until the end of its next turn, for any way the spell ends | none |
| C-E20 | Hold Person and 'humanoid' | SRD: a humanoid | A type check; player characters are humanoid unless the species says otherwise | none |
| C-E21 | Branding Smite and Divine Smite on one hit | Independent | Both apply, both double on a critical hit | none |
| C-E22 | Invisibility ends 'if it attacks or casts a spell' | SRD wording; when exactly is not stated | It ends after the attack or spell resolves, so that attack still has advantage | none |
| C-E23 | Revivify: the minute and the material | SRD: died within the last minute; a 300 gp diamond is consumed | Time of death is recorded on the clock; in version 1 the DM confirms the diamond | none |
| C-E24 | Dispel Magic on a concentration spell | SRD: the spell ends | `ConcentrationEnded` with the reason DISPELLED | none |
| C-E25 | Spells that need vision, elevation or created creatures | Not in the engine before version 2 | Tier 0 (10.9) | none |

### 10.11 Engine decisions and settings (E-69 to E-91 decided)

E-69 to E-88 were accepted on 2026-11-16, with the missileRoll setting added (per dart by default, one roll for all darts as the option) and three refinements of defaults recorded as E-89 to E-91. They are propagated by reference and the other documents do not copy them: Architecture 4.9 (tiers, E-69), 4.13 and 4.15 (settings registry and pre-flight), the reaction window point list (E-68, with ON\_FALL from E-84) and the test table in 12.1 (T-40 to T-47, defined in 10.13); Requirements FR-SPL-009 to FR-SPL-014; and the spells step of the AI Build Brief.

| ID | Question | Default |
| --- | --- | --- |
| E-69 | Do the tiers share bookkeeping? | Yes. Every tier records the cast, spends the slot, applies the action economy and the bonus action rule, tracks concentration and checks V and costed components; tiers differ only in effect resolution (10.2). This amends the wording of Architecture 4.9 |
| E-70 | When are the slot and action spent? | When casting begins, before the ON\_CAST window. A countered spell has still spent them. A long casting that is interrupted by broken concentration spends no slot (when supported) |
| E-71 | How is the bonus action spell rule enforced? | By default (OWN\_TURN) only on the turn the bonus action spell is cast, in both orders, including reaction spells on that turn, from per-turn flags; ANY\_TURN extends the limit to reaction spells on other creatures' turns until the start of the caster's next turn (E-89); setting `bonusActionSpellRule` |
| E-72 | When does a new concentration spell end the old one? | When the new cast is committed, even if it is then countered; setting `counteredConcentration`. All effects and zones of the old spell end at once with a reason |
| E-73 | What is a damage instance, and how does Magic Missile count? | One attack that hits, or one spell effect on one target, with all its damage types summed. Magic Missile darts are separate instances applied in order, each with its own roll unless missileRoll is ONE\_ROLL (one roll shared by all darts); settings missileRoll and `missileDamageSources` |
| E-74 | How are squares chosen for areas? | Origin on a grid intersection. By default (HALF\_ALL) a square is in the area if its center is inside the shape, for every shape: the proxy for 'covers at least half' (DMG p.251 for circles). Integer geometry in units of 2.5 feet, with no floating point. Setting `areaCoverageRule` can switch cones, cubes and lines to any positive overlap (DMG\_XGTE, the Xanathar's template method); see E-90 |
| E-75 | Where does an area that starts at the caster begin? | On the boundary of the caster's space; a cube touches the space along at least a full edge; the caster is never inside its own cone, cube or line |
| E-76 | Does version 1 model height? | No. Cylinders are circles and elevation is ignored, behind a seam for version 2 |
| E-77 | What does a Counterspell decider know? | By default (ALERT) the engine works out which creatures could counter the cast and tells only those players that a spell is being cast, with no name or level; the decider picks the slot level blind and the DM sees the truth. Setting `counterspellInfo`: ALERT (default), REVEAL (name and level shown) or SILENT (no alert; the DM prompts a player or the player declares); see E-91 |
| E-78 | How is preparation recorded? | `PrepareSpellsAction`, allowed once after each completed long rest and outside encounters; it advances the clock by the total of the spell levels in minutes unless the DM waives it; always-prepared spells do not count |
| E-79 | Which components does version 1 enforce? | V (through conditions and zones) and costed or consumed components (through inventory items and the focus and component pouch flags of 11.7). S and the free hand follow RS-EQP-016. Setting `componentEnforcement`: CONDITIONS\_AND\_COSTS, FULL or OFF |
| E-80 | How do rituals and long casting times work? | Outside encounters only, as campaign actions the DM confirms; they advance the clock; a ritual spends no slot and cannot be upcast. Inside an encounter a casting time over 1 action is rejected |
| E-81 | Do cantrips scale by class level or character level? | Total character level |
| E-82 | How are durations stored? | As an absolute end time or end turn, with the kinds INSTANT, ROUNDS, CLOCK, CONCENTRATION, UNTIL\_TURN\_START, UNTIL\_TURN\_END, UNTIL\_DISPELLED and UNTIL\_LONG\_REST |
| E-83 | In what order are options inside step 2 of a window? | Options that cancel the action first, then options that soften it, then initiative order. This refines E-62 |
| E-84 | Does Feather Fall need a new point? | Yes: ON\_FALL joins the canonical list of E-68 when falling is run by the engine (version 1.5) |
| E-85 | How does a spell join tier 2? | By meeting the three tests of 10.2 and being added to the list in 10.9 with its pack data; the list for version 1 is the 40 spells listed there |
| E-86 | What stays at tier 0 in version 1? | The groups in 10.9: summons, shape change, images and duplicates, walls, anything needing vision or elevation, free-form control, information and travel |
| E-87 | Whose numbers does a cast use? | The casting class's save DC and attack bonus. A spell from another list gained through Magical Secrets counts as a bard spell; oath spells count as paladin spells |
| E-88 | How is 'a creature you can see' decided? | By the vision seam, with full vision by default and the DM's override. Conditions such as blinded and invisible apply |
| E-89 | How far does the bonus action spell limit reach? | By default (OWN\_TURN) only on the turn the bonus action spell is cast, as the Sage Advice Compendium says. Setting bonusActionSpellRule at ANY\_TURN extends it to reaction spells on other creatures' turns until the start of the caster's next turn. This refines E-71 (decided 2026-11-16) |
| E-90 | How is 'covers at least half a square' decided for every shape? | By the center test: a square is in the area if its center is inside the shape, an integer test with no area calculation. This is the default (HALF\_ALL), checked against exact areas for spheres and sampled areas for cones, lines and cubes with no difference. DMG\_XGTE is the option for any positive overlap for cones, cubes and lines. This refines E-74 (decided 2026-11-16) |
| E-91 | What does a Counterspell tell the players by default? | ALERT: the engine decides which creatures can counter the cast (they know the spell, have a free reaction and a slot, are within 60 feet and can see the caster) and alerts only those players that a spell is being cast, with no name or level. REVEAL shows the name and level (combat-focused campaigns). SILENT sends no alert and leaves it to the DM or the player to declare (story-focused campaigns). The old LEVEL value is dropped. This replaces the defaults of E-77 (decided 2026-11-16) |

**New settings.**

| Setting | Values (default first) | Tier | Asked when |
| --- | --- | --- | --- |
| `bonusActionSpellRule` | OWN\_TURN, ANY\_TURN, REACTION\_EXEMPT, OFF | Ask on first use | The first bonus action spell or reaction spell on a turn |
| `counterspellInfo` | ALERT, REVEAL, SILENT | Ask on first use | The first cast that a possible counterspeller could answer; asked in the pre-flight of the cast, before any die is rolled |
| `missileDamageSources` | PER\_DART, SINGLE | Ask on first use | The first Magic Missile |
| `missileRoll` | PER\_DART, ONE\_ROLL | Ask on first use | The first Magic Missile, in the same pre-flight as missileDamageSources and before any die is rolled |
| `areaCoverageRule` | HALF\_ALL, DMG\_XGTE | Ask on first use | The first area spell |
| `counteredConcentration` | ENDS\_OLD, KEEP\_OLD | Silent | Never; the default applies |
| `componentEnforcement` | CONDITIONS\_AND\_COSTS, FULL, OFF | Silent | Never; the default applies |

### 10.12 Worked examples

Each example becomes a named test with forced dice (T-40 to T-45). All characters are 5th-level wizards or bards with +3 proficiency.

**WE-1: Fireball on the grid.** A wizard with Intelligence 16 (modifier +3) has a spell save DC of 8 + 3 + 3 = 14. It casts Fireball with a 3rd-level slot (3rd-level slots go from 2 to 1) at a grid intersection. The area is a sphere of radius 20 feet, which covers 52 squares; goblin C stands 21 feet from the origin and is outside. No creature has an eligible option, so no window opens. Damage is rolled once, 8d6 forced to 6, 5, 4, 4, 3, 2, 2, 1, total 27. Goblin A (Dexterity +2) rolls 9, total 11, fails and takes 27. Goblin B (Dexterity +2) rolls 15, total 17, succeeds and takes half, 13 (27 / 2 rounded down). Events in order: `SpellCast`, damage rolled, save A, save B, damage applied to A and B.

**WE-2: A Counterspell chain.** Aelin and Bram are 5th-level wizards, each with two 3rd-level slots and Counterspell prepared. On her turn Aelin casts Fireball with a 3rd-level slot (her 3rd-level slots: 1 left). The ON\_CAST window opens (depth 1). Bram can see her and has a reaction, so he is offered Counterspell. He casts it with a 3rd-level slot (his slots: 1 left, reaction used), and the engine commits that cast first. Its own ON\_CAST window opens (depth 2): Aelin is offered Counterspell, because a leveled action spell does not stop a reaction spell on the same turn (C-E2). She uses her last 3rd-level slot. Bram's Counterspell has level 3 and her slot is level 3, so it fails with no roll. A third window opens for her Counterspell (depth 3) and nobody is eligible, so there is no prompt. Fireball then resolves as in WE-1. Aelin's reaction and both of her slots are spent, and so is Bram's reaction and one slot. Replaying the action after each answer yields the same log.

**WE-3: The bonus action rule.** A bard casts Healing Word (a bonus action, a 1st-level slot) on an ally at 0 hit points. She then tries Cure Wounds as her action and the command is rejected with `SPELL_BONUS_ACTION_RULE`, changing nothing. Vicious Mockery, a 1-action cantrip, is allowed. On a wizard's turn the order is reversed: Fireball as the action, then Misty Step as a bonus action, is rejected too. The same wizard, after Misty Step, is hit by an arrow: under `bonusActionSpellRule` OWN\_TURN no Shield option is offered on that turn, and under REACTION\_EXEMPT it is.

**WE-4: Two sources against one concentration.** A wizard (Constitution +2, no save proficiency) concentrates on Haste, cast on an ally. In one resolved action an arrow deals 7 damage and a dragon's breath deals 26. The first save has DC 10 (half of 7 is 3, below 10). It rolls 7, total 9, and fails: Haste ends with the reason SAVE\_FAILED, the ally becomes lethargic until the end of its next turn (C-E19), and the second save, which would have had DC 13, is skipped and logged. Had the first save succeeded, the second would have used DC 13 (26 / 2).

**WE-5: Sleep.** The caster rolls 5d8, forced to 9, 6, 5, 4, 3, a pool of 27 hit points. Within 20 feet of the point are goblin 1 (7 hit points), goblin 2 (7), goblin 3 (9), a skeleton (13, undead) and an ogre (59). The skeleton is skipped. In ascending order of current hit points, with the tie between goblins 1 and 2 broken by initiative order: goblin 1 sleeps (pool 20), goblin 2 sleeps (13), goblin 3 sleeps (4), and the ogre, whose 59 exceeds the 4 left, is not affected. Goblin 3 wakes when it takes damage or when an ally uses an action to shake it.

### 10.13 Tests and verification debt

The tests T-40 to T-47 are listed in Architecture 12.1 and defined here. Each must fail if the feature it covers is removed, and there are no golden files for rules: expected values are written by hand in the test data.

| ID | Test | Runs |
| --- | --- | --- |
| T-40 | Pack validation and tier-2 scenarios: every spell definition validates against the schema; every tier-2 spell has a named scenario with forced dice and exact events (WE-1 and WE-5 are two of them); every scaling formula is checked against a hand-written table of the SRD text; the per-class counts of 10.1 are recounted | Every push (engine) |
| T-41 | Casting preconditions as a data-driven truth table: slots, armor proficiency, V and costed components, range, clear path, target filters, and the bonus action rule in both orders (WE-3) under each value of `bonusActionSpellRule` | Every push (engine) |
| T-42 | Concentration: one slot per creature, a new concentration spell ends the old one (also when countered, both values of `counteredConcentration`), DC from the event amount, separate saves per instance, Magic Missile under both values of `missileDamageSources`, incapacitation, dispel, the cascade of effects and zones, and WE-4 | Every push (engine) |
| T-43 | Area geometry: the sphere counts of 10.6 (4, 12, 32, 52, 112); cone, cube, line and cube-from-self cases at the eight axis and diagonal directions and a direction such as (3, 1), under both values of `areaCoverageRule`; properties: results are unchanged by rotating by 90 degrees and by reflection, and grow monotonically with the radius; a comparison of the center test with the exact half-area rule for each reference radius, whose disagreements are listed in the test; the forbidden-API rule for floating point and `Math.sqrt`, `sin`, `cos`, `atan2`, `hypot` in area code | Every push (engine) |
| T-44 | Reaction spells: WE-2 and variants (a chain to the depth cap, a countered Shield, Shield against Magic Missile, cancel before soften inside step 2, no leak of the spell's name or level under ALERT and SILENT, the three values of `counterspellInfo`, the slot spent when a Counterspell fails) | Every push (engine); pull request with the real database for persistence and resume |
| T-45 | Preparation and rituals: `PrepareSpellsAction` once per long rest, the clock cost, always-prepared spells not counted, a ritual spending no slot and refusing a higher slot, and refusal inside an encounter | Every push (engine); pull request with the real database |
| T-46 | Scaling by slot and character level, for every tier-2 spell, against the hand-written table (a mutation of any dice step must fail) | Every push (engine); nightly mutation testing |
| T-47 | Settings of Part E: each setting changes exactly the outcomes it should and no others; the pre-flight lists `counterspellInfo`, `missileDamageSources` and `areaCoverageRule` for the casts that can need them, and a spell that needs an undecided setting inside a chain uses the default (RS-REA-014) | Every push (engine) |

**Verification debt V-6.** Cleared when each line is checked against the source named.

| Item | What is unverified | Check |
| --- | --- | --- |
| Class list counts | Counted by hand from the 5thsrd.org tables | The pack validation test recounts from the seed |
| Books and rulings (cleared except Xanathar's pp.85 to 86 and the Mearls answer) | Read on 2026-11-16: Crawford's tweets of 5 December 2016 and 29 June 2017 (a bonus action spell, then Shield: 'Yes', PHB p.202) on a Sage Advice mirror; the PHB p.196 rule that damage to several targets at the same time is rolled once, and Crawford's Sage Advice of 9 September 2016 on Magic Missile (rules as written: one roll; either is fine at the table), both quoted in a forum thread; the DMG p.251 rule for circles, in Crawford's tweet of 1 March 2016 on a Sage Advice mirror ('If an area of effect is circular and covers at least half a square, it affects the square'); the official Sage Advice Compendium PDF, which says the bonus action limit applies only on the turn the bonus action spell is cast ('that other spell must be a cantrip') and that a reaction spell can be cast on your own turn. For cones, cubes and lines two community guides disagree (half in one, any part in the other) and none quotes Xanathar's pp.85 to 86. Not found in the sources I could reach: the Mearls Magic Missile answer and the Xanathar's pages | The defaults of C-E1 to C-E8, C-E13 and C-E14 hold; the two real disputes already have settings (bonusActionSpellRule, areaCoverageRule). Open: Xanathar's pp.85 to 86 and the Mearls answer; neither blocks a default because each dispute has a setting |
| Spell entries (cleared) | All 19 entries marked 'to read' and the rest of Fireball were read on 2026-11-16 (5thsrd.org, aidedd.org, dnd-wiki.org, the SRD Sphinx edition) and every row of 10.9 matches its text. Notes for the pack data: Slow affects up to six creatures in a 40-foot cube; Shatter's 10-foot sphere gives constructs disadvantage on the save and also damages unattended objects; Blur does not work against creatures that do not rely on sight; one source adds 'deals damage' to the end of Invisibility (the 2024 wording), and the SRD wording of C-E22 stands; the Lightning Bolt sentence about flammable objects was not seen in the copy read | Done. Where a row of 10.9 or 10.7 ever disagrees with the entry, the entry wins |
| Roll once or per dart (cleared by a setting) | PHB p.196 says damage to several targets at the same time is rolled once, and Sage Advice (reported, 9 September 2016) calls one roll the rules as written for Magic Missile and per dart acceptable. Area damage is rolled once (C-E7). Magic Missile follows the setting missileRoll: per dart by default, one roll for all darts as the option (decided 2026-11-16) | Done in C-E6, C-E7 and the settings table; T-42 and T-47 must cover both values of missileRoll |
| Cantrip scaling by character level (cleared) | The SRD entries say 'when you reach 5th level'. The official Sage Advice Compendium says 'Cantrips scale with character level', with the example of a barbarian 2 and cleric 3 casting sacred flame as a 5th-level character. The PHB p.201 page number itself was not seen | Cleared against the Sage Advice Compendium; C-E12 and E-81 (total character level) hold |
| Class ritual features and oath spells (cleared) | The bard text ('You can cast any bard spell you know as a ritual if that spell has the ritual tag') was read on dnd-wiki SRD5:Bard. The Oath of Devotion list (3rd: Protection from Evil and Good, Sanctuary; 5th: Lesser Restoration, Zone of Truth; 9th: Beacon of Hope, Dispel Magic; 13th: Freedom of Movement, Guardian of Faith; 17th: Commune, Flame Strike) was read on the SRD 5.1 paladin page of 5e.d20srd.org, together with the Oath of Devotion features there (Aura of Devotion, Purity of Spirit, Holy Nimbus) | Cleared; Part D's treatment of Oath of Devotion as the SRD oath is confirmed |
| Center test against true half coverage (cleared) | An exact integer comparison on 2026-11-16 gave identical squares for the five reference radii (5, 10, 15, 20 and 30 feet: 4, 12, 32, 52 and 112 squares, origin on an intersection, flat map). The earlier wording that the center test was slightly more generous was wrong for these radii. For cones (15, 30 and 60 feet, along an axis and a diagonal), lines (100 and 60 feet) and a 15-foot cube from self, a sampled comparison (40 by 40 points per square) also found no difference; that check is sampled, not exact | T-43 repeats the comparison for these radii; other radii are not claimed; T-43 adds the cone, line and cube cases |
| Dispel Magic on non-spell magic | The SRD text was read: a spell of 3rd level or lower on the target ends; a spell of 4th level or higher needs a check with the spellcasting ability against DC 10 plus its level; a higher slot ends spells up to its level. It does not say whether curses and item effects have a spell level | DM adjudication. Part F (E-92) gives items no spell level, so this stays with the DM |
| Hands and focus | The free hand and component pouch rules were waiting for Part F | Cleared by Part F: RS-EQP-016 and E-98 (free hand), the flags in 11.7 (focus and pouch), T-52 |

## 11. Part F: Equipment and armor class (draft)

Part F covers what a character wears, carries and wields, and how that changes the numbers: armor class, attack options, hands, magic items, currency and weight. The decisions in 11.9 (E-92 to E-113) were accepted on 2026-11-19. Part F is propagated by reference to the Architecture, the Requirements Specification, the AI Build Brief and the Decision Log; this section is the only place that defines these rules, settings and tests. The scope was agreed in three answers: (1) a curated list of about 20 magic items resolves fully (tier 2) and every other magic item is tier 0; (2) all SRD gear, tools, mounts, vehicles and services are seed data at tier 0, with mechanics for only a few items and a data shape that lets more be added later; (3) encumbrance is off by default, with the standard and variant rules as DM options.

### 11.1 What was checked

| Question | What was checked | Finding |
| --- | --- | --- |
| Armor, shields, proficiency, Strength minimums, donning times | SRD 5.1 equipment pages (an SRD reference site and a Sphinx build of the SRD) | The 12 armors and the shield, the three categories, the Dexterity caps, the Strength minimums, Stealth disadvantage, the penalties for wearing armor without proficiency, and the don and doff times. The donning and Strength wording was confirmed against a second SRD rendition (V-7). |
| Weapons and properties | SRD weapon table (aidedd copy) | 37 weapons: 10 simple melee, 4 simple ranged, 18 martial melee, 5 martial ranged. The property definitions (ammunition, loading, thrown, versatile, heavy, light, finesse, reach, lance, net), improvised weapons and silvering. |
| Magic items | SRD magic item list (a gmbinder copy, read in three parts) | Every entry on the curated list in 11.6 was read and matches, together with the SRD attunement text (V-7). |
| Object interaction, drawing weapons, throwing two weapons | Sage Advice Compendium 2.7 (2021), the October 2020 Sage Advice thread, a community guide on weapon and item juggling | One free interaction per turn. Two-weapon fighting lets a character throw two weapons but does not make drawing both free. Tables differ on drawing a weapon with each attack (C-F1). |
| Carrying capacity | SRD Strength page | Capacity is 15 times Strength in pounds. The variant thresholds are 5 times and 10 times Strength. |
| Not checked | Gaps in this research | The Player's Handbook and Dungeon Master's Guide pages themselves, which were reached only through the Sage Advice Compendium and one secondary quotation of the Guide. No open items remain (V-7). |

### 11.2 Item data and the three tiers

Items follow the same tier model as spells (Part E). The tier says how much of an item the engine resolves, and every item not on a curated list stays at tier 0.

| Tier | What the engine does | Examples |
| --- | --- | --- |
| 0 | Stores the item, shows its text, tracks quantity and weight. The DM adjudicates any effect. | Rope, rations, most wondrous items, every magic item not in 11.6, all mounts and services |
| 1 | Resolves a dice effect when the item is used. | Potion of Healing, Acid (vial), Alchemist's fire, Holy water |
| 2 | Full resolution through effect primitives: bonuses to AC, attacks, damage and saves, ability score changes, charges, attunement and recharging. | Armor and weapons (mundane and +1 to +3), Ring of Protection, Wand of Magic Missiles |

An item is a definition (content) plus instances (records on a character, a container or the ground). The engine reads structured fields only. It never decides behavior from an item's name.

| Field | Meaning |
| --- | --- |
| Definition: id, name, category, source | Content id, display name, category (weapon, armor, shield, ammunition, gear, tool, potion, scroll, ring, rod, staff, wand, wondrous, mount, vehicle, service, trade good), and the book it comes from |
| Definition: cost, weight | Cost in copper pieces and weight in hundredths of a pound, both whole numbers |
| Definition: tier, rarity | The tier from the table above. Rarity is common, uncommon, rare, very rare or legendary, and applies to magic items only |
| Definition: requiresAttunement | Yes or no, with an optional prerequisite (a class, an alignment, a spellcaster) |
| Definition: weaponData | Category (simple or martial, melee or ranged), damage dice, damage type, properties, versatile dice, normal and long range |
| Definition: armorData | Category, base AC, Dexterity cap, Strength minimum, Stealth disadvantage, don and doff minutes |
| Definition: effects | Tier 1 and tier 2 mechanics as structured effect entries (11.6) |
| Instance: owner, location | A character, a container, or a ground position |
| Instance: quantity | Whole number, used for ammunition and gear stacks |
| Instance: equipped | A slot, or a hand, or none (11.4) |
| Instance: attuned, charges, identified | Attunement flag, current charges, and whether the players know what the item is |
| Instance: silvered, customName, notes | Silvering flag, a name the table gave it, and a DM note |

### 11.3 Armor and armor class

Armor class is a computed stat, not a stored number. The engine picks the highest eligible base formula, adds every modifier, applies floors, and keeps the breakdown so the sheet can show why the number is what it is. This replaces the Part B statement that a character's AC is fixed at creation.

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-EQP-001 | Base formulas. Unarmored: 10 plus Dexterity modifier. Light armor: the armor's base plus Dexterity modifier. Medium armor: the armor's base plus Dexterity modifier, at most +2. Heavy armor: the armor's base only (Dexterity is not added, and a negative Dexterity does not reduce it). | `AcBase` candidates with a formula, an eligibility test and a label |
| RS-EQP-002 | Alternative bases do not combine. Unarmored Defense (barbarian: 10 plus Dexterity plus Constitution, and a shield is allowed; monk: 10 plus Dexterity plus Wisdom, and no armor and no shield), Draconic Resilience (13 plus Dexterity while unarmored) and Mage Armor (13 plus Dexterity while the target wears no armor) each offer a base. The engine takes the highest eligible base. | One highest-wins selection. The chosen base is stored on the breakdown |
| RS-EQP-003 | Modifiers add to the chosen base: a shield (+2), the +N of magic armor and magic shields, item bonuses (Ring of Protection, Cloak of Protection, Bracers of Defense), the Defense fighting style (+1 while wearing armor), and temporary effects from spells (Shield, Shield of Faith, Haste). | `AcModifier` entries from equipment and from effects (Part E), each with a source label |
| RS-EQP-004 | Floors apply after the sum. A floor effect such as Barkskin sets AC to at least a stated number. | `AcFloor` entries, applied last |
| RS-EQP-005 | Only one shield counts. A shield is carried in one hand and needs that hand (11.4). | The hands model refuses a second shield |
| RS-EQP-006 | Armor Strength minimum. If the wearer's Strength is below the armor's minimum, speed drops by 10 feet. | A speed modifier computed from equipped armor |
| RS-EQP-007 | Stealth disadvantage. Wearing armor marked with Stealth disadvantage gives disadvantage on Dexterity (Stealth) checks. | A check-disadvantage source read by the check roller (Part B) |
| RS-EQP-008 | Armor without proficiency. Wearing armor or a shield the character is not proficient with gives disadvantage on every Strength or Dexterity ability check, saving throw and attack roll, and the wearer cannot cast spells. | Computed from equipped items and the proficiency list (Part D). Replaces the sheet flag behind CAST\_ARMOR\_NOT\_PROFICIENT |
| RS-EQP-009 | Recalculation. AC is recomputed whenever armor or a shield changes, a magic item is equipped, unequipped, attuned or loses attunement, an ability score changes, or an effect that touches AC starts or ends. Cover is not part of AC (Part B applies it per attack). | A pure function `computeArmorClass(state) -> AcBreakdown`, called by the triggers above |
| RS-EQP-010 | Donning and doffing. Light armor: 1 minute to don, 1 minute to doff. Medium: 5 and 1. Heavy: 10 and 5. A shield: 1 action each way. Armor gives its AC only after the full donning time. With help, the time is halved. | Armor changes are handled by E-97 |

The SRD armor table, with every value the engine uses:

| Category | Armor | Cost | Base AC | Strength | Stealth | Weight |
| --- | --- | --- | --- | --- | --- | --- |
| Light | Padded | 5 gp | 11 + Dex | none | Disadvantage | 8 lb |
| Light | Leather | 10 gp | 11 + Dex | none | none | 10 lb |
| Light | Studded leather | 45 gp | 12 + Dex | none | none | 13 lb |
| Medium | Hide | 10 gp | 12 + Dex (max 2) | none | none | 12 lb |
| Medium | Chain shirt | 50 gp | 13 + Dex (max 2) | none | none | 20 lb |
| Medium | Scale mail | 50 gp | 14 + Dex (max 2) | none | Disadvantage | 45 lb |
| Medium | Breastplate | 400 gp | 14 + Dex (max 2) | none | none | 20 lb |
| Medium | Half plate | 750 gp | 15 + Dex (max 2) | none | Disadvantage | 40 lb |
| Heavy | Ring mail | 30 gp | 14 | none | Disadvantage | 40 lb |
| Heavy | Chain mail | 75 gp | 16 | 13 | Disadvantage | 55 lb |
| Heavy | Splint | 200 gp | 17 | 15 | Disadvantage | 60 lb |
| Heavy | Plate | 1,500 gp | 18 | 15 | Disadvantage | 65 lb |
| Shield | Shield | 10 gp | +2 | none | none | 6 lb |

### 11.4 Hands, equipping and object interaction

A creature has two hands. Items are worn (in a slot) or held (in a hand) or carried (in a pack or container). Only worn and held items affect the numbers.

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-EQP-011 | Each hand holds at most one item: a weapon, a shield, a spellcasting focus, a wand, a torch. A two-handed weapon uses both hands. A shield uses one. | `HandState` with two positions. The engine refuses an equip that would need a hand that is taken, and names what is in the way |
| RS-EQP-012 | Versatile weapons. A versatile weapon is used one-handed or two-handed. The grip is chosen for each attack and is free to change. The two-handed damage die applies when the grip is two-handed, which needs the other hand to be empty. | The attack option builder lists both grips when both are possible |
| RS-EQP-013 | Worn slots (an engine convention, not an SRD rule): body armor 1, shield (a hand), head 1, neck 1, cloak 1, arms (bracers) 1, hands (gloves, gauntlets) 1, feet 1, waist 1, rings 2. Items that are not worn (wands, potions, bags, tools) take no slot. The DM can override a slot limit with a note. | `EquipSlot` enum and limits table. A worn magic item with no slot is carried only |
| RS-EQP-014 | Object interaction. A character can interact with one object for free on a turn, during the move or the action: draw or sheathe a weapon, open a door, pick up an item, drop an item, equip or unequip a held item. A second interaction needs the Use an Object action (RS-ACT-010). | A per-turn counter of free interactions, reset at the start of the creature's turn (E-99) |
| RS-EQP-015 | A weapon that is thrown leaves the hand and becomes a ground item at the target's position or at the end of its range. Picking it up is an object interaction. A weapon that is dropped lands at the creature's position. | Thrown and dropped items become ground items on the encounter map (the map itself is described in 13.4 and 13.9) |
| RS-EQP-016 | Somatic components need a free hand or a hand holding a material component or focus the spell uses. A character holding a weapon in one hand and a shield in the other cannot meet an S component until a hand is freed. | Part E's component check reads `HandState`. This settles the wait in RS-SPL-015 and E-79 |
| RS-EQP-017 | Armor changes. Armor cannot be donned or doffed inside an encounter by default. Outside an encounter the game clock advances by the donning or doffing time. A shield stays one action, inside or outside an encounter. | E-97 and the armorChangeInEncounter setting |

Light from items. From version 1.5 an item or a spell effect that gives light carries an emits record (RS-ENV-003), so a torch, a lantern or a light spell lights its area without a DM note. Before version 1.5 the light of an item stays a DM note. The rows that point to Part H for the map point to 13.4 and 13.9.

### 11.5 Weapons and properties

Every SRD weapon, with the values the engine uses. Range is normal/long in feet.

| Category | Weapon | Cost | Damage | Weight | Properties |
| --- | --- | --- | --- | --- | --- |
| Simple melee | Club | 1 sp | 1d4 bludgeoning | 2 lb | Light |
| Simple melee | Dagger | 2 gp | 1d4 piercing | 1 lb | Finesse, light, thrown (20/60) |
| Simple melee | Greatclub | 2 sp | 1d8 bludgeoning | 10 lb | Two-handed |
| Simple melee | Handaxe | 5 gp | 1d6 slashing | 2 lb | Light, thrown (20/60) |
| Simple melee | Javelin | 5 sp | 1d6 piercing | 2 lb | Thrown (30/120) |
| Simple melee | Light hammer | 2 gp | 1d4 bludgeoning | 2 lb | Light, thrown (20/60) |
| Simple melee | Mace | 5 gp | 1d6 bludgeoning | 4 lb | none |
| Simple melee | Quarterstaff | 2 sp | 1d6 bludgeoning | 4 lb | Versatile (1d8) |
| Simple melee | Sickle | 1 gp | 1d4 slashing | 2 lb | Light |
| Simple melee | Spear | 1 gp | 1d6 piercing | 3 lb | Thrown (20/60), versatile (1d8) |
| Simple ranged | Crossbow, light | 25 gp | 1d8 piercing | 5 lb | Ammunition (80/320), loading, two-handed |
| Simple ranged | Dart | 5 cp | 1d4 piercing | 1/4 lb | Finesse, thrown (20/60) |
| Simple ranged | Shortbow | 25 gp | 1d6 piercing | 2 lb | Ammunition (80/320), two-handed |
| Simple ranged | Sling | 1 sp | 1d4 bludgeoning | none | Ammunition (30/120) |
| Martial melee | Battleaxe | 10 gp | 1d8 slashing | 4 lb | Versatile (1d10) |
| Martial melee | Flail | 10 gp | 1d8 bludgeoning | 2 lb | none |
| Martial melee | Glaive | 20 gp | 1d10 slashing | 6 lb | Heavy, reach, two-handed |
| Martial melee | Greataxe | 30 gp | 1d12 slashing | 7 lb | Heavy, two-handed |
| Martial melee | Greatsword | 50 gp | 2d6 slashing | 6 lb | Heavy, two-handed |
| Martial melee | Halberd | 20 gp | 1d10 slashing | 6 lb | Heavy, reach, two-handed |
| Martial melee | Lance | 10 gp | 1d12 piercing | 6 lb | Reach, special |
| Martial melee | Longsword | 15 gp | 1d8 slashing | 3 lb | Versatile (1d10) |
| Martial melee | Maul | 10 gp | 2d6 bludgeoning | 10 lb | Heavy, two-handed |
| Martial melee | Morningstar | 15 gp | 1d8 piercing | 4 lb | none |
| Martial melee | Pike | 5 gp | 1d10 piercing | 18 lb | Heavy, reach, two-handed |
| Martial melee | Rapier | 25 gp | 1d8 piercing | 2 lb | Finesse |
| Martial melee | Scimitar | 25 gp | 1d6 slashing | 3 lb | Finesse, light |
| Martial melee | Shortsword | 10 gp | 1d6 piercing | 2 lb | Finesse, light |
| Martial melee | Trident | 5 gp | 1d6 piercing | 4 lb | Thrown (20/60), versatile (1d8) |
| Martial melee | War pick | 5 gp | 1d8 piercing | 2 lb | none |
| Martial melee | Warhammer | 15 gp | 1d8 bludgeoning | 2 lb | Versatile (1d10) |
| Martial melee | Whip | 2 gp | 1d4 slashing | 3 lb | Finesse, reach |
| Martial ranged | Blowgun | 10 gp | 1 piercing | 1 lb | Ammunition (25/100), loading |
| Martial ranged | Crossbow, hand | 75 gp | 1d6 piercing | 3 lb | Ammunition (30/120), light, loading |
| Martial ranged | Crossbow, heavy | 50 gp | 1d10 piercing | 18 lb | Ammunition (100/400), heavy, loading, two-handed |
| Martial ranged | Longbow | 50 gp | 1d8 piercing | 2 lb | Ammunition (150/600), heavy, two-handed |
| Martial ranged | Net | 1 gp | none | 3 lb | Special, thrown (5/15) |

Property effects and attack options:

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-EQP-018 | Proficiency. A weapon is proficient if its category (simple or martial) or its own name is in the character's weapon proficiencies (Part D). Without proficiency the proficiency bonus is not added to the attack roll. | Proficiency lookup on the character sheet |
| RS-EQP-019 | Ability modifier. Strength for melee, Dexterity for ranged, as RS-ATK-003. Finesse lets the attacker use Strength or Dexterity, the same one for the attack and the damage. A thrown melee weapon uses the modifier it would use in melee. | The option builder picks the better modifier for finesse and shows which |
| RS-EQP-020 | Light. A light weapon in each hand allows the two-weapon attack of RS-ATK-009. | Off-hand option generated when both hands hold light weapons at the first attack (Compendium) |
| RS-EQP-021 | Heavy. Small and Tiny creatures have disadvantage on attack rolls with heavy weapons. | Size check on the option |
| RS-EQP-022 | Reach. A reach weapon adds 5 feet to the wielder's reach for attacks and opportunity attacks. | The threatened area (RS-ENV-030) reads the weapon's reach |
| RS-EQP-023 | Thrown. The weapon can be thrown for a ranged attack at the listed range. A melee weapon thrown this way is not improvised. | A thrown option per carried throwable weapon |
| RS-EQP-024 | Ammunition. Each ranged attack uses one piece of ammunition. Using the weapon in melee makes it an improvised weapon. | Stack quantity falls by one per attack (E-102) |
| RS-EQP-025 | Loading. Only one piece of ammunition can be fired per action, bonus action or reaction, whatever the number of attacks. | The option builder limits the Attack action to one shot for a loading weapon |
| RS-EQP-026 | Two-handed. The weapon needs both hands. | Hands check (RS-EQP-011) |
| RS-EQP-027 | Lance. Disadvantage when attacking a target within 5 feet. It needs two hands when the wielder is not mounted. | The mounted state is a DM-set condition until mounts are modeled in version 2 (E-127) |
| RS-EQP-028 | Net. A Large or smaller creature hit by a net is restrained until freed. Formless and Huge or larger creatures are unaffected. One net attack per action whatever the number of attacks. | A tier 1 effect: applies the restrained condition on a hit |
| RS-EQP-029 | Improvised weapons. An object not made as a weapon deals 1d4 damage of a fitting type, is thrown at 20/60, and does not add proficiency unless the DM allows it. A ranged weapon used in melee, or a melee weapon without the thrown property that is thrown, is improvised. | Improvised option generated for any held object, with a DM override |
| RS-EQP-030 | Unarmed strike: RS-ATK-007 (1 plus Strength, proficient). Silvered and magical flags: an instance can be silvered (100 gp per weapon or ten pieces of ammunition). A weapon with a +N bonus counts as magical. Part G reads both flags for damage resistance. A weapon or armor definition also carries a material field, and an instance has a corrosion counter that starts at 0 and only goes down further; the rust monster of Part G reads and changes them (RS-MON-026). Corrosion is added to a weapon's damage rolls and subtracted from the armor class that armor or a shield gives. | `silvered` and `magical` read from the instance and definition; \`material\` on the definition and \`corrosion\` on the instance |

The attack option builder runs whenever an attack is declared. For each held weapon, for each throwable weapon carried, for unarmed strikes and improvised objects, it produces an option with a name, the ability used, the attack bonus (ability, proficiency, magic bonus, effects), the damage dice and type, the reach or range, and any special notes (loading, heavy, lance, net). Part B resolves the chosen option through the usual windows.

### 11.6 Magic items, attunement and charges

The curated list below is the set of magic items the engine resolves (accepted scope, answer 1). Every other magic item is stored at tier 0: the players see its text and the DM adjudicates it. The list can grow without changing the engine, because each entry is data built from a small set of effect primitives. The primitives named here are additions to those of Part E: `AC_MODIFIER`, `ATTACK_BONUS`, `DAMAGE_BONUS`, `EXTRA_DAMAGE_DICE`, `SAVE_BONUS`, `ABILITY_FLOOR` (sets a score to a value only if it is lower), `HEAL_DICE`, `CHARGES` and `CONTAINER_WEIGHT`. These are names for this section. In content the Content Schema Specification (section 7) stores `ATTACK_BONUS`, `DAMAGE_BONUS`, `SAVE_BONUS` and `AC_MODIFIER` as `modifier` entries on the matching stat key, `EXTRA_DAMAGE_DICE` as `add_damage_dice`, `ABILITY_FLOOR` as a `modifier` with the maximum operation, `HEAL_DICE` as `heal`, `CHARGES` as an item field with `spend_resource`, and `CONTAINER_WEIGHT` as an item field.

| Item | Tier | Attunement | What the engine does |
| --- | --- | --- | --- |
| Potion of Healing (2d4 + 2), Greater (4d4 + 4), Superior (8d4 + 8), Supreme (10d4 + 20) | 1 | No | `HEAL_DICE` on the drinker. Drinking or giving one is an action. The potion is used up |
| Weapon +1, +2, +3 | 2 | No | `ATTACK_BONUS` and `DAMAGE_BONUS` of N on the weapon. The weapon counts as magical |
| Armor +1, +2, +3 | 2 | No | `AC_MODIFIER` of N while the armor is worn |
| Shield +1, +2, +3 | 2 | No | `AC_MODIFIER` of N on top of the shield's own +2 |
| Ring of Protection | 2 | Yes | `AC_MODIFIER` +1 and `SAVE_BONUS` +1 on every saving throw |
| Cloak of Protection | 2 | Yes | The same as the ring |
| Bracers of Defense | 2 | Yes | `AC_MODIFIER` +2 only while the wearer has no armor and no shield |
| Amulet of Health | 2 | Yes | `ABILITY_FLOOR` Constitution 19 (no effect if the score is already 19 or more) |
| Gauntlets of Ogre Power | 2 | Yes | `ABILITY_FLOOR` Strength 19 (same rule) |
| Wand of Magic Missiles | 2 | No | 7 charges. Spend 1 charge to cast Magic Missile at level 1, and 1 more for each level above that. At dawn it regains 1d6 + 1 charges. If the last charge is spent, roll a d20: on a 1 the wand crumbles. The spell resolves as in Part E with its settings |
| Bag of Holding | 2 | No | `CONTAINER_WEIGHT`: the bag always weighs 15 lb, its contents do not count toward encumbrance, and it holds up to 500 lb. Taking something out is an action. Special cases (such as other extradimensional spaces) are tier 0 and left to the DM |
| Flame Tongue | 2 | Yes | A bonus action lights or douses it, and it goes out if dropped or sheathed. While lit it adds `EXTRA_DAMAGE_DICE` 2d6 fire to hits. Its light is a DM note in version 1 and an emitter from version 1.5 (RS-ENV-003, RS-ENV-006) |

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-EQP-031 | Effects of a magic item apply only while it is worn or held as its description requires, and, if it needs attunement, only while the wearer is attuned. | The effect list is read from items that are active under that test |
| RS-EQP-032 | Attunement. A character can be attuned to at most 3 items. An item is attuned to one creature at a time, and a character cannot attune to two copies of the same item. Attuning takes a short rest spent focused on that one item, which cannot be the rest used to learn the item's properties, and must be done outside an encounter. Attunement ends if the character no longer meets a prerequisite, if the item is more than 100 feet away for 24 hours, if the character dies, if another creature attunes to it, or if the character ends it with another short rest (not possible with a cursed item). | `ATTUNE` is a downtime action that advances the game clock by one hour. The engine ends attunement by itself when an ending condition can be seen in the state, and asks the DM for the rest |
| RS-EQP-033 | Bonuses from different items add (Ring of Protection with a Cloak of Protection gives +2). Two copies of the same item definition count once. | A de-duplication by item definition when effects are gathered (E-104) |
| RS-EQP-034 | Charges. An item with charges stores its current and maximum. Spending charges is part of using the item. A recharge rule names when (dawn) and how much (a dice expression). A destroy-on-empty rule names the die and the result. | `CHARGES` effect and the ON\_DAWN trigger in section 8.2 |
| RS-EQP-035 | Identification. An item the players have not identified is shown by its mundane look (a plain longsword, an unmarked ring), its magic bonuses and attunement are hidden, and its effects still apply for the engine. Studying it for a short rest or casting Identify reveals it. The DM can reveal it at any time. | `identified` flag. The player view is filtered on the server (E-109) |
| RS-EQP-036 | Using an item. Using a magic item as its description says (drinking a potion, igniting a Flame Tongue) costs the action or bonus action the description names. | The item entry names the action type |

### 11.7 Gear, currency and encumbrance

All SRD adventuring gear, tools, mounts and vehicles, trade goods and services ship as seed data in the content pack with cost and weight, at tier 0. The data shape in 11.2 is what lets a later release add mechanics to any of them without a schema change. A short list is at tier 1 or is a flag the engine reads:

| Item | What the engine does |
| --- | --- |
| Acid (vial) | Thrown as an improvised weapon, 2d6 acid damage on a hit |
| Alchemist's fire | Thrown as an improvised weapon, 1d4 fire damage at the start of each of the target's turns until it uses an action to end it (a Dexterity check, DC 10) |
| Holy water | Thrown as an improvised weapon, 2d6 radiant damage on a hit against a fiend or an undead |
| Healer's kit | 10 uses. One use stabilizes a creature at 0 hit points without a Medicine check |
| Arcane focus, druidic focus, holy symbol, component pouch | A flag the component check in Part E reads: it supplies material components that have no listed cost, and it can be the item held to meet RS-EQP-016. A holy symbol worn visibly or borne on a shield needs no free hand to serve as a focus |

| ID | Rule | Engine support |
| --- | --- | --- |
| RS-EQP-037 | Denominations. Copper (cp), silver (sp), electrum (ep), gold (gp), platinum (pp). 1 sp is 10 cp, 1 ep is 50 cp, 1 gp is 100 cp, 1 pp is 1,000 cp. | A conversion table. All prices are stored in copper |
| RS-EQP-038 | Coins are stored as a whole number per denomination and are never converted unless a payment needs change. Paying uses coins from the largest denomination down, never overshooting the price, and skips electrum unless the player chooses it. If the coins that do not overshoot cannot cover the price, the engine breaks the smallest coin that covers the remainder and returns the change largest-first, again skipping electrum. A payment that cannot be made is refused. | A `CurrencyLedger` with `pay(costInCopper)` and `receive(...)` |
| RS-EQP-039 | Coin weight. 50 coins weigh 1 pound, whatever the denomination. | Used only when encumbrance is on |
| RS-EQP-040 | Carrying capacity. A creature can carry 15 times its Strength score in pounds. It can push, drag or lift 30 times its Strength score. A Tiny creature has half of each, and a Large or larger creature has double. | `EncumbranceCalculator`. Used only when the encumbranceRule setting is not OFF |
| RS-EQP-041 | Standard rule (STANDARD). The capacity above is a hard limit: the engine refuses to add an item that would take the total past it, unless the DM overrides. | The refusal names the weight and the limit |
| RS-EQP-042 | Variant rule (VARIANT). In addition to the hard limit: over 5 times Strength the creature is encumbered (speed down 10 feet). Over 10 times Strength it is heavily encumbered (speed down 20 feet, and disadvantage on ability checks, attack rolls and saving throws that use Strength, Dexterity or Constitution). | Speed and disadvantage sources computed from total weight |
| RS-EQP-043 | Total weight is the sum of everything worn, held and carried, plus coins. The contents of a Bag of Holding are excluded and the bag counts as 15 lb. | The weight sum reads `CONTAINER_WEIGHT` |
| RS-EQP-044 | Ammunition recovery. At the end of a battle a character can spend a minute searching to recover half of the ammunition that was expended, rounded to the nearest whole number with a half rounding up. The ammunitionRecovery setting can turn this off. | An end-of-encounter action offered to the player. Advances the game clock by one minute |

### 11.8 Conflicts and defaults

| ID | Case | What the sources say | Default | Setting |
| --- | --- | --- | --- | --- |
| C-F1 | Drawing a weapon between attacks (Extra Attack, or throwing two weapons) | The SRD gives one free object interaction per turn. The Sage Advice Compendium lets a two-weapon fighter throw two weapons but does not make drawing both free. Some tables allow one draw with each attack. | One free interaction per turn | objectInteraction |
| C-F2 | Donning or doffing armor in an encounter | The SRD gives the times in minutes. Some tables allow a hurried change. | Not allowed in an encounter | armorChangeInEncounter |
| C-F3 | Recovering ammunition | The SRD allows recovering half after a battle. Some tables recover everything that is not broken. The SRD rounds every division down; the project owner chose rounding to the nearest whole number, with .5 rounding up, for this case only. | Half, rounded to the nearest whole number (.5 rounds up) | ammunitionRecovery |
| C-F4 | Unarmored Defense or Draconic Resilience with Mage Armor | The AC formulas are alternatives. They do not combine, and the character uses the best one. | Highest eligible base | none |
| C-F5 | Versatile weapon with a shield | The shield takes one hand, so the weapon is used one-handed. | One-handed die | none |
| C-F6 | Bonuses from different items | Nothing in the rules stops a Ring of Protection and a Cloak of Protection adding. Some tables cap them. | They add. The same definition counts once | none (a DM note can override one item) |
| C-F7 | Armor Strength minimum | The SRD reduces speed by 10 feet. Older editions gave disadvantage on attacks. | SRD: speed reduced | none |
| C-F8 | Somatic components with both hands full | Strictly, a free hand is needed. Many tables let a weapon hand or shield hand cast. | Follows the Part E setting | componentEnforcement |
| C-F9 | Unidentified magic items | A short rest or Identify reveals them. Some tables show the full item at once. | Hidden from players | unidentifiedItems |

### 11.9 Decisions and settings

Accepted on 2026-11-19. The owner confirmed the defaults behind E-97, E-99, E-102, E-104 and E-111 on 2026-11-18 (E-102 with rounding to the nearest whole number) and accepted the rest of E-92 to E-113 on 2026-11-19.

| ID | Question | Default |
| --- | --- | --- |
| E-92 | How are items modeled? | A definition in content, instances on owners, and the three tiers of 11.2. Every magic item not on the curated list of 11.6 is tier 0. |
| E-93 | Does the engine read item names? | No. Behavior comes only from structured fields and effect entries. |
| E-94 | Is armor class stored? | No. It is computed by `computeArmorClass` from the current state, and the breakdown is kept for display. This replaces the Part B statement that AC is fixed at creation. |
| E-95 | How do alternative AC bases combine? | They do not. The highest eligible base wins (RS-EQP-002). |
| E-96 | How is armor proficiency enforced? | It is computed from the worn armor and shield and the proficiency list. It replaces the sheet flag behind CAST\_ARMOR\_NOT\_PROFICIENT. |
| E-97 | Can armor change in an encounter? | No. Outside an encounter the game clock advances by the donning or doffing time. A shield is one action either way. |
| E-98 | How many hands, and how do they affect spells? | Two. RS-EQP-016 settles the somatic component wait left by RS-SPL-015 and E-79. |
| E-99 | How much object interaction is free? | One interaction per turn. The second needs Use an Object. |
| E-100 | Where do attack options come from? | They are generated from held weapons, throwable carried weapons, unarmed strikes and improvised objects. Part B resolves the chosen option. |
| E-101 | How are weapon properties represented? | As enumerated data values, each with one engine rule (RS-EQP-019 to RS-EQP-028). |
| E-102 | How is ammunition tracked? | As a stack quantity that falls by one per shot. Recovery is half after the battle, rounded to the nearest whole number with .5 rounding up (an owner choice for this case, not the SRD's general round-down rule). |
| E-103 | What counts as an improvised weapon? | RS-EQP-029. |
| E-104 | Do item bonuses stack? | Bonuses from different items add. The same definition counts once. |
| E-105 | How does attunement work? | Three items, one hour outside an encounter, and the ending conditions of RS-EQP-032. The engine ends it by itself when the state shows an ending condition. |
| E-106 | How do charges recharge? | Through the ON\_DAWN trigger, using the dice expression on the item. |
| E-107 | How is money stored? | Whole coins per denomination, prices in copper, and the change rule of RS-EQP-038. |
| E-108 | Is encumbrance tracked? | Off by default. STANDARD and VARIANT are DM options. |
| E-109 | Who sees an unidentified item's properties? | Only the DM. The player view is filtered on the server. |
| E-110 | How are gear, mounts and services handled? | As tier 0 seed data. The tier 1 list of 11.7 has mechanics. More can be added as data. |
| E-111 | Are there equipment slots? | Yes, with the limits of RS-EQP-013 as an engine convention the DM can override. |
| E-112 | How are magical and silvered weapons marked? | By a flag on the definition or instance. Part G reads the flags for resistance. |
| E-113 | How does starting equipment resolve? | The item names that Part D lists resolve to content ids. The character receives the items and the player marks what is worn or held. The engine does not pick for them. |

| Setting | Values (default first) | Tier | Asked when |
| --- | --- | --- | --- |
| encumbranceRule | OFF, STANDARD, VARIANT | Structural | When the campaign is created. Changing it later asks for confirmation |
| objectInteraction | ONE\_PER\_TURN, PER\_ATTACK | Silent | Never asked. Read when an object interaction is spent |
| armorChangeInEncounter | BLOCKED, DM\_OVERRIDE | Silent | Never asked. Read when an armor change is requested in an encounter |
| ammunitionRecovery | HALF, NONE | Silent | Never asked. Read at the end of an encounter |
| unidentifiedItems | HIDDEN, SHOWN | Silent | Never asked. Read when the player view is built |

### 11.10 Worked examples

**WE-F1. Fighter in chain mail.** A fighter with Strength 16 and Dexterity 10 wears chain mail (base 16, Strength minimum 13, met), carries a shield and has the Defense fighting style. AC is 16 + 2 (shield) + 1 (Defense) = 19. Heavy armor ignores Dexterity. She holds a longsword in one hand and the shield in the other, so the sword is used one-handed (1d8). If she sheathes the shield as her free interaction, the sword can be used two-handed (1d10) and AC falls to 17.

**WE-F2. Wizard with Mage Armor.** A wizard with Dexterity 14 casts Mage Armor. The base is 13 + 2 = 15. Bracers of Defense add +2 because she has no armor and no shield, giving 17, and an attuned Ring of Protection adds 1, giving 18. If she picks up a shield she is not proficient with, the bracers stop applying, AC changes by +2 - 2 = 0, and she can no longer cast spells (RS-EQP-008). The sheet shows both reasons.

**WE-F3. Barbarian changes armor.** A barbarian with Dexterity 14 and Constitution 16, wearing nothing and carrying a shield, uses Unarmored Defense: 10 + 2 + 3 + 2 = 17. At camp he puts on scale mail (a 5 minute change outside an encounter, so the clock advances 5 minutes). Unarmored Defense is no longer eligible, so the base is 14 + 2 = 16, plus the shield is 18. Scale mail also gives Stealth disadvantage.

**WE-F4. Archer and ammunition.** A halfling ranger holds a longbow (two-handed, heavy) with 20 arrows. As a Small creature she has disadvantage on attacks with it (RS-EQP-021). She fires 12 arrows in a battle, so the stack is 8. After the battle she searches for a minute and recovers 6, leaving 14. Had she fired 13, she would recover 7 (6.5 rounds up). With ammunitionRecovery set to NONE she would have 8.

**WE-F5. Paying with change.** A character holds 1 pp, 3 gp and 4 sp and buys an item for 7 gp 5 sp (750 cp). The ledger spends the 3 gp and the 4 sp, which leaves 410 cp. No smaller coin remains, so it breaks the 1 pp and returns 590 cp as 5 gp and 9 sp. The character ends with 5 gp and 9 sp, which is 590 cp, the original 1,340 cp minus 750.

### 11.11 Tests and verification debt

These tests run as unit tests on the pure Java engine. Dice are seeded.

| ID | Test | Runs |
| --- | --- | --- |
| T-48 | AC for every armor in 11.3 with Dexterity -1, 0, 2 and 5: light adds all, medium caps at +2, heavy ignores Dexterity | Unit |
| T-49 | Alternative bases: barbarian, monk, Mage Armor and worn armor in every combination. The highest eligible base wins and the breakdown names it | Unit |
| T-50 | Modifiers and floors: shield, +N armor, +N shield, Ring and Cloak of Protection, Bracers of Defense with and without armor or shield, a Barkskin floor. AC is recomputed after each trigger in RS-EQP-009 | Unit |
| T-51 | Armor proficiency: wearing armor or a shield without proficiency gives disadvantage on Strength and Dexterity rolls and blocks casting. A Strength shortfall lowers speed by 10 | Unit |
| T-52 | Hands: a two-handed weapon with a shield is refused; versatile grips; two light weapons give an off-hand option; an S component is refused with both hands full, accepted with a free hand, and accepted when a hand holds the spell's focus | Unit |
| T-53 | Object interaction: one free interaction per turn, a second is refused without Use an Object, and PER\_ATTACK allows one per attack | Unit |
| T-54 | Attack options: finesse picks the better modifier, thrown weapons, a ranged weapon in melee is improvised, heavy against Small, loading limits the Attack action to one shot, lance within 5 feet, net applies restrained | Unit |
| T-55 | Armor change: refused in an encounter; outside one the clock advances by the category's time; a shield costs one action | Unit |
| T-56 | Attunement: the fourth item is refused; each ending condition ends it; a Ring and a Cloak of Protection add; two copies of the same definition count once | Unit |
| T-57 | Charges: Wand of Magic Missiles spends charges per spell level, recharges 1d6 + 1 at dawn, and on the last charge a d20 roll of 1 destroys it | Unit |
| T-58 | Currency: conversion, the change rule of RS-EQP-038 (WE-F5), and refusal when the total is too low | Unit |
| T-59 | Encumbrance: OFF changes nothing; STANDARD refuses past the limit; VARIANT thresholds for several Strength scores and sizes; the Bag of Holding counts 15 lb | Unit |
| T-60 | Ammunition: the stack falls by one per shot; recovery is half rounded to the nearest, with .5 up, including an odd count; NONE recovers nothing | Unit |
| T-61 | Identification: the player view hides the properties of an unidentified item, the DM view shows them, and the effects still apply | Unit |

Verification debt V-7, checked on 2026-11-18. Each item was checked against a source, and the result is stated:

| Item | Result | Source |
| --- | --- | --- |
| Donning times and Strength minimum | Cleared. The Sphinx build of the SRD gives the same sentences and the same table: 1, 5 and 10 minutes to don, 1, 1 and 5 to doff, a shield one action, and a speed reduction of 10 feet when Strength is below the listed score | SRD 5.1 (two renditions) |
| Magic item entries | Cleared. Every entry on the curated list was read and matches 11.6: Potions of Healing, Weapon, Armor and Shield +N, Ring and Cloak of Protection, Bracers of Defense, Amulet of Health, Gauntlets of Ogre Power, Wand of Magic Missiles, Bag of Holding and Flame Tongue. Two details were added: Flame Tongue goes out if dropped or sheathed, and a character cannot attune to two copies of one item | SRD 5.1 magic items |
| Attunement text | Cleared. Three items, one creature at a time, a short rest that cannot be the rest used to learn the item's properties, the four ending conditions, and ending it with a short rest unless the item is cursed | SRD 5.1 magic items |
| Drawing a weapon to throw | Cleared as far as official sources go. The Compendium says two-weapon fighting lets a character throw two weapons but does not make drawing both free, and that a second object interaction on a turn takes an action. Only a feature such as the Dual Wielder feat (not in the SRD) changes that. The default in C-F1 stands | Sage Advice Compendium 2.7 |
| Net | Cleared. A DC 10 Strength check as an action frees a creature, or 5 slashing damage to the net (AC 10) frees it and destroys the net. Only one attack can be made with a net per action, bonus action or reaction | SRD 5.1 (two renditions) |
| Grip change on a versatile weapon | Cleared by the SRD text. The versatile damage applies when the weapon is used with two hands, so the grip is decided for each attack (RS-EQP-012). No Sage Advice ruling exists | SRD 5.1 and the Compendium |
| Drinking and giving a potion | Cleared. The 2014 Dungeon Master's Guide (p. 139) says drinking a potion or giving one to another character takes an action. The Compendium confirms the giving half and cites the same page, and a second source quotes the sentence for both. The SRD potion entry itself gives no action cost. The 2024 rules make it a bonus action; this specification follows the SRD 5.1 (2014) rules, so it is an action | Sage Advice Compendium 2.7 and DMG p. 139 as quoted by a secondary source |
| Tier 1 gear values | Cleared. Acid 2d6 acid, Alchemist's fire 1d4 fire each turn until a DC 10 Dexterity check, Holy water 2d6 radiant against a fiend or undead, Healer's kit ten uses and one use stabilizes a creature at 0 hit points. Focuses: a holy symbol is held, worn visibly or borne on a shield | SRD 5.1 adventuring gear |
| Ammunition recovery rounding | Decided by the owner on 2026-11-18: round to the nearest whole number, and .5 rounds up. This differs from the SRD's general round-down rule and applies to this case only | Owner decision |

### 11.12 What Part F means for the engine

| Component | Job |
| --- | --- |
| ItemCatalog | Loads item definitions from content and answers lookups by id |
| Inventory | Holds instances on characters, containers and the ground. Moves them and enforces hand and slot rules |
| ArmorClassCalculator | `computeArmorClass(state)` with the breakdown |
| HandsModel | Two hands, grips, and the free-hand test for somatic components |
| AttackOptionBuilder | Builds the options of 11.5 from the current state |
| AttunementService | Attunement start, the limit of 3, and ending conditions |
| ChargeService | Spends charges and recharges on the ON\_DAWN trigger |
| CurrencyLedger | Per-denomination coins, payment and change |
| EncumbranceCalculator | Capacity, thresholds and the speed and disadvantage sources, when the setting is on |
| PlayerViewFilter | Hides the properties of unidentified items from the player view |

All of these are pure Java in the rules engine, with no database access. The Architecture already has `inventory_item` with equipped, attunement and currency. Part F adds the hand or slot position, charges, an identified flag, a silvered flag and a ground location. Those additions are recorded in the Architecture by reference to this section.

## 12. Part G: Monsters and encounters

Part G covers monsters: the stat block, hit points, challenge rating and XP, special traits, legendary actions and lair actions, creatures made by spells, and the monster roster, plus the XP side of encounters. It is a first draft written on 2026-11-19. The owner accepted the decisions in 12.12 (E-114 to E-133) on 2026-11-19, and Part G is propagated to the other documents. The small amendments to Part B, Part E and Part F, listed in 12.7 and 12.15, are applied in the sections they change. The scope follows the owner's answers of 2026-11-19: (1) about 18 monsters resolve fully (tier 2) and every other SRD stat block is seed data; (2) monster hit points are the printed average, with rolled as a setting; (3) legendary actions, Legendary Resistance and lair actions live inside the reaction window; (4) mounts wait for v2; (5) a spell that creates a creature states who controls it; (6) only per-monster XP and total XP ship now, and the difficulty readout waits for the simulator (v1.5); (7) the roster includes community favorites.

### 12.1 What was researched and how the disagreements are settled

| Question | What was checked | Finding |
| --- | --- | --- |
| Stat block fields and rules | The SRD 5.1 PDF supplied by the owner on 2026-11-19 (Monsters chapter, combat chapter and the summoning spells); earlier reads through 5thsrd.org and a Sphinx build of the SRD were checked against it | The fields of 12.2, the 14 creature types, hit die by size, Multiattack, recharge, limited use, innate and ordinary spellcasting, legendary and lair actions, shapechangers and swarms. |
| Stat blocks read | The SRD 5.1 PDF, read page by page for every stat block in the roster | All 18 tier 2 stat blocks and the two tier 1 stat blocks (kobold and orc) were checked against the PDF on 2026-11-19, which clears V-8. The check found four differences from the earlier reads, all corrected in this section: the giant spider's poison leaves the target poisoned for 1 hour and paralyzed (RS-MON-027); no SRD stat block has lair actions (RS-MON-034); Life Drain makes a zombie 24 hours after a kill (RS-MON-016); and the dragons have no spellcasting, so the mage is the only tier 2 monster that casts spells (12.10). |
| Legendary and lair action text | SRD 5.1 PDF, Monsters chapter (Legendary Creatures) and the legendary stat blocks | Timing, regaining, incapacitation, surprise, initiative count 20, ties and regional effects. Legendary Resistance is an ordinary stat block trait, not part of those rules. Surprise delays lair actions as it does legendary actions. No SRD 5.1 stat block has lair action options or regional effects. |
| Mounted combat | SRD combat chapter | Recorded for v2 in 12.8. |
| Multiattack and opportunity attacks | SRD 5.1, Monsters chapter (Multiattack) and combat chapter (opportunity attacks) | An opportunity attack is a single melee attack, and a creature cannot use Multiattack for it. A breath weapon is not a melee attack, so it is excluded too. |
| Challenge rating tables | The SRD 5.1 PDF, 5thsrd.org, a World Anvil copy and a community companion table | Proficiency bonus and XP by CR. The PDF prints CR 27 as 105,000. Two copies on the web print 150,000 out of order, so they are wrong. No roster monster is above CR 17. |
| Encounter building | The Basic Rules (2014 method), the Roll20 compendium and a community review (2024 method), Sly Flourish | Neither method is in SRD 5.1 (the PDF has no encounter-building rules), and the chapter list of SRD 5.2 has no encounter building. A third 2024 table disagreed with the other two and was set aside. |
| Legendary Resistance against automatic failure | Official sources and community threads | No official ruling found, so the interaction is a DM setting (E-118). |
| Monsters outside the SRD | The SRD 5.1 monster and NPC index (342 entries) | Beholder, Mind Flayer and Displacer Beast are not in it and are not shipped. |
| Gaps that remain | Gaps in this research | V-8 was cleared on 2026-11-19 against the SRD 5.1 PDF. What remains is outside the SRD: an official Legendary Resistance ruling (E-118 is a DM setting because there is none) and the 2024 encounter budget table (not shipped, E-129). The aboleth was not read because it is not in the roster. |

Nine disagreements between the SRD, the community and Part B came up in the research. Each is settled by a decision in 12.12.

| ID | Disagreement | Settled by |
| --- | --- | --- |
| C-G1 | Monsters die at 0 hit points (RS-DMG-015), but Undead Fortitude and Regeneration change what happens at 0 | A zero-hit-point hook with ordered handlers (PB-G1, E-117) |
| C-G2 | Whether Legendary Resistance can turn an automatic failure into a success | A DM setting; the default is that it can (E-118) |
| C-G3 | Legendary actions and lair actions do not fit the reaction window as written | The window gains the ON\_TURN\_END point and lair actions get an initiative slot at count 20 (E-119, E-120) |
| C-G4 | Swarms occupy another creature's space, which RS-MOV-006 forbids | A narrow exception for swarms (E-121) |
| C-G5 | How Pack Tactics checks that an ally is next to the target | Distance from the grid in the pure-Java engine, not the C++ pathfinder; the DM confirms in theatre of the mind (E-122) |
| C-G6 | Average or rolled monster hit points | Average by default, rolled as a setting (E-115) |
| C-G7 | The 2014 or the 2024 encounter math | The 2024 XP-budget method, built with the v1.5 readout (E-128, E-129) |
| C-G8 | Whether stat block bonuses must match the challenge rating | Yes. Checked at pack build, a warning for homebrew (E-116) |
| C-G9 | The Gelatinous Cube's Engulf | Future work; the cube stays tier 0 (E-130) |

### 12.2 Stat block fields and validation

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MON-001 | A monster is a content definition. Its fields are: name; size; type and tags; alignment (display only); armor class value with its source text; hit point dice and printed average; speeds (walk, burrow, climb, fly with a hover flag, swim); the six ability scores; saving throw and skill bonuses; damage vulnerabilities, resistances and immunities; condition immunities; senses with ranges; languages (display only); challenge rating, XP and proficiency bonus; traits; actions; bonus actions; reactions; legendary actions with their count; lair actions; and regional effects (text only). The engine reads the structured fields and never the name (the rule of E-93). | `MonsterDef` in content (`MONSTER_DEF`); spawning creates a combatant (Architecture 4.12) |
| RS-MON-002 | The creature types are aberration, beast, celestial, construct, dragon, elemental, fey, fiend, giant, humanoid, monstrosity, ooze, plant and undead. A type can carry tags (goblinoid, shapechanger). Types have no rules of their own; a rule that names a type (Divine Smite against undead and fiends, a spell that affects only humanoids) reads the field. | `creatureType` enum and a tag set |
| RS-MON-003 | Size sets the space (RS-MOV-008) and the hit die: Tiny d4, Small d6, Medium d8, Large d10, Huge d12, Gargantuan d20. Hit points are written as N hit dice plus N times the Constitution modifier. | Hit die table in data; `footprint` from RS-MOV-008 |
| RS-MON-004 | The printed hit point average is the average of the dice plus the modifier, rounded down (RS-CNV-001). A pack monster whose printed average differs fails the pack build; a homebrew monster gets a warning. Checked on the stat blocks read: 7d10+21 gives 59, 9d8 gives 40, 8d10+40 gives 84, 3d8+9 gives 22, 5d8+5 gives 27. | Content validator |
| RS-MON-005 | The proficiency bonus comes from the challenge rating (12.3). Every saving throw, skill and attack bonus on a stat block is the ability modifier plus the proficiency bonus, or twice the bonus for expertise, or the modifier alone when not proficient. A pack monster whose bonus does not follow this fails the pack build; a homebrew monster gets a warning, because a DM may break it on purpose. The save DCs of special traits are checked as warnings only, since the SRD sets some by hand. Spot checks on the stat blocks read: the mage's Intelligence save of +6 is +3 plus 3 at CR 6, the owlbear's beak at +7 is +5 plus 2 at CR 3, the bugbear's Stealth of +6 is +2 plus twice 2. | Content validator (T-62) |
| RS-MON-006 | Damage vulnerabilities, resistances and immunities, and condition immunities, use RS-DMG-005 and RS-DMG-006. The qualifier "from nonmagical attacks that aren't silvered" is met by a damage instance that is magical or comes from a silvered weapon (E-112). The printed text is kept for display. A qualifier that names another material (adamantine) is not modeled: a monster that uses one is tier 1 and the DM applies it. | Flags per damage type with a qualifier |
| RS-MON-007 | Senses and languages are data in version 1. They are shown on the sheet and have no engine effect; the vision seam is 13.3 and RS-ENV-015 (version 1.5). Passive Perception is shown to the DM. | Data fields |
| RS-MON-008 | An action is a named entry with one or more attacks, a save effect or another effect. Multiattack is one action that lists the attacks it makes; an entry in the list can name a replacement (the wight's Life Drain replaces one longsword attack). A monster's attack uses the attack rules of Part B with the printed bonus and damage; reach, range and the number of targets are data. The engine does not count a monster's ammunition; the SRD lets the DM assume 2d4 pieces for a thrown weapon and 2d10 for a projectile weapon. | `ActionDef(attacks, replacements, cost)` |
| RS-MON-009 | A limited-use ability. "N/day" is a resource with maximum N that recharges on a long rest (RS-RES-005). "Recharge X-Y" is a resource with maximum 1 that is spent when used. At the start of each of the monster's turns while it is spent, the engine rolls a d6 and the ability recharges on a roll of X or higher. It also recharges when the monster finishes a short or a long rest. The roll is recorded and replayed unchanged (RS-REA-R10). The event is START\_OF\_OWN\_TURN (PB-G6). | `RechargeRule(START_OF_OWN_TURN, d6, X)` |
| RS-MON-010 | An opportunity attack by a monster is one melee weapon attack or unarmed strike. It is never a Multiattack (SRD 5.1: an opportunity attack must be a single melee attack) and never a breath weapon, which is not a melee attack. | RS-ATK-008 option list excludes composite actions |
| RS-MON-011 | A monster with Spellcasting has a caster level, an ability, a save DC, an attack bonus, spell slots and spell lists on the stat block. It casts through the casting rules of Part E with those numbers and its own slots, which recharge on a long rest. A spell that Part E leaves at tier 0 is a DM-adjudicated option. | Part E casting with the monster's numbers |

### 12.3 Challenge rating, proficiency bonus and XP

A challenge rating (CR) is 0, 1/8, 1/4, 1/2 or a whole number from 1 to 30. A monster's proficiency bonus and XP follow from its CR, and these tables are the only place the values are written. Content stores each monster's printed XP and proficiency bonus, and the validator checks both against these tables (RS-MON-005). A CR 0 monster is worth 0 XP, or 10 XP if it can deal damage.

| Challenge rating | Proficiency bonus |
| --- | --- |
| 0 to 4 | +2 |
| 5 to 8 | +3 |
| 9 to 12 | +4 |
| 13 to 16 | +5 |
| 17 to 20 | +6 |
| 21 to 24 | +7 |
| 25 to 28 | +8 |
| 29 to 30 | +9 |

| CR | XP | CR | XP | CR | XP |
| --- | --- | --- | --- | --- | --- |
| 0 | 0 or 10 | 11 | 7,200 | 22 | 41,000 |
| 1/8 | 25 | 12 | 8,400 | 23 | 50,000 |
| 1/4 | 50 | 13 | 10,000 | 24 | 62,000 |
| 1/2 | 100 | 14 | 11,500 | 25 | 75,000 |
| 1 | 200 | 15 | 13,000 | 26 | 90,000 |
| 2 | 450 | 16 | 15,000 | 27 | 105,000 |
| 3 | 700 | 17 | 18,000 | 28 | 120,000 |
| 4 | 1,100 | 18 | 20,000 | 29 | 135,000 |
| 5 | 1,800 | 19 | 22,000 | 30 | 155,000 |
| 6 | 2,300 | 20 | 25,000 |  |  |
| 7 | 2,900 | 21 | 33,000 |  |  |
| 8 | 3,900 |  |  |  |  |
| 9 | 5,000 |  |  |  |  |
| 10 | 5,900 |  |  |  |  |

### 12.4 Hit points and the zero-hit-point hook

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MON-012 | A spawned monster has its own hit points. The campaign setting monsterHp picks how they start. AVERAGE (the default) uses the printed average. ROLLED rolls the hit point dice once at spawn, with a minimum of 1; the roll is recorded and replayed unchanged. Several monsters from one definition roll separately. The DM can edit hit points, and while a reaction window is open such an edit is rejected (RS-REA-012). | `monsterHp` setting; `hpMax` and `hpCurrent` on the combatant |
| RS-MON-013 | Monsters die at 0 hit points by default (RS-DMG-015). A trait can change that through the zero-hit-point hook. After damage is applied and a creature is at 0, the engine runs handlers in a fixed order before the death step of RS-DMG-009: (1) defender handlers that keep it up, which can leave it at 1 hit point (Undead Fortitude); (2) defender handlers that delay death (the troll's Regeneration); (3) source riders, which change the state of a target that drops to 0 (the giant spider's poison). The first handler that applies and stops the outcome ends the chain. Instant death (RS-DMG-010) is checked first and is never replaced. | Ordered handler list at the 0 hit points step of RS-DMG-017 (PB-G1) |
| RS-MON-014 | Undead Fortitude: when damage reduces the creature to 0, it makes a Constitution saving throw with DC 5 plus the damage taken, unless the damage is radiant or from a critical hit. On a success it drops to 1 hit point instead. Damage taken is the damage of that instance after resistance and vulnerability, including any part that temporary hit points absorbed (the reading of RS-DMG-013). If the instance includes any radiant damage, there is no save. The save is an ordinary saving throw, so its windows open as for any save (RS-REA-013). | Defender handler, kind STAY\_UP |
| RS-MON-015 | Regeneration and delayed death (troll). When damage reduces the troll to 0 it does not die: it falls unconscious (RS-DMG-011) and is marked as dying by trait. At the start of its turn it regains 10 hit points if the trait works, which ends the unconscious condition, and it acts normally that turn. If the trait does not work, it dies. The trait does not work at the start of the troll's next turn if it took acid or fire damage since the start of its last turn; damage counts only if at least 1 point remains after resistance and immunity. The mark and the suppression clear at that start of turn. | Defender handler, kind DELAY\_DEATH; `regenSuppressed` flag; START\_OF\_OWN\_TURN trigger |
| RS-MON-016 | Hit point maximum reduction (wight, Life Drain). A hit deals its damage, and the target makes a Constitution saving throw with the DC on the stat block. On a failure the target's hit point maximum is reduced by the damage taken, until the target finishes a long rest. Reductions add up. If the maximum reaches 0 the target dies. Current hit points fall to the new maximum if they were above it (the rule of E-5). A humanoid slain by Life Drain rises as a zombie 24 hours later under the wight's control, unless it is restored to life or its body is destroyed, and a wight controls at most twelve zombies at once. The engine does not run the delay: it shows the DM a reminder, and the DM creates the zombie with the effect of RS-MON-037. | Effect type `reduce_hp_max`, duration until long rest, cumulative |

### 12.5 Special traits as engine mechanics

Each row turns a stat block trait into one mechanic the engine can run. The mechanic is built from the effect types of Architecture 4.4 where possible; the rows that need a new effect type say so, and 12.15 lists them.

| ID | Trait and monsters | Mechanic | Engine mapping |
| --- | --- | --- | --- |
| RS-MON-017 | Nimble Escape (goblin) | On its turn the creature can take Disengage or Hide as a bonus action. | A bonus-action option on the turn budget (RS-CMB-011) |
| RS-MON-018 | Adjacency from the grid (used by RS-MON-019 and RS-MON-020) | "Within 5 feet of a creature" means the distance between the two footprints is at most 5 feet, measured with `GridGeometry.distance` and `footprint` of the pure-Java engine (not the C++ pathfinder service), using the campaign's diagonal rule. A large creature is reached from any of its cells. An ally counts only if it is not incapacitated, which includes unconscious and dead. With no grid (theatre of the mind) the engine asks the DM to confirm the condition and records the answer. The aura resolver of section 9.6 uses the same function. | `allyNextToTarget(attacker, target)` in the engine rules, backed by `engine.grid` |
| RS-MON-019 | Pack Tactics (wolf) | The creature has advantage on an attack roll against a creature if at least one of its allies is within 5 feet of that creature and is not incapacitated. | `grant_advantage` with the condition of RS-MON-018 |
| RS-MON-020 | Martial Advantage (hobgoblin) | Once per turn, a weapon attack that hits a creature within 5 feet of an ally of the hobgoblin that is not incapacitated deals an extra 2d6 damage. The extra dice double on a critical hit (RS-DMG-003). | `add_damage_dice` with the condition of RS-MON-018 and a once-per-turn token (E-50) |
| RS-MON-021 | Surprise Attack (bugbear) | An attack that hits a creature the bugbear surprised, during the first round of combat, deals an extra 2d6 damage. Brute is part of the printed damage and has no rule of its own. | `add_damage_dice` conditioned on round 1 and the target's surprised state (Part B) |
| RS-MON-022 | Rampage (gnoll) | When the creature reduces another creature to 0 hit points with a melee attack on its turn, it can take a bonus action to move up to half its speed and make one bite attack. The option is offered after the zero-hit-point step and only if the bonus action is available. | A conditional bonus-action grant; new effect entry `grant_bonus_action_option` |
| RS-MON-023 | Swarm (swarm of rats) | A swarm of Tiny beasts takes the space of a Medium creature. It can occupy another creature's space and another creature can occupy its space (PB-G5). It can move through any opening a Tiny creature fits through, with no squeezing penalty. It cannot regain hit points or gain temporary hit points. Its bite has reach 0 and hits one creature in its space, and its damage is lower when it has half its hit points or fewer. | `shareSpace` flag on the footprint check; `noHealing` flag; a damage formula that depends on `hpCurrent` against half of `hpMax` |
| RS-MON-024 | Shapechanger (mimic, werewolf) | A monster can have forms, and its current form is a field of the combatant. A trait or action can be limited to a form (the mimic's Adhesive and False Appearance in object form; the werewolf's bite, claws and spear by form), and armor class and speed can differ by form. Changing form takes an action. Anything not named per form stays the same. The creature returns to its true form when it dies. This is not Polymorph: nothing swaps the stat block, which waits for v2. A curse that a bite can cause (lycanthropy) is a DM note on the character in version 1. | `form` on the combatant; a form gate on traits and actions |
| RS-MON-025 | Adhesive and Grappler (mimic) | In object form the mimic sticks to anything that touches it. A creature of Huge size or smaller that touches it is grappled, with the escape DC on the stat block and disadvantage on the escape checks. The mimic has advantage on attack rolls against a creature it has grappled. The DM triggers the touch. A hit by the mimic's Pseudopod while it is in object form also subjects the target to Adhesive. | The grappled condition of 7.8 with an escape DC and a flag; `grant_advantage` |
| RS-MON-026 | Rust Metal and Antennae (rust monster) | A nonmagical metal weapon that hits the monster takes a permanent, cumulative -1 penalty to its damage rolls after it deals damage, and is destroyed at -5. Nonmagical metal ammunition that hits is destroyed after it deals damage. The Antennae action corrodes a nonmagical ferrous metal object the monster can see within 5 feet. An object that is not worn or carried loses a 1-foot cube. If a creature wears or carries it, that creature can make a Dexterity saving throw (the DC on the stat block) to avoid the touch. Metal armor or a metal shield that is touched takes a permanent, cumulative -1 to the AC it offers, and is destroyed when armor offers AC 10 or a shield offers +0. A held metal weapon rusts as above. Magical items are unaffected. | A `corrosion` counter on the item instance read by `computeArmorClass` (Part F) and by damage rolls; a `material` field on weapons and armor (12.15); new effect entry `corrode_item` |
| RS-MON-027 | Web and poison (giant spider) | Web (Recharge 5-6) is a ranged attack. On a hit the target is restrained and a web object appears with the armor class, hit points, vulnerability and immunities on the stat block. The target stays restrained until it breaks free with the check on the stat block or the web is destroyed. If the bite's poison damage reduces a target to 0 hit points, the target is stable and poisoned for 1 hour, even after it regains hit points, and is paralyzed while it is poisoned in this way (a source rider, RS-MON-013). Web Sense and Web Walker are data for the DM. | `apply_condition` restrained linked to an object combatant; a source rider handler |
| RS-MON-028 | Sunlight Sensitivity (wight) | Disadvantage on attack rolls and on sight-based Wisdom (Perception) checks in sunlight. Lighting is derived from version 1.5 (RS-ENV-007), and before that the DM sets an `inSunlight` flag on the combatant (E-132). | A DM-set flag and `grant_disadvantage` conditioned on it |
| RS-MON-029 | Keen senses (owlbear, wolf, rats, troll, werewolf) | Advantage on Wisdom (Perception) checks that rely on the named sense. The DM marks a check as relying on it. | A sense tag on the check and `grant_advantage` |

### 12.6 Legendary actions, Legendary Resistance and lair actions

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MON-030 | Legendary actions. A creature with legendary actions has a number of legendary action points each round (the stat block says how many, usually 3). Each option costs one or more points. It can use one legendary action at a time, and only at the end of another creature's turn. It regains all its points at the start of its own turn. It cannot use a legendary action while incapacitated. If it is surprised, it cannot use one until after its first turn in the combat. Using one is not an action, bonus action or reaction and does not use the creature's reaction. | New window point ON\_TURN\_END; cost kind LEGENDARY\_ACTION with a points pool on the combatant (PB-G2) |
| RS-MON-031 | The ON\_TURN\_END window. When a creature's turn ends, the engine checks every other creature with legendary actions that has points left and is eligible under RS-MON-030. For each one it asks the DM to pick an option it can pay for, or to pass. The windows open one at a time in initiative order, starting after the creature whose turn ended. Each option resolves like the same action taken on its own turn, including its own reaction windows. A creature that takes an option here can still be the target of ordinary reactions. | Reaction window rules of Part B (RS-REA-001, RS-REA-002), with the new point |
| RS-MON-032 | Legendary Resistance (N/day). When the creature fails a saving throw, it can choose to succeed instead, and the use is spent. The offer appears at the AFTER\_SAVE\_FAIL point (RS-REA-013) to the DM, with the uses left. The uses return on a long rest. A use spent is not returned if the DM undoes the choice before the roll is applied; once applied it stands. Legendary Resistance changes the outcome of the save and not the roll, so nothing that reads the roll (Lucky, Bardic Inspiration already spent) is affected. | Effect type `spend_resource` (uses per day), outcome override at AFTER\_SAVE\_FAIL |
| RS-MON-033 | Automatic failure and Legendary Resistance. Some conditions make a save fail with no roll (a stunned or paralyzed creature failing Strength and Dexterity saves). The campaign setting legendaryResistanceOverridesAutoFail decides whether Legendary Resistance can turn that failure into a success. The default is on: an automatic failure opens a window that offers only Legendary Resistance (the WS2 window of RS-REA-013 and RS-REA-R24 with the other options removed, since there was no roll to react to). With the setting off, an automatic failure stands. Evasion and similar traits apply after Legendary Resistance. | `legendaryResistanceOverridesAutoFail` setting (default true); a WS2-only window |
| RS-MON-034 | Lair actions. A creature with lair actions uses one at initiative count 20, which loses all ties, and only while the DM has marked the fight as taking place in its lair. It uses at most one per round and cannot repeat the same option two rounds in a row where the stat block says so. A lair action is not the creature's own turn: it takes no action, bonus action or reaction from the creature. The creature must be able to act: it cannot use one while incapacitated or otherwise unable to take actions, and if it is surprised it cannot use one until after its first turn in the combat. No SRD 5.1 stat block has lair actions, so the mechanism is built for homebrew monsters and tested with a test definition. | A LAIR entry in the initiative order at count 20 (PB-G3); an `inLair` flag on the encounter |
| RS-MON-035 | Regional effects. A creature's regional effects are listed on the stat block as text for the DM to apply near its lair. They are not engine mechanics in version 1. When the creature dies, the DM removes them; the engine reminds the DM when the creature that owns them is marked dead. No SRD 5.1 stat block has regional effects either. | Reminder on death only |
| RS-MON-036 | The legendary creature's own turn. At the start of its turn the creature regains its legendary action points (RS-MON-030) and rolls its recharge dice (RS-MON-009). Both belong to the START\_OF\_OWN\_TURN event (PB-G6), which runs before the creature's turn options appear and after start-of-turn effects such as the troll's Regeneration. | START\_OF\_OWN\_TURN event handlers, in a fixed order |

### 12.7 Amendments to Part B

Part G needs six changes to rules that Part B has already fixed, and two pointers that went stale. The owner accepted them on 2026-11-19, and they are applied to their sections in the same revision as the rest of Part G, as listed in 12.15.

| ID | Where in Part B | Change | Decision |
| --- | --- | --- | --- |
| PB-G1 | RS-DMG-009, RS-DMG-015, RS-DMG-017 and the death-state paragraph | Add the zero-hit-point hook of RS-MON-013 between applying damage and the death step. Without a handler the outcome is the one Part B already gives. | E-117 |
| PB-G2 | RS-REA-001, RS-REA-002; the E-68 list of window points; Architecture 5.4 | Add the window point ON\_TURN\_END and the cost kind LEGENDARY\_ACTION (RS-MON-030, RS-MON-031). | E-119 |
| PB-G3 | Initiative rules, section 7.1 | Add a LAIR entry at count 20 that loses all ties (RS-MON-034). It is not a combatant, so it never takes damage and has no turn options other than the lair actions of the creature that owns it. | E-120 |
| PB-G4 | RS-REA-013 and RS-REA-R24 | Add the Legendary Resistance offer at AFTER\_SAVE\_FAIL and the WS2-only window for automatic failure (RS-MON-032, RS-MON-033). | E-118 |
| PB-G5 | RS-MOV-004, RS-MOV-006, RS-MOV-008 | Add the swarm exception: a swarm can share a space with another creature and another creature can enter its space. Entering a swarm's space costs movement as difficult terrain (RS-MOV-004). A creature that leaves a swarm's space leaves a reach-0 threat, so it can provoke an opportunity attack under RS-MOV-008, and the swarm's bite has reach 0. | E-121 |
| PB-G6 | RS-RES-002 | Add the event START\_OF\_OWN\_TURN to the list of events that expire or trigger effects, and the handler order of RS-MON-036. | E-123 |
| PB-G7 | Part F tier table, the row that says mounts wait for Part G | Replace the pointer with: mounts are deferred to version 2 (E-127). The mounted combat rules are recorded in 12.8. | E-127 |
| PB-G8 | Section 9.6, the aura row that points to Part G | Replace the pointer with the adjacency function of RS-MON-018, which the aura resolver uses. | E-122 |

### 12.8 Summoned creatures and mounts

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MON-037 | A spell or feature can create a creature (Find Familiar, Animate Dead, Conjure Animals and similar). The creating effect names the monster definition, how many appear, who controls them, and how they join the initiative order. The engine spawns each one as a combatant with its own hit points (RS-MON-012), placed where the spell says. | Effect type `create_creature` with `definitionId`, `count`, `controller`, `join` |
| RS-MON-038 | Control. The `controller` field is either the DM or the caster's player. It says who decides the creature's choices. In version 1 the DM enters every choice for every creature (RS-REA-003), so the field tells the DM whom to ask and is shown on the combatant; it does not give the player any controls. The caster commands in words, as the spell says. | `controller` on the combatant |
| RS-MON-039 | Initiative. A creature created singly rolls its own initiative with its own modifier, unless the spell says it shares the caster's count. A group created at once rolls once for the whole group and acts as one entry, using the join options of Part B. If the encounter is not running when the creature appears, it is queued and joins when the next round starts. | Part B join options; one roll per group |
| RS-MON-040 | Duration. A created creature is tied to the effect that made it. When the effect ends (concentration lost, duration over, dispelled), the creature is removed without dying: no death saves, no zero-hit-point hook, no death effects. A creature that is reduced to 0 hit points dies as any monster does, unless the spell says it disappears at 0 hit points (the Conjure spells and Find Familiar do), in which case it is removed without dying; either way the effect that made it is not ended by that. A spell that grants control for a time (Animate Dead, 24 hours) ends the control and not the creature: after the time the creature stays but obeys no command. | Removal bound to the effect's end (RS-RES-002) |
| RS-MON-041 | Created creatures are excluded from the encounter XP total (RS-MON-045) and give no XP when defeated. The creature's stat block is the one the spell names; Polymorph and other spells that swap a stat block are not covered (version 2). | `created` flag on the combatant |
| RS-MON-042 | Find Familiar and Animate Dead leave the list of spells that wait in Part E and become tier 2, so that the creatures they create resolve fully (E-131). For Find Familiar that means a form from the spell's list (a stat block in the seed data), its own initiative, no attacks, disappearance at 0 hit points, one familiar at a time, and delivery of a touch spell with the familiar's reaction. For Animate Dead it means a skeleton or a zombie, a bonus action to command it within 60 feet, control for 24 hours, and reasserting control over up to four creatures. Conjure Animals and the other group summons stay at their Part E tier; the engine spawns the creatures but the DM adjudicates anything the tier does not resolve. Find Steed stays adjudicated by the DM. | Part E amendment, listed in 12.15 |
| RS-MON-043 | Mounts are version 2. The SRD rules are recorded here so that the later design starts from them. Mounting or dismounting costs a creature an amount of movement equal to half its speed. A controlled mount acts on its rider's initiative and can take only the Dash, Disengage and Dodge actions. An independent mount keeps its own initiative and acts as it likes. If the mount is moved against its will, or the rider is knocked prone while mounted, the rider makes a DC 10 Dexterity saving throw and falls prone in a space within 5 feet of the mount on a failure. An attack that targets the mount's space can target either the rider or the mount. A mount must be a willing creature at least one size larger than the rider, with a suitable anatomy. Mounting or dismounting happens once during the rider's move. If the mount is knocked prone, the rider can use a reaction to dismount and land on their feet; otherwise the rider is dismounted and falls prone within 5 feet of it. | Not implemented; E-127 |

### 12.9 Encounter experience points and the 2024 budget method

SRD 5.1 has no encounter-building chapter, and SRD 5.2 has none either, so the method here is not an SRD rule. It is the owner's design choice (decision 7). Only the experience point values of each monster and the total of an encounter ship in version 1.

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MON-044 | Experience points per monster. A monster's XP is read from the challenge rating table of 12.3. The value shows on the monster's card. A custom monster that has a challenge rating gets the value in the same way. | `xpForCr` lookup |
| RS-MON-045 | Encounter total. The encounter total is the sum of the XP of every monster that is part of the encounter, whether it has been defeated or not, including monsters the DM adds in the middle of the fight. Created creatures (RS-MON-041) and object combatants (such as a web) are left out. The total has no multiplier for the number of monsters and is not divided among characters. | `encounterXpTotal(encounter)` |
| RS-MON-046 | No automatic awards. Advancement is by milestone by default (E-4, section 3.6). The total is a figure for the DM to read; the engine does not add it to any character. Awarding XP and levelling by it is version 1.5. | No write to the characters |
| RS-MON-047 | The 2024 method (specified now, shipped later). Each character in the party has an XP budget for the encounter that depends on the character's level, in three bands: Low, Moderate and High. The party's budget in each band is the sum of its characters' budgets. The encounter total of RS-MON-045 is compared with the three party budgets, and the encounter is labelled below Low, Low, Moderate, High or above High. The labels are this project's own words. The readout waits for the simulator of version 1.5, because the label should be checked against simulated fights, and it is not part of version 1. | Specified only; E-128, E-129 |
| RS-MON-048 | No budget table in version 1. The per-level budgets are entered together with the readout. The two public tables that were checked disagree on some levels (see 12.1), so the table is taken from a source that is checked when the readout is built. The method of 2014 (a multiplier on the XP total by the number of monsters) is not implemented. | Data not shipped; E-129 |

### 12.10 The monster roster

Part G follows the tier pattern of Part F. A tier says how much of a monster the engine resolves, so that the work goes into the monsters that exercise the rules and the rest are available as data.

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MON-049 | Tier 2 monsters resolve fully. Every trait, action, reaction and legendary option on the stat block is an engine mechanic (12.5, 12.6) and has a test. | Full handlers |
| RS-MON-050 | Tier 1 monsters resolve their numbers: armor class, hit points, speed, attacks, damage, saving throws and skills. Special traits and actions beyond that are shown as text for the DM to adjudicate. | Numbers resolved; traits as text |
| RS-MON-051 | Tier 0 monsters are seed data: the stat block is stored and shown, and the DM adjudicates everything beyond a plain attack roll against armor class. Every other monster in SRD 5.1 is tier 0. A monster can move up a tier in a later version without a change to its data. | Seed data only |
| RS-MON-052 | Monsters that are not in SRD 5.1 are not shipped. The beholder, the mind flayer and the displacer beast are well loved but are not in the SRD, so a DM adds them with the custom monster editor of version 1.5. The gelatinous cube is in the SRD but its Engulf needs a mechanic that Part G does not specify (12.15), so it stays tier 0. | Excluded from seed data |

The roster below was chosen to cover each mechanic of 12.4 to 12.6 at least once, and to include monsters that tables ask for often.

| Monster | Challenge rating | Tier | What it exercises |
| --- | --- | --- | --- |
| Goblin | 1/4 | 2 | Nimble Escape (RS-MON-017) |
| Wolf | 1/4 | 2 | Pack Tactics (RS-MON-019); keen senses (RS-MON-029) |
| Zombie | 1/4 | 2 | Undead Fortitude (RS-MON-014) |
| Skeleton | 1/4 | 2 | Vulnerability and immunities (RS-MON-006) |
| Swarm of Rats | 1/4 | 2 | Swarm (RS-MON-023); damage that depends on its hit points |
| Hobgoblin | 1/2 | 2 | Martial Advantage (RS-MON-020) |
| Gnoll | 1/2 | 2 | Rampage (RS-MON-022) |
| Rust Monster | 1/2 | 2 | Rust Metal and Antennae (RS-MON-026) |
| Bugbear | 1 | 2 | Surprise Attack (RS-MON-021) |
| Giant Spider | 1 | 2 | Web and poison (RS-MON-027); Recharge (RS-MON-009) |
| Mimic | 2 | 2 | Shapechanger, Adhesive and Grappler (RS-MON-024, RS-MON-025) |
| Owlbear | 3 | 2 | Multiattack (RS-MON-008); keen senses |
| Werewolf | 3 | 2 | Forms and form-gated attacks (RS-MON-024); immunity to nonmagical, non-silvered weapons |
| Wight | 3 | 2 | Life Drain (RS-MON-016); Sunlight Sensitivity (RS-MON-028) |
| Troll | 5 | 2 | Regeneration and delayed death (RS-MON-015); keen smell |
| Mage | 6 | 2 | Spellcasting through Part E (RS-MON-011); the validation example |
| Young blue dragon | 9 | 2 | Multiattack and a line-shaped breath weapon with Recharge (RS-MON-009) |
| Adult red dragon | 17 | 2 | Legendary actions, Legendary Resistance and Frightful Presence inside Multiattack (RS-MON-030 to RS-MON-033). It has no lair actions in SRD 5.1. |
| Kobold | 1/8 | 1 | Numbers resolved; Pack Tactics and Sunlight Sensitivity as text |
| Orc | 1/2 | 1 | Numbers resolved; Aggressive as text |

The roster has 18 tier 2 monsters. The mage is the only tier 2 monster that casts spells, so a monster's spellcasting (RS-MON-011) is exercised by one stat block and not by every caster in the SRD.

### 12.11 Edge cases

| ID | Situation | Ruling |
| --- | --- | --- |
| RS-MON-R1 | A zombie is reduced to 0 hit points by a critical hit. | No Undead Fortitude save. It dies. |
| RS-MON-R2 | A zombie takes 6 slashing and 4 radiant damage in one attack. | The instance includes radiant damage, so there is no save. It dies if reduced to 0. |
| RS-MON-R3 | A zombie has temporary hit points that absorb all the damage. | Its hit points are not 0, so the hook does not run. |
| RS-MON-R4 | A troll takes fire damage that its resistance reduces to 0. | The damage does not suppress Regeneration (RS-MON-015). |
| RS-MON-R5 | A troll that is at 0 hit points and unconscious takes more damage. | Monsters make no death saving throws, so it stays at 0 unless the damage meets the instant death rule (RS-DMG-010). Acid or fire damage that gets through suppresses its Regeneration. |
| RS-MON-R6 | A troll at 0 hit points is healed above 0 before its turn. | It is no longer dying by trait, and the mark clears. |
| RS-MON-R7 | A legendary creature is surprised in the first round. | It cannot use a legendary action until after its first turn (RS-MON-030). |
| RS-MON-R8 | A legendary creature is incapacitated at the end of another creature's turn. | It cannot use a legendary action, and the window does not open for it. |
| RS-MON-R9 | Two legendary creatures are in the fight. | Their windows open one after the other in initiative order, starting after the creature whose turn ended (RS-MON-031). |
| RS-MON-R10 | A legendary creature's own turn ends. | It cannot use a legendary action at the end of its own turn. |
| RS-MON-R11 | A creature with lair actions dies, or the fight is not marked as in the lair. | Its LAIR entry is removed on death. Without the flag it has no lair actions. |
| RS-MON-R12 | A character and a lair action are both at count 20. | The lair action goes after the character (RS-MON-034). |
| RS-MON-R13 | An automatic failure on a save, with legendaryResistanceOverridesAutoFail off. | The failure stands and no offer is made. |
| RS-MON-R14 | A stunned dragon with Legendary Resistance and Evasion fails a Dexterity save automatically. | With the default setting, Legendary Resistance is offered first. If used, the save succeeds, and Evasion then applies to the success. |
| RS-MON-R15 | A creature has two Recharge abilities. | One d6 is rolled for each at the start of its turn (RS-MON-009). A short rest recharges both. |
| RS-MON-R16 | A swarm of rats is healed, or gains temporary hit points. | It gains nothing (RS-MON-023). Its bite uses the lower damage at half its maximum or fewer. |
| RS-MON-R17 | A character moves out of a swarm's space. | The swarm's reach is 0, so leaving is leaving its reach and can provoke an opportunity attack (PB-G5). |
| RS-MON-R18 | A fireball catches a swarm that shares a space with a character. | Both are in the area and each makes its own save. |
| RS-MON-R19 | A rust monster is hit by a magic weapon, or its antennae touch magic armor. | Nothing happens to it (RS-MON-026). A nonmagical weapon that reaches -5 is destroyed after the hit's damage is dealt. |
| RS-MON-R20 | A character grappled by a mimic's Adhesive tries to escape. | The escape is an ordinary check against the DC on the stat block, with disadvantage. |
| RS-MON-R21 | A werewolf changes form. | Armor class, speed and the actions of the new form apply at once. Hit points and conditions stay. |
| RS-MON-R22 | An ally that counts for Pack Tactics is unconscious, or there is no grid. | An unconscious ally does not count. With no grid, the DM confirms the condition and the answer is recorded (RS-MON-018). |
| RS-MON-R23 | A monster makes an opportunity attack. | It makes one melee attack. It does not use Multiattack or a breath weapon (RS-MON-010). |
| RS-MON-R24 | The setting monsterHp is ROLLED and the roll comes to less than 1. | The hit points are 1 (RS-MON-012). |

### 12.12 Engine decisions

The owner accepted all twenty decisions on 2026-11-19. They are recorded in the Decision Log as DEC-104.

| ID | Decision | Rules |
| --- | --- | --- |
| E-114 | A monster definition is data. Spawning turns a definition into a combatant with its own hit points, conditions and resources, and the definition is never changed by play. | RS-MON-001 |
| E-115 | Monster hit points start at the printed average by default. The campaign setting monsterHp can switch to rolling the dice once at spawn. | RS-MON-012 |
| E-116 | Stat block bonuses are validated against the proficiency bonus of the challenge rating. The shipped data must pass the check. | RS-MON-004, RS-MON-005 |
| E-117 | The zero-hit-point hook is a Part B extension point. It runs ordered handlers between applying damage and the death step: stay up, delay death, then source riders. | RS-MON-013 to RS-MON-015, RS-MON-027 |
| E-118 | Legendary Resistance is a stat block resource, offered at AFTER\_SAVE\_FAIL. By default it also turns an automatic failure into a success, and a DM option turns that off. | RS-MON-032, RS-MON-033 |
| E-119 | Legendary actions use a new window point, ON\_TURN\_END, and a points pool that refills at the start of the creature's own turn. | RS-MON-030, RS-MON-031 |
| E-120 | Lair actions are a LAIR entry in the initiative order at count 20, which loses ties. It is not a combatant. | RS-MON-034 |
| E-121 | Swarms are handled by one exception to the movement rules: they share space. The other rules of movement stay as they are. | RS-MON-023, PB-G5 |
| E-122 | "Within 5 feet of an ally" is computed from the pure-Java `engine.grid` package, not from the C++ pathfinder, so that it works when the pathfinder is not installed. With no grid the DM confirms. | RS-MON-018 |
| E-123 | START\_OF\_OWN\_TURN is a real engine event. Regeneration, legendary action points and recharge rolls run from it in a fixed order. | RS-MON-009, RS-MON-036 |
| E-124 | A monster's form is a field of the combatant and gates traits and actions. Forms do not swap stat blocks. | RS-MON-024 |
| E-125 | Item degradation is a per-instance counter on the item. Weapons and armor get a material field. | RS-MON-026 |
| E-126 | A spell can create creatures through one effect type. The caster's player or the DM is recorded as the controller, and in version 1 the DM enters every choice. | RS-MON-037 to RS-MON-041 |
| E-127 | Mounted combat is deferred to version 2. The SRD rules are recorded and Find Steed stays with the DM. | RS-MON-043 |
| E-128 | Version 1 ships per-monster XP and the encounter total. The 2024 budget method is specified, and its readout waits for the simulator of version 1.5. | RS-MON-044 to RS-MON-047 |
| E-129 | No encounter budget table ships in version 1. It is added with the readout, from a source that is checked when the readout is built. | RS-MON-048 |
| E-130 | The roster follows the tier pattern of Part F. The gelatinous cube stays at tier 0 and its Engulf is future work. | RS-MON-049 to RS-MON-052 |
| E-131 | Find Familiar and Animate Dead move to tier 2 in Part E. | RS-MON-042 |
| E-132 | Lighting conditions are DM-set flags in version 1 and are derived from version 1.5 (13.2, 13.3). | RS-MON-028 |
| E-133 | A monster's opportunity attack is one melee attack. It is never Multiattack and never a breath weapon. | RS-MON-010 |

### 12.12.1 New settings

| Setting | Values | Default | Rule |
| --- | --- | --- | --- |
| monsterHp | AVERAGE, ROLLED | AVERAGE | RS-MON-012 |
| legendaryResistanceOverridesAutoFail | true, false | true | RS-MON-033 |

Tiers of the two settings (Architecture 4.15). `monsterHp` is Structural: it changes how every spawned monster starts, so the setup wizard asks it with the other hit point choices. `legendaryResistanceOverridesAutoFail` is Ask on first use: it matters only when a creature with Legendary Resistance meets an automatic failure, so it is asked the first time that can happen. Both appear in `requiredSettings` for the actions that read them: spawning a monster for the first, and any action that can force a saving throw on a creature with Legendary Resistance for the second.

The lair flag `inLair` is a property of the encounter and not a campaign setting (RS-MON-034).

### 12.13 Worked examples

| ID | Example | Result |
| --- | --- | --- |
| WE-G1 | A zombie has 2 hit points left and takes 7 slashing damage from a longsword that is not a critical hit. | It is reduced to 0, so the hook runs. DC is 5 + 7 = 12. The zombie makes a Constitution saving throw with its +3: it succeeds on a roll of 9 or higher and stays at 1 hit point; on a lower roll it dies. If the 7 damage had included any radiant damage, there would be no save. |
| WE-G2 | A troll is reduced to 0 hit points. A character then hits it with a torch for 5 fire damage before the troll's turn. | The troll falls unconscious and is marked as dying by trait. The fire damage got through its defenses, so Regeneration is suppressed. At the start of its turn it does not regain hit points and it dies. Without the fire damage it would regain 10 hit points, wake up and act. |
| WE-G3 | Two wolves attack a character. One wolf is adjacent to the character. The other wolf attacks. | The first wolf is within 5 feet of the character and is not incapacitated, so the second wolf's bite has advantage (RS-MON-019). If the first wolf were unconscious, there would be no advantage. With no grid, the DM is asked to confirm. |
| WE-G4 | A mage is loaded from its definition. It has challenge rating 6, a spell attack bonus of +6, a spell save DC of 14, and 9d8 hit dice with a printed average of 40. | Challenge rating 6 gives a proficiency bonus of +3. Intelligence 17 gives +3, so the spell attack is 3 + 3 = +6 and the save DC is 8 + 3 + 3 = 14. The hit points are the average of 9d8 (40.5) with a Constitution modifier of 0, rounded down to 40. All three agree and the definition passes (RS-MON-005). |
| WE-G5 | An adult red dragon has 3 legendary action points. At the end of a character's turn the DM uses Tail Attack (1 point). At the end of the next character's turn the DM uses Wing Attack (2 points). A spell then stuns the dragon and forces a Dexterity saving throw. | Both options are paid for from the pool, which is now empty, so the dragon cannot act at the end of the third turn. At the start of its own turn it regains 3 points. The stunned dragon fails the save automatically, and with the default setting the engine offers Legendary Resistance. If the DM uses it, the save succeeds, one of the 3 uses for the day is spent, and 2 are left. |
| WE-G6 | A swarm of rats has 24 hit points and starts a round at 14. A character hits it for 3 damage. | The swarm is at 11, below half of its 24 (12), so its bite now uses the lower damage dice. A healing spell has no effect on it. |
| WE-G7 | A fighter hits a rust monster five times with a nonmagical steel longsword. | After each hit's damage is dealt, the longsword takes -1 to its damage rolls, so the damage is lowered by 1 on the second hit, 2 on the third, and so on. After the fifth hit's damage it reaches -5 and is destroyed. A magic longsword would take no penalty. |
| WE-G8 | An encounter has four goblins (XP 50 each) and a hobgoblin (XP 100). The party has four characters of level 3. | The encounter total is 4 x 50 + 100 = 300 XP (RS-MON-045). For the illustration, assume per-character budgets for level 3 of 150 (Low), 225 (Moderate) and 400 (High); these figures are assumed here and are not shipped data (RS-MON-048). The party budgets are 600, 900 and 1,600, so the 2024 method would label the encounter below Low. In version 1 the DM sees only the total of 300. |

### 12.14 Tests

| ID | Test | Rules covered |
| --- | --- | --- |
| T-62 | Every shipped stat block passes the validation: proficiency bonus by challenge rating, attack and save bonuses, hit point average, and XP by challenge rating. A definition with a wrong bonus is flagged. | RS-MON-004, RS-MON-005 |
| T-63 | Hit points: AVERAGE gives the printed average. ROLLED rolls once at spawn, records the roll, and replays it unchanged. A roll below 1 becomes 1. Several monsters from one definition roll separately. | RS-MON-012 |
| T-64 | The zero-hit-point hook runs handlers in the fixed order, stops at the first one that applies, and never replaces instant death. | RS-MON-013 |
| T-65 | Undead Fortitude: the DC uses the damage taken; no save for a critical hit or for radiant damage in the instance; temporary hit points that absorb the damage prevent the hook. | RS-MON-014, RS-MON-R1 to RS-MON-R3 |
| T-66 | Troll: falls unconscious at 0, regains 10 at the start of its turn, is suppressed by acid or fire damage that survives resistance, dies when suppressed, and the mark clears when it is healed. | RS-MON-015, RS-MON-R4 to RS-MON-R6 |
| T-67 | Giant spider: the poison rider leaves a target stable and poisoned at 0 hit points. Web restrains, and the web object has the stated armor class, hit points and vulnerability. | RS-MON-027 |
| T-68 | Recharge: the d6 is rolled at the start of the monster's own turn, recharges on the stated numbers, and a short or long rest recharges it. Two recharge abilities roll separately. | RS-MON-009, RS-MON-R15 |
| T-69 | Legendary actions: window at the end of another creature's turn, points spent and refilled at the creature's own turn, nothing while incapacitated or surprised, and no use at the end of its own turn. | RS-MON-030, RS-MON-031, RS-MON-R7 to RS-MON-R10 |
| T-70 | Lair actions: one LAIR entry at count 20 that loses ties, removed on the creature's death, absent without the lair flag, and none until after the creature's first turn if it is surprised. Run with a test definition, because no SRD 5.1 stat block has lair actions. | RS-MON-034, RS-MON-R11, RS-MON-R12 |
| T-71 | Legendary Resistance: offered at AFTER\_SAVE\_FAIL, spends a use, and returns on a long rest. An automatic failure opens the WS2-only window when the setting is on, and does nothing when it is off. Evasion applies after it. | RS-MON-032, RS-MON-033, RS-MON-R13, RS-MON-R14 |
| T-72 | Swarm: shares space, enters at the difficult terrain cost, leaving provokes, no healing or temporary hit points, damage change at half hit points. | RS-MON-023, PB-G5, RS-MON-R16 to RS-MON-R18 |
| T-73 | Adjacency: footprint distance from the engine grid for Pack Tactics, Martial Advantage and auras; an unconscious ally does not count; with no grid the DM's answer is recorded. | RS-MON-018 to RS-MON-020, RS-MON-R22 |
| T-74 | Surprise Attack applies only in the first round against a surprised target. Rampage is offered only after a melee hit that reduces a creature to 0 on the monster's turn, and only with a bonus action available. | RS-MON-021, RS-MON-022 |
| T-75 | Forms: a form change applies armor class, speed and form-gated actions at once, and keeps hit points and conditions. Adhesive grapples as stated. | RS-MON-024, RS-MON-025, RS-MON-R20, RS-MON-R21 |
| T-76 | Rust monster: cumulative penalty per hit, destruction at -5, no effect on magic items, armor class reduction and its destruction limits. | RS-MON-026, RS-MON-R19 |
| T-77 | Creature creation: spawn with own hit points, a group rolls initiative once, removal at the end of the effect without death effects, no XP, excluded from the encounter total. | RS-MON-037 to RS-MON-041 |
| T-78 | Encounter XP: per-monster XP from the table, the total includes defeated and added monsters, leaves out created creatures and objects, and awards nothing automatically. | RS-MON-044 to RS-MON-046 |
| T-79 | Life Drain reduces the hit point maximum by the damage taken on a failed save, stacks, ends on a long rest, kills at 0, and a monster's opportunity attack is one melee attack and never Multiattack or a breath weapon. | RS-MON-016, RS-MON-010, RS-MON-R23 |

### 12.15 What Part G does not cover, and what it changes elsewhere

| Topic | Where it goes |
| --- | --- |
| Gelatinous Cube Engulf | Future work (E-130). The cube is tier 0. |
| Mounts | Version 2 (E-127). The rules are recorded in 12.8. |
| Polymorph and other stat block swaps | Version 2. |
| Light and sunlight | Sections 13.2 and 13.3, from version 1.5. Until then the DM sets flags (E-132). |
| The encounter difficulty readout | Version 1.5, with the simulator (E-128). |
| Custom monster editor | Version 1.5. |
| Monsters that are not in SRD 5.1 | Not shipped (RS-MON-052). |
| Treasure and random encounters | Not specified. |

On acceptance of Part G, the changes below are made, in the same revision as the acceptance, and each document gets its own change note.

| Document | Change |
| --- | --- |
| Ruleset, Part B | PB-G1 to PB-G8 of 12.7. |
| Ruleset, Part E | Find Familiar and Animate Dead move to tier 2 (E-131). |
| Ruleset, Part F | Weapons and armor get a material field (RS-MON-026). The pointer to Part G for mounts is replaced (PB-G7). |
| Architecture | Section 4.4: new effect types `reduce_hp_max`, `create_creature`, `corrode_item` and `grant_bonus_action_option`, added to the catalog. Section 4.17 is new and lists the monster components. Section 5.4: the window point ON\_TURN\_END (also added to the check on the point column of pending\_reaction), the cost kind LEGENDARY\_ACTION and the zero-hit-point hook. The package `engine.grid` is named as the source of adjacency. |
| Requirements | The monster subset table, the open question about monsters, and the per-monster XP requirement. |
| AI Build Brief | The monster definition, the spawn step, the hook, the legendary and lair rules, and the new tests. |
| Decision Log | One entry, DEC-104, for the decisions E-114 to E-133. |

## 13. Part H: the environment (light, vision, cover, falling, hazards and madness)

Part H covers the environment around a creature: light and darkness, what a creature can see, line of sight and cover on the grid, reach and opportunity attacks, falling, hazards (breath, water, traps, poisons, diseases) and madness. It was drafted on 2026-11-19 after the research of the same day, and the owner accepted it as drafted on 2026-11-19. The direction was eleven recommendations, with two changes. First, once elevation arrives in version 2 the engine calculates the fall distance itself. Second, madness gets a tracker inside the simulator and is not left to the DM's notes. The decisions in 13.12 (E-134 to E-153) write that direction as engine rules and are accepted with it.

Part H is built in phases, because the grid is flat in version 1 (E-76) and automatic line of sight, cover and falling are already committed to version 1.5 and later (FR-GRD-009, E-84, FR-VIS-002):

| Version | What the engine does with Part H |
| --- | --- |
| 1 | Scene ambient light and DM overrides per creature. Senses are data. Cover and obscurement are chosen by the DM. Fall damage is a calculator: the DM enters the distance. Hazards and traps are seed data the DM triggers. The madness tracker and the afflictions tracker are complete. |
| 1.5 | Light sources and zones, per-cell light and obscurement, automatic line of sight and cover from the map, reach and opportunity attacks checked per step, the fall event with the ON\_FALL window and a DM-entered distance, the optional flanking rule. |
| 2 | Elevation (floor height per cell, altitude per creature), the fall distance calculated from it, three-dimensional reach and distance, fog of war from the same pairwise rules, Fly and Levitate resolved by the engine. |

Every rule in this part reads its inputs through a port (13.9), so that a later version replaces an adapter and not the rule.

### 13.1 What was researched and how the disagreements are settled

The sources below were read on 2026-11-19. Every SRD rule in this part is paraphrased, not copied (SRD 5.1 text is licensed CC BY 4.0, attribution in section 1).

| Source | What it settles | Status |
| --- | --- | --- |
| SRD 5.1 PDF: Adventuring (falling, suffocating, vision and light, food and water), Combat (cover, underwater combat), Gamemastering (traps, diseases, poisons, madness) | The text of every SRD rule below, the three madness tables, the sample traps, diseases and poisons | Verified against the PDF |
| Sage Advice Compendium (official, D&D Beyond) | Darkvision does not see through magical darkness. An opportunity attack needs the attacker to see the mover. A creature gives half cover | Verified |
| Dungeon Master's Guide, variant rules for cover and line of sight on a grid (corner tracing) and for flanking | The counts of blocked lines that make half and three-quarters cover, and the flanking rule | Quoted on public pages and cited to the Dungeon Master's Guide. The book itself was not read (V-9, cleared with a note) |
| Xanathar's Guide to Everything, falling | A falling creature drops 500 feet at once and 500 feet more at the end of each of its turns | Quoted on public pages. The book itself was not read (V-9, cleared with a note) |
| RuggRogue (integer shadowcasting) and the RogueBasin field-of-view comparison | Exact integer line-of-sight maths is feasible, and symmetric methods exist. Part H does not need shadowcasting (13.9) | Read, used only as method background |

The community disagrees on fifteen points. Each is settled below. Where the default is a table choice and not a rule, a setting or a DM override exists (13.12.1).

| ID | Disagreement | Default | Why |
| --- | --- | --- | --- |
| C-H1 | Does darkvision see through magical darkness? | No. Only truesight does | The official ruling. Darkvision turns ordinary darkness into dim light. Magical darkness is a zone flag that blocks darkvision only (RS-ENV-004) |
| C-H2 | Does dim light penalize attack rolls? | No. It lightly obscures: disadvantage on sight-based Wisdom (Perception) checks only, and passive Perception is reduced by 5 | That is what the SRD says. Tables that add an attack penalty are using a house rule |
| C-H3 | For a Perception check, whose cell counts, the viewer's or the target's? | The cell of the thing being perceived. With no specific target, the viewer's cell | The SRD says creatures in a lightly obscured area have the disadvantage, and says a creature is blinded when trying to see something in a heavily obscured area. The two sentences are not parallel. A DM override on any check settles the odd case |
| C-H4 | Do creatures block line of sight? | No. A creature in the way gives half cover and nothing more | The SRD cover text and the official clarification. Cover and sight are separate values on a cell (RS-ENV-025) |
| C-H5 | In corner tracing, which corner does the attacker use? | The one that gives the target the least cover. Ties go to a fixed corner order | The variant rule lets the attacker choose. A fixed tie order keeps the engine deterministic |
| C-H6 | Does a line that only touches the corner or edge of a wall count as blocked? | No. The line must pass through the inside of the cell | Gives the attacker the benefit of the doubt and is easy to state exactly (RS-ENV-020) |
| C-H7 | Corner tracing or centre to centre? | Corner tracing. The setting coverMethod can switch to CENTERS | Corner tracing is the variant rule. Centre to centre is simpler to read on screen and harsher on cover |
| C-H8 | Flanking | Off. The setting flanking can turn on the variant advantage rule (version 1.5) | Flanking is not in SRD 5.1. It is an optional rule that changes every melee fight |
| C-H9 | Can a creature make an opportunity attack against a mover it cannot see? | No | The official ruling |
| C-H10 | How fast does a creature fall? | 500 feet when the fall begins and again at the end of each of the creature's turns. The setting fallDescentPerRound can change it, and 0 makes every fall instant | SRD 5.1 is silent. Xanathar's gives 500 feet at once and 500 more at the end of each of the creature's turns while it is still falling |
| C-H11 | Does a fall of less than 10 feet make the creature prone? | No. It lands prone only if it takes fall damage | The SRD says the creature lands prone unless it avoids taking damage. A fall under 10 feet deals no damage |
| C-H12 | Falling into water or onto a soft surface | The DM sets the damage dice, including none | SRD 5.1 has no rule. A DM override on the fall event is the honest answer (RS-ENV-038) |
| C-H13 | Does Sunlight Sensitivity apply under a Daylight spell or any other bright magical light? | Only where the light is flagged as sunlight in its data. Scene ambient light can be sunlight | The monster text says sunlight. A light source carries a sunlight flag that the data sets (RS-ENV-007) |
| C-H14 | Is madness a rule or an option? | An option the DM turns to when a source of madness appears. The tracker does the bookkeeping | The SRD presents madness as an option for horror campaigns. The owner asked for the tracker so that the DM does not keep this by hand |
| C-H15 | What cures or suppresses madness? | Calm emotions suppresses. Lesser restoration ends short-term and long-term madness. Greater restoration or stronger magic ends indefinite madness. Remove curse and dispel evil are offered when the DM judges the source allows it | The SRD curing text |

**V-9 (verification debt, cleared with a note).** Four items rested on second-hand sources. On 19 November 2026 they were checked against quotations of the books on public pages. The books are paywalled and were not read, so the printed text is still unseen. (a) The variant corner rule, confirmed by two pages that quote it: one or two blocked lines make half cover, three or four make three-quarters cover when the attack can still reach the target, and total cover is complete concealment. RS-ENV-022 is the engine's reading. It counts only cells with cover TOTAL and takes the best corner and square, so four blocked lines there leave no line of effect and give total cover. Creatures never give more than half cover (C-H4), a decision of this document and not the book's counting. (b) Flanking, confirmed by a page that quotes the Dungeon Master's Guide in blocks: the attacker and an ally stand on opposite sides or corners of the enemy's space, a creature cannot flank an enemy it cannot see or while it is incapacitated, and a Large or larger creature flanks if one square of its space qualifies. RS-ENV-034 states all four points. The word adjacent was not seen in a quotation, and the engine asks only that the ally can attack the target. (c) Xanathar's falling, confirmed by two quotations: a creature instantly descends up to 500 feet, and if it is still falling on its next turn it descends up to 500 feet at the end of that turn. RS-ENV-041 now drops the first 500 feet at once and the rest at the end of each of the creature's turns. (d) The words the Dungeon Master's Guide uses for line of effect were not found in any quotation. RS-ENV-026 is the engine's own definition and does not depend on them. The numbers sit in one data class of the engine, so a correction is a data change, and the tests check the rules as written here.

### 13.2 Light

This section defines the light level of a cell. The light level is a computed value and is never stored on a creature (RS-ENV-008). The SRD names three levels: bright light, dim light and darkness.

| ID | Rule | Engine |
| --- | --- | --- |
| RS-ENV-001 | There are three light levels. Bright light lets creatures see normally, and daylight, torches, lanterns and fires give it. Dim light, also called shadows, lightly obscures the area. Darkness heavily obscures it. Dim light is usually the border between a source of bright light and the dark around it. Twilight, dawn and a very bright full moon also count as dim light | An enum LightLevel with the order DARKNESS, DIM, BRIGHT. The order is used to pick the best available level (RS-ENV-005) |
| RS-ENV-002 | A cell gets light from three kinds of input: the ambient light of the scene, light sources (emitters), and zone flags | The inputs are read through the LightModel port (13.9). Each input gives a level for a cell or says nothing about it |
| RS-ENV-003 | An emitter is data on an item, a spell effect or a zone: a bright radius, a dim radius, a shape (sphere, or cone for a bullseye lantern) and a sunlight flag. The radii come from the item or spell data and are not written in the rules. An emitter that a creature carries is anchored to the creature and follows it. The radius is measured with the campaign distance metric. A cell is lit only if the emitter has line of sight to it (RS-ENV-021), so a closed door or a wall stops light | An emits record with bright\_ft, dim\_ft, shape and sunlight. The emitter stores an anchor (a creature, an item or a cell), never a cell copied at creation, so there is nothing stale to move. Cones reuse the exact area geometry of Part E |
| RS-ENV-004 | Magical darkness is a zone flag, MAGICAL\_DARKNESS. Every cell of the zone is darkness for every kind of sight except truesight, and darkvision does not help (C-H1). Nonmagical light never brightens such a cell. Where the zone overlaps magical light, the spell text decides which of the two spells ends, using the spell levels in the data, and the engine ends that spell's effect in the normal way | A zone flag, read by the light model and by the darkvision rule (RS-ENV-011). The overlap rule is a Part E effect that ends the lower-level spell, not special code in the light model |
| RS-ENV-005 | The light level of a cell is the best level available from every input that reaches it, with the single exception of RS-ENV-004. A torch inside a dark room makes a lit area with a dim border, and the same room in daylight stays bright | max over inputs by the LightLevel order, then the MAGICAL\_DARKNESS check |
| RS-ENV-006 | In version 1 the scene has an ambient light (bright, dim or darkness) and a sunlit flag, and the DM can override the light of any creature (a lightOverride on the combatant). An override wins over the computed value. The campaign setting lightingMode decides whether the engine derives light and sight (AUTOMATIC, from version 1.5) or only the DM's flags are used (DM\_ONLY). In version 1 only DM\_ONLY exists | Scene.ambient and Scene.sunlit. Combatant.lightOverride is nullable. AUTOMATIC is not offered until the sources and zones exist |
| RS-ENV-007 | The light result carries a sunlight flag. It is true where the ambient light of the scene is sunlight or where the lighting emitter is flagged as sunlight in its data. Bright light that is not sunlight does not set it (C-H13). Sunlight Sensitivity (RS-MON-028) reads this flag, and the DM flag inSunlight of E-132 stays as an override | LightResult(level, sunlit). RS-MON-028 reads LightResult.sunlit when AUTOMATIC, and the override flag when DM\_ONLY |
| RS-ENV-008 | Light is derived, never copied. When an emitter is lit, doused, moved or destroyed, when a door opens or closes, or when a zone starts or ends, the scene's lightVersion goes up and every derived value that depends on it is recomputed when it is next read. No combatant row, no cell row and no cache entry keeps a light level across a version change | An integer lightVersion on the scene. The memo is keyed by lightVersion, so a stale entry cannot be read |

### 13.3 Vision and obscurement

This section turns light, obscuring zones and senses into one answer: can this viewer see this target, and how well. The answer is the sight state of RS-ENV-015. It replaces the default of E-21.

| ID | Rule | Engine |
| --- | --- | --- |
| RS-ENV-009 | An area is lightly obscured or heavily obscured. Dim light, patchy fog and moderate foliage lightly obscure it. Darkness, opaque fog and dense foliage heavily obscure it. A creature trying to see something in a heavily obscured area is treated as blinded for that purpose. The obscurement of a cell is the worse of two values: the one the light level gives (dim is light, darkness is heavy) and the one a zone flag gives (OBSCURE\_LIGHT, OBSCURE\_HEAVY) | An enum Obscurement (NONE, LIGHT, HEAVY). Zone flags OBSCURE\_LIGHT and OBSCURE\_HEAVY are data on zones (fog, foliage) |
| RS-ENV-010 | Senses are data on a creature, each with a range in feet: darkvision, blindsight, tremorsense and truesight. Every creature has normal sight. A sense is measured to the target with the campaign distance metric. Senses are shown on the sheet in version 1 and are used by the engine from version 1.5 | A SenseSet map from sense to range. Part D and Part G data already carry the ranges |
| RS-ENV-011 | Darkvision: within its range, a viewer treats darkness as dim light, so a heavily obscured cell that is dark becomes lightly obscured for that viewer. It does not help against magical darkness (RS-ENV-004), against fog, or against anything that is not darkness. Colour is not modelled | Adjusts the light part of RS-ENV-009 for one viewer, only for level DARKNESS and only when the cell is not under MAGICAL\_DARKNESS |
| RS-ENV-012 | Blindsight: within its range, the viewer perceives its surroundings without relying on sight. Light, obscurement, blindness and invisibility do not matter inside the range. A cell that blocks sight (RS-ENV-025) still blocks it, because the sense does not go through a solid wall | Step 2 of RS-ENV-015 |
| RS-ENV-013 | Truesight: within its range, the viewer sees in normal and magical darkness, sees invisible creatures and objects, and sees the true form of a shapechanger or a creature changed by magic. It does not see through fog or foliage, and it does not see through a wall. Seeing illusions and the Ethereal Plane are data for the DM | Step 4 and step 5 of RS-ENV-015. A zone flag OBSCURE\_LIGHT or OBSCURE\_HEAVY still applies to truesight |
| RS-ENV-014 | Tremorsense: within its range, the viewer detects the location of creatures that touch the same surface and are not flying. It is not sight. In version 1.5 the engine marks the creature as located and the DM confirms the surface, because the engine has no surface model | A located flag in the sight state (RS-ENV-015), set by the DM's confirmation |
| RS-ENV-015 | The sight state of a viewer for a target is computed in this fixed order, and the first step that answers wins. (1) With no line of sight (RS-ENV-021) the answer is NOT\_SEEN. (2) Blindsight in range, or tremorsense in range where RS-ENV-014 allows, gives SEEN, whether or not the viewer is blinded. (3) A blinded viewer has no other sense, so the answer is NOT\_SEEN. (4) An invisible target is NOT\_SEEN unless the viewer has truesight in range. (5) The obscurement of the target's cell, after RS-ENV-011 and RS-ENV-013, gives HEAVY as NOT\_SEEN, LIGHT as SEEN\_OBSCURED, and NONE as SEEN | An enum SightState (SEEN, SEEN\_OBSCURED, NOT\_SEEN) from SightResolver.stateOf(viewer, target, scene, map). Pure function of its inputs, with no clock and no randomness |
| RS-ENV-016 | The sight state feeds these rules, and no others read light or senses directly. (a) The can-see predicate of E-21, true unless the state is NOT\_SEEN. (b) Attack rolls: an attacker the target cannot see has advantage, and a target the attacker cannot see gives disadvantage, and the two cancel. (c) A spell or effect that needs a target the caster can see. (d) The opportunity attack requirement (RS-ENV-031). (e) Hiding. (f) Sunlight Sensitivity (RS-ENV-007). (g) Disadvantage on sight-based Perception (RS-ENV-017). The frightened condition uses line of sight only (RS-ENV-021), not the sight state | One function, canSee(viewer, target), reads SightResolver. The existing condition and advantage rules call it and do not look at light |
| RS-ENV-017 | Disadvantage on Wisdom (Perception) checks that rely on sight applies when the cell of the thing being perceived is lightly obscured (C-H3). With no specific target the viewer's own cell is used. Passive Perception is reduced by 5 under the same condition, as for any passive check with disadvantage. Dim light has no other effect on a creature that is not trying to perceive something | A check modifier of scope SIGHT\_PERCEPTION. The DM can add or remove it on any check |
| RS-ENV-018 | The DM can override the sight state of any pair of creatures for the rest of the scene, for example to rule on a lit statue, a reflection or a theatre-of-the-mind scene. The override is recorded and shown on every roll it affects | An override table keyed by (viewer, target) with the scene's id. It is read first by SightResolver |

### 13.4 Line of sight and cover

This section is the geometry behind RS-ENV-015 and the cover rule of Part B. It lives in the pure-Java package engine.grid, next to the distance metric, so that it works without the C++ pathfinder (the Grid specification keeps line of sight out of the pathfinder until its later endpoints, and the Java code stays the reference for any later accelerator). The map-facing parts are version 1.5 (FR-GRD-009). In version 1 the DM names the cover and the obscurement.

| ID | Rule | Engine |
| --- | --- | --- |
| RS-ENV-019 | Space is a lattice. Every engine coordinate is an integer and one cell is 2 units wide, so a cell centre is an integer point and a cell corner is an integer point. The corners of a creature's space are the corners of the rectangle of cells it occupies. All geometry uses 64-bit integer arithmetic (cross products and comparisons) with no floating point and no division, so a result is the same on every machine and in every replay | Class engine.grid.geometry.Lattice. Cell (cx, cy) spans x from 2cx to 2cx+2 and y from 2cy to 2cy+2. The grid cap (Architecture, Grid and Pathfinder Spec) keeps every product well inside a long |
| RS-ENV-020 | A line is blocked by a cell if it passes through the inside of the cell. Touching an edge or a corner does not block it (C-H6). Two exceptions keep walls solid: a line that runs along the edge shared by two blocking cells is blocked, and so is a line that passes through the corner shared by two blocking cells that touch only at that corner. The cells of the two creatures' own spaces never block a line between them | segmentBlocked(a, b, blockingCells), an exact integer test. The two exceptions have their own tests (T-84) |
| RS-ENV-021 | Line of sight. A viewer has line of sight to a target if at least one line from a corner of the viewer's space to a corner of the target's space is not blocked by a cell that blocks sight or that lies in a zone with the flag OBSCURE\_HEAVY (dense fog, dense foliage). The cells of the two creatures' own spaces are not counted. Creatures never block sight (C-H4). Under coverMethod CENTERS the only line is centre to centre. Line of sight is symmetric: if A has it to B, B has it to A, because each line can be walked in either direction | LineOfSight.exists(a, b, map). Up to 16 lines for two single-cell creatures, stopping at the first clear one. The symmetry is a property test (T-83) |
| RS-ENV-022 | Cover from solid cells follows the variant rule of the Dungeon Master's Guide for grids. For each corner of the attacker's space and each square the target occupies, trace the four lines to the corners of that square and count the lines that are blocked by cells with cover TOTAL. None blocked gives no cover. One or two give half cover. Three give three-quarters cover. Four give total cover: the attacker already uses the corner and the square with the least cover, so four blocked lines there mean that no line from the attacker reaches the target, which is also the condition for no line of effect (RS-ENV-026). The attacker uses the corner and the target square that give the least cover (C-H5). Ties are broken by a fixed order: corners clockwise from the top left, squares in row order | CoverCalculator.solidCover(attacker, target, map). At most 4 x 4 x 4 = 64 lines for a Large target, which occupies four squares, and the loop stops early at NONE |
| RS-ENV-023 | Cover from partial cells and from creatures uses the line from the centre of the attacker's space to the centre of the target's space. Each cell on that line with cover HALF or THREE\_QUARTERS gives that degree. Each creature other than the attacker and the target whose space the line passes through gives half cover, friend or foe, and a creature never gives more (SRD, C-H4). Under coverMethod CENTERS this line is also the only line for solid cells (RS-ENV-022 then gives TOTAL if a cell with cover TOTAL is on it) | CoverCalculator.partialCover(attacker, target, map, creatures) |
| RS-ENV-024 | The cover of a target is the most protective of RS-ENV-022 and RS-ENV-023. Degrees are never added. Half cover gives +2 to armor class and Dexterity saving throws. Three-quarters cover gives +5. Total cover means the target cannot be targeted directly by an attack or a spell, though an area effect can include it (RS-ENV-026). Cover counts only when the attack or effect comes from the other side of the cover, which the geometry already guarantees | enum CoverDegree (NONE, HALF, THREE\_QUARTERS, TOTAL). The bonus is a modifier from Part B with a source tag that names the cells or creature that cause it |
| RS-ENV-025 | A cell holds two separate values: blocksSight (true or false) and cover (NONE, HALF, THREE\_QUARTERS, TOTAL). They are independent. A solid wall has blocksSight true and cover TOTAL. A portcullis or an arrow slit has cover THREE\_QUARTERS and blocksSight false. A low wall has cover HALF. Dense fog is a zone with OBSCURE\_HEAVY: it blocks sight through its cells (RS-ENV-021) and gives no cover. Thin fog is a zone with OBSCURE\_LIGHT: it blocks nothing and gives no cover. Creatures are never cells and never block sight | map\_cell columns blocks\_sight boolean NOT NULL DEFAULT false and cover text NOT NULL DEFAULT 'NONE', with a CHECK on the four values. The Architecture owns the final schema (13.9) |
| RS-ENV-026 | Area effects. The origin of the area, a grid intersection, takes the place of the attacker's corner. Cover applies to the Dexterity saving throw of each creature in the area, by RS-ENV-022 to RS-ENV-024 with the origin as the only corner. The engine also answers lineOfEffect(origin, cell): true if at least one line from the origin to a corner of the cell is not blocked by a cell with cover TOTAL. Part E uses it to decide whether an effect reaches a cell | lineOfEffect(origin, cell, map), a function used by the area code of Part E |
| RS-ENV-027 | The DM can override the cover of any attack or effect, in both directions, and in version 1 the DM chooses the cover and no map is needed. An override is recorded on the roll and shown to the player. A computed result that the DM did not override is shown with its cause, for example half cover from a creature | The override is a field of the attack record. The cause comes from the source tags of RS-ENV-024 |
| RS-ENV-028 | A door is a cell whose state the DM can change: closed, it has blocksSight true and cover TOTAL, open it has neither. A secret door has a hidden flag: players see and are treated as meeting an ordinary wall until the DM reveals it, and the DM sees it as a door. Changing a door raises the scene's lightVersion and its mapVersion (RS-ENV-008, 13.9) | map\_cell.state (a small enum) and map\_cell.hidden. The player view is built from the player's own visible set (13.9) |

### 13.5 Reach, opportunity attacks and flanking

The opportunity attack itself is RS-ATK-008 in Part B, and it already requires that the attacker can see the mover, and excludes Disengage, teleporting and forced movement. This section supplies the missing geometry: which cells a creature threatens and when a step leaves that area. It also holds the optional flanking rule.

| ID | Rule | Engine |
| --- | --- | --- |
| RS-ENV-029 | Reach is the distance at which a creature can make a melee attack. Without other data it is 5 feet. A weapon with the reach property, or a monster entry, gives the stated value. Reach is measured from the footprint of the attacker to the footprint of the target with the campaign distance metric (the function that E-122 already names). A creature with reach 0, such as a swarm, threatens only its own space | reachOf(creature) reads the equipped weapon and the stat block. distanceFt(footprintA, footprintB) comes from engine.grid with the campaign metric |
| RS-ENV-030 | The threatened area of a creature is the set of cells within its reach. A creature threatens an area only while it could make an opportunity attack: it is not incapacitated, it has its reaction available, and it carries or has a melee attack. Disarmed, restrained and similar states are decided by their own rules | threatenedBy(creature, scene) returns a predicate on cells. It is recomputed on read and never stored (RS-ENV-008) |
| RS-ENV-031 | The engine checks every step of a move, not only the start and the end. A step from cell A to cell B provokes an opportunity attack from a hostile creature H when the mover is within H's reach at A, is not within it at B, moves by its own movement, and H can see the mover (RS-ENV-016 and C-H9). The check uses the sight state at the moment of the step. A mover that H cannot see provokes nothing from H, and a mover that becomes visible later is not retroactively attacked | One check per step in ON\_MOVE\_STEP, which already exists in the reaction window list (E-68). The path comes from the pathfinder or from the DM's drag, and is walked step by step in either case |
| RS-ENV-032 | A mover that stays inside a reach of 10 feet while changing cells, for example from 10 feet to 5 feet, does not leave it and does not provoke. The reach check never uses the straight-line distance, and always uses the campaign metric, so a diagonal step costs what the table's metric says | The same distance function as RS-ENV-029 for both cells of the step |
| RS-ENV-033 | When the check finds one or more creatures that can attack, the reaction window offers each of them the opportunity attack as an option, in the order of the window. The attack is a normal melee attack, and a monster's opportunity attack is one melee attack and never Multiattack or a breath weapon (E-133). An earlier option can remove the trigger, for example an opportunity attack that kills the mover | The existing window machinery. Part H adds the trigger source and no new option kind |
| RS-ENV-034 | Flanking is an optional rule (C-H8) and is off by default. When the setting flanking is ADVANTAGE, a melee attack roll has advantage if the attacker and an ally are on opposite sides of the target, in the sense that a straight line between their cells passes through the target's space, and neither creature is incapacitated. The attacker must be able to see the target (RS-ENV-016), and the ally must be able to attack it. A Large or larger creature flanks if at least one cell of its space qualifies. It applies to melee attacks only | A modifier source flanking, computed at the roll. Available from version 1.5, when the grid gives positions |

### 13.6 Falling

The SRD rule is already in RS-MOV-007: 1d6 bludgeoning damage per 10 feet fallen, to a maximum of 20d6, and the creature lands prone unless it avoids taking damage. Part H makes a fall an event the engine tracks, so that a reaction such as Feather Fall can be cast in time (E-84), and it separates two questions that the rule mixes: how far the creature fell, and what that costs. The first question is answered through a provider, so that version 2 can calculate it from elevation without changing anything else.

| ID | Rule | Engine |
| --- | --- | --- |
| RS-ENV-035 | A creature falls when it is flying and cannot stay aloft (RS-MOV-007), when it moves or is moved over a drop, a pit or a hole with nothing to stand on, when the floor under it gives way, or when the DM says so. A creature that hovers, or that magic holds up, does not fall | In version 1.5 a fall is started by the DM with a button, by a hazard (RS-ENV-042), or by the loss of flight. In version 2 the loss of footing is found from the elevation of the cells |
| RS-ENV-036 | A fall is a record with its own id: the creature, the starting cell, the distance in feet, the state and the cause. The states are PENDING, DESCENDING, LANDED and CANCELLED. A fall in the state LANDED or CANCELLED is final. Resolving the same fall twice changes nothing, so a retry, a replay or a double click cannot hurt the creature twice | Table fall\_event(id uuid, combatant, distance\_ft, remaining\_ft, state, cause). The id is the idempotency key of the resolve step (13.9) |
| RS-ENV-037 | The distance of a fall is the whole number of feet that the provider returns, rounded down to a multiple of 10. A fall of 9 feet is a fall of 0 feet. Rounding happens once, by the caller of the provider, and nowhere else | FallDistanceProvider.distanceFt(creature, scene) returns an integer. The caller applies the rounding, so that an adapter cannot forget it (13.9) |
| RS-ENV-038 | Damage is one d6 for every full 10 feet, to a maximum of 20 dice, so 200 feet or more deals 20d6. The damage is bludgeoning and follows the normal rules of resistance and vulnerability. The DM can override the number of dice, including none, for a fall into water, onto a soft surface or onto a creature, and the override is recorded on the fall (C-H12). The damage is applied when the creature lands | A dice expression from the dice engine, kept in the fall record and rolled at landing. The override is a nullable column on the fall |
| RS-ENV-039 | The creature lands prone unless it avoids taking damage from the fall (C-H11). It avoids damage when the distance is under 10 feet, when the DM override is none, when an effect says it takes no fall damage (Feather Fall), or when it is immune to the damage. Resistance does not avoid damage. A creature that lands prone does so after the damage is applied | The prone condition of Part B, applied after the damage step. A test covers each of the four ways to avoid damage |
| RS-ENV-040 | The ON\_FALL window opens when the fall begins, before any damage, and offers every option that can change the outcome. Feather Fall is a reaction spell, with the range in its own data, and can always be cast in this window, so a creature never loses the chance because the fall is resolved in one step. The DM can cancel the fall in this window, for example when the creature catches a ledge. Cancelling leaves the creature where it is. ON\_FALL joins the point list of E-68 in version 1.5 (E-84) | A point ON\_FALL with the facts creature, distance and cause. It uses the existing window of RS-REA, including nesting and resume by replay |
| RS-ENV-041 | A fall is not instant when it is long. A creature descends the number of feet in the setting fallDescentPerRound as soon as the fall begins and again at the end of each of its own turns while it is still falling, 500 by default (C-H10). A fall that is no longer than that lands at once. A longer one drops that many feet at once, stays DESCENDING, loses that many more feet from remaining\_ft when each of the creature's turns ends, and the ON\_FALL window is offered again at the end of each of those turns, so that a spell can be cast during the fall. With the setting at 0 every fall is instant | fall\_event.remaining\_ft, advanced when the creature's turn ends (the moment at which ON\_TURN\_END is raised, PB-G2). The 500 is a data constant |
| RS-ENV-042 | A hazard can start a fall, and can add damage to it. A spiked pit adds its piercing damage to the fall damage, with its own saving throw if the data has one (RS-ENV-051). The two are separate effects of the same trigger, so the fall damage and the hazard damage are rolled and shown separately | The hazard data holds an effect list. One effect is start\_fall with a distance, so a pit does not need its own falling code |
| RS-ENV-043 | Flight and levitation. In versions 1 and 1.5 Fly and Levitate stay at tier 0 (Part E): the DM decides when the effect ends and starts the fall. In version 2, with elevation, the engine knows the creature's altitude, so the end of the effect, a knock prone, speed 0 or a forced move that leaves the creature in the air starts the fall by itself, and Fly and Levitate can be resolved | Altitude is not stored before version 2. The seam is the same fall event, started by a different caller |

**Fall distance in version 2 (the owner's change).** The provider is the only place that knows where the distance comes from. In version 1 and version 1.5 the adapter is DmEnteredFallDistance: the DM types the distance when the fall starts, and the engine does the rest. In version 2 the adapter becomes ElevationFallDistance. Each cell then has a floor height in feet (floor\_ft) and each creature has an altitude in feet (altitude\_ft). The distance is the creature's altitude minus the floor height of the cell it lands in, which is the cell under it when the fall starts, unless the DM moves the landing. A creature whose space covers several cells lands on the highest floor under its footprint. The DM can still type a distance or override the damage, so that water and soft landings work in both versions. No rule above changes between the two versions, because nothing above reads a height. The columns floor\_ft and altitude\_ft are added by the version 2 migration, not before (E-143).

### 13.7 Hazards

A hazard is content, not engine code. The engine ships the pieces a hazard is made from: zones, effects, the reaction window, the clock, the initiative order and the fall event. A trap, a poison or a pit is a data record that combines them. This keeps the engine small and lets the DM add hazards later without a release. Numbers such as saving throw DCs, damage and detection DCs live in the hazard data and are not repeated here.

| ID | Rule | Engine |
| --- | --- | --- |
| RS-ENV-044 | A hazard definition has: a name, an area (a zone shape or a single cell or object), a trigger, how it is detected, how it is disabled, a list of effects from Part E, a visibility (hidden until detected or always visible), and whether it resets. The trigger is one of STEP\_ON, ENTER\_AREA, START\_TURN\_IN\_AREA, ACTION (a creature does something, such as opening a chest) and DM (the DM triggers it) | A hazard\_definition record in the content pack, validated at load like a monster definition (E-116). Effects reuse the catalog of Part E, including start\_fall (RS-ENV-042) |
| RS-ENV-045 | Breath. A creature can hold its breath for 1 minute plus its Constitution modifier in minutes, with a minimum of 30 seconds. When it runs out of breath or is choking, it can survive a number of rounds equal to its Constitution modifier, with a minimum of 1 round. At the start of its next turn after that it drops to 0 hit points and is dying, and it cannot regain hit points or be stabilized until it can breathe again | A breath record on the combatant: NORMAL, HOLDING (with the clock time it started) or CHOKING (with the rounds left). The clock moves HOLDING to CHOKING, and START\_OF\_OWN\_TURN (E-123) counts the rounds. The DM ends it by marking that the creature can breathe |
| RS-ENV-046 | A creature that is dying from suffocation has a marker that blocks healing and stabilizing until the marker is cleared by breathing. Death saving throws are made as usual while it is set | A flag suffocating on the dying state of Part B. Cleared by the same DM action as RS-ENV-045 |
| RS-ENV-047 | Underwater combat. A creature with no swimming speed has disadvantage on a melee weapon attack unless the weapon is a dagger, a javelin, a shortsword, a spear or a trident. A ranged weapon attack automatically misses a target beyond the weapon's normal range, and within it has disadvantage unless the weapon is a crossbow, a net or a weapon thrown like a javelin (a spear, a trident or a dart). Creatures and objects that are fully immersed have resistance to fire damage | A flag UNDERWATER on a zone, a cell or a creature, set by the DM in version 1 and by cell data in version 1.5. It adds attack modifiers from Part B and a fire resistance source. Weapon lists come from the SRD and live in item data |
| RS-ENV-048 | A simple trap does its work at once. When it triggers, the engine finds the creatures in its area, asks for the saving throws or rolls the attacks that the data lists, applies the damage and the conditions, and marks the trap as spent or ready to reset. Several effects of one trigger are rolled in the order of the data | Resolved as one action with a log entry. It uses the same save and damage code as a spell, and is idempotent by trap and event id (13.9) |
| RS-ENV-049 | A complex trap acts over several turns. When it triggers, it joins the initiative order as a TRAP entry with the initiative bonus of its data, rolled once, and is not a combatant. On its turn it runs the actions of its data, for example moving a rolling sphere in a straight line. It is removed when its data says it is finished. This is the same idea as the LAIR entry of E-120, with a rolled count instead of a fixed one, and the engine reuses the same entry type. A trap that triggers outside combat starts combat only if the DM says so | An initiative entry of kind TRAP, with a bonus, a script of actions from the data and a state. The order and the ties follow the initiative rules of Part B |
| RS-ENV-050 | Detection and disabling. A creature actively looking makes a Wisdom (Perception) check against the detection DC of the hazard. Passive Perception is compared with the same DC to see whether anyone notices it in passing. A creature can also use Intelligence (Investigation) to work out how a trap works, and a Dexterity check with thieves' tools, or another check named by the data, to disable it. Any character can use Intelligence (Arcana) to detect or disarm a magic trap, with the same DCs. Detect magic shows a magic trap, and dispel magic can disable it with the DC of its data. The data also lists the simple ways to stop a trap, such as wedging a spike under a pressure plate | The checks are ordinary d20 tests (Part A). The DCs and the named checks are data. The engine records detected and disabled as hazard states, and the passive comparison is offered to the DM at the start of a scene or when a creature enters the area |
| RS-ENV-051 | The SRD sample traps ship as seed data: collapsing roof, falling net, fire-breathing statue, the four pits (simple, hidden, locking and spiked), poison darts, poison needle and rolling sphere. Each is read from the SRD text. Their numbers are not repeated in this document | The content pack. A validation test checks each record against the SRD values (T-93) |
| RS-ENV-052 | A poison is a record with a delivery (ingested, inhaled, contact or injury), a saving throw, effects and a duration on the game clock. The sample poisons of the SRD ship as seed data. The DM exposes a creature to a poison, and the engine runs the saves and the effects. An effect that has a delay, such as a poison that acts at midnight, is a timer on the clock. A poison that applies the poisoned condition uses the condition of Part B and its duration | A poison\_definition in the content pack. It is applied as an effect from Part E with a timer. The poisoned condition has its own source tag so that two poisons do not cancel each other |
| RS-ENV-053 | A disease is tracked like madness: one instance per infected creature, with its own record on the clock (13.8). The three sample diseases of the SRD ship as seed data: cackle fever, sewer plague and sight rot | See RS-MAD-001 and RS-MAD-012 |
| RS-ENV-054 | Terrain hazards such as fire, webs, difficult terrain and fog are zones from Part E. They need no new engine. A zone can carry a hazard definition (a trigger START\_TURN\_IN\_AREA, for example) and the obscuring and light flags of RS-ENV-004 and RS-ENV-009 | A zone with hazard\_definition and zone flags |
| RS-ENV-055 | Hidden hazards and hidden information. A hazard that is hidden and not detected is not sent to any player. The player's view is built from the player's own visible set, and a hidden hazard, a secret door or an unseen creature is not in it, not in its data, not in the page the player's browser receives and not in the realtime updates. The DM sees all of it, with a marker | A server-side filter in the view-model builder, with a test that serializes a player's page and looks for hidden ids (T-98) |
| RS-ENV-056 | A trigger that needs a fact the engine does not have is confirmed by the DM. A pressure plate that needs a minimum weight, a chest that opens with the wrong key and a tripwire cut with the wrong tool are examples. A trigger of kind STEP\_ON or ENTER\_AREA with no such condition runs by itself in version 1.5, as soon as a creature's step enters the cell. In version 1 the DM triggers every hazard | The data field requires\_dm\_confirmation. The step trigger is a listener on ON\_MOVE\_STEP |

### 13.8 Afflictions and the madness tracker

The SRD presents madness as an option for campaigns with a horror theme. The owner asked for a tracker inside the simulator, because a simulator that leaves madness to the DM's notes does not make the game easier to run. The tracker is a generic record of an affliction on a creature, with a timer on the game clock, effects that map onto the conditions and modifiers the engine already has, and hooks for cures. Madness is its first and fullest user. Diseases use the same record (RS-MAD-012). It is complete in version 1, because it needs no map and no vision.

| ID | Rule | Engine |
| --- | --- | --- |
| RS-MAD-001 | An affliction is a tracked instance on one creature. Its kind is MADNESS\_SHORT, MADNESS\_LONG, MADNESS\_INDEFINITE or DISEASE. It has a source (for example a spell, a place or a creature), a definition key that names its table result or disease, a state (ACTIVE, SUPPRESSED or ENDED), the clock time it started, the clock time it ends if it ends by time, and the reason it ended | Table affliction (13.9). The effects are read from the definition key, so editing a table row in the content pack changes every later instance and no existing one |
| RS-MAD-002 | The DM inflicts madness with an action that names the creature, the kind and the source. Most sources call for a Wisdom or Charisma saving throw. The save is an ordinary saving throw made through the normal flow, with the DC the DM sets, and the tracker starts only if it fails. The DM can also add an instance without a save. The result is rolled on the table of the kind with a d100 by the dice engine, or the DM picks a row. A roll is recorded and replays unchanged | Action inflict\_affliction(target, kind, source, dc?, ability?, row?). The roll uses the dice engine with a recorded result (Part A) |
| RS-MAD-003 | Short-term madness lasts 1d10 minutes. Long-term madness lasts 1d10 times 10 hours. Indefinite madness lasts until it is cured. The duration is rolled by the dice engine when the instance starts and is stored as an end time on the game clock. The DM can enter a duration instead | ends\_at\_clock is a count of seconds on the clock of Part C. For indefinite instances and for diseases it is empty |
| RS-MAD-004 | The short-term madness table is the first table below, with ten results. It is rolled with a d100 | Data in the content pack |
| RS-MAD-005 | The long-term madness table is the second table below, with twelve results. It is rolled with a d100 | Data in the content pack |
| RS-MAD-006 | The indefinite madness table is the third table below, with twelve flaws. A flaw has no engine effect. It is written on the character's sheet, shown to the player and the DM, and lasts until it is cured | Data in the content pack. The flaw text is kept as a character flaw record linked to the instance |
| RS-MAD-007 | Expiry runs on the clock. When the game clock moves, every ACTIVE or SUPPRESSED instance whose end time is not after the new time is ended with the reason EXPIRED, and the end time recorded is the instance's own end time and not the time of the advance, so a DM who skips an hour sees the madness end at the right moment in the log. Ending an instance twice does nothing. A suppressed instance keeps counting (RS-MAD-010) | An idempotent step ExpireAfflictions(clockTime), run by the clock advance of Part C and keyed by instance id and state, so a replay or a retry ends nothing twice (13.9) |
| RS-MAD-008 | The mechanical part of a result is expressed with four kinds of effect that the engine already has or gets in this part. A CONDITION applies a condition of Part B for as long as the instance is ACTIVE. A MODIFIER gives disadvantage with a scope, for example ability checks, or Wisdom and Charisma checks. A TRIGGER runs a step when something happens, for example when the creature takes damage. A FLAG is text shown to the DM and the player, for the parts of a result that only the DM can play, such as a compulsion or a delusion. The engine enforces conditions, modifiers and triggers and displays flags | Effect kinds on the affliction definition. Conditions and modifiers carry the instance id as their source tag, so ending the instance removes exactly its effects and cannot remove another source's |
| RS-MAD-009 | The first short-term result, paralysis, ends if the creature takes any damage. The instance ends with the reason DAMAGED, and the condition goes with it | A TRIGGER on damage taken that ends the instance. It runs at the AFTER\_DAMAGE\_TAKEN point |
| RS-MAD-010 | Cure and suppression. Calm emotions suppresses the effects of madness, and the instance becomes SUPPRESSED while the spell lasts. Its effects stop, and its timer keeps running. Lesser restoration ends short-term and long-term madness. Greater restoration, or magic more powerful than that, is needed to end indefinite madness. Remove curse and dispel evil can also work, depending on the source, and the engine offers them to the DM as choices and does not decide. A long rest does not end madness. The cure is a spell effect of Part E that names the kinds it ends or suppresses | Effect types ends\_affliction(kinds) and suppress\_affliction(kinds). The DM-judged cures use a confirm step |
| RS-MAD-011 | A creature can carry several instances, and each is its own record. Conditions do not stack, so two instances that both give the same condition leave it applied until the last of them ends. A new result does not replace an older one. The DM can end any instance at any time. The player sees the afflictions of their own character, with the time left. The DM sees all of them in one panel. A disease that has not yet shown symptoms is visible to the DM only | A DM panel and a sheet card read from the same query. Visibility is a field of the definition and of the state (13.9) |
| RS-MAD-012 | A disease is an affliction of kind DISEASE. It has a time before symptoms, a saving throw the DM or the long rest triggers, a counter of progress, and a result. The SRD sample diseases are seed data. The time before symptoms is a timer on the clock. The saving throw at the end of each long rest is offered by the long rest of Part C for every creature with such a disease. A disease can start another affliction: cackle fever gives indefinite madness after three failed saving throws, and the new instance records which disease caused it. The numbers of each disease are in its data, not in this document | affliction.progress (an integer counter) and affliction.caused\_by (a nullable reference to another instance). The long rest hook is the one Part C already has for end-of-rest effects |
| RS-MAD-013 | The tracker records and enforces. It does not play the character. A result such as a compulsion, a delusion or an urge to eat something strange is shown as an instruction to the DM and the player. The engine never takes a decision about behaviour on its own, except the conditions, modifiers and triggers above | Flags are display text only |

**Short-term madness (RS-MAD-004).** Roll d100, duration 1d10 minutes.

| d100 | Effect (paraphrased from the SRD) | Engine effect |
| --- | --- | --- |
| 01 to 20 | The creature retreats into its mind and is paralyzed. The effect ends if the creature takes any damage | CONDITION paralyzed. TRIGGER on damage taken ends the instance (RS-MAD-009) |
| 21 to 30 | The creature is incapacitated and spends the duration screaming, laughing or weeping | CONDITION incapacitated |
| 31 to 40 | The creature is frightened and must use its action and its movement each round to flee from the source of the fear | CONDITION frightened with the source. FLAG must flee |
| 41 to 50 | The creature babbles and cannot speak normally or cast spells | FLAG cannot speak normally. Casting is removed from its action options |
| 51 to 60 | The creature must use its action each round to attack the nearest creature | FLAG must attack the nearest creature. The attack option is offered first |
| 61 to 70 | The creature has vivid hallucinations and has disadvantage on ability checks | MODIFIER disadvantage on ability checks |
| 71 to 75 | The creature does whatever anyone tells it to do, unless the order is clearly self-destructive | FLAG obeys orders |
| 76 to 80 | The creature has an overpowering urge to eat something strange, such as dirt, slime or offal | FLAG urge to eat |
| 81 to 90 | The creature is stunned | CONDITION stunned |
| 91 to 100 | The creature falls unconscious | CONDITION unconscious, with the ordinary ways to wake it from Part B |

**Long-term madness (RS-MAD-005).** Roll d100, duration 1d10 times 10 hours.

| d100 | Effect (paraphrased from the SRD) | Engine effect |
| --- | --- | --- |
| 01 to 10 | The creature feels compelled to repeat one activity again and again, such as washing hands, touching things, praying or counting coins | FLAG compulsion, with the activity written by the DM |
| 11 to 20 | The creature has vivid hallucinations and has disadvantage on ability checks | MODIFIER disadvantage on ability checks |
| 21 to 30 | The creature suffers extreme paranoia and has disadvantage on Wisdom and Charisma checks | MODIFIER disadvantage on Wisdom checks and Charisma checks |
| 31 to 40 | The creature regards something, usually the source of the madness, with intense revulsion, as if under the antipathy effect of the antipathy and sympathy spell | FLAG revulsion, with the object written by the DM |
| 41 to 45 | The creature has a powerful delusion. The DM chooses a potion, and the creature imagines that it is under its effects | FLAG delusion, with the potion chosen by the DM |
| 46 to 55 | The creature becomes attached to a lucky charm, a person or an object, and has disadvantage on attack rolls, ability checks and saving throws while it is more than 30 feet from it | MODIFIER disadvantage on those rolls while the distance to the named anchor is more than 30 feet. In version 1 the DM says when it applies. In version 1.5 the engine measures the distance |
| 56 to 65 | The creature is blinded (25 percent) or deafened (75 percent) | CONDITION blinded or deafened, chosen by a d100 rolled at the start of the instance and recorded: 25 or less is blinded |
| 66 to 75 | The creature has uncontrollable tremors or tics, with disadvantage on attack rolls, ability checks and saving throws that involve Strength or Dexterity | MODIFIER disadvantage on Strength and Dexterity checks, on Strength and Dexterity saving throws, and on attack rolls made with a Strength or Dexterity modifier |
| 76 to 85 | The creature has partial amnesia: it knows who it is and keeps its racial traits and class features, but does not recognize other people or remember anything from before the madness | FLAG partial amnesia |
| 86 to 90 | Whenever the creature takes damage, it must succeed on a DC 15 Wisdom saving throw or be affected as if it failed a saving throw against the confusion spell, for 1 minute | TRIGGER on damage taken: a saving throw with that DC, then the confusion effect for 1 minute if the spell exists in the pack, and the DM's table otherwise |
| 91 to 95 | The creature loses the ability to speak | FLAG cannot speak. Casting with a verbal component is removed from its options |
| 96 to 100 | The creature falls unconscious, and no amount of jostling or damage wakes it | CONDITION unconscious with waking off |

**Indefinite madness (RS-MAD-006).** Roll d100. The flaw lasts until it is cured, and it is a flaw, not a mechanical effect.

| d100 | Flaw (paraphrased from the SRD) |
| --- | --- |
| 01 to 15 | Being drunk keeps the character sane |
| 16 to 25 | The character keeps whatever it finds |
| 26 to 30 | The character tries to become more like someone it knows, in clothes, mannerisms and name |
| 31 to 35 | The character bends the truth, exaggerates or lies to be interesting |
| 36 to 45 | Achieving its goal is the only thing of interest, and the character ignores everything else |
| 46 to 50 | The character finds it hard to care about anything that happens around it |
| 51 to 55 | The character does not like being judged by others |
| 56 to 70 | The character thinks it is the smartest, wisest, strongest, fastest and most beautiful person it knows |
| 71 to 80 | The character is convinced that powerful enemies hunt it and that their agents are everywhere |
| 81 to 85 | There is only one person the character trusts, and only the character can see this special friend |
| 86 to 95 | The character cannot take anything seriously, and the more serious the situation, the funnier it finds it |
| 96 to 100 | The character has found that it really likes killing people |

### 13.9 Seams for later versions

This section records the design choices that keep version 1.5 and version 2 from rewriting version 1. It was written with the owner's request in mind that future engine work is considered while Part H is designed. The load is small: one DM and up to six players at one table, up to about 60 combatants, a grid at the size cap of the Architecture, and a few actions a minute. That is the lowest load class, so the design goal is clarity and exactness. It is not scale, and nothing here needs a cache server, a queue or a second process. The levers are used in this order: choose an exact algorithm with early exit, memoize by version number, prune by radius, and only then consider anything cleverer.

**Ports.** Each port is a Java interface in the pure engine. Rules call the port. Adapters implement it, and a later version swaps the adapter.

| Port | Contract | Adapters | Why it is a seam |
| --- | --- | --- | --- |
| LightModel | lightAt(cell, scene) returns LightResult(level, sunlit) | Version 1: SceneAmbientLight, which reads the scene ambient and the creature override. Version 1.5: SourcesAndZonesLight, which adds emitters and zone flags | Sources, zones and fog of war all change what light is, and no rule reads anything else (RS-ENV-016) |
| SightResolver | stateOf(viewer, target, scene, map) returns SightState | One implementation, over LightModel, senses and LineOfSight. In version 1 with DM\_ONLY it returns the DM's flags | The single place that answers "can it see" (E-21 replaced) |
| LineOfSight, CoverCalculator, lineOfEffect | Pure functions on the lattice (RS-ENV-019 to RS-ENV-026) | The Java implementation is the reference. A faster native one could be added later and must pass a differential test against it | Exact integer geometry with no framework. The C++ pathfinder stays free of D&D rules, as its specification requires |
| FallDistanceProvider | distanceFt(creature, scene) returns an integer number of feet, or nothing if the DM must enter it | Version 1 and 1.5: DmEnteredFallDistance. Version 2: ElevationFallDistance | The owner's change: the distance becomes automatic with elevation, and no fall rule changes |
| HazardTrigger | A listener on ON\_MOVE\_STEP, START\_OF\_OWN\_TURN and the DM's action, which finds the hazards a creature triggers | DM only in version 1, step listener in version 1.5 | A hazard is data, and triggering it is the only code |
| VisionModel (Architecture 4.11) | visibleCells(viewer, scene, map) | FullVision now, RadiusVision and SrdVision in version 2. SrdVision is built from the ports above | Fog of war (FR-VIS-002) reuses the same rules and adds no new ones |

**Phasing.** The table shows what each version adds, so that every schema and every interface is chosen once.

| Capability | Version 1 | Version 1.5 | Version 2 |
| --- | --- | --- | --- |
| Light | Scene ambient and DM override | Emitters, zones, per-cell light, setting lightingMode | Feeds fog of war |
| Sight state | DM flags | Computed (RS-ENV-015) | Used by SrdVision |
| Line of sight and cover | DM names the cover | Computed from the map (FR-GRD-009) | Three-dimensional |
| Reach and opportunity attacks | Reaction window with the DM's confirmation of reach | Checked per step (RS-ENV-031) | Three-dimensional distance |
| Flanking | Not offered | Setting, off by default | Same |
| Falling | Damage calculator with a DM-entered distance | Fall event, ON\_FALL window, DM-entered distance | Distance from elevation |
| Hazards | Seed data, triggered by the DM | Step triggers, TRAP entries | Same |
| Madness and diseases | Complete | Lucky charm distance measured | Same |

**Budgets.** These are design targets and not measurements. They are checked by a benchmark test (T-99) when the code exists, and a missed target is a defect to fix before the next version.

| Operation | Target |
| --- | --- |
| Line of sight between two creatures on a 200 by 200 map | 0.2 ms |
| Cover between two creatures | 1 ms |
| Sight states of 30 creatures against each other (900 ordered pairs), cold | 100 ms |
| The same, repeated with no change to the map or the light | 5 ms |
| Visible set of one viewer, version 2, with a limited radius | 20 ms |
| Visible set of one viewer across the whole 200 by 200 map, version 2 | 60 ms |

Version 2 does not need shadow casting. A viewer's visible set is found by applying the pairwise rule of RS-ENV-021 to every cell inside the viewer's largest range, and a range of 24 cells is about 1,800 cells. That needs no second algorithm, gives exactly the answer the pairwise rule gives, and keeps one definition of line of sight. If the benchmark misses a budget, shadow casting with integer arithmetic is the next lever, and it must give the same sets (the differential test of T-83).

**Determinism and replay.** Every function in the pure engine takes its inputs as arguments and returns a value. None reads the clock, a random source or a hash order. Ties are broken by fixed orders (corners clockwise, squares in row order, creatures by id). Dice come from the dice engine and are recorded, so a replay produces the same fall damage, the same madness result and the same duration. Part H adds no floating point to the engine.

**Idempotency.** A fall is resolved once, keyed by its id and state (RS-ENV-036). A trap is triggered once for each trigger event, with a unique key of hazard instance and event id in the table hazard\_trigger\_log, so a double submit or a replayed request cannot fire it twice. An affliction ends once, keyed by id and state (RS-MAD-007). The creation of an affliction carries the action id that every state-changing action carries, so the same click cannot add two.

**Hidden information.** The browser receives only what the viewer may see. The page and the realtime updates for a player are built from a server-side projection that is a function of the player's visible set and role. Hidden hazards, secret doors, unseen creatures and every DM-only field (detection and disabling DCs, monster hit points not yet revealed, undetected diseases) are left out in the query and in the view model, and are not hidden afterwards by styling. In version 1 the visible set is the set the DM allows. Every DM-only action, such as inflicting madness, triggering a hazard or overriding a sight state, checks the DM role in the service and is denied by default.

**Schema sketch.** The Architecture owns the final schema and Flyway migrations. This is what Part H needs, with the constraints that belong in the database. Everything is additive, so no migration is destructive.

| Table or column | Content and constraints | Version |
| --- | --- | --- |
| scene.ambient\_light, scene.sunlit, scene.light\_version, scene.map\_version | ambient\_light is text NOT NULL DEFAULT 'BRIGHT' with a CHECK on BRIGHT, DIM and DARKNESS. sunlit is boolean NOT NULL DEFAULT false. The two versions are integers NOT NULL DEFAULT 0 | 1 |
| combatant.light\_override | Nullable text with the same CHECK. Null means computed | 1 |
| combatant.breath\_state and its columns | A state, not flags: breath\_state is NORMAL, HOLDING or CHOKING, with breath\_started\_at\_clock set only for HOLDING and breath\_rounds\_left set only for CHOKING, enforced by one CHECK | 1 |
| map\_cell | Primary key (map\_id, x, y). blocks\_sight boolean NOT NULL DEFAULT false. cover text NOT NULL DEFAULT 'NONE' with a CHECK on four values. door\_state text, nullable, with a CHECK. hidden boolean NOT NULL DEFAULT false | 1.5 |
| zone\_flag | One row per flag, with a primary key (zone\_id, flag) and a CHECK on the flag names (MAGICAL\_DARKNESS, OBSCURE\_LIGHT, OBSCURE\_HEAVY, LIGHT\_BRIGHT, LIGHT\_DIM, UNDERWATER) | 1.5 |
| fall\_event | id uuid, combatant, distance\_ft and remaining\_ft with CHECK of at least 0, state with a CHECK, cause, damage\_dice\_override nullable | 1.5 |
| hazard\_instance and hazard\_trigger\_log | Instance: definition key, scene, cell or zone, state with a CHECK (HIDDEN, DETECTED, DISABLED, TRIGGERED, SPENT). Log: unique (hazard\_instance\_id, trigger\_event\_id) | 1 |
| affliction | id uuid (version 7), combatant (foreign key, ON DELETE CASCADE), kind and state with CHECKs, definition\_key and source NOT NULL, started\_at\_clock NOT NULL, ends\_at\_clock nullable, ended\_at\_clock and end\_reason nullable, progress integer NOT NULL DEFAULT 0 with CHECK of at least 0, caused\_by nullable (self reference, ON DELETE SET NULL). CHECKs: ends\_at\_clock is required for the two timed madness kinds and is later than started\_at\_clock, and an ENDED row has an end time and a reason. A partial index on (state, ends\_at\_clock) for the rows that are not ended | 1 |
| map\_cell.floor\_ft and combatant.altitude\_ft | Integer NOT NULL DEFAULT 0. floor\_ft has a CHECK that it is a multiple of 5. Adding a NOT NULL column with a constant default does not rewrite the table in PostgreSQL 16, so keeping these columns out until version 2 costs nothing later | 2 |

**Module boundaries.** Part H adds the packages engine.light, engine.vision, engine.fall, engine.hazard and engine.affliction, and puts the geometry in engine.grid.geometry. They depend on the engine core (dice, clock, ids) and on engine.grid, never on Spring or on the web and persistence layers. A boundary test checks the imports (T-100). Adapters, repositories and controllers live outside, in the application layer, and implement the ports. Every state change writes an entry to the encounter event log, which is what the replay and the table's history read.

### 13.10 Amendments to earlier parts

Part H needs small changes to Part B, Part C, Part E, Part F and Part G. The rows that only point to Part H were updated in the draft, so that no reader was sent to a part that was not yet written. The other changes were made in the revision in which the owner accepted Part H, as for Part G.

| ID | Where | Change | Applied |
| --- | --- | --- | --- |
| PB-H1 | Part B, E-21 (what counts as can see) | The default stays until version 1.5. From version 1.5, with lightingMode AUTOMATIC, can see is the sight state of RS-ENV-015, and the rows that mention the vision work now point to 13.3 | Applied |
| PB-H2 | Part B, RS-MOV-007 | The fall sentence points to 13.6. Its damage formula is unchanged and 13.6 only adds the fall event, the provider and the rounding | Applied |
| PB-H3 | Part B, reaction window point list (E-68, RS-REA) | ON\_FALL is added to the canonical points in version 1.5, as E-84 already says. No other point changes. RS-ENV-031 uses ON\_MOVE\_STEP as it is | Applied |
| PB-H4 | Part B, the cover rule and the hiding rule | In version 1.5 cover is the computed degree of RS-ENV-024, and hiding reads obscurement, cover and the sight state (RS-ENV-016). The DM can still override both | Applied |
| PB-H5 | Part B, dying and death saving throws | A suffocating creature has the marker of RS-ENV-046. It blocks healing and stabilizing until it can breathe | Applied |
| PB-H6 | Part C, the game clock and the long rest | The clock advance runs ExpireAfflictions (RS-MAD-007). The end of a long rest offers the saving throws of diseases (RS-MAD-012). Neither adds a new rest rule | Applied |
| PB-H7 | Part E, zones and effects | New zone flags (RS-ENV-004, RS-ENV-009, RS-ENV-047). New effect types start\_fall, ends\_affliction and suppress\_affliction. Area code asks lineOfEffect (RS-ENV-026). Targeting that needs a target the caster can see calls canSee. Darkness and Daylight use the overlap rule through the spell data. In version 2 Fly and Levitate are resolved by the engine (RS-ENV-043) | Applied |
| PB-H8 | Part F, items | An item or a spell effect that gives light carries an emits record (RS-ENV-003). The torch-like item rows no longer say that the light is only a DM note once version 1.5 exists. The rows that point to Part H for the map point to 13.4 and 13.9 | Applied |
| PB-H9 | Part G, RS-MON-028 and E-132 | Sunlight Sensitivity reads the sunlight flag of RS-ENV-007 when lightingMode is AUTOMATIC. The DM flag inSunlight stays, as an override and as the only source in version 1. The senses on stat blocks are used by RS-ENV-010 | Applied |
| PB-H10 | Ruleset roadmap and header | The Part H row is marked as accepted, and the Ruleset becomes 1.0 when the owner accepts Part H | Applied |

### 13.11 Edge cases

| ID | Case | Resolution | Test |
| --- | --- | --- | --- |
| RS-ENV-R1 | A creature standing in darkness looks at a creature standing in torchlight | The viewer sees it, because the target's cell is what is checked (RS-ENV-015). The lit creature cannot see the viewer if the viewer has no darkvision, so the viewer's attack has advantage and the lit creature's attack has disadvantage. This is the light asymmetry the rules intend | T-81 |
| RS-ENV-R2 | Darkvision of 60 feet and a target in darkness at 60 feet and at 65 feet | At 60 feet the cell counts as dim light and the target is SEEN\_OBSCURED. At 65 feet it is dark and the target is NOT\_SEEN. The range is measured with the campaign metric | T-81 |
| RS-ENV-R3 | A creature with darkvision stands inside magical darkness | It sees nothing in the zone. Darkvision does not apply (RS-ENV-004) | T-80, T-81 |
| RS-ENV-R4 | Truesight against fog and against an invisible creature in magical darkness | The invisible creature in magical darkness is SEEN. A creature in dense fog is not, because truesight does not see through fog (RS-ENV-013) | T-81 |
| RS-ENV-R5 | A blinded creature with blindsight of 10 feet | It perceives targets within 10 feet and nothing beyond. Blindness does not turn the sense off (RS-ENV-015 step 2) | T-81 |
| RS-ENV-R6 | A bank of dense fog lies between the viewer and a target that stands in a clear cell | The fog blocks the line as a wall does, so the target is NOT\_SEEN. Thin fog does not block lines, and lightly obscures a target that stands in it | T-81 |
| RS-ENV-R7 | A viewer in a lit cell searches for a hidden creature in a dim cell | Passive Perception is reduced by 5 and an active check has disadvantage, because the cell of the thing perceived counts (C-H3) | T-82 |
| RS-ENV-R8 | A torch is carried, dropped, doused or the door closes on it | The emitter follows its anchor. A dropped torch becomes an item that emits at its cell. Dousing or a closed door raises the light version, and nothing keeps the old light (RS-ENV-008) | T-80 |
| RS-ENV-R9 | A line runs exactly along the seam between two wall cells | It is blocked (RS-ENV-020). A line that runs along the edge of a single wall cell is not | T-84 |
| RS-ENV-R10 | A line passes exactly through the corner shared by two wall cells that touch diagonally | It is blocked. A line that touches the corner of a single wall cell only is not | T-84 |
| RS-ENV-R11 | A one-cell pillar stands directly between an attacker and a target three cells behind it | Two of the four lines from the best corner are blocked, so the target has half cover (WE-H3). With CENTERS the centre line is blocked and the cover is total | T-85 |
| RS-ENV-R12 | The target is a Large creature that is partly behind a wall | The cover is the least over the four squares it occupies (RS-ENV-022) | T-85 |
| RS-ENV-R13 | An ally stands in the line of a ranged attack | It gives half cover, friend or foe. The attacker's own space and the target's space never count | T-85 |
| RS-ENV-R14 | A target is behind a creature (half cover) and a portcullis (three-quarters cover) | Three-quarters cover. The most protective degree applies and degrees are not added | T-85 |
| RS-ENV-R15 | The setting coverMethod changes during a scene | The new method applies to the next attack. A roll that was already made keeps its recorded cover | T-85 |
| RS-ENV-R16 | An area effect has its origin behind a wall corner | lineOfEffect is false for a cell if every corner line to it is blocked. A creature in such a cell is not in the area (RS-ENV-026) | T-85 |
| RS-ENV-R17 | A portcullis, and a bank of fog | The portcullis gives three-quarters cover and does not block sight. Fog blocks sight (dense) or obscures (thin) and gives no cover. The two values are independent (RS-ENV-025) | T-86 |
| RS-ENV-R18 | A secret door | The DM sees a door and a player sees a wall until the DM reveals it. The door state is not in the player's data (RS-ENV-028) | T-86, T-98 |
| RS-ENV-R19 | A mover leaves the reach of one creature and enters the reach of another | Leaving provokes, entering does not. Each creature is checked on its own at each step | T-87 |
| RS-ENV-R20 | A mover with a creature of reach 10 next to it moves from 10 feet away to 5 feet away | It stays inside the reach and does not provoke (RS-ENV-032) | T-87 |
| RS-ENV-R21 | An invisible rogue moves away from a fighter, and a creature is pushed away from one | The fighter cannot see the rogue, so there is no opportunity attack. Forced movement and teleporting never provoke (RS-ATK-008) | T-87 |
| RS-ENV-R22 | Falls of 9, 10, 19 and 250 feet | 0 dice and no damage and no prone, 1d6, 1d6, and 20d6 (RS-ENV-037 and RS-ENV-038) | T-89 |
| RS-ENV-R23 | The DM overrides the dice to none for a fall into deep water | No damage and the creature does not land prone (RS-ENV-039) | T-89 |
| RS-ENV-R24 | Two creatures fall from the same pit and one casts Feather Fall for both | There are two fall records, each with its own window. One cast can name both creatures, if the spell data allows that many, and each fall resolves with no damage | T-90 |
| RS-ENV-R25 | A creature catches a ledge | The DM cancels the fall in the ON\_FALL window. The creature stays, takes no damage and is not prone | T-90 |
| RS-ENV-R26 | A fall of 1,500 feet with the default setting | The fall stays DESCENDING after the first 500 feet, which drop at once, for two more turn ends of the creature, loses 500 feet each time, and the window is offered again at each turn end. It lands when remaining\_ft reaches 0 | T-90 |
| RS-ENV-R27 | A creature with Constitution 8 holds its breath | The minimum applies: 30 seconds, then 1 round of choking | T-95 |
| RS-ENV-R28 | The DM advances the clock by 3 hours while a character has short-term madness that ends after 4 minutes | The instance ends at its own end time and the log shows that time. The effects are gone at the new time (RS-MAD-007) | T-96 |
| RS-ENV-R29 | Short-term madness gives stunned and a second instance gives stunned too | The condition is applied until the last instance that gives it ends (RS-MAD-011) | T-97 |
| RS-ENV-R30 | Calm emotions ends while a madness instance is still running | The instance returns to ACTIVE, with the time that is left. If it ran out during the suppression it ended as EXPIRED and never resumed | T-97 |
| RS-ENV-R31 | Cackle fever causes indefinite madness, and the fever is then cured | The madness is its own instance and stays until it is cured (RS-MAD-012) | T-97 |
| RS-ENV-R32 | One player finds a hidden trap | The hazard becomes DETECTED for every player at the table. The engine does not track what each character knows in versions 1 and 1.5 | T-98 |

### 13.12 Engine decisions

The twenty decisions below write the owner's accepted direction of 2026-11-19 as engine rules. The owner accepted them with Part H on 2026-11-19, and they are recorded in the Decision Log as DEC-105.

| ID | Decision | Rules |
| --- | --- | --- |
| E-134 | Light is derived and never stored. A LightModel port gives the light level and the sunlight flag of a cell. Version 1 reads the scene ambient light and the DM's override per creature. Version 1.5 adds emitters and zones | RS-ENV-001 to RS-ENV-008 |
| E-135 | The light level of a cell is the best level available. Magical darkness is a zone flag, and it blocks darkvision only | RS-ENV-004, RS-ENV-005, RS-ENV-011 |
| E-136 | Dim light lightly obscures. It gives disadvantage on Wisdom (Perception) checks that rely on sight, and reduces passive Perception by 5, and has no other effect. The cell of the thing perceived is the one that counts | RS-ENV-017, C-H2, C-H3 |
| E-137 | What a creature can see is one pure function, SightResolver, that replaces the can-see default of E-21 from version 1.5. Every rule that needs sight reads it and none reads light or senses directly. The DM can override any pair | RS-ENV-015, RS-ENV-016, RS-ENV-018 |
| E-138 | Line of sight and cover are exact integer geometry on a lattice, in the pure package engine.grid.geometry. Corner tracing is the default, the setting coverMethod can switch to centre to centre, and the DM can override any result | RS-ENV-019 to RS-ENV-024, RS-ENV-026, RS-ENV-027 |
| E-139 | A cell has two independent values, blocksSight and cover. Creatures never block sight and give half cover. Dense fog blocks lines of sight and gives no cover | RS-ENV-023, RS-ENV-025 |
| E-140 | Flanking is off by default. The setting flanking turns on the variant advantage rule in version 1.5 | RS-ENV-034 |
| E-141 | The threatened area comes from reach and the campaign distance metric. Every step of a move is checked, and an opportunity attack needs the attacker to see the mover | RS-ENV-029 to RS-ENV-033 |
| E-142 | A fall is an event with its own id. The ON\_FALL window opens when it begins and before damage, so that Feather Fall can always be cast. The DM can cancel the fall in the window | RS-ENV-035, RS-ENV-036, RS-ENV-040 |
| E-143 | The distance of a fall comes through a FallDistanceProvider. Versions 1 and 1.5 use the distance the DM enters. Version 2 calculates it from elevation. The columns floor\_ft and altitude\_ft are added by the version 2 migration. This is the owner's change of 2026-11-19 | RS-ENV-037, RS-ENV-043, 13.6 |
| E-144 | Fall damage rounds the distance down to a multiple of 10 feet and is capped at 20d6. A creature lands prone only if it takes fall damage. The DM can override the dice. A fall descends 500 feet at once and 500 feet at the end of each of the creature's turns by default, with a setting | RS-ENV-038 to RS-ENV-041 |
| E-145 | Hazards are content built from zones, effects, the clock and the initiative order, and not engine code. The DM triggers them in version 1, and step triggers run by themselves in version 1.5 | RS-ENV-044, RS-ENV-048, RS-ENV-051 to RS-ENV-056 |
| E-146 | A complex trap is a TRAP entry in the initiative order with a rolled bonus. It is the same entry type as the LAIR entry of E-120 and it is not a combatant | RS-ENV-049 |
| E-147 | Madness and diseases are one tracked model, the affliction, and it is complete in version 1 | RS-MAD-001, RS-MAD-012 |
| E-148 | A madness result is rolled with the dice engine or chosen by the DM. Its duration is stored as an end time on the game clock. Its mechanical effects are conditions, modifiers and triggers that the engine already has, and the rest is shown as flags | RS-MAD-002 to RS-MAD-009, RS-MAD-013 |
| E-149 | Cures and suppression are Part E effect types. Remove curse and dispel evil are offered to the DM and not decided by the engine. A long rest does not end madness | RS-MAD-010, RS-MAD-011 |
| E-150 | A player's browser receives only a server-side projection of what that player can see. Hidden hazards, secret doors, unseen creatures and every DM-only field are left out at the source | RS-ENV-055, 13.9 |
| E-151 | Performance. Part H uses an exact algorithm with early exit, memoizes by the light and map versions, and adds no cache server. The budgets of 13.9 are tested by a benchmark. Version 2 fog of war applies the pairwise rule within a radius, and shadow casting is used only if a budget is missed | 13.9, T-99 |
| E-152 | Falls, trap triggers, afflictions and their expiry are idempotent, each keyed in the database by a unique constraint or a state check. Dice results are recorded so that a replay gives the same outcome | RS-ENV-036, RS-MAD-007, 13.9 |
| E-153 | The new engine packages are pure Java with no Spring. Adapters in the application layer implement the ports. A boundary test checks the imports | 13.9, T-100 |

#### 13.12.1 New settings

| Setting | Values | Default | Rule |
| --- | --- | --- | --- |
| lightingMode | AUTOMATIC, DM\_ONLY | AUTOMATIC from version 1.5. Version 1 has only DM\_ONLY | RS-ENV-006 |
| coverMethod | CORNERS, CENTERS | CORNERS | RS-ENV-022, RS-ENV-023 |
| flanking | OFF, ADVANTAGE | OFF | RS-ENV-034 |
| fallDescentPerRound | Whole feet, 0 or more. 0 makes every fall instant | 500 | RS-ENV-041 |

Tiers of the four settings (Architecture 4.15). lightingMode and flanking are Structural: lightingMode decides whether the engine or the DM answers every question about sight, and flanking changes every melee roll, so the setup wizard asks them with the other combat style choices when a grid map is used. coverMethod and fallDescentPerRound are Ask on first use: the first asks at the first attack where cover could apply, and the second at the first fall. They appear in requiredSettings for the actions that read them. Where the map or the lighting does not exist yet (version 1), the settings are not asked.

### 13.13 Worked examples

Numbers that come from item, trap or spell data (light radii, DCs, bonuses, armor class) are example values. The other numbers come from the rules above.

| ID | Example | Result |
| --- | --- | --- |
| WE-H1 | A human guard carries a torch with 20 feet of bright light and a further 20 feet of dim light, in a dark corridor. A dwarf with darkvision of 60 feet stands 50 feet from the guard. The guard has no darkvision | The guard stands in bright light, so the dwarf sees it normally (SEEN). The dwarf's cell is 50 feet from the torch, beyond the dim light, so it is darkness, and the guard has no darkvision, so the guard cannot see the dwarf (NOT\_SEEN). The dwarf's attack has advantage and the guard's attack has disadvantage. If the dwarf steps to 35 feet from the torch, its cell is dim light and the guard sees it as SEEN\_OBSCURED. Both attacks are then rolled normally, and only a sight-based Perception check against the dwarf has disadvantage |
| WE-H2 | A dwarf with darkvision of 60 feet and an elf with truesight of 120 feet stand in a sphere of magical darkness with a human cultist. Each is 30 feet from the cultist, who has no special senses | The dwarf cannot see the cultist, because darkvision does not work in magical darkness (RS-ENV-004). The elf sees the cultist, because truesight does (RS-ENV-013). The cultist sees neither of them. The elf's attack has advantage, because the cultist cannot see the elf, and the cultist's attack on the elf has disadvantage. The dwarf and the cultist cannot see each other, so in both directions the advantage and the disadvantage cancel |
| WE-H3 | An archer in cell (0, 0) shoots at a target in cell (4, 0). A one-cell stone pillar with cover TOTAL stands in cell (2, 0). The target has armor class 15 | In lattice units the archer's space spans x 0 to 2 and y 0 to 2, the pillar spans x 4 to 6, and the target spans x 8 to 10. From the archer's corner (2, 2), the lines to the target's corners (8, 2) and (10, 2) run along the top edge of the pillar and are not blocked (RS-ENV-020). The lines to (8, 0) and (10, 0) cross the inside of the pillar and are blocked. Two blocked lines give half cover. The other three corners give two as well, so the cover is half: armor class 17 against this attack, and +2 on the target's Dexterity saving throws against the archer. With coverMethod CENTERS the centre line is blocked and the cover is total |
| WE-H4 | A fighter with reach 5 feet stands next to a goblin in bright light. The goblin walks away in three steps of 5 feet | The first step leaves the fighter's reach. The fighter can see the goblin and has its reaction, so the reaction window offers an opportunity attack at ON\_MOVE\_STEP. The later steps are outside the reach and provoke nothing from the fighter. If the goblin had used Disengage, or the fighter could not see it, nothing would be offered |
| WE-H5 | A character falls 35 feet into a pit. A wizard nearby has Feather Fall prepared and a reaction. A second character falls 5 feet | The DM enters 35. The distance is rounded down to 30, which is 3d6 bludgeoning damage. The ON\_FALL window opens first. If the wizard casts Feather Fall, the creature takes no damage and does not land prone. If not, the 3d6 is rolled at landing, the damage is applied and the creature lands prone. The 5-foot fall rounds to 0: no damage and no prone |
| WE-H6 | Version 2. A creature is pushed off a ledge whose floor is at 40 feet, into a cell whose floor is at 10 feet. In another case it falls into a pool and the DM overrides the dice to none | The provider returns 40 minus 10, which is 30 feet, so the result is 3d6, exactly as in WE-H5, with nobody entering a distance. In the pool case the override gives no damage and the creature does not land prone. No rule of 13.6 changed between the two versions |
| WE-H7 | A character fails the DC 15 Wisdom saving throw against a horror and the DM inflicts short-term madness. The d100 gives 34 and the d10 gives 7. The clock reads 14:00:00. A cleric casts calm emotions at 14:03:00 and its concentration breaks at 14:03:30 | Row 31 to 40: frightened, and the flag that the character must flee. The duration is 7 minutes, 420 seconds, so the instance ends at 14:07:00. The condition frightened carries the instance as its source. At 14:03:00 the instance is SUPPRESSED and the condition is not applied. At 14:03:30 it is ACTIVE again with 3 minutes 30 seconds left. At 14:07:00 it ends as EXPIRED. Lesser restoration cast at any time before that would have ended it as CURED |
| WE-H8 | A character steps onto the pressure plate of a complex trap that releases a rolling sphere. Combat is already running. The data of the trap gives an initiative bonus of +8 (an example value) and the d20 gives 9. A character rolled 14 | The trigger runs once (a step trigger in version 1.5, the DM in version 1). The sphere joins the initiative order as a TRAP entry at 17, ahead of the character at 14. On its turn it runs the actions in its data, moving in a straight line and attacking or forcing saves as the data says. It leaves the order when its data says it is done |
| WE-H9 | A creature with Constitution 14 is pushed into deep water at 10:00:00 and cannot reach air | It holds its breath for 1 plus 2, which is 3 minutes, so at 10:03:00 it is CHOKING. It has 2 rounds. At the start of its turn after those 2 rounds it drops to 0 hit points and is dying, with the suffocating marker, and cannot be healed or stabilized until it can breathe. This is the SRD example |

### 13.14 Tests

The engine tests of Part H use no database, no network and no real clock. Random inputs come from a fixed seed, and the clock is an input. The persistence tests run against a real PostgreSQL 16. Each test below is written before the code it covers, and each worked example of 13.13 and each edge case of 13.11 is also a test case.

| ID | Test | Rules covered |
| --- | --- | --- |
| T-80 | Light model. A table of scenes gives the level of a cell from the ambient light, from emitters that have line of sight, from the best level of several inputs, and from the MAGICAL\_DARKNESS exception. The sunlight flag is set only by sunlight. A DM override wins. The light version goes up when an emitter is lit, doused, moved or destroyed and when a door opens or closes, and a memo entry from an older version is never returned | RS-ENV-001 to RS-ENV-008, RS-ENV-R3, RS-ENV-R8 |
| T-81 | Sight resolver. One case for each step of the fixed order: no line of sight, blindsight and tremorsense, blinded, invisible with and without truesight, and obscurement after darkvision and truesight. The cases of WE-H1 and WE-H2 and of RS-ENV-R1 to RS-ENV-R6 are included. A DM override of a pair is read first. The function takes no clock and no random source, and the same inputs give the same answer on every call | RS-ENV-009 to RS-ENV-016, RS-ENV-018 |
| T-82 | Sight-based Perception. Disadvantage and the passive reduction of 5 apply only when the cell of the thing perceived is lightly obscured, use the viewer's own cell when there is no target, and do not touch other checks. The DM can add or remove the modifier | RS-ENV-017, RS-ENV-R7 |
| T-83 | Line of sight properties. With a fixed seed, random maps check that line of sight is symmetric, that creatures never block it, that the viewer's and the target's own spaces are not counted, and that dense fog blocks it and thin fog does not. A differential test compares the pairwise rule with a slow reference implementation, and any later faster algorithm must pass the same test | RS-ENV-019, RS-ENV-021, RS-ENV-025 |
| T-84 | Segment test. An exact integer test of a line against blocking cells: through the inside, along the edge of one cell, touching one corner, along the seam of two cells, through the shared corner of two diagonal cells, with an end on a boundary, and with the largest coordinates of the grid cap, where nothing overflows | RS-ENV-019, RS-ENV-020, RS-ENV-R9, RS-ENV-R10 |
| T-85 | Cover. The thresholds of 0, 1, 2, 3 and 4 blocked lines. The least cover over corners and squares, with the fixed tie order. A Large target. A creature in the line gives half cover and never more. The most protective degree applies and degrees are not added. The CENTERS method and a change of method between attacks. The origin and the line of effect of an area effect. A DM override in both directions, recorded on the roll. The case of WE-H3 gives the exact numbers | RS-ENV-022 to RS-ENV-027, RS-ENV-R11 to RS-ENV-R16 |
| T-86 | Cell values. Blocking of sight and cover are independent for a wall, a portcullis, an arrow slit, a low wall, dense fog and thin fog. A door changes both when it opens or closes and raises the light version and the map version. A secret door acts as a wall for players | RS-ENV-025, RS-ENV-028, RS-ENV-R17, RS-ENV-R18 |
| T-87 | Reach and opportunity attacks, step by step. Leaving a reach provokes and entering does not. Each hostile creature is checked on its own. A move from 10 feet to 5 feet inside a reach of 10 does not provoke. A mover that cannot be seen, forced movement, teleporting and Disengage provoke nothing. A hostile creature that is incapacitated or has no reaction provokes nothing. A monster makes one melee attack. An earlier attack that kills the mover removes the trigger. A diagonal step uses the campaign metric. The case of WE-H4 is included | RS-ENV-029 to RS-ENV-033, RS-ENV-R19 to RS-ENV-R21 |
| T-88 | Flanking. With the setting off nothing happens. With the setting on, a melee attack has advantage when a straight line between the attacker and an ally passes through the target's space and the ally can attack, and a ranged attack never does | RS-ENV-034 |
| T-89 | Fall damage. Distances from 0 to 250 feet give the right number of dice, with the rounding and the cap. The four ways to avoid damage are each tested, and resistance does not avoid prone. Prone is applied after damage. A DM override of none is recorded. Resolving the same fall twice, or replaying it, changes nothing. A persistence test checks the unique key and the constraints of the fall table. A contract test runs the same cases against every FallDistanceProvider, so that the elevation adapter of version 2 joins it with the same expected results (WE-H5, WE-H6) | RS-ENV-036 to RS-ENV-039, RS-ENV-R22, RS-ENV-R23 |
| T-90 | The fall window and long falls. Feather Fall in the window, a DM cancel, two creatures falling from the same pit, a fall of 1,500 feet with the default setting, a setting of 0, and a change of the setting while a fall is descending. The window is offered again at each turn end, and the remaining distance survives a restart and a replay | RS-ENV-040, RS-ENV-041, RS-ENV-R24 to RS-ENV-R26 |
| T-91 | Hazards and traps. Each trigger kind. A simple trap rolls its effects in the order of its data and resolves once for a repeated trigger. A pit starts a fall and adds its own damage, and the two are rolled separately. A complex trap joins the initiative order as a TRAP entry and leaves it when its data says so (WE-H8). A zone can carry a hazard. A trigger that needs the DM waits for the DM, and in version 1 every trigger is the DM's. Replaying a recorded encounter gives the same states | RS-ENV-042, RS-ENV-044, RS-ENV-048, RS-ENV-049, RS-ENV-054, RS-ENV-056 |
| T-92 | Detection, disabling, water and poison. Checks run against the DCs of the data, and the passive comparison is offered to the DM. Arcana, detect magic and dispel magic work on a magic trap. The UNDERWATER flag changes attack rolls by the weapon lists of the item data and gives fire resistance. A poison has a delivery, a save, a timer on the clock and its own source tag, so that two poisons do not cancel each other | RS-ENV-047, RS-ENV-050, RS-ENV-052 |
| T-93 | Seed content. Every trap, poison and disease record and every row of the three madness tables is checked against the SRD text. The loader rejects a record with an unknown effect kind, a missing trigger or a missing DC | RS-ENV-044, RS-ENV-051 to RS-ENV-053, RS-MAD-004 to RS-MAD-006 |
| T-94 | Starting an affliction. A failed save starts it and a passed save does not. The DM can start one without a save. The d100 result is recorded and replays unchanged, and the DM can pick a row. The durations are rolled and stored as seconds on the clock, empty for indefinite madness and diseases, and the DM can enter one | RS-MAD-001 to RS-MAD-003 |
| T-95 | Breath. Holding changes to choking by the clock. The minimums of 30 seconds and 1 round apply to a low Constitution. The creature drops to 0 hit points and is dying with the suffocating marker, cannot be healed or stabilized, and still makes death saving throws. The DM clearing the state ends it. The cases of WE-H9 and RS-ENV-R27 are included | RS-ENV-045, RS-ENV-046, RS-ENV-R27 |
| T-96 | Expiry. A clock advance ends every instance whose end time has passed, at its own end time and not at the time of the advance. A jump of several hours, a second advance, a replay and a retry end nothing twice. A suppressed instance keeps counting and can expire while suppressed | RS-MAD-007, RS-ENV-R28 |
| T-97 | Effects, cure and disease. A condition, a modifier, a trigger and a flag. Paralysis ends on damage with the reason DAMAGED. An instance removes only its own effects. Two instances that give one condition leave it until the last ends. Calm emotions suppresses and resumes with the right time left (WE-H7). Lesser and greater restoration end the right kinds. A DM-judged cure is only offered. A long rest does not end madness. A disease keeps its progress, is offered its save at the end of a long rest, and can start another instance that records its cause | RS-MAD-008 to RS-MAD-013, RS-ENV-053, RS-ENV-R29 to RS-ENV-R31 |
| T-98 | Hidden information and roles. A player's page, realtime update and data export are serialized for a scene with a hidden hazard, a secret door and an unseen creature, and none of their ids or texts is in them. The DM view has the marker. A detected hazard is detected for every player. A player who calls a DM-only action is refused, and so is a request with no role | RS-ENV-018, RS-ENV-028, RS-ENV-055, RS-ENV-R18, RS-ENV-R32 |
| T-99 | Benchmark. The budgets of 13.9 are measured on a reference map with a fixed seed. The result goes to the build log, and a missed target is a defect | 13.9 |
| T-100 | Module boundaries. The imports of engine.light, engine.vision, engine.fall, engine.hazard, engine.affliction and engine.grid.geometry are checked: no Spring, no web layer and no persistence | 13.9 |

### 13.15 What Part H does not cover, and what it changes elsewhere

These items stay out of Part H.

| Topic | Where it goes |
| --- | --- |
| Food, water and starvation | Not modelled. The SRD rules need a supplies model that no part has defined. The DM applies exhaustion with the condition tools that exist |
| Environmental hazards that are not in SRD 5.1, such as extreme heat and cold or quicksand | Not shipped. The DM can build them as custom hazard definitions (RS-ENV-044) |
| Weather | Not modelled. The DM sets the ambient light, the zone flags and the UNDERWATER flag by hand |
| The numbers of Daylight, Darkness and similar spells | Spell data of Part D. The environment reads only the emitters and flags that the data declares (RS-ENV-003, RS-ENV-004) |
| A surface model for tremorsense | Not planned. The DM confirms the located creature (RS-ENV-014) |
| What each character knows | Not tracked in versions 1 and 1.5. A detected hazard is detected for the whole table (RS-ENV-R32). Per-character knowledge would be a later part |
| Fog of war display and token drawing | Architecture and Requirements. Version 2 computes the visible set from RS-ENV-021 (13.9) and the web layer draws it |
| Mounted combat and flying mounts | Version 2, with elevation. They use the same fall event (RS-ENV-043) |
| Polymorph, Wild Shape and other changes of form | Unchanged by Part H. The senses of the new form come from its stat block data (RS-ENV-010) |
| Pathfinding | The Grid and Pathfinder Spec. Part H requires only that a path is walked one step at a time (RS-ENV-031) |

The owner accepted Part H on 2026-11-19. These changes are made in the other documents in the revision of the acceptance, and each document carries its own change note.

| Document | Change |
| --- | --- |
| Ruleset | The changes to Parts B, C, E, F and G that 13.10 lists and that are not pointer-only, made in the revision in which the owner accepted Part H. The rows that only pointed to Part H are already updated in this draft. The version header and the history table, so that the Ruleset becomes 1.0. No existing value changes |
| Architecture | A new section for the environment modules (engine.light, engine.vision, engine.fall, engine.hazard, engine.affliction and engine.grid.geometry), their ports and the tables of 13.9. The vision seam of the earlier section is amended to point at RS-ENV-015. The boundary test T-100 joins the architecture tests |
| Requirements | New functional requirements for light and vision, line of sight and cover, reach and opportunity attacks, falling, hazards and afflictions, each with its phase. The automatic line of sight and cover requirement (FR-GRD-009) points at RS-ENV-021 to RS-ENV-026. The madness tracker is a version 1 requirement |
| AI Build Brief | Build-order entries and the pure-engine boundary for the new packages. The test list T-80 to T-100. A pointer to the schema sketch of 13.9. The companion versions list |
| Decision Log | DEC-105 for E-134 to E-153 and the four settings of 13.12.1, and a history row |
| Grid and Pathfinder Spec | Checked at propagation. The cell attributes of RS-ENV-025 and the per-step path of RS-ENV-031 must be readable from its map and path model. An amendment follows only if they are not |

## 14. Version and revision history

Current version: **1.2**. The specification became 1.0 on 2026-11-19, when the owner accepted Part H.

| Version | Date | Changes |
| --- | --- | --- |
| 0.1 | 2026-10-12 | Part A (foundations) first draft: ability scores, proficiency, d20 tests, advantage, checks, saves, levels and hit points, time |
| 0.2 | 2026-10-12 | Part A edge-case audit: dice and roll composition (3.8), modifiers and stacking (3.9), ability score maximum, ceiling and floor, advantage scope, forced save outcomes, hit point maximum as a stat; engine decisions E-8 to E-13 proposed |
| 0.3 | 2026-10-14 | Part A decisions closed (E-1, E-3 to E-5, E-8 to E-13); ability score generation methods (RS-ABL-007); feats decided as content |
| 0.4 | 2026-10-14 | Part B (combat and conditions) drafted from the full SRD Combat and Conditions chapters; engine decisions E-14 to E-21 proposed |
| 0.5 | 2026-10-15 | Part B decisions E-14, E-16, E-17, E-19, E-20, E-21 decided; join options for mid-combat arrivals (RS-CMB-007 to RS-CMB-009); temporary hit points at 0 HP researched |
| 0.6 | 2026-10-16 | E-15 and E-18 decided; version numbering and revision history added |
| 0.7 | 2026-10-21 | Dice engine made implementable: exact notation grammar (keep and drop, reroll once, floor), validity rules, concrete limits, error codes, draw order, result model, roll composition API, test contract and worked examples with forced dice (section 3.10); proposed defaults D-1 to D-6 (section 3.11) |
| 0.8 | 2026-10-21 | Dice engine decisions D-1 to D-6 accepted (sections 3.10 and 3.11 now decided); no change to their content |
| 0.9 | 2026-10-30 | Dice engine refinements from the Step 2 review (D-7 to D-13): reroll history, replacing one d20 of a pair, single-group dice(), plan-wide limits, signed fixed terms, naturalD20 as the effective value (researched), implementation checks; the aggregate-limit test case corrected |
| 0.10 | 2026-10-30 | Verification debt V-1 cleared against SRD text: Inspiration, Backgrounds, Multiclassing, Rogue, Fighter and Bard entries, spellcasting basics, belt and manual wording. E-6 (spell save DC and attack modifier) and E-7 (level-one hit points) decided; E-22 proposed; residuals tracked as V-3 |
| 0.11 | 2026-11-14 | V-3 cleared: the equal-potency tie-break (most recent applies, suppressed instances resume), multiclass hit dice and feature rules, and the Bard, Wizard and Paladin entries verified; E-22 decided (broad reading by default, with a campaign setting to narrow it or turn it off) |
| 0.12 | 2026-11-14 | Part B verification debt V-2 cleared: immunity (with the nonmagical qualifier and condition immunity), hiding against passive Perception, concentration rules (new RS-ACT-012, readied spells), falling damage |
| 0.13 | 2026-11-16 | Part C (rests, time and resources) drafted as section 8: rest rules, the game clock and effect expiry, the recharge model with the MVP class features verified, Arcane Recovery and Song of Rest, worked examples, engine requirements; engine decisions E-23 to E-31 proposed. The history section is now 9 |
| 0.14 | 2026-11-16 | Part C decisions E-23 to E-31 decided; V-4 cleared against the SRD resting page and the DMG rest variants: Hit Dice healing minimum 0 noted as a Player's Handbook rule, the rest variant presets set the long rest interval, and the Epic Heroism spellcaster tweak noted as not built in |
| 0.15 | 2026-11-16 | Edge-case review: Part C section 8.8 (proposed decisions E-32 to E-44) and dice decision D-14 (critical doubling with keep or drop) proposed |
| 0.16 | 2026-11-16 | D-14 decided as a critical dice mode setting (roll again and add by default, extra die only, reject); D-15 proposed (a critical flag on each die); RS-DMG-018 and E-45 proposed for Savage Attacker-style reroll features |
| 0.17 | 2026-11-16 | Part C edge cases E-32 to E-44, dice decision D-15 and Savage Attacker decision E-45 accepted and marked decided; DieRoll now has a critical flag |
| 0.18 | 2026-11-16 | Part D (classes) drafted as section 9: class data model and level-up, verified class tables for Fighter, Rogue, Paladin, Bard and Wizard to level 12, feature rows with engine support, decision windows and the order of an attack, 22 edge cases and community conflicts with defaults and settings, engine decisions E-46 to E-60 proposed, worked examples, verification debt V-5. The history section is now 10 |
| 0.19 | 2026-11-16 | V-5 cleared: Evasion, death saves with auras, Archery, Dueling, Great Weapon Fighting and starting wealth confirmed; Uncanny Dodge now halves before resistance and vulnerability (E-54, RS-DMG-017 updated); a note on the Bardic Inspiration second die; row C-D23 added (Dueling with a thrown weapon) |
| 0.20 | 2026-11-16 | One reaction window, not two: Part B gains RS-REA-001 to RS-REA-010 (points, options with costs, deciders, order, nesting to depth 6 which closes the missing depth definition, information rule, scene scope, cancel keeps the dice, resume by replay, blocked actions and authority) and 18 edge cases with test references; Part D section 9.4 and decision E-46 now describe the generalized reaction window; E-61 to E-64 decided |
| 0.21 | 2026-11-16 | Part D decisions E-47 to E-60 rectified for the single reaction window and decided; new decisions E-65 to E-68; Part B gains RS-REA-011 to RS-REA-014 (follow-up choices, frozen state and pins, the orders of a saving throw and a check, settings during a chain), the four-step order inside a window, canonical point names, and edge cases RS-REA-R19 to R29 |
| 0.22 | 2026-11-16 | Part E (spellcasting) drafted as section 10, from the SRD 5.1 spellcasting chapter, class lists and spell entries: sources and class list counts, spell data and the tiers, slots, preparation and rituals, the order of a cast and the bonus action rule, concentration in the engine, areas of effect on the grid with exact integer geometry, effect primitives, reaction spells in the reaction window, the 40-spell curated tier-2 list, 25 edge cases and community conflicts, engine decisions E-69 to E-88 proposed, five worked examples, tests T-40 to T-47 proposed, verification debt V-6. The history section is now 11 |
| 0.23 | 2026-11-16 | Engine decisions E-69 to E-88 accepted, with defaults refined the same day (E-89 to E-91: bonus action limit on the caster's own turn with an ANY\_TURN option; coverage by half decided with the center test for every shape; Counterspell alerts eligible players by default with REVEAL and SILENT options); new setting missileRoll (Magic Missile: one roll per dart by default, or one roll for all darts) with C-E6, C-E7 and E-73 updated; verification debt V-6 cleared: all 40 tier-2 spell entries read, the center-test claim corrected (identical to the true half-area rule at the five reference radii), cantrip scaling and the Oath of Devotion list confirmed, only Xanathar's page numbers and the Mearls answer left as stated residuals; Part E propagated by reference to the other documents on 2026-11-18 |
| 0.24 | 2026-11-18 | Part F (equipment and armor class) drafted as section 11: item tiers and data, armor class as a computed stat, hands and object interaction, the SRD weapon and armor tables with property rules, a curated list of magic items, attunement and charges, currency and encumbrance, conflicts C-F1 to C-F9, decisions E-92 to E-113 and five settings (accepted on 2026-11-19), worked examples WE-F1 to WE-F5, tests T-48 to T-61 and verification debt V-7 (cleared on 2026-11-19; the five defaults behind E-97, E-99, E-102, E-104 and E-111 confirmed by the owner, with ammunition recovery rounded to the nearest whole number, .5 up). The Part B statement that AC is fixed at creation is amended. The version history heading is now section 12. Propagated by reference on 2026-11-19 to the Architecture, the Requirements Specification, the AI Build Brief and the Decision Log. |
| 0.25 | 2026-11-19 | Part G (monsters and encounters) written as section 12, status Accepted: the monster definition and its checks, hit points and the zero-hit-point hook, special traits as engine mechanics, legendary actions, Legendary Resistance and lair actions, summoned creatures, encounter experience points and the 2024 budget method (specified, readout deferred), the tiered roster of 18 tier 2 monsters, conflicts C-G1 to C-G9, rules RS-MON-001 to RS-MON-052, edge cases RS-MON-R1 to RS-MON-R24, amendments PB-G1 to PB-G8 to Part B, decisions E-114 to E-133, two settings, worked examples WE-G1 to WE-G8, tests T-62 to T-79 and verification debt V-8, cleared on the same day against the SRD PDF. The owner accepted Part G on 2026-11-19 and it is propagated to the other documents. The version history heading is now section 13. |
| 0.26 | 2026-11-19 | Part H (the environment) drafted as section 13, status Proposed: light and darkness, vision and obscurement, line of sight and cover on an integer lattice, reach and opportunity attacks, falling with a fall event and a distance provider, hazards (breath, underwater combat, traps, poisons, diseases), the madness and affliction tracker, the seams for versions 1.5 and 2, conflicts C-H1 to C-H15, rules RS-ENV-001 to RS-ENV-056 and RS-MAD-001 to RS-MAD-013, edge cases RS-ENV-R1 to RS-ENV-R32, amendments PB-H1 to PB-H10, decisions E-134 to E-153, four settings, worked examples WE-H1 to WE-H9, tests T-80 to T-100 and verification debt V-9. The rows that only pointed to Part H are updated, and the roadmap row of Part H is marked as drafted. |
| 1.0 | 2026-11-19 | Part H (the environment) accepted by the owner as drafted, so the Ruleset becomes 1.0. Verification debt V-9 cleared with a note: the books were not read, and the corner rule, the flanking rule and the falling rate were checked against quotations on public pages. RS-ENV-022 now gives three-quarters cover for three blocked lines and total cover for four, RS-ENV-034 states the four points of the Dungeon Master's Guide, and RS-ENV-041 drops the first 500 feet at once and the rest at the end of each of the creature's turns. The amendments PB-H3 to PB-H8 are applied to Parts B, C, E and F, PB-H1, PB-H2, PB-H9 and PB-H10 are confirmed, decisions E-134 to E-153 and the four settings are accepted, and the roadmap row and the header are updated. Part H is then applied to the Requirements Specification, the Decision Log, the Architecture and the AI Build Brief, and the two map-size benchmarks in the test table are set to the 200 by 200 hard cap of the Grid and Pathfinder Specification. |
| 1.1 | 2026-11-21 | The Content Schema Specification, accepted by the owner on 2026-11-21, is applied by reference. The three pointers that said it was to be written (sections 9.1, 10.2 and 10.7) now name it, and the provisional effect names of section 10.7 are final. The item primitives of section 11.6 and the four labels of the Part D catalog (attack\_bonus, ac\_bonus, damage\_bonus, crit\_threshold) are mapped to registry names, and the catalog gains the registry names proficiency\_fraction and roll\_floor. The feature trigger ON\_TURN\_START in RS-FTR-008 and in the hazard listener of section 13.7 is corrected to START\_OF\_OWN\_TURN, the trigger point name that the registry and Part G use. The header now shows the version (it still read 0.25). No rule changes. |
| 1.2 | 2026-11-22 | SRD verification (Decision Log OQ-07) recorded in section 1: the attribution text is now given word for word and checked against the official SRD 5.1 Creative Commons PDF, and the spell, monster and condition lists are checked against the SRD. No rule changed. |
