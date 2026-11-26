package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.InteractionBudget.Result;
import org.junit.jupiter.api.Test;

class InteractionBudgetTest {
  @Test
  void T_53_oneFreeInteractionPerTurnAndTheSecondNeedsUseAnObject() {
    Result first = InteractionBudget.NEW_TURN.tryFree(ObjectInteractionRule.ONE_PER_TURN, 0);
    assertThat(first.free()).isTrue();
    Result second = first.budget().tryFree(ObjectInteractionRule.ONE_PER_TURN, 0);
    assertThat(second.free()).isFalse();
    assertThat(second.budget()).isEqualTo(first.budget());
  }

  @Test
  void T_53_aNewTurnResetsTheBudget() {
    var spent = InteractionBudget.NEW_TURN.tryFree(ObjectInteractionRule.ONE_PER_TURN, 0).budget();
    assertThat(spent.tryFree(ObjectInteractionRule.ONE_PER_TURN, 0).free()).isFalse();
    assertThat(InteractionBudget.NEW_TURN.tryFree(ObjectInteractionRule.ONE_PER_TURN, 0).free())
        .isTrue();
  }

  @Test
  void T_53_perAttackAllowsOneBeforeEachAttack() {
    var rule = ObjectInteractionRule.PER_ATTACK;
    var afterFirst = InteractionBudget.NEW_TURN.tryFree(rule, 0);
    assertThat(afterFirst.free()).isTrue();
    assertThat(afterFirst.budget().tryFree(rule, 0).free()).isFalse(); // no attack made yet
    var second = afterFirst.budget().tryFree(rule, 1); // one attack made: a second draw
    assertThat(second.free()).isTrue();
    assertThat(second.budget().tryFree(rule, 1).free()).isFalse();
    assertThat(second.budget().tryFree(rule, 2).free()).isTrue();
  }

  @Test
  void oneAttackDoesNotGiveAnExtraInteractionUnderOnePerTurn() {
    var spent = InteractionBudget.NEW_TURN.tryFree(ObjectInteractionRule.ONE_PER_TURN, 0).budget();
    assertThat(spent.tryFree(ObjectInteractionRule.ONE_PER_TURN, 3).free()).isFalse();
  }
}
