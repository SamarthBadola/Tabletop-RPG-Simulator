package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import org.junit.jupiter.api.Test;

class D20TestResolutionTest {
  @Test
  void RS_SAV_005_forcedSaveHasASourceAndNeedsNoRoll() {
    var failure = new D20Test.SaveOverride(D20Test.AutomaticOutcome.FAILURE, "effect");
    var success = new D20Test.SaveOverride(D20Test.AutomaticOutcome.SUCCESS, "feature");

    assertThat(failure.success()).isFalse();
    assertThat(failure.source()).isEqualTo("effect");
    assertThat(success.success()).isTrue();
  }

  @Test
  void RS_CHK_002_checkTotalIncludesProficiencyAndMeetsDcOnEqualityOrHigher() {
    ForcedDice dice = new ForcedDice(11);
    var roll =
        RollPlan.builder()
            .d20(RollMode.NORMAL)
            .fixed("ability", "Strength", 3)
            .fixed("proficiency", "Athletics", 2)
            .build()
            .roll(dice);
    dice.assertExhausted();

    D20Test.Result result =
        D20Test.resolve(roll, 15, D20Test.Kind.ABILITY_CHECK, D20Test.NaturalMode.OFF);

    assertThat(result.total()).isEqualTo(16);
    assertThat(result.success()).isTrue();
    assertThat(result.automaticOutcome()).isEqualTo(D20Test.AutomaticOutcome.NONE);
  }

  @Test
  void RS_D20_002_naturalTwentyDoesNotAutomaticallySucceedWhenModeIsOff() {
    ForcedDice dice = new ForcedDice(20);
    var roll = RollPlan.builder().d20(RollMode.NORMAL).build().roll(dice);
    dice.assertExhausted();

    D20Test.Result result =
        D20Test.resolve(roll, 21, D20Test.Kind.ABILITY_CHECK, D20Test.NaturalMode.OFF);

    assertThat(result.total()).isEqualTo(20);
    assertThat(result.success()).isFalse();
  }

  @Test
  void RS_D20_002_naturalTwentyAndOneFollowTheEnabledCheckMode() {
    ForcedDice twenty = new ForcedDice(20);
    var highRoll = RollPlan.builder().d20(RollMode.NORMAL).build().roll(twenty);
    twenty.assertExhausted();
    ForcedDice one = new ForcedDice(1);
    var lowRoll =
        RollPlan.builder().d20(RollMode.NORMAL).fixed("bonus", "test", 20).build().roll(one);
    one.assertExhausted();

    D20Test.Result success =
        D20Test.resolve(highRoll, 30, D20Test.Kind.ABILITY_CHECK, D20Test.NaturalMode.CHECKS_ONLY);
    D20Test.Result failure =
        D20Test.resolve(lowRoll, 10, D20Test.Kind.ABILITY_CHECK, D20Test.NaturalMode.CHECKS_ONLY);

    assertThat(success.success()).isTrue();
    assertThat(success.automaticOutcome()).isEqualTo(D20Test.AutomaticOutcome.SUCCESS);
    assertThat(failure.success()).isFalse();
    assertThat(failure.automaticOutcome()).isEqualTo(D20Test.AutomaticOutcome.FAILURE);
  }

  @Test
  void checkOnlyNaturalModeDoesNotChangeSavingThrows() {
    ForcedDice dice = new ForcedDice(20);
    var roll = RollPlan.builder().d20(RollMode.NORMAL).build().roll(dice);
    dice.assertExhausted();

    D20Test.Result result =
        D20Test.resolve(roll, 21, D20Test.Kind.SAVING_THROW, D20Test.NaturalMode.CHECKS_ONLY);

    assertThat(result.success()).isFalse();
    assertThat(result.automaticOutcome()).isEqualTo(D20Test.AutomaticOutcome.NONE);
  }

  @Test
  void RS_SAV_001_dexteritySaveAddsTheAbilityModifierAndComparesWithDc() {
    ForcedDice dice = new ForcedDice(10);
    var roll =
        RollPlan.builder().d20(RollMode.NORMAL).fixed("ability", "Dexterity", 2).build().roll(dice);
    dice.assertExhausted();

    D20Test.Result result =
        D20Test.resolve(roll, 13, D20Test.Kind.SAVING_THROW, D20Test.NaturalMode.OFF);

    assertThat(result.total()).isEqualTo(12);
    assertThat(result.success()).isFalse();
  }
}
