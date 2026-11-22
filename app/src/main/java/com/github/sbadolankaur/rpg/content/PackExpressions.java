package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.content.ContentExpression;
import com.github.sbadolankaur.rpg.engine.content.ContentExpression.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

final class PackExpressions {
  private PackExpressions() {}

  static void check(
      PackLayout layout,
      List<ContentPack> dependencies,
      PackSchemas schemas,
      List<ContentFinding> findings) {
    var tables = new HashMap<String, JsonNode>();
    dependencies.forEach(
        pack ->
            pack.definitions()
                .forEach(
                    definition -> {
                      if (definition.kind().equals("ruleTable"))
                        tables.put(
                            definition.key(), PackJson.MAPPER.valueToTree(definition.fields()));
                    }));
    for (PackLayout.Draft draft : layout.definitions()) {
      if (draft.kind() == ContentKind.RULE_TABLE) {
        tables.put(draft.key(), draft.body());
        checkTable(draft, findings);
      }
    }
    for (PackLayout.Draft draft : layout.definitions()) {
      for (var field : schemas.annotated(draft, "x-expression")) {
        if (field.node().asString().equals("all") && field.pointer().endsWith("/restore")) continue;
        Map<String, Type> variables =
            variables(draft.kind(), field.pointer(), field.node().asString());
        try {
          var formula =
              ContentExpression.parse(
                  field.node().asString(),
                  variables,
                  (table, column) -> column(tables.get(table), column));
          Type expected = asType(field.value());
          if (formula.type() != expected
              && !(expected == Type.ROLL && formula.type() == Type.INT)) {
            throw new IllegalArgumentException("Expected " + field.value() + " formula");
          }
          sample(formula, variables, draft);
        } catch (IllegalArgumentException invalid) {
          findings.add(
              new ContentFinding(
                  draft.file(),
                  field.pointer(),
                  "CS-EXP-010",
                  "Invalid " + field.value() + " formula in its declared context"));
        }
      }
    }
  }

  private static void checkTable(PackLayout.Draft draft, List<ContentFinding> findings) {
    JsonNode table = draft.body();
    String index = table.path("index").asString();
    var columns = new LinkedHashMap<String, Type>();
    for (JsonNode column : table.path("columns")) {
      String name = column.path("name").asString();
      if (name.equals(index)
          || columns.putIfAbsent(name, asType(column.path("type").asString())) != null) {
        findings.add(
            new ContentFinding(
                draft.file(), "/columns", "CS-EXP-005", "Column names must be unique"));
      }
    }
    var indices = new LinkedHashSet<Object>();
    int position = 0;
    for (JsonNode row : table.path("rows")) {
      JsonNode indexed = row.path(index);
      Object id =
          index.equals("level") && indexed.isIntegralNumber()
              ? indexed.longValue()
              : index.equals("name") && indexed.isString() ? indexed.asString() : null;
      boolean valid = id != null && indices.add(id) && row.size() == columns.size() + 1;
      for (var column : columns.entrySet()) {
        JsonNode value = row.path(column.getKey());
        valid &=
            switch (column.getValue()) {
              case INT -> value.isIntegralNumber();
              case NAME -> value.isString();
              case ROLL -> value.isString() && isDice(value.asString());
              case BOOL -> false;
            };
      }
      if (!valid)
        findings.add(
            new ContentFinding(
                draft.file(),
                "/rows/" + position,
                "CS-EXP-005",
                "A row needs a distinct index and a value of the declared type for every column"));
      ++position;
    }
  }

