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

  @Test
  void proficiencyComponentFloorsOnlyAfterMultiplyingItsExactMultiplier() {
    assertThat(ProficiencyBonus.applyMultiplier(3, new Ratio(1, 2))).isEqualTo(1);
    assertThat(ProficiencyBonus.applyMultiplier(3, Ratio.of(2))).isEqualTo(6);
    assertThat(ProficiencyBonus.applyMultiplier(0, new Ratio(7, 3))).isZero();
  }

  @Test
  void ratioBreakdownUsesTheSameSetPrecedenceWithoutRounding() {
    String key = "skill:stealth:prof_multiplier";
    Modifier earlier = modifier(key, ModOp.SET, Ratio.of(2), "expertise", 1);
    Modifier later = modifier(key, ModOp.SET, new Ratio(1, 2), "curse", 2);
    var result = new RatioStat(key, Ratio.ONE, List.of(later, earlier)).breakdown();

    assertThat(result.value()).isEqualTo(new Ratio(1, 2));
    assertThat(result.winningSet()).contains(later);
    assertThat(result.overriddenSets()).containsExactly(earlier);
  }

  private static Modifier modifier(
      String target, ModOp op, Ratio value, String source, long applicationSequence) {
    return new Modifier(
        target, op, value, null, DurationSpec.permanent(), source, source, 1, applicationSequence);
  }
}
