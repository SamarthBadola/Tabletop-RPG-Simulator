package com.github.sbadolankaur.rpg.character.application;

import com.github.sbadolankaur.rpg.character.application.CharacterCatalog.Choice;
import com.github.sbadolankaur.rpg.character.application.CharacterCatalog.Data;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore.State;
import com.github.sbadolankaur.rpg.engine.character.CharacterRules;
import com.github.sbadolankaur.rpg.engine.character.CharacterRules.AttackLine;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.Exhaustion;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AbilityScores;
import com.github.sbadolankaur.rpg.engine.kernel.ProficiencyBonus;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class CharacterSheets {
  static final Map<String, Ability> SKILLS =
      Map.ofEntries(
          Map.entry("acrobatics", Ability.DEX), Map.entry("animal_handling", Ability.WIS),
          Map.entry("arcana", Ability.INT), Map.entry("athletics", Ability.STR),
          Map.entry("deception", Ability.CHA), Map.entry("history", Ability.INT),
          Map.entry("insight", Ability.WIS), Map.entry("intimidation", Ability.CHA),
          Map.entry("investigation", Ability.INT), Map.entry("medicine", Ability.WIS),
          Map.entry("nature", Ability.INT), Map.entry("perception", Ability.WIS),
          Map.entry("performance", Ability.CHA), Map.entry("persuasion", Ability.CHA),
          Map.entry("religion", Ability.INT), Map.entry("sleight_of_hand", Ability.DEX),
          Map.entry("stealth", Ability.DEX), Map.entry("survival", Ability.WIS));

  private CharacterSheets() {}

  static AbilityScores scores(State state, CharacterCatalog catalog) {
    return CharacterRules.scores(
        state.base(), catalog.speciesBonuses(state.header().species()), state.improvements(), 30);
  }

  static View derive(State state, CharacterCatalog catalog, SettingsView settings, boolean dm) {
    var header = state.header();
    var scores = scores(state, catalog);
    int level = state.rawHealth().size();
    int proficiency = ProficiencyBonus.forLevel(level);
    var abilities = new EnumMap<Ability, Score>(Ability.class);
    for (Ability ability : Ability.values()) {
      int modifier = scores.modifier(ability);
      boolean proficient =
          proficient(state, "save", ability.name().toLowerCase(java.util.Locale.ROOT));
      abilities.put(
          ability,
          new Score(scores.score(ability), modifier, modifier + (proficient ? proficiency : 0)));
    }
    var skills = new TreeMap<String, Integer>();
    SKILLS.forEach(
        (key, ability) ->
            skills.put(
                key,
                scores.modifier(ability) + (proficient(state, "skill", key) ? proficiency : 0)));

    var features = catalog.features(state.classes(), header.style(), header.background());
    var featureLines = new ArrayList<FeatureLine>();
    for (var feature : features) {
      featureLines.add(
          new FeatureLine(
              feature.key(),
              feature.label(),
              catalog.require("feature", feature.key()).text("text")));
    }
    for (String key : state.feats()) {
      var feat = catalog.require("feat", key);
      featureLines.add(new FeatureLine(key, feat.text("name"), feat.text("text")));
    }
    var effects =
        features.stream()
            .flatMap(feature -> catalog.require("feature", feature.key()).rows("effects").stream())
            .toList();
    var inventory =
        state.inventory().stream()
            .map(
                item -> {
                  var data = catalog.require("item", item.key());
                  return new InventoryLine(
                      item.id(),
                      item.key(),
                      data.text("name"),
                      data.text("category"),
                      item.quantity(),
                      item.slot());
                })
            .toList();
    var armor =
        inventory.stream()
            .filter(item -> "ARMOR".equals(item.slot()))
            .findFirst()
            .map(item -> catalog.require("item", item.key()).object("armorData"))
            .orElse(null);
    boolean shield =
        inventory.stream()
            .anyMatch(item -> "OFF_HAND".equals(item.slot()) && item.category().equals("shield"));
    boolean defensive =
        effects.stream()
            .anyMatch(
                effect -> modifier(effect, "ac") && effect.text("activeWhen").equals("armor_worn"));
    int armorClass =
        CharacterRules.armorClass(
            scores,
            armor == null
                ? null
                : new CharacterRules.Armor(armor.number("baseAc"), armor.text("dexterity")),
            shield,
            defensive);
    int speed = catalog.require("species", header.species()).number("speed");
    if (armor != null && scores.score(Ability.STR) < armor.number("strengthRequired"))
      speed = Math.max(0, speed - 10);

    int critical =
        effects.stream()
            .filter(effect -> modifier(effect, "crit_threshold"))
            .mapToInt(effect -> effect.number("value"))
            .min()
            .orElse(20);
    int attacks =
        effects.stream()
            .filter(effect -> modifier(effect, "attacks_per_action"))
            .mapToInt(effect -> effect.number("value"))
            .max()
            .orElse(1);
    var attackLines = new ArrayList<AttackLine>();
    long heldWeapons =
        inventory.stream()
            .filter(item -> item.slot() != null && item.category().equals("weapon"))
            .count();
    for (var item : inventory) {
      if (!("MAIN_HAND".equals(item.slot()) || "BOTH_HANDS".equals(item.slot()))
          || !item.category().equals("weapon")) continue;
      var data = catalog.require("item", item.key()).object("weaponData");
      boolean ranged = data.text("kind").equals("ranged");
      boolean twoHands = "BOTH_HANDS".equals(item.slot());
      String dice =
          twoHands && data.has("versatileDamage")
              ? data.text("versatileDamage")
              : data.text("damage");
      int hit = ranged ? numericEffect(effects, "attack:ranged_weapon") : 0;
      int damage =
          !ranged && !twoHands && heldWeapons == 1
              ? numericEffect(effects, "damage:melee_weapon")
              : 0;
      if (!ranged && twoHands && dice.contains("d")) {
        int rerollThreshold =
            effects.stream()
                .filter(effect -> effect.text("type").equals("damage_die_reroll"))
                .mapToInt(effect -> effect.number("atOrBelow"))
                .max()
                .orElse(0);
        if (rerollThreshold > 0) dice += "r<=" + rerollThreshold;
      }
      boolean finesse = data.strings("properties").contains("finesse");
      boolean thrown = data.strings("properties").contains("thrown");
      boolean proficient =
          proficient(state, "weapon", data.text("proficiencyGroup"))
              || proficient(state, "weapon", item.key());
      var weapon =
          new CharacterRules.Weapon(
              item.name(),
              dice,
              data.text("damageType"),
              ranged,
              thrown,
              finesse,
              hit,
              damage,
              critical);
      attackLines.add(CharacterRules.attack(scores, level, weapon, proficient));
      if (thrown && !ranged) {
        int thrownBonus =
            settings.get("archeryThrown", Boolean.class)
                ? numericEffect(effects, "attack:ranged_weapon")
                : 0;
        attackLines.add(
            CharacterRules.attack(
                scores,
                level,
                new CharacterRules.Weapon(
                    item.name() + " (thrown)",
                    data.text("damage"),
                    data.text("damageType"),
                    false,
                    true,
                    finesse,
                    thrownBonus,
                    0,
                    critical),
                proficient));
      }
    }
    if (attackLines.isEmpty()) {
      attackLines.add(
          CharacterRules.attack(
              scores,
              level,
              new CharacterRules.Weapon(
                  "Unarmed strike", "1", "bludgeoning", false, false, false, 0, 0),
              true));
    }

    var classes =
        state.classes().stream()
            .map(
                entry ->
                    new ClassLine(
                        entry.key(),
                        catalog.choice("class", entry.key()).label(),
                        entry.level(),
                        entry.subclass() == null
                            ? ""
                            : catalog.choice("subclass", entry.subclass()).label()))
            .toList();
    var hitDiceCount = new TreeMap<Integer, Integer>();
    for (var entry : state.classes()) {
      hitDiceCount.merge(
          catalog.require("class", entry.key()).number("hitDie"), entry.level(), Integer::sum);
    }
    var hitDice =
        state.spentHitDice().stream()
            .map(
                die ->
                    new HitDice(die.size(), die.spent(), hitDiceCount.getOrDefault(die.size(), 0)))
            .toList();
    var resources =
        state.resources().stream()
            .map(
                resource -> {
                  var definition = catalog.require("resource", resource.key());
                  return new ResourceLine(
                      resource.key(), definition.text("name"), resource.used(), 1);
                })
            .toList();
    var conditions =
        java.util.stream.Stream.concat(
                state.conditions().stream(),
                state.combat().effects().stream()
                    .filter(
                        effect ->
                            !effect
                                .end()
                                .expired(
                                    state.combat().gameSeconds(),
                                    0,
                                    null,
                                    ConditionInstance.Boundary.CLOCK))
                    .map(ConditionInstance::key))
            .distinct()
            .map(
                key ->
                    catalog
                        .optional("condition", key)
                        .map(data -> new Choice(key, data.text("name")))
                        .orElseGet(
                            () -> new Choice(key, CombatConditions.names().getOrDefault(key, key))))
            .toList();
    int baseMaximum = CharacterRules.maximumHp(scores, state.rawHealth());
    return new View(
        header.id(),
        header.name(),
        catalog.choice("species", header.species()).label(),
        header.background() == null
            ? ""
            : catalog.choice("background", header.background()).label(),
        level,
        proficiency,
        header.hp(),
        Exhaustion.maximumHp(baseMaximum, state.combat().exhaustion()),
        baseMaximum,
        header.temporaryHp(),
        armorClass,
        scores.modifier(Ability.DEX),
        CombatConditions.speed(
            Exhaustion.speed(speed, state.combat().exhaustion()),
            conditions.stream().map(Choice::key).collect(java.util.stream.Collectors.toSet())),
        speed,
        state.combat().exhaustion(),
        attacks,
        header.copper(),
        header.xp(),
        classes,
        Map.copyOf(abilities),
        Map.copyOf(skills),
        List.copyOf(featureLines),
        List.copyOf(attackLines),
        inventory,
        hitDice,
        resources,
        conditions,
        state.conditions(),
        state.grantedClass(),
        dm);
  }

  private static boolean proficient(State state, String kind, String key) {
    return state.proficiencies().stream()
        .anyMatch(proficiency -> proficiency.kind().equals(kind) && proficiency.key().equals(key));
  }

  private static boolean modifier(Data effect, String stat) {
    return effect.text("type").equals("modifier") && effect.text("stat").equals(stat);
  }

  private static int numericEffect(List<Data> effects, String stat) {
    return effects.stream()
        .filter(effect -> modifier(effect, stat))
        .mapToInt(effect -> effect.number("value"))
        .sum();
  }

  public record FeatureLine(String key, String name, String text) {}

  public record Score(int value, int modifier, int save) {}

  public record ClassLine(String key, String name, int level, String subclass) {}

  public record InventoryLine(
      UUID id, String key, String name, String category, int quantity, String slot) {}

  public record HitDice(int size, int spent, int maximum) {}

  public record ResourceLine(String key, String name, int used, int maximum) {}

  public record View(
      UUID id,
      String name,
      String species,
      String background,
      int level,
      int proficiency,
      int currentHp,
      int maximumHp,
      int baseMaximumHp,
      int temporaryHp,
      int armorClass,
      int initiative,
      int speed,
      int baseSpeed,
      int exhaustion,
      int attacksPerAction,
      long copper,
      long xp,
      List<ClassLine> classes,
      Map<Ability, Score> abilities,
      Map<String, Integer> skills,
      List<FeatureLine> features,
      List<AttackLine> attacks,
      List<InventoryLine> inventory,
      List<HitDice> hitDice,
      List<ResourceLine> resources,
      List<Choice> conditions,
      List<String> baseConditionKeys,
      String grantedClass,
      boolean dm) {}
}
