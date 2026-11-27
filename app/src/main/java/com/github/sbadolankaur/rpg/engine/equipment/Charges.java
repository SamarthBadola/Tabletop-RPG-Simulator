package com.github.sbadolankaur.rpg.engine.equipment;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;

/**
 * Charges of an item (RS-EQP-034, E-106). Spending is part of using the item, recharge happens at
 * dawn, and an item can be destroyed when its last charge is spent.
 */
public final class Charges {
  private static final String SOURCE = "srd-5.1:magic-items";

  private Charges() {}

  /** The current and maximum charges, and whether the item has been destroyed. */
  public record State(int current, int max, boolean destroyed) {
    public State {
      if (max < 0 || current < 0 || current > max) {
        throw new IllegalArgumentException("Charges must be between 0 and the maximum");
      }
    }
  }

  /**
   * The item's data: the dice that dawn returns, with a flat bonus, and the destroy-on-empty rule
   * as a die size and the result that destroys (0 for no such rule).
   */
  public record Rules(String rechargeDice, int rechargeBonus, int destroyDie, int destroyOn) {}

  public record SpendResult(State state, boolean destroyedNow, Integer destroyRoll) {}

  /** A Wand of Magic Missiles spends one charge at level 1 and one more for each level above. */
  public static int magicMissileCost(int spellLevel) {
    if (spellLevel < 1) throw new IllegalArgumentException("A spell level is at least 1");
    return spellLevel;
  }

  public static SpendResult spend(State state, int amount, Rules rules, RandomSource dice) {
    if (amount < 1) throw new IllegalArgumentException("Spend at least one charge");
    if (state.destroyed() || state.current() < amount) {
      throw new IllegalStateException("Not enough charges");
    }
    var next = new State(state.current() - amount, state.max(), false);
    if (next.current() == 0 && rules.destroyDie() > 0) {
      int roll = roll(dice, "1d" + rules.destroyDie(), 0);
      if (roll == rules.destroyOn()) {
        return new SpendResult(new State(0, state.max(), true), true, roll);
      }
      return new SpendResult(next, false, roll);
    }
    return new SpendResult(next, false, null);
  }

  /** Returns charges at dawn, up to the maximum. A destroyed item does not recharge. */
  public static State rechargeAtDawn(State state, Rules rules, RandomSource dice) {
    if (state.destroyed() || rules.rechargeDice() == null) return state;
    int regained = Math.max(0, roll(dice, rules.rechargeDice(), rules.rechargeBonus()));
    return new State(Math.min(state.max(), state.current() + regained), state.max(), false);
  }

  private static int roll(RandomSource dice, String expression, int bonus) {
    var plan =
        RollPlan.builder()
            .bonusDice("Charges", SOURCE, DiceExpression.parse(expression))
            .fixed("Bonus", null, bonus)
            .build();
    return plan.roll(dice).total();
  }
}
