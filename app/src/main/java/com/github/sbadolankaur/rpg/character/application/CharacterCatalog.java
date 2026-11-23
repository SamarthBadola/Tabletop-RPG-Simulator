package com.github.sbadolankaur.rpg.character.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore.ClassLevel;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.content.Definition;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CharacterCatalog {
  private final ContentSet content;

  CharacterCatalog(ContentSet content) {
    this.content = content;
  }

  List<Choice> choices(String kind) {
    return content.index().values().stream()
        .filter(value -> value.kind().equals(kind))
        .distinct()
        .map(value -> new Choice(value.key(), value.name()))
        .sorted(Comparator.comparing(Choice::label).thenComparing(Choice::key))
        .toList();
  }

  java.util.Optional<Data> optional(String kind, String key) {
    return content.find(kind, key).map(value -> new Data(value.fields()));
  }

  boolean matchesEquipmentPick(String key, String category) {
    Data item = require("item", key);
    return switch (category) {
      case "martial_weapon" -> item.object("weaponData").text("proficiencyGroup").equals("martial");
      case "simple_weapon" -> item.object("weaponData").text("proficiencyGroup").equals("simple");
      case "armor", "gear" -> item.text("category").equals(category);
      default -> false;
    };
  }

  Data require(String kind, String key) {
    if (key == null) throw new CampaignException(Code.VALIDATION_FAILED);
    var value = content.find(kind, key);
    if (value.isEmpty()) {
      throw new CampaignException(
          choices(kind).isEmpty() ? Code.PACK_NOT_ENABLED : Code.VALIDATION_FAILED);
    }
    return new Data(value.get().fields());
  }

  Choice choice(String kind, String key) {
    Definition value =
        content.find(kind, key).orElseThrow(() -> new CampaignException(Code.PACK_NOT_ENABLED));
    return new Choice(value.key(), value.name());
  }

  List<Choice> subclasses(String classKey) {
    return choices("subclass").stream()
        .filter(value -> require("subclass", value.key()).text("class").equals(classKey))
        .toList();
  }

  List<Choice> styles(String classKey) {
    return content.find("ruleTable", classKey + "-fighting-styles").stream()
        .flatMap(table -> new Data(table.fields()).rows("rows").stream())
        .map(row -> choice("feature", row.text("feature")))
        .toList();
  }

  Map<Ability, Integer> speciesBonuses(String key) {
    var bonuses = new EnumMap<Ability, Integer>(Ability.class);
    for (var entry : require("species", key).rows("abilityBonuses")) {
      if (entry.has("ability")) bonuses.put(ability(entry.text("ability")), entry.number("amount"));
    }
    return Map.copyOf(bonuses);
  }

  List<Choice> features(List<ClassLevel> classes, String style, String background) {
    var keys = new LinkedHashSet<String>();
    if (style != null && !style.isBlank()) keys.add(style);
    if (background != null) keys.addAll(require("background", background).strings("features"));
    for (var level : classes) {
      collectFeatures(keys, require("class", level.key()), level.level());
      if (level.subclass() != null)
        collectFeatures(keys, require("subclass", level.subclass()), level.level());
    }
    return keys.stream().map(key -> choice("feature", key)).toList();
  }

  List<String> resources(String classKey, int level) {
    return resources(classKey, level, null);
  }

  List<String> resources(String classKey, int level, String subclass) {
    var features = new java.util.ArrayList<>(level(classKey, level).strings("features"));
    if (subclass != null) {
      require("subclass", subclass).rows("levels").stream()
          .filter(row -> row.number("level") == level)
          .flatMap(row -> row.strings("features").stream())
          .forEach(features::add);
    }
    return features.stream()
        .map(key -> require("feature", key).object("cost").text("resource"))
        .filter(key -> !key.isEmpty())
        .toList();
  }

  Data level(String classKey, int level) {
    return require("class", classKey).rows("levels").stream()
        .filter(row -> row.number("level") == level)
        .findFirst()
        .orElseThrow(() -> new CampaignException(Code.VALIDATION_FAILED));
  }

  private static void collectFeatures(java.util.Set<String> keys, Data source, int level) {
    for (var row : source.rows("levels")) {
      if (row.number("level") <= level) keys.addAll(row.strings("features"));
    }
  }

  static Ability ability(String name) {
    try {
      return Ability.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      throw new CampaignException(Code.VALIDATION_FAILED);
    }
  }

  public record Choice(String key, String label) {}

  record Data(Map<?, ?> fields) {
    boolean has(String key) {
      return fields.containsKey(key);
    }

    String text(String key) {
      return fields.get(key) instanceof String value ? value : "";
    }

    int number(String key) {
      return fields.get(key) instanceof Number value ? Math.toIntExact(value.longValue()) : 0;
    }

    Data object(String key) {
      return new Data(fields.get(key) instanceof Map<?, ?> value ? value : Map.of());
    }

    List<String> strings(String key) {
      return fields.get(key) instanceof List<?> values
          ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
          : List.of();
    }

    List<List<Data>> alternatives(String key) {
      if (!(fields.get(key) instanceof List<?> values)) return List.of();
      return values.stream()
          .filter(List.class::isInstance)
          .map(
              value ->
                  ((List<?>) value)
                      .stream()
                          .filter(Map.class::isInstance)
                          .map(entry -> new Data((Map<?, ?>) entry))
                          .toList())
          .toList();
    }

    List<Data> rows(String key) {
      return fields.get(key) instanceof List<?> values
          ? values.stream()
              .filter(Map.class::isInstance)
              .map(value -> new Data((Map<?, ?>) value))
              .toList()
          : List.of();
    }
  }
}
