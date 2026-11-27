package com.github.sbadolankaur.rpg.engine.equipment;

import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AbilityScores;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes armor class from the current state and explains it (RS-EQP-001 to RS-EQP-004, E-94,
 * E-95). Armor class is never stored. The highest eligible base wins, every modifier is added, and
 * the floors apply last. The function is pure: the same input always gives the same breakdown.
 */
public final class ArmorClassCalculator {
  private ArmorClassCalculator() {}

  /** Unarmored: 10 plus Dexterity (RS-EQP-001). It is always a candidate when no armor is worn. */
  public static final BaseRule UNARMORED =
      new BaseRule("Unarmored", 10, List.of(Ability.DEX), true, true);

  public static final BaseRule BARBARIAN =
      new BaseRule(
          "Unarmored Defense (Barbarian)", 10, List.of(Ability.DEX, Ability.CON), true, true);
  public static final BaseRule MONK =
      new BaseRule("Unarmored Defense (Monk)", 10, List.of(Ability.DEX, Ability.WIS), true, false);
  public static final BaseRule DRACONIC =
      new BaseRule("Draconic Resilience", 13, List.of(Ability.DEX), true, true);
  public static final BaseRule MAGE_ARMOR =
      new BaseRule("Mage Armor", 13, List.of(Ability.DEX), true, true);

  /**
   * A base formula that does not come from worn armor. {@code requiresNoArmor} makes it eligible
   * only without body armor and {@code allowsShield} says whether a shield may be held with it.
   */
  public record BaseRule(
      String label,
      int constant,
      List<Ability> abilities,
      boolean requiresNoArmor,
      boolean allowsShield) {
    public BaseRule {
      abilities = List.copyOf(abilities);
    }
  }

  /** When a modifier counts (RS-EQP-003). */
  public enum When {
    ALWAYS,
    WHILE_ARMOR_WORN,
    WHILE_NO_ARMOR_AND_NO_SHIELD
  }

  public record Modifier(String label, int amount, When when) {
    public static Modifier always(String label, int amount) {
      return new Modifier(label, amount, When.ALWAYS);
    }
  }

  /** A floor sets armor class to at least this number, after everything is added (RS-EQP-004). */
  public record Floor(String label, int minimum) {}

  /** What is worn and which formulas and bonuses apply. */
  public record Input(
      AbilityScores scores,
      ArmorSpec armor,
      int armorBonus,
      boolean shield,
      int shieldBonus,
      List<BaseRule> alternativeBases,
      List<Modifier> modifiers,
      List<Floor> floors) {
    public Input {
      Objects.requireNonNull(scores, "scores");
      alternativeBases = List.copyOf(alternativeBases);
      modifiers = List.copyOf(modifiers);
      floors = List.copyOf(floors);
    }

    /** A plain character: the given armor and shield and nothing else. */
    public static Input of(AbilityScores scores, ArmorSpec armor, boolean shield) {
      return new Input(scores, armor, 0, shield, 0, List.of(), List.of(), List.of());
    }
  }

  /** One line of the breakdown: where a part of the number comes from. */
  public record Line(String label, int amount) {}

  public record Breakdown(
      int total, String baseLabel, List<Line> lines, List<String> floorsApplied) {
    public Breakdown {
      lines = List.copyOf(lines);
      floorsApplied = List.copyOf(floorsApplied);
    }
  }

  public static Breakdown compute(Input in) {
    int dex = in.scores().modifier(Ability.DEX);
    boolean armored = in.armor() != null;

    String baseLabel;
    int base;
    if (armored) {
      baseLabel = in.armor().id();
      base = in.armor().baseAc() + in.armor().dexterityRule().apply(dex);
    } else {
      baseLabel = null;
      base = Integer.MIN_VALUE;
      var candidates = new ArrayList<BaseRule>();
      candidates.add(UNARMORED);
      candidates.addAll(in.alternativeBases());
      for (var rule : candidates) {
        if (in.shield() && !rule.allowsShield()) continue;
        int value = rule.constant();
        for (var ability : rule.abilities()) value += in.scores().modifier(ability);
        if (value > base) { // the first of equal bases stays, so the order is fixed
          base = value;
          baseLabel = rule.label();
        }
      }
    }

    var lines = new ArrayList<Line>();
    lines.add(new Line(baseLabel, base));
    int total = base;
    if (armored && in.armorBonus() != 0)
      total = add(lines, total, "Armor +" + in.armorBonus(), in.armorBonus());
    if (in.shield()) {
      total = add(lines, total, "Shield", 2);
      if (in.shieldBonus() != 0)
        total = add(lines, total, "Shield +" + in.shieldBonus(), in.shieldBonus());
    }
    for (var modifier : in.modifiers()) {
      boolean applies =
          switch (modifier.when()) {
            case ALWAYS -> true;
            case WHILE_ARMOR_WORN -> armored;
            case WHILE_NO_ARMOR_AND_NO_SHIELD -> !armored && !in.shield();
          };
      if (applies) total = add(lines, total, modifier.label(), modifier.amount());
    }

    var floorsApplied = new ArrayList<String>();
    for (var floor : in.floors()) {
      if (total < floor.minimum()) {
        total = floor.minimum();
        floorsApplied.add(floor.label());
      }
    }
    return new Breakdown(total, baseLabel, lines, floorsApplied);
  }

  private static int add(List<Line> lines, int total, String label, int amount) {
    lines.add(new Line(label, amount));
    return total + amount;
  }
}
