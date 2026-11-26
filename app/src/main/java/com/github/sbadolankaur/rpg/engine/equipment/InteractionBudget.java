package com.github.sbadolankaur.rpg.engine.equipment;

/**
 * The free object interactions spent in one turn (RS-EQP-014, E-99). A second interaction needs the
 * Use an Object action. Create a new budget at the start of each of the creature's turns.
 */
public record InteractionBudget(int used) {
  public static final InteractionBudget NEW_TURN = new InteractionBudget(0);

  public InteractionBudget {
    if (used < 0) throw new IllegalArgumentException("Used interactions cannot be negative");
  }

  /** The outcome of trying a free interaction: whether it was free and the budget afterwards. */
  public record Result(boolean free, InteractionBudget budget) {}

  /**
   * Tries one free interaction. With ONE_PER_TURN there is one in the whole turn. With PER_ATTACK
   * the first comes before the first attack and one more after each attack already made.
   */
  public Result tryFree(ObjectInteractionRule rule, int attacksMadeThisTurn) {
    int allowed =
        rule == ObjectInteractionRule.ONE_PER_TURN ? 1 : 1 + Math.max(0, attacksMadeThisTurn);
    if (used < allowed) return new Result(true, new InteractionBudget(used + 1));
    return new Result(false, this);
  }
}
