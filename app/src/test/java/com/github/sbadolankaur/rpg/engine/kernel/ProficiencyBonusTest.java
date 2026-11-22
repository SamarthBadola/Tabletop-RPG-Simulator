package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProficiencyBonusTest {
  @ParameterizedTest
  @CsvSource({"1,2", "4,2", "5,3", "8,3", "9,4", "12,4", "13,5", "16,5", "17,6", "20,6"})
  void RS_PRF_001_proficiencyBonusUsesTotalLevelBands(int level, int expectedBonus) {
    assertThat(ProficiencyBonus.forLevel(level)).isEqualTo(expectedBonus);
  }

  @Test
  void proficiencyLevelMustBeWithinTheRulesetRange() {
    assertThatThrownBy(() -> ProficiencyBonus.forLevel(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProficiencyBonus.forLevel(21))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({"0,1,2,0", "2,1,2,1", "3,1,1,3", "3,2,1,6"})
  void RS_PRF_003_and_RS_PRF_004_multiplyOnlyTheProficiencyComponent(
      int proficiency, int numerator, int denominator, int expected) {
    assertThat(ProficiencyBonus.applyMultiplier(proficiency, numerator, denominator))
        .isEqualTo(expected);
  }

  @Test
  void ratioValuedProficiencyComponentFloorsTheExactProduct() {
    assertThat(ProficiencyBonus.applyMultiplier(3, new Ratio(1, 2))).isEqualTo(1);
    assertThat(ProficiencyBonus.applyMultiplier(3, new Ratio(2, 1))).isEqualTo(6);
  }
}
