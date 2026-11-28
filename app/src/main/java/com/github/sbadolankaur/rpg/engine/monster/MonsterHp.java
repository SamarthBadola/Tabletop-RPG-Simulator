package com.github.sbadolankaur.rpg.engine.monster;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;

/** The hit points a spawned monster starts with (RS-MON-012, E-115). */
public final class MonsterHp {
  private static final String SOURCE = "srd-5.1:monsters";

  private MonsterHp() {}

  /** The campaign setting monsterHp. */
  public enum Mode {
    AVERAGE,
    ROLLED
  }

  /** The starting hit points, and the roll behind them when the mode is ROLLED. */
  public record Start(int hitPoints, RollResult roll) {}

  /**
   * AVERAGE gives the printed average. ROLLED rolls the dice once, with a minimum of 1. The caller
   * records the roll and replays it unchanged, as with every roll (RS-REA-R10).
   */
  public static Start start(HitDice dice, Mode mode, RandomSource source) {
    if (mode == Mode.AVERAGE) return new Start(dice.average(), null);
    var plan =
        RollPlan.builder()
            .bonusDice(
                "Hit points",
                SOURCE,
                DiceExpression.parse(dice.count() + "d" + dice.size().hitDie()))
            .fixed("Constitution", null, dice.bonus())
            .build();
    RollResult roll = plan.roll(source);
    return new Start(Math.max(1, roll.total()), roll);
  }
}
