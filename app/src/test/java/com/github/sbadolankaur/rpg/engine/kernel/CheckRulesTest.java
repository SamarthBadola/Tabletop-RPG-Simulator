package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import org.junit.jupiter.api.Test;

class CheckRulesTest {
  @Test
  void RS_CHK_005_equalContestTotalsLeaveTheSituationUnchanged() {
    assertThat(CheckRules.contest(14, 14)).isEqualTo(CheckRules.ContestResult.TIE);
    assertThat(CheckRules.contest(17, 12)).isEqualTo(CheckRules.ContestResult.WIN_A);
    assertThat(CheckRules.contest(12, 17)).isEqualTo(CheckRules.ContestResult.WIN_B);
  }

  @Test
  void RS_CHK_006_passiveCheckAddsOrSubtractsFiveForAdvantageMode() {
    assertThat(CheckRules.passive(4, RollMode.NORMAL)).isEqualTo(14);
    assertThat(CheckRules.passive(4, RollMode.ADVANTAGE)).isEqualTo(19);
    assertThat(CheckRules.passive(4, RollMode.DISADVANTAGE)).isEqualTo(9);
  }

  @Test
  void RS_CHK_008_groupCheckSucceedsWhenAtLeastHalfTheGroupSucceeds() {
    assertThat(CheckRules.groupCheck(4, 8)).isTrue();
    assertThat(CheckRules.groupCheck(3, 8)).isFalse();
    assertThat(CheckRules.groupCheck(3, 5)).isTrue();
    assertThat(CheckRules.groupCheck(2, 5)).isFalse();
    assertThat(CheckRules.groupCheck(1, 1)).isTrue();
  }

  @Test
  void checkCountsMustBeValid() {
    assertThatThrownBy(() -> CheckRules.groupCheck(-1, 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CheckRules.groupCheck(6, 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CheckRules.groupCheck(0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
