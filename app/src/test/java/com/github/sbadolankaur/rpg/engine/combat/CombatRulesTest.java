package com.github.sbadolankaur.rpg.engine.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.DeathMode;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.dice.CriticalDiceMode;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.kernel.DurationKind;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatRulesTest {
  @Test
  void attacksUseCoverAndNaturalOutcomes() {
    assertThat(
            CombatRolls.attack(5, 18, CombatRolls.Cover.HALF, 20, false, RollMode.NORMAL, draw(15))
                .hit())
        .isTrue();
    var critical =
        CombatRolls.attack(0, 30, CombatRolls.Cover.NONE, 20, false, RollMode.NORMAL, draw(20));
    assertThat(critical.critical()).isTrue();
    assertThat(
            CombatRolls.attack(15, 10, CombatRolls.Cover.NONE, 20, true, RollMode.NORMAL, draw(1))
                .hit())
        .isFalse();
  }

  @Test
  void RS_DMG_003_doublesDiceAndAddsTheModifierOnce() {
    var plan =
        RollPlan.builder()
            .dice("Dagger", "weapon", DiceExpression.parse("1d4"), true)
            .dice("Sneak Attack", "feature", DiceExpression.parse("2d6"), true)
            .fixed("Dexterity", "ability", 3)
            .build();
    assertThat(
            CombatRolls.damage(plan, true, CriticalDiceMode.ROLL_AGAIN_ADD, draw(3, 2, 6, 1, 4, 5))
                .total())
        .isEqualTo(24);
  }

  @Test
  void RS_DMG_005_resistanceFollowsFlatAdjustmentsAndPrecedesVulnerability() {
    assertThat(CombatHealth.damageAmount(25, -5, false, true, false, false)).isEqualTo(10);
    assertThat(CombatHealth.damageAmount(7, 0, false, true, false, false)).isEqualTo(3);
    assertThat(CombatHealth.damageAmount(20, 0, false, true, true, false)).isEqualTo(20);
  }

  @Test
  void RS_DMG_010_distinguishesInstantDeathFromDying() {
    var health = CombatHealth.of(6, 12, 0);
    assertThat(health.damage(18, false, DeathMode.PLAYER, false, false).health().state())
        .isEqualTo(State.DEAD);
    assertThat(health.damage(17, false, DeathMode.PLAYER, false, false).health().state())
        .isEqualTo(State.DYING);
  }

  @Test
  void RS_DMG_012_deathSavesAccumulateAndNaturalTwentyRestoresOneHitPoint() {
    var health = CombatHealth.of(0, 12, 0).deathSave(14, 14).deathSave(6, 6);
    assertThat(health.deathSave(1, 1).state()).isEqualTo(State.DEAD);
    assertThat(health.deathSave(20, 20)).isEqualTo(CombatHealth.of(1, 12, 0));
    assertThat(CombatHealth.of(14, 20, 0).heal(8).current()).isEqualTo(20);
  }

  @Test
  void RS_DMG_013_damageFullyAbsorbedAtZeroStillCountsByDefault() {
    var health = CombatHealth.of(0, 12, 5);
    var hit = health.damage(3, false, DeathMode.PLAYER, false, false);
    assertThat(hit.health().temporary()).isEqualTo(2);
    assertThat(hit.health().failures()).isEqualTo(1);
    assertThat(hit.hitPointsLost()).isZero();
    assertThat(health.damage(3, false, DeathMode.PLAYER, false, true).health().failures()).isZero();
    assertThat(CombatHealth.of(12, 12, 10).temporary(12).temporary()).isEqualTo(12);
    assertThat(CombatHealth.of(12, 12, 10).temporary(3).temporary()).isEqualTo(10);
  }

  @Test
  void RS_ATK_009_offHandDamageIncludesOnlyNegativeModifiersUnlessTheStyleApplies() {
    assertThat(CombatRolls.offHandModifier(3, false)).isZero();
    assertThat(CombatRolls.offHandModifier(-2, false)).isEqualTo(-2);
    assertThat(CombatRolls.offHandModifier(3, true)).isEqualTo(3);
  }

  @Test
  void RS_MOV_005_and_RS_ACT_003_movementCostsAccumulateAndDashTracksEffectiveSpeed() {
    var dash = TurnBudget.start().dash();
    assertThat(dash.movementRemaining(30)).isEqualTo(60);
    assertThat(dash.movementRemaining(15)).isEqualTo(30);
    assertThat(TurnBudget.start().move(15, 30).movementRemaining(30)).isEqualTo(15);
    assertThat(TurnBudget.movementCost(5, true, true, false)).isEqualTo(15);
    var movement = TurnBudget.start().move(20, 60).move(10, 30);
    assertThat(movement.movementRemaining(30)).isZero();
    assertThat(movement.movementRemaining(60)).isEqualTo(30);
  }

  @Test
  void RS_CND_proneAndParalyzedDependOnDistanceAndRangedThreat() {
    var none = java.util.Set.<String>of();
    var prone = java.util.Set.of(CombatConditions.PRONE);
    assertThat(
            CombatConditions.attack(
                    none, prone, 5, true, true, false, false, false, false, RollMode.NORMAL)
                .mode())
        .isEqualTo(RollMode.ADVANTAGE);
    assertThat(
            CombatConditions.attack(
                    none, prone, 10, true, true, false, false, false, false, RollMode.NORMAL)
                .mode())
        .isEqualTo(RollMode.DISADVANTAGE);
    var paralyzed = java.util.Set.of("srd-5.1:paralyzed");
    assertThat(CombatConditions.automaticCritical(paralyzed, 5)).isTrue();
    assertThat(CombatConditions.automaticCritical(paralyzed, 30)).isFalse();
    assertThat(
            CombatConditions.attack(
                    none, none, 5, true, true, true, false, false, false, RollMode.NORMAL)
                .mode())
        .isEqualTo(RollMode.DISADVANTAGE);
  }

  @Test
  void RS_CMB_010_attackBudgetSpendsOneActionAndOneBonus() {
    var attack = TurnBudget.start().attack(2, true);
    assertThat(attack.actions()).isZero();
    assertThat(attack.attacksLeft()).isEqualTo(1);
    assertThat(attack.offHand().attack(2, false).attacksLeft()).isZero();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> attack.offHand().offHand())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void exhaustionUsesCumulativeThresholdsAndRoundsDown() {
    assertThat(Exhaustion.disadvantagesChecks(1)).isTrue();
    assertThat(Exhaustion.disadvantagesAttacksAndSaves(2)).isFalse();
    assertThat(Exhaustion.disadvantagesAttacksAndSaves(3)).isTrue();
    assertThat(Exhaustion.maximumHp(31, 4)).isEqualTo(15);
    assertThat(Exhaustion.speed(35, 2)).isEqualTo(17);
    assertThat(Exhaustion.speed(35, 5)).isZero();
    assertThat(Exhaustion.dead(6)).isTrue();
  }

  @Test
  void conditionDurationChecksAbsoluteClockAndNamedTurnAndConvertsRemainingRounds() {
    UUID actor = UUID.fromString("9b12e767-2991-4076-be03-d8497bec9445");
    UUID other = UUID.fromString("9b12e767-2991-4076-be03-d8497bec9446");
    var clock = ConditionInstance.End.clock(16);
    assertThat(clock.expired(15, 6, actor, ConditionInstance.Boundary.START)).isFalse();
    assertThat(clock.expired(16, 6, actor, ConditionInstance.Boundary.START)).isTrue();
    var turn = ConditionInstance.End.turn(DurationKind.UNTIL_TURN_END, 3, actor);
    assertThat(turn.expired(99, 5, other, ConditionInstance.Boundary.END)).isFalse();
    assertThat(turn.expired(99, 3, actor, ConditionInstance.Boundary.START)).isFalse();
    assertThat(turn.expired(99, 3, actor, ConditionInstance.Boundary.END)).isTrue();
    assertThat(ConditionInstance.End.turn(DurationKind.ROUNDS, 5, actor).afterEncounter(30, 2))
        .isEqualTo(ConditionInstance.End.clock(48));
  }

  private static com.github.sbadolankaur.rpg.engine.dice.RandomSource draw(Integer... values) {
    var remaining = new ArrayDeque<>(List.of(values));
    return bound -> remaining.removeFirst() - 1;
  }
}
