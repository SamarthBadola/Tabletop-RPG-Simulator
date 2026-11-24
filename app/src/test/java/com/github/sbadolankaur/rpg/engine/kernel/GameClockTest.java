package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GameClockTest {
  @Test
  void RS_TIM_001_oneRoundIsSixSecondsAndTenRoundsMakeAMinute() {
    assertThat(GameClock.secondsForRounds(1)).isEqualTo(6);
    assertThat(GameClock.roundsForMinutes(1)).isEqualTo(10);
  }

  @Test
  void RS_TIM_002_oneHourIsSixHundredRounds() {
    assertThat(GameClock.roundsForMinutes(60)).isEqualTo(600);
    assertThat(GameClock.secondsForRounds(600)).isEqualTo(3600);
  }

  @Test
  void exactDawnCrossesOnceAndASecondAdvanceDoesNotRepeatIt() {
    int eightAm = 8 * 3600;
    int sixAm = 6 * 3600;
    var first = GameClock.advance(0, 22 * 3600, eightAm, sixAm);
    assertThat(first.dawns()).containsExactly(22 * 3600L);
    assertThat(first.timeOfDaySeconds()).isEqualTo(sixAm);
    assertThat(GameClock.advance(first.end(), 0, eightAm, sixAm).dawns()).isEmpty();
    assertThat(GameClock.advance(first.end(), 1, eightAm, sixAm).dawns()).isEmpty();
  }

  @Test
  void sequentialAdvancesCrossTheSameDawnsAsTheirSum() {
    int start = 21 * 3600 + 30 * 60;
    int dawn = 6 * 3600;
    for (long initial : new long[] {0, 8 * 3600, 86400, 8 * 86400}) {
      for (long delta : new long[] {0, 1, 4 * 3600, 10 * 3600, 2 * 86400}) {
        long later = 3 * 86400;
        var first = GameClock.advance(initial, delta, start, dawn);
        var second = GameClock.advance(first.end(), later, start, dawn);
        var together = GameClock.advance(initial, delta + later, start, dawn);
        assertThat(second.end()).isEqualTo(together.end());
        assertThat(second.timeOfDaySeconds()).isEqualTo(together.timeOfDaySeconds());
        assertThat(
                java.util.stream.Stream.concat(first.dawns().stream(), second.dawns().stream())
                    .toList())
            .containsExactlyElementsOf(together.dawns());
      }
    }
  }

  @Test
  void clockAdvanceRejectsInvalidTimeAndMoreThanThreeHundredSixtySixDays() {
    assertThatThrownBy(() -> GameClock.advance(0, 367 * 86400L, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GameClock.advance(0, 1, 86400, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timeConversionsRejectNegativeValuesAndDetectOverflow() {
    assertThatThrownBy(() -> GameClock.secondsForRounds(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GameClock.roundsForMinutes(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GameClock.secondsForRounds(Long.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }
}
