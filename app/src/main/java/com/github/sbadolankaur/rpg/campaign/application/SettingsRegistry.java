package com.github.sbadolankaur.rpg.campaign.application;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class SettingsRegistry {
  public enum Tier {
    STRUCTURAL,
    ASK_ON_FIRST_USE,
    SILENT
  }

  public enum Type {
    CHOICE,
    BOOLEAN,
    INTEGER,
    TIME
  }

  public static final List<String> WIZARD_KEYS =
      List.of("ruleset", "distanceMetric", "restVariant", "advancement", "hitPointMethod");

  private static final Map<String, Definition> DEFINITIONS = definitions();

  private SettingsRegistry() {}

  public static List<Definition> all() {
    return List.copyOf(DEFINITIONS.values());
  }

  public static Definition require(String key) {
    Definition definition = DEFINITIONS.get(key);
    if (definition == null) {
      throw invalid();
    }
    return definition;
  }

  private static Map<String, Definition> definitions() {
    Map<String, Definition> all = new LinkedHashMap<>();
    add(
        all,
        choice(
            "ruleset",
            "Ruleset",
            "SRD_5_1",
            Tier.STRUCTURAL,
            "SRD_5_1",
            "Play with the SRD 5.1 rules. Optional packs become available with the pack loader."));
    add(
        all,
        choice(
            "distanceMetric",
            "Diagonal distance",
            "EQUAL_COST",
            Tier.STRUCTURAL,
            "EQUAL_COST",
            "A diagonal costs the same as a straight move.",
            "TRUE_DIAGONAL",
            "A diagonal costs about 1.41 times a straight move.",
            "ALTERNATING",
            "Diagonal costs alternate between 5 and 10 feet."));
    add(
        all,
        choice(
            "restVariant",
            "Rest variant",
            "STANDARD",
            Tier.STRUCTURAL,
            "STANDARD",
            "Short rests take one hour and long rests eight hours.",
            "EPIC_HEROISM",
            "Short rests take five minutes and long rests one hour.",
            "GRITTY_REALISM",
            "Short rests take eight hours and long rests seven days.",
            "CUSTOM",
            "Use the individual rest length settings."));
    add(
        all,
        choice(
            "advancement",
            "Advancement",
            "MILESTONE",
            Tier.STRUCTURAL,
            "MILESTONE",
            "The DM decides when characters gain levels."));
    add(
        all,
        choice(
            "hitPointMethod",
            "Hit points on level up",
            "PLAYER_CHOICE",
            Tier.STRUCTURAL,
            "PLAYER_CHOICE",
            "The player chooses between rolling and taking the fixed average.",
            "FIXED",
            "Use the fixed class average.",
            "ROLLED",
            "Roll the class Hit Die."));
    add(
        all,
        choice(
            "autoOutcomeOnNat20Checks",
            "Natural 20 and 1",
            "OFF",
            Tier.ASK_ON_FIRST_USE,
            "OFF",
            "Checks and saves compare the total with the DC.",
            "CHECKS_ONLY",
            "Apply automatic success and failure on checks.",
            "SAVES_ONLY",
            "Apply automatic success and failure on saves.",
            "CHECKS_AND_SAVES",
            "Apply automatic success and failure on checks and saves."));
    add(
        all,
        choice(
            "criticalDiceMode",
            "Critical dice mode",
            "ROLL_AGAIN_ADD",
            Tier.ASK_ON_FIRST_USE,
            "ROLL_AGAIN_ADD",
            "Roll another group and add its result.",
            "EXTRA_DIE_ONLY",
            "Add an ordinary die without keep or drop modifiers.",
            "REJECT",
            "Reject flagged keep or drop groups in pack definitions."));
    add(
        all,
        choice(
            "duplicateProficiencyReplacement",
            "Duplicate proficiencies",
            "BROAD",
            Tier.ASK_ON_FIRST_USE,
            "BROAD",
            "Offer a replacement for duplicate skill and tool proficiencies.",
            "BACKGROUND_ONLY",
            "Offer a replacement only for a background overlap.",
            "OFF",
            "Duplicate proficiencies grant no replacement."));
    add(
        all,
        choice(
            "abilityScoreGenerationMethod",
            "Ability score generation",
            "STANDARD_ARRAY",
            Tier.ASK_ON_FIRST_USE,
            "STANDARD_ARRAY",
            "Allocate the standard array.",
            "POINT_BUY",
            "Spend points on scores.",
            "ROLL_4D6_DROP_LOWEST",
            "Roll four dice and drop the lowest.",
            "ROLL_3D6",
            "Roll three dice.",
            "MANUAL",
            "The DM enters the scores."));
    add(
        all,
        choice(
            "visionModel", "Vision", "FULL", Tier.SILENT, "FULL", "Use full vision in version 1."));
    add(
        all,
        toggle(
            "diagonalCornerCutting",
            "Diagonal corner cutting",
            false,
            Tier.ASK_ON_FIRST_USE,
            "Allow diagonals past blocked corners.",
            "Forbid diagonals past blocked corners."));
    add(
        all,
        toggle(
            "groupInitiative",
            "Group initiative",
            false,
            Tier.ASK_ON_FIRST_USE,
            "Identical monsters share a roll.",
            "Roll initiative separately."));
    add(
        all,
        toggle(
            "feats.enabled",
            "Feats",
            true,
            Tier.SILENT,
            "A character can choose a feat.",
            "Use ability score improvements only."));
    add(
        all,
        toggle(
            "temporaryHpAbsorbsDeathSaveFailure",
            "Temporary HP at zero HP",
            false,
            Tier.ASK_ON_FIRST_USE,
            "Fully absorbed damage avoids a death save failure.",
            "Fully absorbed damage still counts."));
    add(
        all,
        toggle(
            "midCombatSurprise",
            "Mid-combat surprise",
            false,
            Tier.ASK_ON_FIRST_USE,
            "Allow surprise for combatants caught off guard by an arrival.",
            "Surprise applies only at the start."));
    add(
        all,
        toggle(
            "interruptedLongRestGivesShortRest",
            "Interrupted long rest",
            false,
            Tier.ASK_ON_FIRST_USE,
            "An interrupted long rest of at least an hour gives short-rest benefits.",
            "An interrupted rest gives no benefits."));
    add(
        all,
        choice(
            "hitDiceMethod",
            "Hit Dice healing",
            "ROLLED",
            Tier.ASK_ON_FIRST_USE,
            "ROLLED",
            "Roll spent Hit Dice.",
            "FIXED",
            "Use the fixed average."));
    add(
        all,
        choice(
            "weaponDiceScope",
            "Weapon damage dice scope",
            "WEAPON_ONLY",
            Tier.ASK_ON_FIRST_USE,
            "WEAPON_ONLY",
            "Apply weapon rerolls to weapon dice only.",
            "ALL_DAMAGE_DICE",
            "Apply them to all damage dice of the attack."));
    add(
        all,
        choice(
            "savageAttackerCriticalDice",
            "Savage Attacker critical dice",
            "WHOLE_WEAPON_ROLL",
            Tier.ASK_ON_FIRST_USE,
            "WHOLE_WEAPON_ROLL",
            "Reroll the whole weapon roll.",
            "CHOOSE_BASE_OR_CRITICAL",
            "Choose whether to reroll base or critical dice."));
    add(
        all,
        choice(
            "rollDecisionInfo",
            "Roll information",
            "TOTAL_SHOWN",
            Tier.ASK_ON_FIRST_USE,
            "TOTAL_SHOWN",
            "Show the full roll total.",
            "NATURAL_DIE_ONLY",
            "Show the natural die only.",
            "OUTCOME_SHOWN",
            "Show the outcome before offering eligible options."));
    add(
        all,
        choice(
            "divineSmiteTiming",
            "Divine Smite timing",
            "AFTER_HIT",
            Tier.ASK_ON_FIRST_USE,
            "AFTER_HIT",
            "Decide after the hit and before damage.",
            "BEFORE_ATTACK",
            "Declare before rolling the attack."));
    add(
        all,
        toggle(
            "auraOfProtectionStacking",
            "Aura of Protection",
            false,
            Tier.ASK_ON_FIRST_USE,
            "Add matching auras.",
            "Use the highest matching aura bonus."));
    add(
        all,
        toggle(
            "archeryThrown",
            "Archery and thrown weapons",
            false,
            Tier.ASK_ON_FIRST_USE,
            "Include thrown attacks.",
            "Exclude thrown attacks."));
    add(
        all,
        choice(
            "bonusActionSpellRule",
            "Bonus action spell limit",
            "OWN_TURN",
            Tier.ASK_ON_FIRST_USE,
            "OWN_TURN",
            "Apply the limit on the caster's own turn.",
            "ANY_TURN",
            "Apply it to reactions until the caster's next turn.",
            "REACTION_EXEMPT",
            "Exempt reaction spells.",
            "OFF",
            "Allow spells within the normal action budget."));
    add(
        all,
        choice(
            "counterspellInfo",
            "Counterspell information",
            "ALERT",
            Tier.ASK_ON_FIRST_USE,
            "ALERT",
            "Alert only eligible counterspellers.",
            "REVEAL",
            "Reveal the spell name and cast level.",
            "SILENT",
            "Leave the prompt to the DM."));
    add(
        all,
        choice(
            "missileDamageSources",
            "Magic Missile damage sources",
            "PER_DART",
            Tier.ASK_ON_FIRST_USE,
            "PER_DART",
            "Each dart counts as its own damage instance.",
            "SINGLE",
            "Combine the darts hitting each creature."));
    add(
        all,
        choice(
            "missileRoll",
            "Magic Missile damage dice",
            "PER_DART",
            Tier.ASK_ON_FIRST_USE,
            "PER_DART",
            "Roll damage for each dart.",
            "ONE_ROLL",
            "Use one damage roll for all the darts."));
    add(
        all,
        choice(
            "areaCoverageRule",
            "Spell area coverage",
            "HALF_ALL",
            Tier.ASK_ON_FIRST_USE,
            "HALF_ALL",
            "Include a square when its center is inside the area.",
            "DMG_XGTE",
            "Count positive overlap for cones, cubes and lines."));
    add(
        all,
        choice(
            "counteredConcentration",
            "Countered concentration spells",
            "ENDS_OLD",
            Tier.SILENT,
            "ENDS_OLD",
            "End existing concentration when the new cast begins.",
            "KEEP_OLD",
            "Keep existing concentration if the new spell is countered."));
    add(
        all,
        choice(
            "componentEnforcement",
            "Spell components",
            "CONDITIONS_AND_COSTS",
            Tier.SILENT,
            "CONDITIONS_AND_COSTS",
            "Check verbal conditions and costed or consumed materials.",
            "FULL",
            "Also enforce somatic and free-hand requirements.",
            "OFF",
            "Use the DM's component ruling."));
    add(
        all,
        integer(
            "roundAdvanceSeconds",
            "Game clock advance per round",
            6,
            0,
            31622400,
            Tier.SILENT,
            "Seconds per completed round. Zero makes the clock manual."));
    add(
        all,
        integer(
            "shortRestMinutes",
            "Custom short rest",
            60,
            1,
            527040,
            Tier.SILENT,
            "Short rest length in minutes when using custom rests."));
    add(
        all,
        integer(
            "longRestMinutes",
            "Custom long rest",
            480,
            1,
            527040,
            Tier.SILENT,
            "Long rest length in minutes when using custom rests."));
    add(
        all,
        integer(
            "longRestIntervalHours",
            "Custom long rest interval",
            24,
            1,
            8784,
            Tier.SILENT,
            "Hours between effective long rests when using custom rests."));
    add(
        all,
        time(
            "startTimeOfDay",
            "Starting time of day",
            "08:00",
            "Time of day when the game clock is zero."));
    add(all, time("dawnTime", "Dawn", "06:00", "Time of day when dawn recharge occurs."));
    return Collections.unmodifiableMap(all);
  }

  private static void add(Map<String, Definition> all, Definition definition) {
    if (all.putIfAbsent(definition.key(), definition) != null) {
      throw new IllegalStateException("Duplicate setting " + definition.key());
    }
  }

  private static Definition choice(
      String key, String label, String defaultValue, Tier tier, String... options) {
    Map<String, String> values = new LinkedHashMap<>();
    for (int index = 0; index < options.length; index += 2) {
      values.put(options[index], options[index + 1]);
    }
    return new Definition(
        key,
        label,
        Type.CHOICE,
        tier,
        defaultValue,
        "Select a value.",
        values,
        text -> {
          if (!values.containsKey(text)) throw invalid();
          return text;
        });
  }

  private static Definition toggle(
      String key, String label, boolean defaultValue, Tier tier, String enabled, String disabled) {
    return new Definition(
        key,
        label,
        Type.BOOLEAN,
        tier,
        defaultValue,
        "Choose whether this rule applies.",
        Map.of("true", enabled, "false", disabled),
        text ->
            switch (text) {
              case "true" -> true;
              case "false" -> false;
              default -> throw invalid();
            });
  }

  private static Definition integer(
      String key,
      String label,
      long defaultValue,
      long min,
      long max,
      Tier tier,
      String description) {
    return new Definition(
        key,
        label,
        Type.INTEGER,
        tier,
        defaultValue,
        description,
        Map.of(),
        text -> {
          long value = Long.parseLong(text);
          if (value < min || value > max) throw invalid();
          return value;
        });
  }

  private static Definition time(
      String key, String label, String defaultValue, String description) {
    return new Definition(
        key,
        label,
        Type.TIME,
        Tier.SILENT,
        defaultValue,
        description,
        Map.of(),
        text -> {
          if (!text.matches("[0-9]{2}:[0-9]{2}")) throw invalid();
          return LocalTime.parse(text).toString();
        });
  }

  private static CampaignException invalid() {
    return new CampaignException(CampaignException.Code.SETTING_INVALID);
  }

  public record Definition(
      String key,
      String label,
      Type type,
      Tier tier,
      Object defaultValue,
      String explanation,
      Map<String, String> options,
      Function<String, Object> parser) {
    public Definition {
      options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public Object parse(String text) {
      if (text == null) throw invalid();
      try {
        return parser.apply(text);
      } catch (NumberFormatException | DateTimeParseException exception) {
        throw invalid();
      }
    }
  }
}
