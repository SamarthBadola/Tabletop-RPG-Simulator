# Tabletop RPG Simulator: Ruleset Specification (v0.6, draft)

Oct 15, 2026 · @Samarth

The source of truth for the D&D mechanics the engine implements, based on SRD 5.1 (CC-BY-4.0). Rules are paraphrased with SRD section references. Part A (foundations) is written; the other parts are listed in section 1 and gate the later build steps.

## 0. How to use this specification

- **Rule IDs** look like `RS-ABL-002`. Every rule has a paraphrase, an SRD reference, an engine mapping, and, where useful, worked examples.
- **Worked examples** use **forced dice** (the dice results are stated), so tests need no random seed. Format: Given, When, Then. Test names should quote the rule ID.
- **Status** per rule: Specified, Needs decision, or Not yet written.
- **Engine decision** marks a place where the SRD is silent or leaves it to the DM, so we chose a default. Each is listed in section 5 for confirmation.
- **Sources read for Part A:** the SRD 5.1 chapters Using Ability Scores and Beyond 1st Level, read in full from an online rendition of the SRD text, plus the Combat chapter for the round length. Not yet read: Inspiration, Multiclassing, Backgrounds, the class chapters and Spellcasting; rules that depend on them are marked for verification. Where Part A cross-references a later chapter, the rule says so and is marked for verification.
- The SRD attribution notice is copied verbatim into the repository `NOTICE` file; this document paraphrases.

## 1. Document map and status