  private static boolean isDice(String text) {
    try {
      return ContentExpression.parse(text, Map.of()).type() == Type.ROLL;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  private static ContentExpression.TableColumn column(JsonNode table, String name) {
    if (table == null) return null;
    JsonNode columns = table.path("columns");
    if (name == null && columns.size() != 1) return null;
    for (JsonNode entry : columns) {
      String key = entry.path("name").asString();
      if (name != null && !name.equals(key)) continue;
      String index = table.path("index").asString();
      Type indexType = index.equals("level") ? Type.INT : Type.NAME;
      Type valueType = asType(entry.path("type").asString());
      var rows = new LinkedHashMap<Object, Object>();
      for (JsonNode row : table.path("rows")) {
        var label = row.path(index);
        var value = row.path(key);
        rows.put(
            indexType == Type.INT ? label.longValue() : label.asString(),
            valueType == Type.INT ? value.longValue() : value.asString());
      }
      return new ContentExpression.TableColumn(indexType, valueType, rows);
    }
    return null;
  }

  private static Map<String, Type> variables(ContentKind kind, String pointer, String expression) {
    var available = new HashMap<String, Type>();
    boolean character =
        switch (kind) {
          case CLASS,
              SUBCLASS,
              SPECIES,
              SUBSPECIES,
              BACKGROUND,
              FEAT,
              FEATURE,
              RESOURCE,
              ITEM,
              SPELL ->
              true;
          default -> false;
        };
    if (character) {
      for (String key : List.of("level", "prof")) available.put(key, Type.INT);
    }
    if (kind == ContentKind.CLASS || kind == ContentKind.SUBCLASS || kind == ContentKind.FEATURE) {
      available.put("class_level", Type.INT);
    }
    if (character || kind == ContentKind.MONSTER) {
      available.put("hp", Type.INT);
      available.put("hp_max", Type.INT);
      for (String ability : List.of("str", "dex", "con", "int", "wis", "cha")) {
        available.put("score:" + ability, Type.INT);
        available.put("mod:" + ability, Type.INT);
      }
    }
    if (kind == ContentKind.SPELL) {
      for (String key :
          List.of("spell_mod", "spell_dc", "spell_attack_bonus", "slot_level", "spell_level"))
        available.put(key, Type.INT);
    }
    if (pointer.contains("attack") || kind == ContentKind.FEATURE || kind == ContentKind.ITEM) {
      for (String key :
          List.of(
              "attack_is_melee",
              "attack_is_weapon",
              "attack_is_ranged",
              "has_advantage",
              "has_disadvantage",
              "other_weapon_held",
              "shield_wielded",
              "armor_worn",
              "enemy_of_target_adjacent",
              "moved_at_most_half_speed")) available.put(key, Type.BOOL);
      available.put("weapon_hands", Type.INT);
      available.put("creature_type", Type.NAME);
      for (String key :
          List.of(
              "ammunition",
              "finesse",
              "heavy",
              "light",
              "loading",
              "range",
              "reach",
              "special",
              "thrown",
              "two_handed",
              "versatile")) available.put("weapon:" + key, Type.BOOL);
    }
    if (kind == ContentKind.FEATURE) {
      available.put("source_conscious", Type.BOOL);
      available.put("distance_to_source", Type.INT);
      var qualified =
          java.util.regex.Pattern.compile("(resource|turn_token):[a-z0-9_.-]+").matcher(expression);
      while (qualified.find())
        available.put(
            qualified.group(), qualified.group(1).equals("resource") ? Type.INT : Type.BOOL);
    }
    if (pointer.endsWith("/activeWhen")) available.put("target_grappled_by_attacker", Type.BOOL);
    if (kind == ContentKind.MONSTER || pointer.contains("/effects/")) {
      for (String key : List.of("size", "creature_type", "damage_type"))
        available.put(key, Type.NAME);
    }
    return available;
  }

  private static void sample(
      ContentExpression formula, Map<String, Type> vars, PackLayout.Draft draft) {
    int spellLevel = draft.body().path("level").asInt(0);
    for (int level : new int[] {1, 5, 11, 17, 20}) {
      for (int score : new int[] {1, 8, 10, 16, 20, 30}) {
        for (int slot : new int[] {spellLevel, Math.min(spellLevel + 1, 9), 9}) {
          var values = new HashMap<String, Object>();
          vars.forEach(
              (name, type) ->
                  values.put(
                      name,
                      switch (type) {
                        case BOOL -> score >= 10;
                        case NAME ->
                            switch (name) {
                              case "size" -> "medium";
                              case "damage_type" -> "fire";
                              default -> "humanoid";
                            };
                        case INT -> sampleInt(name, level, score, slot, spellLevel);
                        case ROLL -> throw new IllegalStateException("A variable cannot roll dice");
                      }));
          formula.evaluate(values, bound -> 0);
        }
      }
    }
  }

  private static long sampleInt(String key, int level, int score, int slot, int spellLevel) {
    if (key.startsWith("score:")) return score;
    if (key.startsWith("mod:")) return Math.floorDiv(score - 10, 2);
    int ability = Math.floorDiv(score - 10, 2);
    int proficiency = 2 + (level - 1) / 4;
    return switch (key) {
      case "level", "class_level" -> level;
      case "spell_level" -> spellLevel;
      case "slot_level" -> slot;
      case "prof" -> proficiency;
      case "spell_mod" -> ability;
      case "spell_attack_bonus" -> ability + proficiency;
      case "spell_dc" -> 8 + ability + proficiency;
      case "hp_max" -> 10L * level;
      case "weapon_hands" -> score >= 10 ? 2 : 1;
      default -> 0;
    };
  }

  private static Type asType(String type) {
    return switch (type) {
      case "int" -> Type.INT;
      case "roll", "dice" -> Type.ROLL;
      case "bool" -> Type.BOOL;
      case "name" -> Type.NAME;
      default -> throw new IllegalArgumentException("Unknown formula type");
    };
  }
}
