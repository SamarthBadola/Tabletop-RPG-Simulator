package com.github.sbadolankaur.rpg.engine.monster;

/** The conditions of the special traits in RS-MON-019 to RS-MON-022. */
public final class TraitRules {
  private TraitRules() {}

  /** Pack Tactics (wolf): advantage when an ally is next to the target (RS-MON-019). */
  public static boolean packTacticsAdvantage(boolean allyNextToTarget) {
    return allyNextToTarget;
  }

  /**
   * Martial Advantage (hobgoblin): once per turn, a weapon attack that hits a creature next to an
   * ally deals an extra 2d6. The dice double on a critical hit (RS-MON-020).
   */
  public static boolean martialAdvantageApplies(
      boolean weaponAttackHit, boolean allyNextToTarget, boolean alreadyUsedThisTurn) {
    return weaponAttackHit && allyNextToTarget && !alreadyUsedThisTurn;
  }

  /** Surprise Attack (bugbear): an extra 2d6 in the first round against a surprised target. */
  public static boolean surpriseAttackApplies(boolean hit, int round, boolean targetSurprised) {
    return hit && round == 1 && targetSurprised;
  }

  /**
   * Rampage (gnoll): after a melee attack on its own turn reduces a creature to 0 hit points, a
   * bonus action to move up to half its speed and bite once. Offered only if the bonus action is
   * available (RS-MON-022).
   */
  public static boolean rampageOffered(
      boolean meleeAttack,
      boolean onOwnTurn,
      boolean reducedTargetToZero,
      boolean bonusActionAvailable) {
    return meleeAttack && onOwnTurn && reducedTargetToZero && bonusActionAvailable;
  }
}
