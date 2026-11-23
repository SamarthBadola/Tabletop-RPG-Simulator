package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvantageSourcesTest {
  @Test
  void RS_ADV_006_onlySourcesMatchingKindAbilityAndConditionsCount() {
    var check = AdvantageSources.RollKind.ABILITY_CHECK;
    var poison =
        new AdvantageSources.Source(
            "poison", RollMode.DISADVANTAGE, check, null, Set.of("poisoned"));
    var help =
        new AdvantageSources.Source("help", RollMode.ADVANTAGE, check, Ability.STR, Set.of());
    var attackOnly =
        new AdvantageSources.Source(
            "attack only", RollMode.DISADVANTAGE, AdvantageSources.RollKind.ATTACK, null, Set.of());
    var sources = List.of(poison, help, attackOnly);

    var healthy = AdvantageSources.resolve(sources, check, Ability.STR, Set.of());
    assertThat(healthy.advantages()).containsExactly("help");
    assertThat(healthy.mode()).isEqualTo(RollMode.ADVANTAGE);
    var poisoned = AdvantageSources.resolve(sources, check, Ability.DEX, Set.of("poisoned"));
    assertThat(poisoned.disadvantages()).containsExactly("poison");
    assertThat(poisoned.mode()).isEqualTo(RollMode.DISADVANTAGE);
  }

  @Test
  void RS_ADV_001_advantageKeepsTheHigherOfTwoForcedDice() {
    AdvantageSources sources = new AdvantageSources(List.of("help"), List.of());
    ForcedDice dice = new ForcedDice(3, 18);
    var result = RollPlan.builder().d20(sources.mode()).build().roll(dice);
    dice.assertExhausted();

    assertThat(sources.mode()).isEqualTo(RollMode.ADVANTAGE);
    assertThat(result.naturalD20()).isEqualTo(18);
  }

  @Test
  void RS_ADV_002_multipleSourcesOfOneKindStillUseOnlyOneExtraDie() {
    AdvantageSources sources = new AdvantageSources(List.of("help", "inspiration"), List.of());
    ForcedDice dice = new ForcedDice(3, 18);
    var result = RollPlan.builder().d20(sources.mode()).build().roll(dice);
    dice.assertExhausted();

    assertThat(result.terms().getFirst().dice()).hasSize(2);
    assertThat(result.naturalD20()).isEqualTo(18);
  }

  @Test
  void RS_ADV_003_advantageAndDisadvantageCancelToOneDie() {
    AdvantageSources sources =
        new AdvantageSources(List.of("help", "inspiration"), List.of("poison"));
    ForcedDice dice = new ForcedDice(13);
    var result = RollPlan.builder().d20(sources.mode()).build().roll(dice);
    dice.assertExhausted();

    assertThat(sources.mode()).isEqualTo(RollMode.NORMAL);
    assertThat(result.terms().getFirst().dice()).hasSize(1);
    assertThat(result.naturalD20()).isEqualTo(13);
  }

  @Test
  void RS_ADV_001_disadvantageKeepsTheLowerOfTwoForcedDice() {
    AdvantageSources sources = new AdvantageSources(List.of(), List.of("poison"));
    ForcedDice dice = new ForcedDice(17, 5);
    var result = RollPlan.builder().d20(sources.mode()).build().roll(dice);
    dice.assertExhausted();

    assertThat(sources.mode()).isEqualTo(RollMode.DISADVANTAGE);
    assertThat(result.naturalD20()).isEqualTo(5);
  }
}
