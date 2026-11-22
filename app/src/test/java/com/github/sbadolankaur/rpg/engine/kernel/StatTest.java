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
  void newestSetWinsAcrossStackKeysAndOlderSetResumesWhenWinnerEnds() {
    Modifier first = modifier("strength", "SET", Ratio.of(10), "first", 1, 1);
    Modifier second = modifier("strength", "SET", Ratio.of(14), "second", 1, 2);

    assertThat(new Stat("strength", 8, List.of(first, second)).value()).isEqualTo(14);
    assertThat(new Stat("strength", 8, List.of(first)).value()).isEqualTo(10);
    assertThat(
            new Stat(
                    "strength",
                    8,
                    List.of(first, second, modifier("strength", "ADD", Ratio.of(2), "bonus", 1, 3)))
                .value())
        .isEqualTo(16);
    assertThat(
            new Stat(
                    "strength",
                    8,
                    List.of(first, modifier("strength", "ADD", Ratio.of(2), "bonus", 1, 3)))
                .value())
        .isEqualTo(12);
  }

  @Test
  void mostRecentSetWinsEvenWhenItLowersTheStatAndBreakdownNamesSuppressedSets() {
    Modifier earlier = modifier("strength", "SET", Ratio.of(14), "belt", 1, 1);
    Modifier later = modifier("strength", "SET", Ratio.of(1), "curse", 1, 2);

    var breakdown = new Stat("strength", 8, List.of(earlier, later)).breakdown();

    assertThat(breakdown.value()).isEqualTo(1);
    assertThat(breakdown.winningSet()).contains(later);
    assertThat(breakdown.overriddenSets()).containsExactly(earlier);
  }

  @Test
  void setThenMaxAppliesMaxAfterTheWinningSet() {
    assertThat(
            new Stat(
                    "strength",
                    8,
                    List.of(
                        modifier("strength", "SET", Ratio.of(12), "set", 1, 1),
                        modifier("strength", "MAX", Ratio.of(19), "belt", 1, 2)))
                .value())
        .isEqualTo(19);
  }

  @Test
  void sameStackKeySelectsMostPotentSetBeforeConsideringOtherStackKeys() {
    Modifier weakerRecent = modifier("strength", "SET", Ratio.of(20), "same", 1, 3);
    Modifier potentOlder = modifier("strength", "SET", Ratio.of(10), "same", 2, 1);
    Modifier otherKey = modifier("strength", "SET", Ratio.of(14), "other", 1, 2);

    var breakdown =
        new Stat("strength", 8, List.of(weakerRecent, potentOlder, otherKey)).breakdown();

    assertThat(breakdown.value()).isEqualTo(14);
    assertThat(breakdown.winningSet()).contains(otherKey);
    assertThat(breakdown.overriddenSets()).containsExactlyInAnyOrder(weakerRecent, potentOlder);
  }

  @Test
  void zeroBaseOrMultiplierRemainsZero() {
    Modifier triple = modifier("speed", "MULTIPLY", Ratio.of(3), "triple", 1, 1);
    Modifier zero = modifier("speed", "MULTIPLY", Ratio.ZERO, "zero", 1, 2);
    assertThat(new Stat("speed", 0, List.of(triple)).value()).isZero();
    assertThat(new Stat("speed", 25, List.of(triple, zero)).value()).isZero();
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
    assertThatThrownBy(
            () ->
                new Modifier(
                    "hp_max",
                    ModOp.ADD,
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
      String target,
      String op,
      Ratio value,
      String stackKey,
      int potency,
      long applicationSequence) {
    return new Modifier(
        target,
        ModOp.valueOf(op),
        value,
        null,
        DurationSpec.permanent(),
        stackKey,
        stackKey,
        potency,
        applicationSequence);
  }
}
