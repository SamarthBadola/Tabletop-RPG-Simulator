package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RatioStatTest {
  @Test
  void proficiencyMultiplierRemainsExactThroughItsModifierPipeline() {
    String key = "skill:stealth:prof_multiplier";
    RatioStat multiplier =
        new RatioStat(
            key,
            new Ratio(1, 2),
            List.of(
                modifier(key, ModOp.ADD, new Ratio(1, 2), "training", 1),
                modifier(key, ModOp.MULTIPLY, new Ratio(3, 2), "expertise", 2),
                modifier(key, ModOp.MIN, Ratio.of(2), "cap", 3)));

    assertThat(multiplier.value()).isEqualTo(new Ratio(3, 2));
    assertThat(ProficiencyBonus.applyMultiplier(3, multiplier.value())).isEqualTo(4);
  }

  private static Modifier modifier(
      String target, ModOp op, Ratio value, String source, long appliedOrder) {
    return new Modifier(
        target, op, value, null, DurationSpec.permanent(), source, source, 1, appliedOrder);
  }
}
