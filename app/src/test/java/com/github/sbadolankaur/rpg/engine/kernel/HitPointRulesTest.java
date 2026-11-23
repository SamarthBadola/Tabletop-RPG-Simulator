package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HitPointRulesTest {
  @Test
  void RS_LVL_003_maximumUsesTheRawHitDieValueAndAtLeastOnePerLevel() {
    assertThat(HitPointRules.maximum(List.of(10, 6, 4), 2)).isEqualTo(26);
    assertThat(HitPointRules.maximum(List.of(1), -2)).isEqualTo(1);
  }

  @Test
  void RS_LVL_004_constitutionModifierChangeRecomputesEveryLevel() {
    int before = HitPointRules.maximum(List.of(10, 6, 4), 2);
    int after = HitPointRules.maximum(List.of(10, 6, 4), 3);

    assertThat(before).isEqualTo(26);
    assertThat(after).isEqualTo(29);
  }

  @Test
  void RS_LVL_007_maximumIncreaseRaisesCurrentHpButLossOnlyClampsIt() {
    assertThat(HitPointRules.afterMaximumChange(20, 20, 25)).isEqualTo(25);
    assertThat(HitPointRules.afterMaximumChange(15, 25, 20)).isEqualTo(15);
    assertThat(HitPointRules.afterMaximumChange(20, 20, 12)).isEqualTo(12);
  }

  @Test
  void RS_LVL_007_maximumIncreaseDoesNotRaiseACreatureAtZeroHp() {
    assertThat(HitPointRules.afterMaximumChange(0, 20, 25)).isZero();
  }

  @Test
  void hitPointInputsMustBeValidAndArithmeticMustNotOverflow() {
    assertThatThrownBy(() -> HitPointRules.maximum(List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HitPointRules.maximum(List.of(0), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HitPointRules.afterMaximumChange(-1, 20, 20))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
