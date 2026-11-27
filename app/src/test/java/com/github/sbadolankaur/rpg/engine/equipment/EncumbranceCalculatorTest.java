package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.EncumbranceCalculator.Level;
import com.github.sbadolankaur.rpg.engine.equipment.EncumbranceCalculator.OverCapacityException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EncumbranceCalculatorTest {
  private static final long LB = 100;

  private static List<CarriedItem> pounds(long pounds) {
    return List.of(CarriedItem.of("load", pounds * LB));
  }

  @Test
  void RS_EQP_040_capacityIsFifteenTimesStrengthAndScalesWithSize() {
    assertThat(EncumbranceCalculator.capacity(10, CreatureSize.MEDIUM)).isEqualTo(150 * LB);
    assertThat(EncumbranceCalculator.capacity(10, CreatureSize.SMALL)).isEqualTo(150 * LB);
    assertThat(EncumbranceCalculator.capacity(10, CreatureSize.TINY)).isEqualTo(75 * LB);
    assertThat(EncumbranceCalculator.capacity(10, CreatureSize.LARGE)).isEqualTo(300 * LB);
    assertThat(EncumbranceCalculator.capacity(10, CreatureSize.HUGE)).isEqualTo(300 * LB);
    assertThat(EncumbranceCalculator.liftLimit(10, CreatureSize.MEDIUM)).isEqualTo(300 * LB);
  }

  @Test
  void T_59_offChangesNothingEvenForAHugeLoad() {
    var result =
        EncumbranceCalculator.compute(EncumbranceRule.OFF, 8, CreatureSize.MEDIUM, pounds(1000), 0);
    assertThat(result.level()).isEqualTo(Level.NONE);
    assertThat(result.speedPenalty()).isZero();
    EncumbranceCalculator.checkCanCarry(
        EncumbranceRule.OFF, 8, CreatureSize.MEDIUM, 1000 * LB, 1000 * LB);
  }

  @Test
  void T_59_standardRefusesPastTheLimitAndNamesTheWeightAndTheLimit() {
    EncumbranceCalculator.checkCanCarry(
        EncumbranceRule.STANDARD, 10, CreatureSize.MEDIUM, 140 * LB, 10 * LB);
    try {
      EncumbranceCalculator.checkCanCarry(
          EncumbranceRule.STANDARD, 10, CreatureSize.MEDIUM, 140 * LB, 11 * LB);
    } catch (OverCapacityException e) {
      assertThat(e.weight()).isEqualTo(151 * LB);
      assertThat(e.limit()).isEqualTo(150 * LB);
      return;
    }
    throw new AssertionError("The load was not refused");
  }

  @Test
  void T_59_standardAddsNoSpeedPenaltyOnItsOwn() {
    var result =
        EncumbranceCalculator.compute(
            EncumbranceRule.STANDARD, 10, CreatureSize.MEDIUM, pounds(140), 0);
    assertThat(result.level()).isEqualTo(Level.NONE);
  }

  @Test
  void T_59_variantThresholdsForStrength10MediumAre50And100Pounds() {
    var at =
        (java.util.function.LongFunction<EncumbranceCalculator.Result>)
            weight ->
                EncumbranceCalculator.compute(
                    EncumbranceRule.VARIANT, 10, CreatureSize.MEDIUM, pounds(weight), 0);
    assertThat(at.apply(50).level()).isEqualTo(Level.NONE);
    assertThat(at.apply(51).level()).isEqualTo(Level.ENCUMBERED);
    assertThat(at.apply(51).speedPenalty()).isEqualTo(10);
    assertThat(at.apply(100).level()).isEqualTo(Level.ENCUMBERED);
    assertThat(at.apply(101).level()).isEqualTo(Level.HEAVILY_ENCUMBERED);
    assertThat(at.apply(101).speedPenalty()).isEqualTo(20);
    assertThat(at.apply(101).disadvantageOnPhysicalRolls()).isTrue();
    assertThat(at.apply(51).disadvantageOnPhysicalRolls()).isFalse();
  }

  @Test
  void T_59_variantThresholdsMoveWithStrengthAndSize() {
    var strong =
        EncumbranceCalculator.compute(
            EncumbranceRule.VARIANT, 16, CreatureSize.MEDIUM, pounds(80), 0);
    assertThat(strong.level()).isEqualTo(Level.NONE); // 5 x 16 = 80
    var tiny =
        EncumbranceCalculator.compute(
            EncumbranceRule.VARIANT, 10, CreatureSize.TINY, pounds(26), 0);
    assertThat(tiny.level()).isEqualTo(Level.ENCUMBERED); // 25 lb threshold
    var large =
        EncumbranceCalculator.compute(
            EncumbranceRule.VARIANT, 10, CreatureSize.LARGE, pounds(100), 0);
    assertThat(large.level()).isEqualTo(Level.NONE); // 100 lb threshold
  }

  @Test
  void RS_EQP_043_aBagOfHoldingCountsFifteenPoundsWhateverItHolds() {
    var bag = new CarriedItem("bag-of-holding", 15 * LB, 400 * LB, true);
    var sack = new CarriedItem("sack", 1 * LB, 30 * LB, false);
    assertThat(EncumbranceCalculator.totalWeight(List.of(bag), 0)).isEqualTo(15 * LB);
    assertThat(EncumbranceCalculator.totalWeight(List.of(sack), 0)).isEqualTo(31 * LB);
  }

  @Test
  void RS_EQP_039_fiftyCoinsWeighOnePoundWhateverTheDenomination() {
    assertThat(EncumbranceCalculator.totalWeight(List.of(), 50)).isEqualTo(1 * LB);
    assertThat(EncumbranceCalculator.totalWeight(List.of(), 125)).isEqualTo(250);
    assertThat(EncumbranceCalculator.totalWeight(pounds(10), 100)).isEqualTo(12 * LB);
  }
}
