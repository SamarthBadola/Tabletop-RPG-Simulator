package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The rules that wearing armor adds beyond armor class (RS-EQP-006 to RS-EQP-008, E-96). */
public final class ArmorRules {
  private ArmorRules() {}

  /** What a character has been trained to wear. */
  public enum Training {
    LIGHT,
    MEDIUM,
    HEAVY,
    SHIELDS
  }

  /** The cost of wearing armor or a shield without training. */
  public record Penalty(
      boolean disadvantageOnStrengthAndDexterity, boolean cannotCast, List<String> reasons) {
    public Penalty {
      reasons = List.copyOf(reasons);
    }

    public static final Penalty NONE = new Penalty(false, false, List.of());
  }

  /** Armor or a shield without proficiency (RS-EQP-008). Computed, never stored as a flag. */
  public static Penalty proficiencyPenalty(
      ArmorSpec armor, boolean shield, Set<Training> training) {
    var reasons = new ArrayList<String>();
    if (armor != null && !training.contains(Training.valueOf(armor.category().name()))) {
      reasons.add("Not proficient with " + armor.category() + " armor");
    }
    if (shield && !training.contains(Training.SHIELDS)) {
      reasons.add("Not proficient with shields");
    }
    return reasons.isEmpty() ? Penalty.NONE : new Penalty(true, true, reasons);
  }

  /** Speed lost because the wearer's Strength is below the armor's minimum (RS-EQP-006, C-F7). */
  public static int speedPenalty(ArmorSpec armor, int strengthScore) {
    return armor != null && strengthScore < armor.strengthMinimum() ? 10 : 0;
  }

  /** Whether the armor gives disadvantage on Dexterity (Stealth) checks (RS-EQP-007). */
  public static boolean stealthDisadvantage(ArmorSpec armor) {
    return armor != null && armor.stealthDisadvantage();
  }
}
