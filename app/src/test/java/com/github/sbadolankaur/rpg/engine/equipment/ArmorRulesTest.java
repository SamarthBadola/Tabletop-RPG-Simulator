package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.ArmorRules.Penalty;
import com.github.sbadolankaur.rpg.engine.equipment.ArmorRules.Training;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArmorRulesTest {
  private static final ArmorSpec CHAIN_MAIL =
      new ArmorSpec("chain-mail", ArmorCategory.HEAVY, 16, DexterityRule.NONE, 13, true);
  private static final ArmorSpec LEATHER =
      new ArmorSpec("leather", ArmorCategory.LIGHT, 11, DexterityRule.FULL, 0, false);

  @Test
  void T_51_wearingArmorWithoutProficiencyGivesDisadvantageAndBlocksCasting() {
    Penalty penalty = ArmorRules.proficiencyPenalty(CHAIN_MAIL, false, Set.of(Training.LIGHT));
    assertThat(penalty.disadvantageOnStrengthAndDexterity()).isTrue();
    assertThat(penalty.cannotCast()).isTrue();
    assertThat(penalty.reasons()).hasSize(1);
  }

  @Test
  void T_51_aShieldWithoutProficiencyHasTheSamePenalty() {
    Penalty penalty = ArmorRules.proficiencyPenalty(LEATHER, true, Set.of(Training.LIGHT));
    assertThat(penalty.cannotCast()).isTrue();
    assertThat(penalty.reasons()).hasSize(1);
    assertThat(ArmorRules.proficiencyPenalty(null, true, Set.of(Training.SHIELDS)))
        .isEqualTo(Penalty.NONE);
  }

  @Test
  void T_51_proficientOrUnarmoredHasNoPenalty() {
    assertThat(
            ArmorRules.proficiencyPenalty(
                CHAIN_MAIL, true, Set.of(Training.HEAVY, Training.SHIELDS)))
        .isEqualTo(Penalty.NONE);
    assertThat(ArmorRules.proficiencyPenalty(null, false, Set.of())).isEqualTo(Penalty.NONE);
  }

  @Test
  void T_51_bothAnArmorAndAShieldCanBeMissingAndBothAreReported() {
    assertThat(ArmorRules.proficiencyPenalty(CHAIN_MAIL, true, Set.of()).reasons()).hasSize(2);
  }

  @Test
  void RS_EQP_006_aStrengthShortfallLowersSpeedByTen() {
    assertThat(ArmorRules.speedPenalty(CHAIN_MAIL, 12)).isEqualTo(10);
    assertThat(ArmorRules.speedPenalty(CHAIN_MAIL, 13)).isZero();
    assertThat(ArmorRules.speedPenalty(LEATHER, 3)).isZero();
    assertThat(ArmorRules.speedPenalty(null, 3)).isZero();
  }

  @Test
  void RS_EQP_007_stealthDisadvantageComesFromTheArmor() {
    assertThat(ArmorRules.stealthDisadvantage(CHAIN_MAIL)).isTrue();
    assertThat(ArmorRules.stealthDisadvantage(LEATHER)).isFalse();
    assertThat(ArmorRules.stealthDisadvantage(null)).isFalse();
  }
}
