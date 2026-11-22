package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.kernel.DurationKind;
import com.github.sbadolankaur.rpg.engine.kernel.ModOp;
import com.github.sbadolankaur.rpg.engine.kernel.Modifier;
import com.github.sbadolankaur.rpg.engine.kernel.Ratio;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

final class ContentVocabulary {
  static final int VERSION = 1;
  private static final List<String> SKILLS =
      names(
          "acrobatics animal_handling arcana athletics deception history insight intimidation "
              + "investigation medicine nature perception performance persuasion religion sleight_of_hand stealth survival");
  private static final Map<String, List<String>> ENUMS =
      Map.ofEntries(
          Map.entry("ability", names("str dex con int wis cha")),
          Map.entry("skill", SKILLS),
          Map.entry("duration", Arrays.stream(DurationKind.values()).map(Enum::name).toList()),
          Map.entry("operation", Arrays.stream(ModOp.values()).map(Enum::name).toList()),
          Map.entry(
              "damageType",
              names(
                  "acid bludgeoning cold fire force lightning necrotic piercing poison psychic radiant slashing thunder")),
          Map.entry("size", names("tiny small medium large huge gargantuan")),
          Map.entry(
              "school",
              names(
                  "abjuration conjuration divination enchantment evocation illusion necromancy transmutation")),
          Map.entry(
              "creatureType",
              names(
                  "aberration beast celestial construct dragon elemental fey fiend giant humanoid monstrosity ooze plant undead")),
          Map.entry(
              "alignment",
              names(
                  "lawful_good neutral_good chaotic_good lawful_neutral neutral chaotic_neutral lawful_evil neutral_evil chaotic_evil unaligned any")),
          Map.entry(
              "recharge",
              names("SHORT_REST LONG_REST DAWN ROLL_INITIATIVE START_OF_OWN_TURN MANUAL")),
          Map.entry("hazardTrigger", names("STEP_ON ENTER_AREA START_TURN_IN_AREA ACTION DM")),
          Map.entry(
              "trigger",
              names(
                  "ON_MOVE_STEP BEFORE_ATTACK_ROLL AFTER_ATTACK_ROLL AFTER_HIT AFTER_DAMAGE_ROLL AFTER_SAVE_ROLL AFTER_SAVE_FAIL AFTER_CHECK_ROLL ON_CAST AFTER_DAMAGE_TAKEN ON_READIED_TRIGGER ON_TURN_END ON_DAWN START_OF_OWN_TURN")),
          Map.entry(
              "itemCategory",
              names("weapon armor shield gear tool consumable magic container ammunition")),
          Map.entry("rarity", names("common uncommon rare very_rare legendary artifact")),
          Map.entry(
              "weaponProperty",
              names(
                  "ammunition finesse heavy light loading range reach special thrown two_handed versatile")),
          Map.entry("rollKind", names("attack save check damage initiative death_save")));

  private static final Map<String, Entry> EFFECTS =
      Map.of(
          "modifier",
          new Entry(
              """
        {"type":"object","additionalProperties":false,
        "required":["type","stat","operation","value"],
        "properties":{"type":{"const":"modifier"},"stat":{"type":"string"},"operation":{"$ref":"#/$defs/operation"},
        "value":{"anyOf":[{"type":"integer","minimum":-1000,"maximum":1000},{"type":"string","pattern":"^-?[0-9]{1,4}/[0-9]{1,4}$"}]},
        "appliesWhen":{"enum":["carried","equipped","attuned"]},
        "activeWhen":{"$ref":"common.schema.json#/$defs/predicate"},
        "duration":{"$ref":"common.schema.json#/$defs/duration"}}}
        """,
              ContentVocabulary::modifier));
  private static final Map<String, Entry> PARTS =
      Map.of(
          "restriction",
              new Entry(
                  """
        {"type":"object","additionalProperties":false,"required":["type","movement"],
         "properties":{"type":{"const":"restriction"},"movement":{"const":"crawl_or_stand"}}}
        """,
                  node -> new Restriction(node.path("movement").asString())),
          "advantage_source",
              new Entry(
                  """
        {"type":"object","additionalProperties":false,
         "required":["type","roll","mode","direction"],
         "properties":{"type":{"const":"advantage_source"},"roll":{"$ref":"#/$defs/rollKind"},
         "mode":{"enum":["advantage","disadvantage"]},"direction":{"enum":["holder","against_holder"]},
         "maxDistance":{"type":"integer","minimum":0},"minDistanceExclusive":{"type":"integer","minimum":0},
         "lineOfSight":{"type":"boolean"}}}
        """,
                  node -> {
                    Long maximum =
                        node.has("maxDistance") ? node.path("maxDistance").longValue() : null;
                    Long minimum =
                        node.has("minDistanceExclusive")
                            ? node.path("minDistanceExclusive").longValue()
                            : null;
                    if (maximum != null && minimum != null && maximum <= minimum) {
                      throw new IllegalArgumentException("Empty distance range");
                    }
                    return new RollAdjustment(
                        node.path("roll").asString(),
                        node.path("mode").asString().equals("advantage")
                            ? RollMode.ADVANTAGE
                            : RollMode.DISADVANTAGE,
                        node.path("direction").asString().equals("against_holder"),
                        minimum,
                        maximum,
                        node.path("lineOfSight").asBoolean(false));
                  }));

