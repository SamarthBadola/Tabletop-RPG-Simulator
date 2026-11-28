package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import org.junit.jupiter.api.Test;

class RegenerationTest {
  private static final CombatHealth DOWN = CombatHealth.of(0, 84, 0);
  private static final Regeneration.State DYING = new Regeneration.State(true, false);

  @Test
  void T_66_aTrollRegainsTenAtTheStartOfItsTurnAndWakesUp() {
    var turn = Regeneration.startOfTurn(DOWN, DYING);
    assertThat(turn.health().current()).isEqualTo(10);
    assertThat(turn.health().state()).isEqualTo(CombatHealth.State.ALIVE);
    assertThat(turn.state()).isEqualTo(Regeneration.State.CLEAR);
    assertThat(turn.regained()).isEqualTo(10);
  }

  @Test
  void WE_G2_fireThatGetsThroughSuppressesItAndTheTrollDies() {
    var state = Regeneration.afterDamage(DYING, Regeneration.suppresses(5));
    assertThat(state.suppressed()).isTrue();
    var turn = Regeneration.startOfTurn(DOWN, state);
    assertThat(turn.health().state()).isEqualTo(CombatHealth.State.DEAD);
    assertThat(turn.regained()).isZero();
  }

  @Test
  void RS_MON_R4_fireThatResistanceReducesToZeroDoesNotSuppress() {
    assertThat(Regeneration.suppresses(0)).isFalse();
    var state = Regeneration.afterDamage(DYING, Regeneration.suppresses(0));
    assertThat(state.suppressed()).isFalse();
    assertThat(Regeneration.startOfTurn(DOWN, state).health().current()).isEqualTo(10);
  }

  @Test
  void RS_MON_R6_healingAboveZeroBeforeTheTurnEndsTheDyingMark() {
    var healed = DOWN.heal(5);
    var state = Regeneration.afterHealing(DYING, healed);
    assertThat(state.dyingByTrait()).isFalse();
  }

  @Test
  void anAwakeTrollRegainsTenUpToItsMaximumAndNothingAtFullHealth() {
    var hurt = CombatHealth.of(80, 84, 0);
    var turn = Regeneration.startOfTurn(hurt, Regeneration.State.CLEAR);
    assertThat(turn.health().current()).isEqualTo(84);
    assertThat(turn.regained()).isEqualTo(4);
    var full = CombatHealth.of(84, 84, 0);
    assertThat(Regeneration.startOfTurn(full, Regeneration.State.CLEAR).regained()).isZero();
  }

  @Test
  void aSuppressedButAwakeTrollSimplyDoesNotRegainThatTurnAndTheSuppressionClears() {
    var hurt = CombatHealth.of(50, 84, 0);
    var turn = Regeneration.startOfTurn(hurt, new Regeneration.State(false, true));
    assertThat(turn.health().current()).isEqualTo(50);
    assertThat(turn.state()).isEqualTo(Regeneration.State.CLEAR);
    assertThat(Regeneration.startOfTurn(turn.health(), turn.state()).health().current())
        .isEqualTo(60);
  }
}
