package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmmunitionTest {
  @Test
  void T_60_eachShotUsesOnePiece_andAnEmptyStackRefuses() {
    assertThat(Ammunition.afterShot(20)).isEqualTo(19);
    assertThat(Ammunition.afterShot(1)).isZero();
    try {
      Ammunition.afterShot(0);
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("An empty stack was not refused");
  }

  @Test
  void T_60_recoveryIsHalfRoundedToTheNearestWholeNumberWithAHalfRoundingUp() {
    assertThat(Ammunition.recovered(0, AmmunitionRecovery.HALF)).isZero();
    assertThat(Ammunition.recovered(1, AmmunitionRecovery.HALF)).isEqualTo(1);
    assertThat(Ammunition.recovered(2, AmmunitionRecovery.HALF)).isEqualTo(1);
    assertThat(Ammunition.recovered(5, AmmunitionRecovery.HALF)).isEqualTo(3);
    assertThat(Ammunition.recovered(6, AmmunitionRecovery.HALF)).isEqualTo(3);
    assertThat(Ammunition.recovered(7, AmmunitionRecovery.HALF)).isEqualTo(4);
  }

  @Test
  void T_60_theSettingCanTurnRecoveryOff() {
    assertThat(Ammunition.recovered(9, AmmunitionRecovery.NONE)).isZero();
  }
}
