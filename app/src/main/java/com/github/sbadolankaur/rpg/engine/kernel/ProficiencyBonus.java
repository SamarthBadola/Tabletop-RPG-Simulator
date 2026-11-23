package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.Objects;

/** Ruleset Part A proficiency progression and multiplier calculation. */
public final class ProficiencyBonus {
  private ProficiencyBonus() {}

  /** Returns the total-level proficiency bonus for character levels 1 through 20. */
  public static int forLevel(int totalLevel) {
    if (totalLevel < 1 || totalLevel > 20) {
      throw new IllegalArgumentException("Character level must be between 1 and 20");
    }
    return Math.floorDiv(totalLevel - 1, 4) + 2;
  }

  /** Applies one of the Ruleset multipliers (0, 1/2, 1, or 2) to the bonus alone. */
  public static int applyMultiplier(int proficiencyBonus, int numerator, int denominator) {
    if (denominator <= 0
        || !((numerator == 0)
            || (long) numerator == denominator
            || 2L * numerator == denominator
            || (long) numerator == 2L * denominator)) {
      throw new IllegalArgumentException("Multiplier must be 0, 1/2, 1, or 2");
    }
    return applyMultiplier(proficiencyBonus, new Ratio(numerator, denominator));
  }

  /** Applies a computed ratio-valued skill proficiency multiplier without intermediate rounding. */
  public static int applyMultiplier(int proficiencyBonus, Ratio multiplier) {
    if (proficiencyBonus < 0 || proficiencyBonus > 6) {
      throw new IllegalArgumentException("Proficiency bonus must be between 0 and 6");
    }
    Objects.requireNonNull(multiplier, "multiplier");
    if (multiplier.numerator() < 0) {
      throw new IllegalArgumentException("Proficiency multiplier cannot be negative");
    }
    return multiplier.floorProduct(proficiencyBonus);
  }
}
