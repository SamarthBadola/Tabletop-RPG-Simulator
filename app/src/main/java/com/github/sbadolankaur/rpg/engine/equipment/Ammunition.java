package com.github.sbadolankaur.rpg.engine.equipment;

/** Ammunition stacks (RS-EQP-024, RS-EQP-044, E-102). */
public final class Ammunition {
  private Ammunition() {}

  /** The stack after one shot. A stack of zero refuses. */
  public static int afterShot(int quantity) {
    if (quantity < 1) throw new IllegalStateException("No ammunition left to fire");
    return quantity - 1;
  }

  /**
   * How many of the expended pieces come back after the battle: half, rounded to the nearest whole
   * number with a half rounding up (an owner decision, C-F3), or none.
   */
  public static int recovered(int expended, AmmunitionRecovery rule) {
    if (expended < 0) throw new IllegalArgumentException("Expended ammunition cannot be negative");
    return rule == AmmunitionRecovery.NONE ? 0 : (expended + 1) / 2;
  }
}
