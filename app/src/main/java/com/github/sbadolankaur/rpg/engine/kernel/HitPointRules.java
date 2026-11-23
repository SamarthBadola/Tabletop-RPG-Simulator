package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.List;
import java.util.Objects;

/** Part A hit-point maximum derivation and current-hit-point adjustment rules. */
public final class HitPointRules {
  private HitPointRules() {}

  /** Recomputes maximum HP from each stored raw hit-die value and the current Constitution mod. */
  public static int maximum(List<Integer> rawHitDieValues, int constitutionModifier) {
    Objects.requireNonNull(rawHitDieValues, "rawHitDieValues");
    if (rawHitDieValues.isEmpty()) {
      throw new IllegalArgumentException("A character must have at least one hit die");
    }
    long total = 0;
    for (Integer rawValue : rawHitDieValues) {
      if (rawValue == null || rawValue < 1) {
        throw new IllegalArgumentException("Every raw hit-die value must be positive");
      }
      long perLevel = Math.max(1L, Math.addExact((long) rawValue, constitutionModifier));
      total = Math.addExact(total, perLevel);
    }
    return Math.toIntExact(total);
  }

  /** Applies the Part A rule for a changed maximum; at 0 HP, a max increase does not heal. */
  public static int afterMaximumChange(int currentHp, int oldMaximum, int newMaximum) {
    if (currentHp < 0 || oldMaximum < 0 || newMaximum < 0 || currentHp > oldMaximum) {
      throw new IllegalArgumentException("Current and maximum hit points must be consistent");
    }
    if (currentHp == 0) return 0;
    if (newMaximum > oldMaximum) {
      long raised = Math.addExact((long) currentHp, newMaximum - (long) oldMaximum);
      return (int) Math.min(raised, newMaximum);
    }
    return Math.min(currentHp, newMaximum);
  }
}
