package com.github.sbadolankaur.rpg.engine.monster;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;

/**
 * Hit point maximum reduction (RS-MON-016, the wight's Life Drain). The caller makes the target's
 * saving throw and calls this only on a failure. The maximum drops by the damage taken, reductions
 * add up, and they last until the target finishes a long rest. At a maximum of 0 the target dies.
 */
public final class LifeDrain {
  private LifeDrain() {}

  /** The health after a drain and the total reduction so far. */
  public record Result(CombatHealth health, int totalReduction, boolean dead) {}

  /**
   * @param baseMaximum the maximum before any reduction
   * @param priorReduction the reduction already in force
   * @param damageTaken the damage the hit dealt
   */
  public static Result apply(
      CombatHealth health, int baseMaximum, int priorReduction, int damageTaken) {
    if (baseMaximum < 1 || priorReduction < 0 || damageTaken < 0) {
      throw new IllegalArgumentException("Invalid hit point maximum reduction");
    }
    int total = priorReduction + damageTaken;
    int newMaximum = baseMaximum - total;
    if (newMaximum <= 0) {
      var dead =
          new CombatHealth(0, Math.max(1, health.maximum()), 0, CombatHealth.State.DEAD, 0, 3);
      return new Result(dead, total, true);
    }
    var next =
        new CombatHealth(
            Math.min(health.current(), newMaximum),
            newMaximum,
            health.temporary(),
            health.state(),
            health.successes(),
            health.failures());
    return new Result(next, total, false);
  }

  /** A long rest ends every reduction. Current hit points stay as they are. */
  public static CombatHealth endOnLongRest(CombatHealth health, int baseMaximum) {
    if (health.state() == CombatHealth.State.DEAD) return health;
    return new CombatHealth(
        health.current(),
        baseMaximum,
        health.temporary(),
        health.state(),
        health.successes(),
        health.failures());
  }
}
