package com.github.sbadolankaur.rpg.engine.combat;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;

/** One use, with an independent absolute expiry on the game clock. */
public record BardicInspiration(int dieFaces, long endsAt) {
  public BardicInspiration {
    if ((dieFaces != 6 && dieFaces != 8 && dieFaces != 10 && dieFaces != 12) || endsAt < 0)
      throw new IllegalArgumentException("Invalid Bardic Inspiration die");
  }

  public boolean active(long now) {
    return now < endsAt;
  }

  public RollResult roll(RandomSource random) {
    return RollPlan.builder()
        .bonusDice(
            "Bardic Inspiration",
            "srd-5.1:bardic-inspiration",
            DiceExpression.parse("1d" + dieFaces))
        .build()
        .roll(random);
  }

  public static BardicInspiration grant(int bardLevel, long now) {
    int die = bardLevel >= 15 ? 12 : bardLevel >= 10 ? 10 : bardLevel >= 5 ? 8 : 6;
    return new BardicInspiration(die, Math.addExact(now, 600));
  }
}
