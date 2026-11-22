package com.github.sbadolankaur.rpg.character.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.character.application.CharacterCatalog.Data;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore.Proficiency;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CharacterCreation {
  static final String GOLD = "srd-5.1:starting-gold";

  private CharacterCreation() {}

  static Map<Ability, Integer> allocate(List<Integer> scores) {
    if (scores.size() != Ability.values().length) invalid();
    var allocated = new EnumMap<Ability, Integer>(Ability.class);
    for (Ability ability : Ability.values()) {
      int value = scores.get(ability.ordinal());
      if (value < 1 || value > 30) invalid();
      allocated.put(ability, value);
    }
    return Map.copyOf(allocated);
  }

  static List<Proficiency> proficiencies(
      CharacterCatalog catalog,
      String species,
      String classKey,
      String backgroundKey,
      List<String> skills,
      List<String> languages,
      String duplicateMode) {
    Data data = catalog.require("class", classKey);
    Data classProfs = data.object("proficiencies");
    Data skill = classProfs.object("skillChoice");
    Data background =
        backgroundKey == null ? new Data(Map.of()) : catalog.require("background", backgroundKey);
    Data speciesData = catalog.require("species", species);
    var backgroundSkills = background.strings("skills");
    if (skills.size() != skill.number("count")
        || new HashSet<>(skills).size() != skills.size()
        || !skill.strings("from").containsAll(skills)) invalid();

    var actual = new ArrayList<Proficiency>();
    data.strings("savingThrows").forEach(key -> actual.add(new Proficiency("save", key, classKey)));
    classProfs.strings("armor").forEach(key -> actual.add(new Proficiency("armor", key, classKey)));
    classProfs
        .strings("weapons")
        .forEach(key -> actual.add(new Proficiency("weapon", key, classKey)));
    classProfs.strings("tools").forEach(key -> actual.add(new Proficiency("tool", key, classKey)));
    for (String key : skills) {
      if (backgroundSkills.contains(key) && !duplicateMode.equals("OFF")) {
        // The replacement is sent independently so the original class pick remains auditable.
        continue;
      }
      actual.add(new Proficiency("skill", key, classKey));
    }
    for (String key : backgroundSkills) actual.add(new Proficiency("skill", key, backgroundKey));
    if (backgroundKey != null) {
      for (String key : background.strings("tools"))
        actual.add(new Proficiency("tool", key, backgroundKey));
    }
    speciesData
        .strings("languages")
        .forEach(key -> actual.add(new Proficiency("language", key, species)));
    for (String language : languages) {
      if (!List.of(
              "dwarvish",
              "elvish",
              "giant",
              "gnomish",
              "goblin",
              "halfling",
              "orc",
              "abyssal",
              "celestial",
              "draconic",
              "deep_speech",
              "infernal",
              "primordial",
              "sylvan",
              "undercommon")
          .contains(language)) invalid();
    }
    int expectedLanguages =
        catalog
                .optional("ruleTable", species + "-languages")
                .map(table -> table.rows("rows").getFirst().number("count"))
                .orElse(0)
            + background.rows("languages").stream().mapToInt(row -> row.number("count")).sum();
    if (languages.size() != expectedLanguages
        || new HashSet<>(languages).size() != languages.size()) invalid();
    int speciesChoiceCount =
        catalog
            .optional("ruleTable", species + "-languages")
            .map(table -> table.rows("rows").getFirst().number("count"))
            .orElse(0);
    for (int i = 0; i < languages.size(); i++) {
      actual.add(
          new Proficiency(
              "language", languages.get(i), i < speciesChoiceCount ? species : backgroundKey));
    }
    return List.copyOf(actual);
  }

  static List<Proficiency> replacements(
      List<Proficiency> original,
      List<String> classSkills,
      List<String> replacements,
      String classKey) {
    long expected =
        classSkills.stream()
            .filter(
                skill ->
                    original.stream()
                        .noneMatch(
                            p ->
                                p.kind().equals("skill")
                                    && p.key().equals(skill)
                                    && p.source().equals(classKey)))
            .count();
    Set<String> used = new HashSet<>();
    original.stream().filter(p -> p.kind().equals("skill")).forEach(p -> used.add(p.key()));
    if (replacements.size() != expected) invalid();
    var result = new ArrayList<>(original);
    for (String replacement : replacements) {
      if (!CharacterSheets.SKILLS.containsKey(replacement) || !used.add(replacement)) invalid();
      result.add(new Proficiency("skill", replacement, classKey));
    }
    return List.copyOf(result);
  }

  static List<EquipmentGroup> equipment(CharacterCatalog catalog, String classKey) {
    var type = catalog.require("class", classKey);
    var groups = new ArrayList<EquipmentGroup>();
    var slots = type.rows("startingEquipment");
    for (int i = 0; i < slots.size(); i++) {
      Data slot = slots.get(i);
      String key = classKey + "-kit-" + i;
      if (slot.has("fixed")) {
        groups.add(
            new EquipmentGroup(key, 0, List.of(option(catalog, key + "-0", slot.rows("fixed")))));
      } else if (slot.has("options")) {
        var options = new ArrayList<EquipmentOption>();
        var alternatives = slot.alternatives("options");
        for (int selection = 0; selection < alternatives.size(); selection++) {
          options.add(option(catalog, key + "-" + selection, alternatives.get(selection)));
        }
        groups.add(new EquipmentGroup(key, 1, List.copyOf(options)));
      } else {
        var pick = slot.object("pick");
        var choices =
            catalog.choices("item").stream()
                .filter(value -> catalog.matchesEquipmentPick(value.key(), pick.text("category")))
                .toList();
        var options = new ArrayList<EquipmentOption>();
        for (int selection = 0; selection < choices.size(); selection++) {
          var item = choices.get(selection);
          options.add(
              new EquipmentOption(key + "-" + selection, item.label(), Map.of(item.key(), 1)));
        }
        groups.add(new EquipmentGroup(key, pick.number("count"), List.copyOf(options)));
      }
    }
    return List.copyOf(groups);
  }

  static Starter starter(
      CharacterCatalog catalog, String classKey, String backgroundKey, List<String> choices) {
    var inventory = new LinkedHashMap<String, Integer>();
    Data gold = catalog.require("class", classKey).object("startingGold");
    boolean buy = choices.contains(GOLD);
    if (buy && (!gold.has("dice") || choices.size() != 1)) invalid();
    if (!buy) {
      var remaining = new ArrayList<>(choices);
      for (var group : equipment(catalog, classKey)) {
        if (group.choose() == 0) {
          add(inventory, group.options().getFirst().items());
          continue;
        }
        int selected = 0;
        for (var option : group.options()) {
          while (remaining.remove(option.key())) {
            add(inventory, option.items());
            selected++;
          }
        }
        if (selected != group.choose()) invalid();
      }
      if (!remaining.isEmpty()) invalid();
    }
    if (!buy && backgroundKey != null) {
      catalog
          .optional("ruleTable", backgroundKey + "-equipment")
          .ifPresent(
              table -> {
                for (var row : table.rows("rows")) {
                  String key = row.text("name");
                  if (!key.equals("copper")) {
                    catalog.require("item", key);
                    inventory.merge(key, row.number("quantity"), Integer::sum);
                  }
                }
              });
    }
    int backgroundCopper =
        !buy && backgroundKey != null
            ? catalog.optional("ruleTable", backgroundKey + "-equipment").stream()
                .flatMap(table -> table.rows("rows").stream())
                .filter(row -> row.text("name").equals("copper"))
                .mapToInt(row -> row.number("quantity"))
                .findFirst()
                .orElse(0)
            : 0;
    return new Starter(
        Map.copyOf(inventory),
        buy ? gold.text("dice") : null,
        buy ? gold.number("multiplier") : 0,
        backgroundCopper);
  }

  private static EquipmentOption option(CharacterCatalog catalog, String key, List<Data> entries) {
    var items = new LinkedHashMap<String, Integer>();
    var labels = new ArrayList<String>();
    for (Data entry : entries) {
      String item = entry.text("item");
      int count = entry.number("count");
      items.merge(item, count, Integer::sum);
      labels.add(catalog.choice("item", item).label() + (count > 1 ? " × " + count : ""));
    }
    return new EquipmentOption(key, String.join(", ", labels), Map.copyOf(items));
  }

  private static void add(Map<String, Integer> destination, Map<String, Integer> incoming) {
    incoming.forEach((id, count) -> destination.merge(id, count, Integer::sum));
  }

  private static void invalid() {
    throw new CampaignException(Code.VALIDATION_FAILED);
  }

  record Starter(
      Map<String, Integer> items, String goldDice, int goldMultiplier, int backgroundCopper) {}

  public record EquipmentGroup(String key, int choose, List<EquipmentOption> options) {}

  public record EquipmentOption(String key, String label, Map<String, Integer> items) {}
}
