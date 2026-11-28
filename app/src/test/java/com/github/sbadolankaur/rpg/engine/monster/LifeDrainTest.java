package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.monster.MonsterActions.Kind;
import org.junit.jupiter.api.Test;

class LifeDrainTest {
  @Test
  void T_79_aFailedSaveLowersTheMaximumByTheDamageTaken() {
    var health = CombatHealth.of(30, 40, 0);
    var result = LifeDrain.apply(health, 40, 0, 6);
    assertThat(result.health().maximum()).isEqualTo(34);
    assertThat(result.health().current()).isEqualTo(30);
    assertThat(result.totalReduction()).isEqualTo(6);
    assertThat(result.dead()).isFalse();
  }

  @Test
  void T_79_currentHitPointsFallToTheNewMaximum() {
    var health = CombatHealth.of(40, 40, 0);
    var result = LifeDrain.apply(health, 40, 0, 10);
    assertThat(result.health().current()).isEqualTo(30);
    assertThat(result.health().maximum()).isEqualTo(30);
  }

  @Test
  void T_79_reductionsAddUp() {
    var first = LifeDrain.apply(CombatHealth.of(40, 40, 0), 40, 0, 10);
    var second = LifeDrain.apply(first.health(), 40, first.totalReduction(), 8);
    assertThat(second.totalReduction()).isEqualTo(18);
    assertThat(second.health().maximum()).isEqualTo(22);
  }

  @Test
  void T_79_aMaximumOfZeroKillsTheTarget() {
    var result = LifeDrain.apply(CombatHealth.of(5, 12, 0), 12, 4, 8);
    assertThat(result.dead()).isTrue();
    assertThat(result.health().state()).isEqualTo(CombatHealth.State.DEAD);
  }

  @Test
  void T_79_aLongRestEndsTheReductionAndKeepsCurrentHitPoints() {
    var drained = LifeDrain.apply(CombatHealth.of(25, 40, 0), 40, 0, 15).health();
    var rested = LifeDrain.endOnLongRest(drained, 40);
    assertThat(rested.maximum()).isEqualTo(40);
    assertThat(rested.current()).isEqualTo(25);
  }

  @Test
  void RS_MON_R23_anOpportunityAttackIsOneMeleeAttackAndNeverMultiattackOrABreathWeapon() {
    assertThat(MonsterActions.canBeOpportunityAttack(Kind.MELEE_WEAPON_ATTACK)).isTrue();
    assertThat(MonsterActions.canBeOpportunityAttack(Kind.UNARMED_STRIKE)).isTrue();
    assertThat(MonsterActions.canBeOpportunityAttack(Kind.MULTIATTACK)).isFalse();
    assertThat(MonsterActions.canBeOpportunityAttack(Kind.BREATH_WEAPON)).isFalse();
    assertThat(MonsterActions.canBeOpportunityAttack(Kind.SPELL)).isFalse();
  }
}
