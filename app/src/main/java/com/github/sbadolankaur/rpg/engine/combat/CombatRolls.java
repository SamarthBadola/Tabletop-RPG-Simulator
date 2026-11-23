package com.github.sbadolankaur.rpg.engine.combat;

import com.github.sbadolankaur.rpg.engine.dice.CriticalDiceMode;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;

public final class CombatRolls {
  private CombatRolls() {}

  public enum Cover {
    NONE(0),
    HALF(2),
    THREE_QUARTERS(5),
    TOTAL(0);

    private final int bonus;

    Cover(int bonus) {
      this.bonus = bonus;
    }

    public int bonus() {
      return bonus;
    }
  }

  public static RollResult d20(String modifier, int bonus, RollMode mode, RandomSource random) {
    return RollPlan.builder().d20(mode).fixed(modifier, null, bonus).build().roll(random);
  }

  public static RollResult coveredSave(int bonus, Cover cover, RollMode mode, RandomSource random) {
    var plan = RollPlan.builder().d20(mode).fixed("Ability modifier", null, bonus);
    if (cover.bonus() != 0) plan.fixed("Cover", null, cover.bonus());
    return plan.build().roll(random);
  }

  public static Attack attack(
      int bonus,
      int armorClass,
      Cover cover,
      int criticalOn,
      boolean criticalOnHit,
      RollMode mode,
      RandomSource random) {
    if (cover == Cover.TOTAL) throw new IllegalArgumentException("Total cover blocks targeting");
    int target = Math.addExact(armorClass, cover.bonus());
    var roll = d20("Attack modifier", bonus, mode, random);
    int natural = roll.naturalD20();
    boolean hit = natural != 1 && (natural >= criticalOn || roll.total() >= target);
    return new Attack(roll, target, hit, hit && (criticalOnHit || natural >= criticalOn));
  }

  public static RollPlan weaponDamage(String label, String source, String notation) {
    var plan = RollPlan.builder();
    for (var term : DiceExpression.parse(notation).terms()) {
      switch (term) {
        case DiceExpression.FixedValue fixed ->
            plan.fixed(label, source, fixed.sign() * fixed.value());
        case DiceExpression.DiceGroup group ->
            plan.dice(label, source, DiceExpression.parse(group.canonical()), true);
      }
    }
    return plan.build();
  }

  public static RollResult damage(
      RollPlan plan, boolean critical, CriticalDiceMode mode, RandomSource random) {
    return (critical ? plan.doubleDiceForCritical(mode) : plan).roll(random);
  }

  public static int offHandModifier(int ability, boolean fightingStyle) {
    return fightingStyle ? ability : Math.min(ability, 0);
  }

  public record Attack(RollResult roll, int target, boolean hit, boolean critical) {}
}
