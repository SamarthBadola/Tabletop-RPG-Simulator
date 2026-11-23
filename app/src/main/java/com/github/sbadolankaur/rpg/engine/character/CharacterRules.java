package com.github.sbadolankaur.rpg.engine.character;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AbilityScores;
import com.github.sbadolankaur.rpg.engine.kernel.HitPointRules;
import com.github.sbadolankaur.rpg.engine.kernel.ProficiencyBonus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CharacterRules {
  private CharacterRules() {}

  public static AbilityScores scores(
      Map<Ability, Integer> allocated,
      Map<Ability, Integer> species,
      Map<Ability, Integer> earned,
      int ceiling) {
    var base = new EnumMap<Ability, Integer>(Ability.class);
    base.putAll(allocated);
    species.forEach(
        (ability, bonus) ->
            base.computeIfPresent(ability, (key, score) -> Math.min(30, score + bonus)));
    return new AbilityScores(base, earned, Map.of(), Map.of(), ceiling);
  }

  public static int maximumHp(AbilityScores scores, List<Integer> rawHealth) {
    return HitPointRules.maximum(rawHealth, scores.modifier(Ability.CON));
  }

  public static Map<Ability, AbilityScores.ImprovementPreview> improvements(
      AbilityScores scores, Map<Ability, Integer> requested) {
    if (requested.isEmpty()
        || requested.size() > 2
        || requested.values().stream().anyMatch(value -> value < 1 || value > 2)
        || requested.values().stream().mapToInt(Integer::intValue).sum() != 2) {
      throw new IllegalArgumentException("Choose one +2 improvement or two +1 improvements");
    }
    var previews = new EnumMap<Ability, AbilityScores.ImprovementPreview>(Ability.class);
    requested.forEach(
        (ability, points) -> previews.put(ability, scores.previewImprovement(ability, points)));
    return Map.copyOf(previews);
  }

  public static int armorClass(AbilityScores scores, Armor armor, boolean shield, boolean defense) {
    int dex = scores.modifier(Ability.DEX);
    int base =
        armor == null
            ? 10 + dex
            : armor.base()
                + switch (armor.dexterityRule()) {
                  case "full" -> dex;
                  case "capped" -> Math.min(dex, 2);
                  case "none" -> 0;
                  default -> throw new IllegalArgumentException("Unknown armor formula");
                };
    return base + (shield ? 2 : 0) + (defense && armor != null ? 1 : 0);
  }

  public static AttackLine attack(
      AbilityScores scores, int level, Weapon weapon, boolean proficient) {
    int str = scores.modifier(Ability.STR);
    int dex = scores.modifier(Ability.DEX);
    int ability = weapon.finesse() ? Math.max(str, dex) : weapon.ranged() ? dex : str;
    int bonus =
        ability + (proficient ? ProficiencyBonus.forLevel(level) : 0) + weapon.attackAdjustment();
    int damageModifier = ability + weapon.damageAdjustment();
    String roll = DiceExpression.parse(weapon.dice()).canonical();
    String damage =
        roll
            + (damageModifier > 0
                ? "+" + damageModifier
                : damageModifier < 0 ? damageModifier : "");
    return new AttackLine(
        weapon.name(), bonus, damage, weapon.damageType(), weapon.thrown(), weapon.criticalOn());
  }

  public record Armor(int base, String dexterityRule) {}

  public record Weapon(
      String name,
      String dice,
      String damageType,
      boolean ranged,
      boolean thrown,
      boolean finesse,
      int attackAdjustment,
      int damageAdjustment,
      int criticalOn) {
    public Weapon(
        String name,
        String dice,
        String damageType,
        boolean ranged,
        boolean thrown,
        boolean finesse,
        int attackAdjustment,
        int damageAdjustment) {
      this(name, dice, damageType, ranged, thrown, finesse, attackAdjustment, damageAdjustment, 20);
    }
  }

  public record AttackLine(
      String name,
      int attackBonus,
      String damage,
      String damageType,
      boolean thrown,
      int criticalOn) {}
}