| Part | Contents | Status | Gates |
| --- | --- | --- | --- |
| A. Foundations | Ability scores, proficiency, d20 tests, advantage, checks, saves, levels and hit points, time | Specified (revised 2026-10-14; verification debt V-1 open) | Build Steps 2 and 3 |
| B. Combat | Initiative, turns, action economy, movement, attacks, critical hits, damage, resistance, death, cover, conditions | Specified (draft; all Part B engine decisions decided) | Combat core |
| C. Rest and resources | Short and long rests, hit dice, recharge | Not yet written | Rests |
| D. Classes | The five MVP classes, tables and features, levels 1 to 5 then to 12 | Not yet written | Class features |
| E. Spellcasting | Slots, preparation, concentration, areas of effect, the curated tier-2 list | Not yet written | Spells |
| F. Equipment and armor class | Armor, weapons, weapon properties | Not yet written | Characters |
| G. Monsters and encounters | Stat block fields, CR and XP, level bands | Not yet written | Monster roster |
| H. Environment | Light, vision and obscurement (for the vision seam), falling, hazards | Not yet written | v2 vision |

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
| RS-ABL-004 | A level-up ability score improvement (and a feat's increase) cannot raise a score above that ability's maximum, which is 20 unless something raises it. Some effects raise a score and its maximum together (the Manual of Bodily Health raises both by 2). The SRD treats 30 as the top of the scale. The score the engine uses is the base plus the ability stat's modifiers, clamped to a ruleset ceiling (default 30, a setting) and floored at 0. | Validation on level-up choices; \`AbilityScores.score\` applies the ability stat's modifiers ; \`maxScore(ability)\` caps improvements and a ceiling setting clamps the result (decision DEC-074, proposed) |
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
| RS-PRF-001 | A character's proficiency bonus depends on total character level across all classes (confirm in Multiclassing): +2 at levels 1 to 4, +3 at 5 to 8, +4 at 9 to 12, +5 at 13 to 16, +6 at 17 to 20. Monsters have the bonus built into their stat block: the engine reads their attack, save and skill bonuses and spell DCs from the stat block instead of recomputing them. | `prof_bonus = floor((level - 1) / 4) + 2`; monsters read the stat block field |
| RS-PRF-002 | The bonus is added to a given roll or number at most once, even if several rules grant it. | One proficiency component per roll; duplicates collapse |
| RS-PRF-003 | The bonus can be multiplied or divided before it is applied (doubled for expertise, halved for the bard's Jack of All Trades), but only once, and only the bonus is multiplied, never the whole check. Halves round down. | `component = floor(prof_bonus x multiplier)`, multiplier in 0, 1/2, 1, 2 |
| RS-PRF-004 | If you lack proficiency, an effect that multiplies the bonus gives nothing: 0 times anything is 0. | Multiplier 0 stays 0 under `MULTIPLY` |
| RS-PRF-005 | Attack rolls and saving throws do not normally multiply the bonus. Half proficiency (the bard's Jack of All Trades) applies to ability checks only (confirm in Part D). | Default multiplier 1 for proficient attacks and saves |
| RS-PRF-006 | Proficiencies come in kinds: skills, tools, saving throws, weapons, armor and languages. Each is stored with its source. The same proficiency from two sources counts once; where character creation would grant a duplicate, the character chooses a replacement of the same kind (confirm in Backgrounds). | pc\_proficiency rows with kind, key and source; duplicates collapse |

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
| RS-ADV-007 | Inspiration (awarded by the DM): a character has it or does not and cannot stockpile it. Spending it gives advantage on one attack roll, saving throw or ability check. Not yet read in the SRD; confirm. | Boolean on the character; creates one advantage source when spent |

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
| RS-SAV-003 | The DC comes from the effect. For a spell it is determined by the caster's spellcasting ability and proficiency bonus (the exact formula is confirmed in Part E; the commonly used value is 8 + proficiency bonus + spellcasting modifier). | Needs verification in Part E |
| RS-SAV-004 | What success and failure mean is defined by the effect: often no harm or reduced harm on success. | Effect definitions carry `onSuccess` and `onFailure` outcomes |
| RS-SAV-005 | An effect can force a save to succeed or fail automatically (for example some conditions make a creature automatically fail Strength and Dexterity saves; Part B). No d20 is rolled and the log records the override and its source. | SaveOverride with a source; result flagged AUTO\_FAIL or AUTO\_SUCCESS |
| RS-SAV-006 | Death saving throws and concentration checks are saves with their own rules (Parts B and E). | Parts B and E |

**Example (forced dice).** Target has Dexterity 14 (+2), not proficient. Effect DC 13. d20 = 10: total 12, fails.

### 3.6 Levels, experience and hit points (SRD: Beyond 1st Level, Using Ability Scores)

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-LVL-001 | A character gains a level on reaching the experience total: level 2 at 300, 3 at 900, 4 at 2,700, 5 at 6,500, 6 at 14,000, 7 at 23,000, 8 at 34,000, 9 at 48,000, 10 at 64,000, 11 at 85,000, 12 at 100,000 (full table to level 20 in data). One award can cross several thresholds; level-up choices are then made one level at a time. | `class_level_def` or a shared advancement table |
| RS-LVL-002 | Advancement is by milestone by default: the DM decides when characters gain a level. XP advancement is an option the DM can select (planned for v1.5); the thresholds stay in the data. The MVP content cap is level 12; XP beyond it is kept but grants no level. | Campaign setting `advancement = XP or MILESTONE` ; milestone is the default and XP is a later option (engine decision E-4, decided) |
| RS-LVL-003 | Each level gives one more Hit Die. The hit point gain is the Hit Die roll plus the Constitution modifier (minimum 1), or the fixed value in the class entry (the die's average, rounded up: d6 gives 4, d8 gives 5, d10 gives 6, d12 gives 7), with the Constitution modifier added in both cases (confirm against the class entries in Part D). | Store the raw die result (or the fixed value) per level; campaign setting for the hit point method: player's choice (default), fixed average only, or rolled only |
| RS-LVL-004 | If the Constitution modifier changes, the hit point maximum changes as though the new modifier had applied from level 1: it moves by the modifier difference times the character's level. | `maxHp = sum over levels of max(1, raw_i + conMod)` |
| RS-LVL-005 | Where a class grants an ability score improvement you raise one score by 2 or two scores by 1, never above 20. Feats are an optional rule: at an improvement a character may take a feat instead. A character can hold any number of feats, limited only by how many are granted (the DM can grant extras). The SRD ships one feat (Grappler); others come from packs and homebrew. A feat is content: its effects, prerequisites and any choices (such as which ability or skill) live in its definition. | `pc_asi` rows by class and class level |
| RS-LVL-006 | Level-one hit points use the class's rules (maximum of the Hit Die plus Constitution modifier); to be confirmed in Part D. | Part D |
| RS-LVL-007 | Hit point maximum is a modifiable stat: the derived base (the sum of per-level gains) plus modifiers from effects, such as spells and features that raise it and drain effects that lower it. When the maximum rises, current hit points rise by the same amount; when it falls, current hit points are clamped to the new maximum if they exceed it (E-5). When a temporary increase ends, that counts as a fall: clamp only. A maximum reduced to 0 is resolved by the effect that did it. | maxHp stat with modifiers; current HP clamped to it |
| RS-LVL-008 | Proficiency bonus uses total character level. Class features that scale with level use the class level unless stated otherwise (confirm in Multiclassing and Part D). Hit dice are tracked per die size. | Class level rows; hit dice per die size |

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
| RS-DIE-004 | When a rule lets you reroll a die, the new result replaces the old one and you must use it, unless the rule says you may choose. A given die is rerolled at most once per use of the rule. Great Weapon Fighting works this way in SRD 5.1 (reroll a 1 or 2 on a damage die for a two-handed melee attack and use the new roll); the halfling's Lucky trait rerolls a natural 1 on a d20. Both are to be verified in the class and race chapters. | Reroll modifier: threshold, once per die, replace |
| RS-DIE-005 | A rule can treat a low d20 result as a minimum value (the rogue's Reliable Talent treats a roll of 9 or lower as 10 on checks that add the proficiency bonus). With advantage or disadvantage the floor applies to each d20 before the kept die is chosen. To verify in Part D. | Floor modifier applied per die |
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
| RS-MOD-002 | Stacking: the effects of different spells or features add together while they overlap; the same spell or feature affecting the same target more than once does not stack with itself. Only the most potent instance applies, or the most recent if they are equal. (Spellcasting, combining magical effects; verify in Part E.) | Every modifier carries a `stackKey` (its source identity) and a comparable potency |
| RS-MOD-003 | Order for stat modifiers: set, then add, then multiply (round down), then minimum and maximum, as decided in DEC-021. An effect that sets a score only if it is higher (the giant-strength belts; verify in Part F) is a maximum operation. | Fixed pipeline order |
| RS-MOD-004 | Lifetime: a modifier is permanent (with a recorded source, such as a species trait or a consumed item), lasts while an item is worn or attuned, lasts for a duration (rounds, until the end of a turn, game time, until a rest), or lasts until used. Ending the source ends the modifier. A permanent effect is never undone retroactively by a later change elsewhere. | Duration kinds from the architecture |
| RS-MOD-005 | The proficiency bonus is never stacked: it is one component (RS-PRF-002) however many modifiers mention it. | One component per roll |
| RS-MOD-006 | The DM can add a labeled one-off modifier, roll bonus, advantage or disadvantage to any roll; it is recorded in the log with its source. | DM override action |

**Examples**

- Two clerics each cast Bless on the same fighter: one +1d4 applies. Bless and Bardic Inspiration on the same attack: both apply, a d4 and a Bardic die.
- Armor class 15 from armor, +1 from a ring and +2 from a spell: 18, because different sources add. The same spell cast twice still adds +2 once.
- Strength 14 and an effect that sets the score to 19 only if higher: 19. A character with 21 stays at 21.

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

| ID | Question | Proposed default |
| --- | --- | --- |
| E-1 | Automatic success or failure on natural 20 or 1 for checks and saves | Decided: off by default, as the SRD gives none; a campaign setting lets the DM enable it for ability checks, saving throws or both |
| E-2 | Feats (Grappler is the only one in the SRD) | Decided (DEC-073): supported, several per character, content-driven; campaign setting feats.enabled, default on. Prerequisite and choice details go in Part D and the content schema. |
| E-3 | Fixed or rolled hit points per level | Decided (player chooses): the player chooses per level between the rolled value and the fixed average (the rulebook's own default), and the DM can force either through a campaign setting. In one forum poll of 121 groups, about 44% let players choose, 26% force the average and 9% force rolls (21% other). Variants such as rolling with a minimum of half the die are common but not built in for version 1. |
| E-4 | Level-up by XP or milestone | Decided: milestone is the default; XP advancement is a DM-selectable option planned for v1.5, with the XP column and thresholds kept in the data model from v1 |
| E-5 | Current HP when maximum changes | Decided: when the maximum rises, current HP rises by the same amount; when it falls, current HP becomes the new maximum only if it was higher; when a temporary increase ends, clamp only. Exception (decided): a creature at 0 HP is not raised, so a stat change cannot silently end a dying state |
| E-6 | Spell save DC formula and spell attack bonus | Confirm in Part E |
| E-7 | Level-one hit points | Confirm in Part D |
| E-8 | An ability improvement that would pass the maximum | Decided: warn the player before the improvement is applied (the choice screen shows that the score would pass the maximum and what would be lost) so they can reallocate; if they proceed, apply up to the maximum and lose the excess |
| E-9 | Game clock during combat | Decided: two separate values. The rules constant stays 6 seconds per round (10 rounds per minute) because every SRD duration is calibrated to it. The clock advance per completed round is a DM setting, default 6 seconds, including 0 for manual only. |
| E-10 | Ability score generation methods (standard array, point buy, rolling 4d6 and dropping the lowest) | Decided: version 1 ships a built-in list of common-practice methods (standard array, point buy, roll 4d6 drop lowest, roll 3d6, manual entry by the DM) from which the DM selects per campaign; DM-created methods are version 2. Not found in the SRD text read so far; verify |
| E-11 | Roll bonuses (including dice) as a separate type from stat modifiers | Decided: two types (RS-MOD-001) |
| E-12 | Stacking rule for repeated effects | Decided: the same source does not stack with itself (most potent, then most recent); different sources add (RS-MOD-002) |
| E-13 | A d20 floor effect combined with advantage or disadvantage | Decided: apply to each die before choosing the kept one (RS-DIE-005) |
| V-1 | Verification debt: Inspiration, Multiclassing, Backgrounds, the class chapters, Spellcasting (combining effects, spell DC) and item wording (belts, manuals) | Read these before marking the dependent rules Specified |

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
| RS-CMB-004 | Initiative: every participant makes a Dexterity check. The DM may make one roll for a whole group of identical creatures, which then act together. Combatants act from the highest total to the lowest and the order stays the same every round. | `initiative` stat (Dexterity check formula, RS-CHK-009); optional group roll |
| RS-CMB-005 | Ties: the DM decides among tied DM-controlled creatures; players decide among their own tied characters; the DM decides between a monster and a player character. Optionally the tied combatants each roll a d20 and the highest goes first. | Tie prompt with an optional roll-off (DEC-026) |
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

The turn budget holds: action, bonus action, movement remaining (in cost units), object interaction, reaction.

### 7.3 Movement and position

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-MOV-001 | A creature may move up to its speed, using as much or as little as it likes. Walking, jumping, climbing and swimming can be combined, and the distance of each part is deducted from the speed. | `movementRemaining` in cost units |
| RS-MOV-002 | Movement can be split around the action and, when one Attack action makes several attacks (for example with Extra Attack), between those attacks. | Moves and attacks interleave within a turn |
| RS-MOV-003 | With several speeds (walking, flying), switching to a new speed subtracts the distance already moved from it; if the result is 0 or less, the new speed cannot be used in this move. | Per-mode speed |
| RS-MOV-004 | Difficult terrain: each foot of movement costs 1 extra foot, even if several things in the space count as difficult terrain. The space of another creature, hostile or not, counts as difficult terrain. | Cost map: difficult and occupied cells cost double (architecture 7.3) |
| RS-MOV-005 | Prone: dropping prone costs no movement. Standing up costs movement equal to half the speed, and is impossible if that much movement is not left or if the speed is 0. A prone creature can only crawl (or use magic such as teleportation); each foot crawled costs 1 extra foot, so one foot of crawling in difficult terrain costs 3. | Stand-up cost; crawl cost; extra costs add (E-16) |
| RS-MOV-006 | A creature can move through a non-hostile creature's space, and through a hostile creature's space only if that creature is at least two sizes larger or smaller. Nobody can willingly end a move in another creature's space, friend or foe. Leaving a hostile creature's reach provokes an opportunity attack (RS-ATK-008). | `noStop` cells; hostile cells blocked unless the size difference is 2 or more |
| RS-MOV-007 | A flying creature that is knocked prone, has its speed reduced to 0 or otherwise cannot move falls, unless it can hover or magic holds it aloft. Fall damage is in Part H. | Fall trigger; Part H |
| RS-MOV-008 | Size and space: Tiny 2.5 by 2.5 ft; Small and Medium 5 by 5; Large 10 by 10; Huge 15 by 15; Gargantuan 20 by 20 or more. Space is the area a creature controls in combat, not its physical size. Eight Medium creatures fit around one Medium creature, and as many as 20 around a Gargantuan one. | `footprint(anchor, size)`; Tiny occupies a whole cell in version 1 (E-20) |
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
| RS-ACT-007 | **Hide:** a Dexterity (Stealth) check; success gives the benefits in RS-ATK-005. | Check request |
| RS-ACT-008 | **Ready:** choose a perceivable trigger and a response (an action, or moving up to the speed). When the trigger occurs the creature can use its reaction right after it, or ignore it; one reaction per round. A readied spell is cast normally but held: it needs a casting time of 1 action and concentration, and fizzles without effect if concentration breaks. | `ReadiedAction` entity; the DM confirms the trigger; held spell with concentration |
| RS-ACT-009 | **Search:** devote attention to finding something; the DM calls for a Wisdom (Perception) or Intelligence (Investigation) check. | Check request |
| RS-ACT-010 | **Use an Object:** an action when an object requires it or when a second object is used in the turn. | `UseObjectAction` |
| RS-ACT-011 | An improvised action: the DM decides whether it is possible and what roll it needs. | `ImprovisedAction` resolved by a DM check or save request |

### 7.5 Making an attack

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-ATK-001 | An attack has three steps: choose a target within range (a creature, an object or a location); determine modifiers (cover, advantage or disadvantage, bonuses and penalties); resolve it by rolling, and on a hit roll damage unless the attack says otherwise. If a creature is making an attack roll, it is making an attack. | Attack resolution pipeline |
| RS-ATK-002 | An attack roll is a d20 plus modifiers. If the total equals or exceeds the target's Armor Class, the attack hits. A character's AC is fixed at creation; a monster's AC is in its stat block. | `D20Test(target = AC)` |
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
| RS-COV-002 | Half cover (an obstacle blocks at least half of the body: a low wall, large furniture, a narrow trunk, or a creature, friend or foe): +2 to AC and Dexterity saves. Three-quarters cover (a portcullis, an arrow slit, a thick trunk): +5. Total cover (completely concealed): cannot be targeted directly by an attack or spell, though some area effects can include it. | Cover becomes a roll bonus or a targeting restriction for that attack |
| RS-COV-003 | Several sources of cover do not add; only the most protective degree applies. | Take the maximum |
| RS-COV-004 | In version 1 the DM sets the cover level for each attack (default none) and the engine suggests half cover when a creature stands between attacker and target. Automatic computation from the grid is planned (FR-GRD-009, v1.5). | Manual selection with a suggestion (E-17) |

### 7.7 Damage, healing and death

| ID | Rule | Engine mapping |
| --- | --- | --- |
| RS-DMG-001 | Current hit points run from the maximum down to 0. Damage is subtracted. Losing hit points has no effect on capabilities until a creature drops to 0. | `hpCurrent` on the character or combatant |
| RS-DMG-002 | A damage roll is the damage dice plus modifiers. A weapon attack adds the same ability modifier as its attack roll; a spell states its dice and modifiers. Penalties can reduce damage to 0 but never below 0. Damage dealt to several targets at once (a fireball) is rolled once for all of them. | Damage total clamped at 0; one roll, applied per target |
| RS-DMG-003 | Critical hit: roll all of the attack's damage dice twice and add them, then add the modifiers as normal. Extra damage dice from features such as Sneak Attack are rolled twice as well. | Dice terms with the crit-doubling flag (RS-DIE-006) |
| RS-DMG-004 | Damage types: acid, bludgeoning, cold, fire, force, lightning, necrotic, piercing, poison, psychic, radiant, slashing, thunder. Types have no rules of their own; other rules such as resistance rely on them. | Damage type registry with these defaults (extensible, FR-CNT-008) |
| RS-DMG-005 | Resistance to a damage type halves that damage; vulnerability doubles it. Both apply after all other modifiers to damage, resistance first. Several instances of resistance or vulnerability to the same type count as one. Halves round down. | One flag per type; applied in the damage pipeline |
| RS-DMG-006 | Immunity: a creature immune to a damage type takes none of it (stat blocks list immunities; wording to confirm in the Monsters chapter). | Immunity flag per type |
| RS-DMG-007 | Healing adds hit points to the current total, capped at the maximum; the excess is lost. A dead creature cannot regain hit points until magic such as Revivify restores it to life. | Heal effect clamps to `maxHp` |
| RS-DMG-008 | Temporary hit points are a separate buffer: lost first, leftover damage carries over to real hit points. They can exceed the maximum, cannot be restored by healing, and do not add together: the creature keeps the old amount or takes the new one. At 0 hit points they do not restore consciousness or stabilize but still absorb damage. They last until used up or a long rest unless the effect gives a duration. | `hpTemp` field with a replace-not-add rule |
| RS-DMG-009 | A creature reduced to 0 hit points either dies outright or falls unconscious. | Death state machine (below) |
| RS-DMG-010 | Instant death: when damage reduces a creature to 0 and damage remains, it dies if the remaining damage equals or exceeds its hit point maximum. | Remaining damage computed after resistance and temporary hit points |
| RS-DMG-011 | Otherwise it falls unconscious, which ends if it regains any hit points. | Unconscious condition applied and removed |
| RS-DMG-012 | Death saving throws: at the start of each of its turns at 0 hit points, the creature rolls a d20 that is not tied to any ability. 10 or higher is a success, otherwise a failure. Three successes make it stable; three failures kill it. They need not be consecutive, and both counts reset when it regains any hit points or becomes stable. A natural 1 counts as two failures; a natural 20 regains 1 hit point. Only features that improve saving throws help. | `deathSaves {successes, failures}`; roll bonuses and advantage apply; no ability modifier |
| RS-DMG-013 | Taking any damage at 0 hit points is one death save failure, even when temporary hit points absorb it (the official clarification is that absorbed damage is still damage taken; a campaign setting can change this, E-18); a critical hit is two; damage equal to or above the hit point maximum is instant death. | Applied after temporary hit points (E-18) |
| RS-DMG-014 | Stabilizing: an action to give first aid to an unconscious creature, a DC 10 Wisdom (Medicine) check. A stable creature makes no death saves but stays unconscious; it stops being stable if it takes any damage; if not healed it regains 1 hit point after 1d4 hours. | `STABLE` state with a recovery timer on the game clock |
| RS-DMG-015 | Most DMs have monsters die the moment they reach 0 hit points; major villains and special NPCs can follow the player rules. | Per-combatant `deathMode` (E-19) |
| RS-DMG-016 | Knocking out: when a melee attack reduces a creature to 0 hit points, the attacker can choose, at that instant, to knock it out: it falls unconscious and is stable. | Choice offered on a melee drop to 0 |
| RS-DMG-017 | Order of applying damage (engine): damage roll, critical doubling of dice, other flat modifiers, resistance, vulnerability, temporary hit points, hit points, then the 0 hit points outcome (instant death check, unconscious, or a death save failure). | Fixed pipeline; each step logged |

Death states: alive, dying (at 0 with success and failure counts), stable (unconscious, no saves), dead. A knocked-out creature is stable.

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

| ID | Question | Proposed default |
| --- | --- | --- |
| E-14 | How surprise is determined | Decided: the engine suggests the comparison of Stealth against passive Perception and the DM confirms; on a tie the DM decides whether the hider is noticed |
| E-15 | A combatant joining mid-combat | Decided: roll initiative and weave the joiner into the order by default, with DM-chosen join options (RS-CMB-007) for arrivals that are thrown in, unaware or ambushing; end-of-round joining is an option; no mid-combat surprise unless the optional house rule is on (RS-CMB-008) |
| E-16 | Extra movement costs that overlap (difficult terrain, crawling, squeezing) | Decided: they add, one extra foot each per foot (the SRD's own example of crawling in difficult terrain costs 3) |
| E-17 | Cover in version 1 | Decided: the DM sets it per attack, with a suggestion when a creature is in the way; automatic from the grid in v1.5 |
| E-18 | Damage at 0 HP that temporary hit points absorb completely | Decided (after research): follow the official clarification. Damage absorbed by temporary hit points is still damage taken, so at 0 HP it causes a death save failure (two for a critical hit) and requires concentration checks; temporary hit points still reduce the damage that counts toward instant death. A campaign setting lets the DM use the house rule that fully absorbed damage causes no failure (default off). When temporary hit points expire nothing happens to real hit points. |
| E-19 | Monsters reduced to 0 HP | Decided: they die, unless the DM marks a combatant to use the player rules or to be knocked out |
| E-20 | Footprint of Tiny creatures | Decided: one whole cell in version 1 |
| E-21 | What counts as 'can see' (Dodge, unseen attackers, ranged attacks in melee) | Decided: true unless the viewer is blinded, the target is invisible, or the DM marks obscuring conditions, until Part H and the vision work define lighting |
| V-2 | Verification debt for Part B | Immunity wording (Monsters), hiding and passive Perception (Adventuring), concentration for readied spells (Part E), falling damage (Part H) |

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

## 8. Version and revision history

Current version: **0.6 (draft)**. The specification stays at 0.x until Parts C to H are written, and becomes 1.0 when it is complete.

| Version | Date | Changes |
| --- | --- | --- |
| 0.1 | 2026-10-12 | Part A (foundations) first draft: ability scores, proficiency, d20 tests, advantage, checks, saves, levels and hit points, time |
| 0.2 | 2026-10-12 | Part A edge-case audit: dice and roll composition (3.8), modifiers and stacking (3.9), ability score maximum, ceiling and floor, advantage scope, forced save outcomes, hit point maximum as a stat; engine decisions E-8 to E-13 proposed |
| 0.3 | 2026-10-14 | Part A decisions closed (E-1, E-3 to E-5, E-8 to E-13); ability score generation methods (RS-ABL-007); feats decided as content |
| 0.4 | 2026-10-14 | Part B (combat and conditions) drafted from the full SRD Combat and Conditions chapters; engine decisions E-14 to E-21 proposed |
| 0.5 | 2026-10-15 | Part B decisions E-14, E-16, E-17, E-19, E-20, E-21 decided; join options for mid-combat arrivals (RS-CMB-007 to RS-CMB-009); temporary hit points at 0 HP researched |
| 0.6 | 2026-10-16 | E-15 and E-18 decided; version numbering and revision history added |