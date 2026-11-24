package com.github.sbadolankaur.rpg.engine.spell;

import static com.github.sbadolankaur.rpg.engine.spell.SpellTurn.CastingTime.ACTION;
import static com.github.sbadolankaur.rpg.engine.spell.SpellTurn.CastingTime.BONUS_ACTION;
import static com.github.sbadolankaur.rpg.engine.spell.SpellTurn.CastingTime.REACTION;
import static com.github.sbadolankaur.rpg.engine.spell.SpellTurn.Rule.ANY_TURN;
import static com.github.sbadolankaur.rpg.engine.spell.SpellTurn.Rule.OFF;
import static com.github.sbadolankaur.rpg.engine.spell.SpellTurn.Rule.OWN_TURN;
import static com.github.sbadolankaur.rpg.engine.spell.SpellTurn.Rule.REACTION_EXEMPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CastingRulesTest {
  @Test
  void tierIndependentSlotCostAllowsCantripsAndUpcastsAndRefusesSpentSlots() {
    var slots = new SlotPool(Map.of(1, 4, 2, 3, 3, 2), Map.of(1, 3, 2, 3));
    assertThat(slots.availableFrom(1)).containsExactly(1, 3);
    assertThat(slots.canSpend(0, 1)).isFalse();
    assertThat(slots.canSpend(2, 1)).isFalse();
    assertThat(slots.spend(0, 0)).isSameAs(slots);
    var after = slots.spend(2, 3);
    assertThat(after.used()).containsEntry(3, 1);
    assertThat(after.remaining(3)).isOne();
    assertThat(slots.remaining(3)).isEqualTo(2);
    assertThatThrownBy(() -> slots.spend(1, 2)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void we3BonusActionLimitAppliesInBothOrders() {
    var afterHealingWord = SpellTurn.start().record(BONUS_ACTION, 1, true);
    assertThat(afterHealingWord.permits(OWN_TURN, ACTION, 1, true)).isFalse();
    assertThat(afterHealingWord.permits(OWN_TURN, ACTION, 0, true)).isTrue();
    var afterFireball = SpellTurn.start().record(ACTION, 3, true);
    assertThat(afterFireball.permits(OWN_TURN, BONUS_ACTION, 2, true)).isFalse();
    assertThat(afterFireball.permits(OWN_TURN, REACTION, 3, true)).isTrue();
    assertThat(SpellTurn.start().record(ACTION, 0, true).permits(OWN_TURN, BONUS_ACTION, 0, true))
        .isTrue();
  }

  @Test
  void reactionExceptionsRespectWhoseTurnItIsAndTheSelectedRule() {
    var afterMistyStep = SpellTurn.start().record(BONUS_ACTION, 2, true);
    assertThat(afterMistyStep.permits(OWN_TURN, REACTION, 1, true)).isFalse();
    assertThat(afterMistyStep.permits(OWN_TURN, REACTION, 1, false)).isTrue();
    assertThat(afterMistyStep.permits(ANY_TURN, REACTION, 1, false)).isFalse();
    assertThat(afterMistyStep.permits(REACTION_EXEMPT, REACTION, 1, true)).isTrue();
    assertThat(afterMistyStep.permits(OFF, ACTION, 1, true)).isTrue();
    var afterShield = SpellTurn.start().record(REACTION, 1, true);
    assertThat(afterShield.permits(OWN_TURN, BONUS_ACTION, 2, true)).isFalse();
    assertThat(afterShield.permits(REACTION_EXEMPT, BONUS_ACTION, 2, true)).isTrue();
  }

  @Test
  void concentrationUsesTheDamageInstanceAmountAndEndsOnIncapacitationWithoutASave() {
    assertThat(ConcentrationRules.afterDamage(7, false).saveDc()).isEqualTo(10);
    assertThat(ConcentrationRules.afterDamage(27, false).saveDc()).isEqualTo(13);
    assertThat(ConcentrationRules.afterDamage(0, false).requiresSave()).isFalse();
    assertThat(ConcentrationRules.afterDamage(0, true).endsImmediately()).isTrue();
    assertThat(ConcentrationRules.afterDamage(26, true).requiresSave()).isFalse();
  }

  @Test
  void cantripsScaleByTotalCharacterLevel() {
    assertThat(SpellScaling.cantripDice(4)).isOne();
    assertThat(SpellScaling.cantripDice(5)).isEqualTo(2);
    assertThat(SpellScaling.cantripDice(11)).isEqualTo(3);
    assertThat(SpellScaling.cantripDice(17)).isEqualTo(4);
  }
}
