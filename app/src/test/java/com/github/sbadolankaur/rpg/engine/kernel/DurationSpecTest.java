package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DurationSpecTest {
  @Test
  void durationRegistryHasAllApprovedNames() {
    assertThat(DurationKind.values())
        .containsExactly(
            DurationKind.INSTANT,
            DurationKind.ROUNDS,
            DurationKind.CLOCK,
            DurationKind.CONCENTRATION,
            DurationKind.UNTIL_TURN_START,
            DurationKind.UNTIL_TURN_END,
            DurationKind.UNTIL_DISPELLED,
            DurationKind.UNTIL_SHORT_OR_LONG_REST,
            DurationKind.UNTIL_LONG_REST,
            DurationKind.UNTIL_USED,
            DurationKind.PERMANENT,
            DurationKind.WHILE_EQUIPPED,
            DurationKind.WHILE_ATTUNED);
  }

  @Test
  void roundsAndTurnDurationsStoreAbsoluteEndRoundAndNamedCreature() {
    DurationSpec rounds = DurationSpec.rounds(3, 12, "creature-7");
    DurationSpec turnEnd = DurationSpec.untilTurnEnd(12, "creature-7");

    assertThat(rounds.endRound()).hasValue(15);
    assertThat(rounds.creatureKey()).hasValue("creature-7");
    assertThat(turnEnd.endRound()).hasValue(12);
    assertThat(turnEnd.kind()).isEqualTo(DurationKind.UNTIL_TURN_END);
  }

  @Test
  void clockAndConcentrationStoreAbsoluteGameClockSeconds() {
    assertThat(DurationSpec.clock(2, 60, 100).endClockSeconds()).hasValue(220);
    assertThat(DurationSpec.concentration(600, 100).endClockSeconds()).hasValue(700);
  }

  @Test
  void roundsConvertToClockAtSixSecondsPerRemainingRoundWhenEncounterEnds() {
    DurationSpec rounds = DurationSpec.rounds(4, 12, "creature-7");

    DurationSpec clock = rounds.asClockWhenEncounterEnds(100, 2);

    assertThat(clock.kind()).isEqualTo(DurationKind.CLOCK);
    assertThat(clock.endClockSeconds()).hasValue(112);
    assertThatThrownBy(() -> rounds.asClockWhenEncounterEnds(100, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void instantIsNeverStoredAsAnActiveDuration() {
    assertThat(DurationSpec.instant().isStoredActive()).isFalse();
    assertThat(DurationSpec.untilUsed().isStoredActive()).isTrue();
  }

  @Test
  void durationInputsRejectInvalidAmountsAndOverflow() {
    assertThatThrownBy(() -> DurationSpec.rounds(0, 1, "creature-7"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DurationSpec.clock(1, 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DurationSpec.rounds(1, Long.MAX_VALUE, "creature-7"))
        .isInstanceOf(ArithmeticException.class);
  }
}
