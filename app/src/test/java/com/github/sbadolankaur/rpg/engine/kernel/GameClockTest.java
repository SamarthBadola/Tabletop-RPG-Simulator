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
  void timeConversionsRejectNegativeValuesAndDetectOverflow() {
    assertThatThrownBy(() -> GameClock.secondsForRounds(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GameClock.roundsForMinutes(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GameClock.secondsForRounds(Long.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }
}