  private ContentVocabulary() {}

  static String export() {
    Map<String, JsonNode> defs = new TreeMap<>();
    ENUMS.forEach(
        (category, values) ->
            defs.put(category, PackJson.MAPPER.valueToTree(Map.of("enum", values))));
    EFFECTS.forEach((name, entry) -> defs.put("effect-" + name, PackJson.parse(entry.schema())));
    PARTS.forEach((name, entry) -> defs.put("part-" + name, PackJson.parse(entry.schema())));
    return PackJson.MAPPER
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(
                new TreeMap<>(
                    Map.of(
                        "$schema",
                        "https://json-schema.org/draft/2020-12/schema",
                        "$id",
                        "https://schemas.tabletop-rpg.invalid/content/v1/vocabulary.json",
                        "x-vocabularyVersion",
                        VERSION,
                        "$defs",
                        defs)))
            .replace("\r\n", "\n")
        + "\n";
  }

  static void check(PackLayout.Draft draft, PackSchemas schemas, List<ContentFinding> findings) {
    PackLayout.walk(
        draft.body(),
        "",
        located -> {
          JsonNode node = located.node();
          String pointer = located.pointer();
          if (!node.isObject()) return;
          boolean part = pointer.matches("^/parts/[0-9]+$");
          if (!part && !pointer.matches(".*/effects/[0-9]+$")) return;
          String name = node.path("type").asString("");
          Entry entry = (part ? PARTS : EFFECTS).get(name);
          if (entry == null) {
            findings.add(
                new ContentFinding(
                    draft.file(),
                    pointer + "/type",
                    "CS-VOC-004",
                    "Unknown vocabulary name: " + safeName(name)));
            return;
          }
          int before = findings.size();
          schemas.checkVocabulary(
              (part ? "part-" : "effect-") + name, draft.file(), pointer, node, findings);
          if (findings.size() == before) {
            try {
              entry.read().apply(node);
            } catch (IllegalArgumentException invalid) {
              findings.add(
                  new ContentFinding(draft.file(), pointer, "CS-VOC-008", invalid.getMessage()));
            }
          }
        });
  }

  private static ModifierOperand modifier(JsonNode node) {
    String stat = node.path("stat").asString();
    var operation = ModOp.valueOf(node.path("operation").asString());
    JsonNode value = node.path("value");
    var parts = value.asString().split("/");
    int numerator = value.isIntegralNumber() ? value.intValue() : Integer.parseInt(parts[0]);
    int denominator = value.isIntegralNumber() ? 1 : Integer.parseInt(parts[1]);
    if (Math.abs(numerator) > 1000 || denominator < 1 || denominator > 1000) {
      throw new IllegalArgumentException(
          "Modifier ratio components must be within the pack limits");
    }
    Ratio ratio = new Ratio(numerator, denominator);
    if (Modifier.isIntegerStat(stat) && operation != ModOp.MULTIPLY && !ratio.isInteger()) {
      throw new IllegalArgumentException(
          "This operation on an integer stat requires a whole operand");
    }
    if (operation == ModOp.MULTIPLY && ratio.numerator() < 0) {
      throw new IllegalArgumentException("A multiplier cannot be negative");
    }
    if (!statKey(stat)) {
      throw new IllegalArgumentException("Unregistered stat key: " + safeName(stat));
    }
    return new ModifierOperand(stat, operation, ratio);
  }

  private static boolean statKey(String key) {
    if (names("ac speed hp_max crit_threshold attacks_per_action jump_distance").contains(key))
      return true;
    for (String skill : SKILLS) {
      if (key.equals("skill:" + skill) || key.equals("skill:" + skill + ":prof_multiplier"))
        return true;
    }
    return Arrays.stream(com.github.sbadolankaur.rpg.engine.kernel.Ability.values())
            .anyMatch(ability -> key.equals(ability.statKey()))
        || ENUMS.get("ability").stream().anyMatch(ability -> key.equals("save:" + ability))
        || key.matches("(attack|damage):(melee_weapon|ranged_weapon|melee_spell|ranged_spell|all)");
  }

  static boolean knows(String category, String value) {
    return ENUMS.getOrDefault(category, List.of()).contains(value);
  }

  static String safeName(String value) {
    return value.substring(0, Math.min(value.length(), 80)).replaceAll("[\\p{Cf}\\p{Cc}]", "?");
  }

  private static List<String> names(String values) {
    return List.of(values.split(" "));
  }

  private record Entry(String schema, Function<JsonNode, Object> read) {}

  record Restriction(String movement) {}

  record RollAdjustment(
      String roll,
      RollMode mode,
      boolean againstHolder,
      Long minExclusive,
      Long maxInclusive,
      boolean requiresSight) {}

  record ModifierOperand(String stat, ModOp operation, Ratio value) {}
}
