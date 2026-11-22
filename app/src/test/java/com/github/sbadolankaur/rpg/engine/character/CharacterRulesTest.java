package com.github.sbadolankaur.rpg.engine.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CharacterRulesTest {
  @Test
  void rollsSixIndependentGroupsAndValidatesAssignments() {
    var draws = new AtomicInteger();
    var result =
        AbilityGeneration.ROLL_4D6_DROP_LOWEST.roll(bound -> draws.getAndIncrement() % bound);
    assertThat(draws.get()).isEqualTo(24);
    assertThat(result.scores()).containsExactly(9, 13, 15, 9, 13, 15);
    assertThat(result.terms()).hasSize(6);
    AbilityGeneration.ROLL_4D6_DROP_LOWEST.validate(
        List.of(15, 9, 13, 9, 15, 13), result.scores(), false);
    assertThatThrownBy(
            () ->
                AbilityGeneration.ROLL_4D6_DROP_LOWEST.validate(
                    List.of(15, 15, 14, 9, 15, 14), result.scores(), false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pointBuyAndManualRespectTheirOwnBounds() {
    AbilityGeneration.POINT_BUY.validate(List.of(15, 14, 13, 12, 10, 8), List.of(), false);
    assertThatThrownBy(
            () ->
                AbilityGeneration.POINT_BUY.validate(
                    List.of(15, 15, 15, 15, 8, 8), List.of(), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                AbilityGeneration.MANUAL.validate(
                    List.of(18, 18, 18, 18, 18, 18), List.of(), false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fighterDerivedValuesAndLevelFourLossUseTheExistingKernel() {
    var scores =
        CharacterRules.scores(
            Map.of(
                Ability.STR,
                19,
                Ability.DEX,
                14,
                Ability.CON,
                16,
                Ability.INT,
                8,
                Ability.WIS,
                12,
                Ability.CHA,
                10),
            Map.of(),
            Map.of(),
            30);
    var losses = CharacterRules.improvements(scores, Map.of(Ability.STR, 2));
    assertThat(losses.get(Ability.STR).lost()).isEqualTo(1);
    assertThat(losses.get(Ability.STR).applied()).isEqualTo(1);
    var levelFive =
        CharacterRules.scores(
            Map.of(
                Ability.STR,
                19,
                Ability.DEX,
                14,
                Ability.CON,
                16,
                Ability.INT,
                8,
                Ability.WIS,
                12,
                Ability.CHA,
                10),
            Map.of(),
            Map.of(Ability.STR, 1),
            30);
    assertThat(CharacterRules.maximumHp(levelFive, List.of(10, 6, 6, 6, 6))).isEqualTo(49);
    assertThat(
            CharacterRules.armorClass(levelFive, new CharacterRules.Armor(16, "none"), true, true))
        .isEqualTo(19);
    var attack =
        CharacterRules.attack(
            levelFive,
            5,
            new CharacterRules.Weapon("Longsword", "1d8", "slashing", false, false, true, 0, 2),
            true);
    assertThat(attack.attackBonus()).isEqualTo(8);
    assertThat(attack.damage()).isEqualTo("1d8+7");
  }
}
