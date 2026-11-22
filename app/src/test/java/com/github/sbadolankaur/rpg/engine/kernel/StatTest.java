package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatTest {
  @Test
  void RS_MOD_003_appliesSetAddMultiplyThenMinAndMax() {
    Stat stat =
        new Stat(
            "hp_max",
            10,
            List.of(
                modifier("hp_max", "SET", Ratio.of(12), "set", 1, 1),
                modifier("hp_max", "ADD", Ratio.of(3), "add", 1, 2),
                modifier("hp_max", "MULTIPLY", new Ratio(1, 2), "half", 1, 3),
                modifier("hp_max", "MULTIPLY", Ratio.of(3), "triple", 1, 4),
                modifier("hp_max", "MIN", Ratio.of(8), "minimum", 1, 5),
                modifier("hp_max", "MAX", Ratio.of(9), "maximum", 1, 6)));

    assertThat(stat.value()).isEqualTo(9);
  }

  @Test
  void RS_MOD_003_multipliesAllRatiosAndFloorsOnlyOnce() {
    assertThat(
            new Stat(
                    "hp_max",
                    5,
                    List.of(
                        modifier("hp_max", "MULTIPLY", new Ratio(1, 2), "half", 1, 1),
                        modifier("hp_max", "MULTIPLY", Ratio.of(3), "triple", 1, 2)))
                .value())
        .isEqualTo(7);
    assertThat(
            new Stat(
                    "hp_max",
                    31,
                    List.of(modifier("hp_max", "MULTIPLY", new Ratio(1, 2), "half", 1, 1)))
                .value())
        .isEqualTo(15);
    assertThat(
            new Stat(
                    "speed",
                    -3,
                    List.of(modifier("speed", "MULTIPLY", new Ratio(1, 2), "half", 1, 1)))
                .value())
        .isEqualTo(-2);
  }

  @Test
  void RS_MOD_002_sameStackKeyUsesMostPotentThenMostRecentAndOtherSourcesStack() {
    Stat stat =
        new Stat(
            "speed",
            10,
            List.of(
                modifier("speed", "ADD", Ratio.of(2), "spell", 1, 1),
                modifier("speed", "ADD", Ratio.of(5), "spell", 2, 2),
                modifier("speed", "ADD", Ratio.of(3), "spell", 2, 3),
                modifier("speed", "ADD", Ratio.of(1), "feature", 1, 4)));

    assertThat(stat.value()).isEqualTo(14);
  }

  @Test
  void integerStatOperationsRejectFractionalOperandsExceptMultiply() {
    assertThatThrownBy(
            () ->
                new Modifier(
                    "hp_max",
                    ModOp.SET,
                    new Ratio(1, 2),
                    null,
                    DurationSpec.permanent(),
                    "spell",
                    "spell",
                    1,
                    1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new Modifier(
                "hp_max",
                ModOp.MULTIPLY,
                new Ratio(1, 2),
                null,
                DurationSpec.permanent(),
                "spell",
                "spell",
                1,
                1))
        .isNotNull();
  }

  private static Modifier modifier(
      String target, String op, Ratio value, String stackKey, int potency, long appliedOrder) {
    return new Modifier(
        target,
        ModOp.valueOf(op),
        value,
        null,
        DurationSpec.permanent(),
        stackKey,
        stackKey,
        potency,
        appliedOrder);
  }
}
