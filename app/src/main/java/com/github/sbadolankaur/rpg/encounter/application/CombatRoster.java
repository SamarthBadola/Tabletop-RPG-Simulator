package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.character.application.CharacterSheets.View;
import com.github.sbadolankaur.rpg.character.application.Characters;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.DeathMode;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls;
import com.github.sbadolankaur.rpg.engine.combat.Exhaustion;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class CombatRoster {
  private final Campaigns campaigns;
  private final Characters characters;
  private final CharacterStore characterStore;
  private final JsonMapper json;

  CombatRoster(
      Campaigns campaigns, Characters characters, CharacterStore characterStore, JsonMapper json) {
    this.campaigns = campaigns;
    this.characters = characters;
    this.characterStore = characterStore;
    this.json = json;
  }

  ContentSet content(UUID campaign, UUID actor) {
    return campaigns.contentFor(campaign, actor);
  }

  JsonNode monster(ContentSet content, String key) {
    return content
        .find("monster", key)
        .map(definition -> json.<JsonNode>valueToTree(definition.fields()))
        .orElseThrow(() -> new CampaignException(Code.PACK_NOT_ENABLED));
  }

  Creature load(UUID campaign, UUID actor, Entry entry, ContentSet content) {
    return entry.character() != null
        ? player(campaign, actor, entry, content)
        : npc(entry, content);
  }

  Creature load(
      UUID campaign, UUID actor, Entry entry, ContentSet content, SettingsView effective) {
    if (entry.character() == null) return npc(entry, content);
    return player(
        campaign,
        entry,
        content,
        characters.combatSheet(campaign, actor, entry.character(), content, effective));
  }

  private Creature player(UUID campaign, UUID actor, Entry entry, ContentSet content) {
    return player(campaign, entry, content, characters.sheet(campaign, actor, entry.character()));
  }

  private Creature player(UUID campaign, Entry entry, ContentSet content, View sheet) {
    var details = characterStore.combatMetadata(entry.character());
    var state =
        sheet.currentHp() > 0
            ? State.ALIVE
            : details.failures() == 3
                ? State.DEAD
                : details.recoveryAt() != null ? State.STABLE : State.DYING;
    var health =
        new CombatHealth(
            sheet.currentHp(),
            sheet.maximumHp(),
            sheet.temporaryHp(),
            state,
            state == State.DYING ? details.successes() : 0,
            state == State.DYING ? details.failures() : state == State.DEAD ? 3 : 0);
    var conditionKeys = Set.copyOf(sheet.baseConditionKeys());
    var abilities = new EnumMap<Ability, Integer>(Ability.class);
    var saves = new EnumMap<Ability, Integer>(Ability.class);
    sheet
        .abilities()
        .forEach(
            (ability, score) -> {
              abilities.put(ability, score.modifier());
              saves.put(ability, score.save());
            });
    String size =
        content
            .find(
                "species", characterStore.find(campaign, entry.character()).orElseThrow().species())
            .map(value -> String.valueOf(value.fields().getOrDefault("size", "medium")))
            .orElse("medium");
    Map<String, Integer> resources = new TreeMap<>();
    sheet.resources().forEach(resource -> resources.put(resource.key(), resource.used()));
    return new Creature(
        entry,
        sheet.name(),
        details.owner(),
        sheet.armorClass(),
        sheet.initiative(),
        sheet.baseSpeed(),
        sheet.baseMaximumHp(),
        sheet.attacksPerAction(),
        size,
        new Vitals(
            health,
            details.recoveryAt(),
            conditionKeys,
            details.effects(),
            details.exhaustion(),
            details.inspiration()),
        abilities,
        saves,
        sheet.skills(),
        sheet.skills().getOrDefault("perception", abilities.get(Ability.WIS)),
        wearsNoisyArmor(sheet, content),
        playerAttacks(sheet, content),
        resources,
        List.of(),
        Set.of(),
        DeathMode.PLAYER,
        sheet.features().stream().map(view -> view.key()).collect(Collectors.toUnmodifiableSet()),
        sheet.inventory().stream()
            .anyMatch(item -> "OFF_HAND".equals(item.slot()) && "shield".equals(item.category())),
        sheet.classes().stream()
            .collect(Collectors.toUnmodifiableMap(line -> line.key(), line -> line.level())));
  }

  private boolean wearsNoisyArmor(View sheet, ContentSet content) {
    return sheet.inventory().stream()
        .filter(item -> "ARMOR".equals(item.slot()))
        .flatMap(item -> content.find("item", item.key()).stream())
        .anyMatch(
            item ->
                json.<JsonNode>valueToTree(item.fields())
                    .path("armorData")
                    .path("stealthDisadvantage")
                    .asBoolean(false));
  }

  private List<AttackOption> playerAttacks(View sheet, ContentSet content) {
    var options = new ArrayList<AttackOption>();
    var main =
        sheet.inventory().stream()
            .filter(
                item ->
                    item.category().equals("weapon")
                        && ("MAIN_HAND".equals(item.slot()) || "BOTH_HANDS".equals(item.slot())))
            .findFirst()
            .orElse(null);
    var mainData = main == null ? json.createObjectNode() : weapon(content, main.key());
    for (var line : sheet.attacks()) {
      boolean thrown = line.name().endsWith(" (thrown)");
      boolean ranged = thrown || mainData.path("kind").asString().equals("ranged");
      int reach = contains(mainData.path("properties"), "reach") ? 10 : 5;
      var range = mainData.path("range");
      boolean light =
          contains(mainData.path("properties"), "light") && !"BOTH_HANDS".equals(main.slot());
      String key = main == null ? "unarmed" : main.key() + (thrown ? "-thrown" : "");
      options.add(
          new AttackOption(
              key,
              line.name(),
              line.attackBonus(),
              line.criticalOn(),
              ranged,
              ranged ? range.path("normal").asInt(5) : reach,
              ranged ? range.path("long").asInt(range.path("normal").asInt(5)) : reach,
              light,
              false,
              false,
              List.of(new DamageDice(line.damage(), line.damageType()))));
    }
    if (contains(mainData.path("properties"), "light")
        && mainData.path("kind").asString().equals("melee")) {
      boolean style =
          sheet.features().stream()
              .anyMatch(
                  feature ->
                      content.find("feature", feature.key()).stream()
                          .anyMatch(
                              definition ->
                                  json.<JsonNode>valueToTree(definition.fields())
                                      .path("effects")
                                      .valueStream()
                                      .anyMatch(
                                          effect ->
                                              effect
                                                  .path("type")
                                                  .asString()
                                                  .equals("two_weapon_fighting"))));
      for (var item : sheet.inventory()) {
        if (!item.category().equals("weapon") || !"OFF_HAND".equals(item.slot())) continue;
        var data = weapon(content, item.key());
        if (!contains(data.path("properties"), "light")
            || !data.path("kind").asString().equals("melee")) continue;
        int ability = sheet.abilities().get(Ability.STR).modifier();
        if (contains(data.path("properties"), "finesse"))
          ability = Math.max(ability, sheet.abilities().get(Ability.DEX).modifier());
        int modifier = CombatRolls.offHandModifier(ability, style);
        String notation = data.path("damage").asString() + (modifier >= 0 ? "+" : "") + modifier;
        int critical = sheet.attacks().getFirst().criticalOn();
        var damage = List.of(new DamageDice(notation, data.path("damageType").asString()));
        options.add(
            new AttackOption(
                item.key() + "-off-hand",
                item.name() + " (off hand)",
                sheet.proficiency() + ability,
                critical,
                false,
                5,
                5,
                false,
                true,
                false,
                damage));
        if (contains(data.path("properties"), "thrown")) {
          var range = data.path("range");
          options.add(
              new AttackOption(
                  item.key() + "-off-hand-thrown",
                  item.name() + " (off hand, thrown)",
                  sheet.proficiency() + ability,
                  critical,
                  true,
                  range.path("normal").asInt(),
                  range.path("long").asInt(),
                  false,
                  true,
                  false,
                  damage));
        }
      }
    }
    return List.copyOf(options);
  }

  private JsonNode weapon(ContentSet content, String key) {
    return json.<JsonNode>valueToTree(content.find("item", key).orElseThrow().fields())
        .path("weaponData");
  }

  private Creature npc(Entry entry, ContentSet content) {
    JsonNode monster = monster(content, entry.monster());
    var abilities = new EnumMap<Ability, Integer>(Ability.class);
    var saves = new EnumMap<Ability, Integer>(Ability.class);
    for (Ability ability : Ability.values()) {
      String key = ability.name().toLowerCase(Locale.ROOT);
      int modifier = Math.floorDiv(monster.path("abilities").path(key).asInt(10) - 10, 2);
      abilities.put(ability, modifier);
      saves.put(ability, monster.path("saves").path(key).asInt(modifier));
    }
    List<AttackOption> attacks = new ArrayList<>();
    int count = 0;
    for (var action : monster.path("actions")) {
      var attack = action.path("attack");
      if (attack.isMissingNode()) continue;
      boolean ranged = attack.path("kind").asString().startsWith("ranged");
      int normal =
          ranged ? attack.path("range").path("normal").asInt(5) : attack.path("reach").asInt(5);
      var dice =
          attack
              .path("hits")
              .valueStream()
              .map(
                  hit ->
                      new DamageDice(
                          hit.path("roll").asString(), hit.path("damageType").asString()))
              .toList();
      attacks.add(
          new AttackOption(
              entry.monster() + "-attack-" + count++,
              action.path("name").asString(),
              Integer.parseInt(attack.path("bonus").asString()),
              20,
              ranged,
              normal,
              ranged ? attack.path("range").path("long").asInt(normal) : normal,
              false,
              false,
              attack.path("kind").asString().endsWith("spell"),
              dice));
    }
    var modifiers =
        monster
            .path("damageModifiers")
            .valueStream()
            .map(
                value ->
                    new Defense(
                        value.path("kind").asString(),
                        value
                            .path("types")
                            .valueStream()
                            .map(JsonNode::asString)
                            .collect(Collectors.toSet()),
                        value.path("unless").asString()))
            .toList();
    Map<String, Integer> skills = new TreeMap<>();
    monster
        .path("skills")
        .properties()
        .forEach(pair -> skills.put(pair.getKey(), pair.getValue().asInt()));
    return new Creature(
        entry,
        monster.path("name").asString(),
        null,
        monster.path("ac").path("value").asInt(),
        abilities.get(Ability.DEX),
        monster.path("speeds").path("walk").asInt(),
        monster.path("hp").path("average").asInt(),
        1,
        monster.path("size").asString(),
        entry.npcVitals(),
        abilities,
        saves,
        skills,
        monster
                .path("senses")
                .path("passivePerception")
                .asInt(10 + skills.getOrDefault("perception", abilities.get(Ability.WIS)))
            - 10,
        false,
        attacks,
        Map.of(),
        modifiers,
        monster
            .path("conditionImmunities")
            .valueStream()
            .map(JsonNode::asString)
            .collect(Collectors.toUnmodifiableSet()),
        entry.tactics().deathMode() == null ? DeathMode.DIE_AT_ZERO : entry.tactics().deathMode(),
        Set.of(),
        false,
        Map.of());
  }

  void writePlayer(Creature current, Vitals next) {
    UUID player = current.entry().character();
    var health = next.health();
    characterStore.combatVitals(
        player,
        health.current(),
        health.temporary(),
        health.successes(),
        health.failures(),
        next.recoveryAt(),
        next.exhaustion(),
        next.effects(),
        next.inspiration());
    for (String key : current.vitals().conditions()) {
      if (!next.conditions().contains(key)) characterStore.condition(player, key, false);
    }
    for (String key : next.conditions()) {
      if (!current.vitals().conditions().contains(key)) characterStore.condition(player, key, true);
    }
  }

  Map<Integer, Integer> spentSlots(Creature creature) {
    return creature.entry().character() == null
        ? Map.of()
        : characterStore.spentSlots(creature.entry().character());
  }

  void spellSlot(Creature creature, int level, int used) {
    characterStore.spellSlot(creature.entry().character(), level, used);
  }

  void setResource(Creature creature, String key, int used) {
    if (!creature.resources().containsKey(key) || used < 0 || used > creature.resourceLimit(key))
      throw new CampaignException(Code.VALIDATION_FAILED);
    characterStore.resource(creature.entry().character(), key, used);
  }

  Map<UUID, String> equipment(Creature creature) {
    UUID character = creature.entry().character();
    return character == null ? Map.of() : characterStore.equippedSlots(character);
  }

  void restoreEquipment(Creature creature, Map<UUID, String> slots) {
    if (creature.entry().character() != null)
      characterStore.equip(creature.entry().character(), slots);
  }

  void dropHeld(Creature creature) {
    if (creature.entry().character() != null) {
      characterStore.dropHeld(creature.entry().character());
    }
  }

  private static boolean contains(JsonNode values, String value) {
    return values.valueStream().anyMatch(element -> element.asString().equals(value));
  }

  record Creature(
      Entry entry,
      String name,
      UUID owner,
      int armor,
      int initiative,
      int baseSpeed,
      int baseMaximumHp,
      int attacksPerAction,
      String size,
      Vitals vitals,
      Map<Ability, Integer> abilities,
      Map<Ability, Integer> saves,
      Map<String, Integer> skills,
      int passivePerceptionBonus,
      boolean noisyArmor,
      List<AttackOption> attacks,
      Map<String, Integer> resources,
      List<Defense> defenses,
      Set<String> immunities,
      DeathMode deathMode,
      Set<String> features,
      boolean shield,
      Map<String, Integer> classLevels) {
    int resourceLimit(String key) {
      return switch (key) {
        case "srd-5.1:action-surge-uses" -> classLevel("srd-5.1:fighter") >= 17 ? 2 : 1;
        case "srd-5.1:bardic-inspiration-uses" -> Math.max(1, abilities.get(Ability.CHA));
        case "srd-5.1:lay-on-hands-pool" -> 5 * classLevel("srd-5.1:paladin");
        default -> 1;
      };
    }

    int classLevel(String key) {
      return classLevels.getOrDefault(key, 0);
    }

    Creature withState(Entry next, Vitals state) {
      return new Creature(
          next,
          name,
          owner,
          armor,
          initiative,
          baseSpeed,
          baseMaximumHp,
          attacksPerAction,
          size,
          state,
          abilities,
          saves,
          skills,
          passivePerceptionBonus,
          noisyArmor,
          attacks,
          resources,
          defenses,
          immunities,
          deathMode,
          features,
          shield,
          classLevels);
    }

    Set<String> conditions() {
      var keys = new HashSet<>(vitals.conditions());
      entry.tactics().conditions().forEach(condition -> keys.add(condition.key()));
      vitals.effects().forEach(condition -> keys.add(condition.key()));
      if (vitals.health().state() != State.ALIVE) keys.add(CombatConditions.UNCONSCIOUS);
      return keys;
    }

    int speed() {
      return CombatConditions.speed(Exhaustion.speed(baseSpeed, vitals.exhaustion()), conditions());
    }

    boolean incapacitated() {
      return CombatConditions.incapacitated(conditions());
    }
  }

  record AttackOption(
      String key,
      String label,
      int bonus,
      int criticalOn,
      boolean ranged,
      int normalRange,
      int longRange,
      boolean light,
      boolean offHand,
      boolean magical,
      List<DamageDice> damage) {}

  record DamageDice(String notation, String type) {}

  record Defense(String kind, Set<String> types, String unless) {
    boolean applies(String type, boolean magical) {
      return types.contains(type) && !(magical && unless.equals("magical"));
    }
  }
}
