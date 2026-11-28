package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.DeathMode;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.monster.ZeroHitPointHook.DamageInstance;
import com.github.sbadolankaur.rpg.engine.monster.ZeroHitPointHook.Defender;
import com.github.sbadolankaur.rpg.engine.monster.ZeroHitPointHook.Result;
import com.github.sbadolankaur.rpg.engine.monster.ZeroHitPointHook.SaveOutcome;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZeroHitPointHookTest {
  private static final Defender ZOMBIE = new Defender(true, false);
  private static final Defender TROLL = new Defender(false, true);
  private static final SaveOutcome PASSES = (ability, dc) -> true;
  private static final SaveOutcome FAILS = (ability, dc) -> false;
  private static final SaveOutcome NEVER =
      (ability, dc) -> {
        throw new AssertionError("No save should have been rolled");
      };

  private static DamageInstance slashing(int amount) {
    return new DamageInstance(amount, false, false, false, false);
  }

  private static CombatHealth zombie(int current) {
    return CombatHealth.of(current, 22, 0);
  }

  @Test
  void WE_G1_undeadFortitudeUsesFivePlusTheDamageAsTheDc() {
    var dcs = new ArrayList<Integer>();
    SaveOutcome recording =
        (ability, dc) -> {
          dcs.add(dc);
          return true;
        };
    var outcome =
        ZeroHitPointHook.apply(
            zombie(2), slashing(7), ZOMBIE, false, DeathMode.DIE_AT_ZERO, recording);
    assertThat(dcs).isEqualTo(List.of(12));
    assertThat(outcome.saveDc()).isEqualTo(12);
    assertThat(outcome.result()).isEqualTo(Result.STAYED_UP);
    assertThat(outcome.health().current()).isEqualTo(1);
    assertThat(outcome.health().state()).isEqualTo(State.ALIVE);
  }

  @Test
  void WE_G1_aFailedSaveLeavesTheZombieDead() {
    var outcome =
        ZeroHitPointHook.apply(zombie(2), slashing(7), ZOMBIE, false, DeathMode.DIE_AT_ZERO, FAILS);
    assertThat(outcome.result()).isEqualTo(Result.NONE);
    assertThat(outcome.health().state()).isEqualTo(State.DEAD);
    assertThat(outcome.saveDc()).isEqualTo(12);
  }

  @Test
  void RS_MON_R1_aCriticalHitAllowsNoSave() {
    var critical = new DamageInstance(7, true, false, false, false);
    var outcome =
        ZeroHitPointHook.apply(zombie(2), critical, ZOMBIE, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(outcome.health().state()).isEqualTo(State.DEAD);
    assertThat(outcome.saveDc()).isZero();
  }

  @Test
  void RS_MON_R2_radiantDamageInTheInstanceAllowsNoSave() {
    var radiant = new DamageInstance(10, false, true, false, false);
    var outcome =
        ZeroHitPointHook.apply(zombie(2), radiant, ZOMBIE, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(outcome.health().state()).isEqualTo(State.DEAD);
  }

  @Test
  void RS_MON_R3_temporaryHitPointsThatAbsorbAllTheDamageKeepTheHookFromRunning() {
    var shielded = CombatHealth.of(2, 22, 10);
    var outcome =
        ZeroHitPointHook.apply(shielded, slashing(7), ZOMBIE, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(outcome.health().current()).isEqualTo(2);
    assertThat(outcome.health().temporary()).isEqualTo(3);
    assertThat(outcome.result()).isEqualTo(Result.NONE);
  }

  @Test
  void T_65_theDcCountsTheDamageThatTemporaryHitPointsAbsorbed() {
    var shielded = CombatHealth.of(2, 22, 3);
    var outcome =
        ZeroHitPointHook.apply(shielded, slashing(5), ZOMBIE, false, DeathMode.DIE_AT_ZERO, PASSES);
    assertThat(outcome.saveDc()).isEqualTo(10);
    assertThat(outcome.result()).isEqualTo(Result.STAYED_UP);
  }

  @Test
  void T_64_instantDeathIsCheckedFirstAndNeverReplaced() {
    var outcome =
        ZeroHitPointHook.apply(
            zombie(2), slashing(24), ZOMBIE, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(outcome.health().state()).isEqualTo(State.DEAD);
    var troll =
        ZeroHitPointHook.apply(
            CombatHealth.of(5, 84, 0),
            slashing(89 + 84 - 5),
            TROLL,
            false,
            DeathMode.DIE_AT_ZERO,
            NEVER);
    assertThat(troll.health().state()).isEqualTo(State.DEAD);
    assertThat(troll.dyingByTrait()).isFalse();
  }

  @Test
  void T_64_theHandlersStopAtTheFirstOneThatApplies() {
    var both = new Defender(true, true);
    var stayUp =
        ZeroHitPointHook.apply(zombie(2), slashing(7), both, false, DeathMode.DIE_AT_ZERO, PASSES);
    assertThat(stayUp.result()).isEqualTo(Result.STAYED_UP);
    assertThat(stayUp.dyingByTrait()).isFalse();
    var critical = new DamageInstance(7, true, false, false, false);
    var delayed =
        ZeroHitPointHook.apply(zombie(2), critical, both, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(delayed.result()).isEqualTo(Result.DELAYED_DEATH);
    var failedSave =
        ZeroHitPointHook.apply(zombie(2), slashing(7), both, false, DeathMode.DIE_AT_ZERO, FAILS);
    assertThat(failedSave.result()).isEqualTo(Result.DELAYED_DEATH);
  }

  @Test
  void T_64_aHandlerThatDoesNotApplyLeavesTheOrdinaryOutcome() {
    var outcome =
        ZeroHitPointHook.apply(
            CombatHealth.of(3, 20, 0),
            slashing(5),
            Defender.NONE,
            false,
            DeathMode.DIE_AT_ZERO,
            NEVER);
    assertThat(outcome.health().state()).isEqualTo(State.DEAD);
    var notDown =
        ZeroHitPointHook.apply(
            CombatHealth.of(10, 20, 0), slashing(5), ZOMBIE, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(notDown.health().current()).isEqualTo(5);
  }

  @Test
  void T_66_aTrollAtZeroFallsUnconsciousAndIsMarkedAsDyingByTrait() {
    var outcome =
        ZeroHitPointHook.apply(
            CombatHealth.of(4, 84, 0), slashing(9), TROLL, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(outcome.result()).isEqualTo(Result.DELAYED_DEATH);
    assertThat(outcome.dyingByTrait()).isTrue();
    assertThat(outcome.health().current()).isZero();
    assertThat(outcome.health().state()).isEqualTo(State.DYING);
    assertThat(outcome.health().failures()).isZero();
  }

  @Test
  void RS_MON_R5_aMonsterAtZeroMakesNoDeathSavesSoMoreDamageChangesNothing() {
    var down = CombatHealth.of(0, 84, 0);
    var outcome =
        ZeroHitPointHook.apply(down, slashing(9), TROLL, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(outcome.health()).isEqualTo(down);
    var instant =
        ZeroHitPointHook.apply(down, slashing(84), TROLL, false, DeathMode.DIE_AT_ZERO, NEVER);
    assertThat(instant.health().state()).isEqualTo(State.DEAD);
  }

  @Test
  void T_67_theSpiderPoisonLeavesATargetStableAndPoisonedAtZero() {
    var poison = new DamageInstance(9, false, false, true, false);
    var character = CombatHealth.of(6, 30, 0);
    var outcome =
        ZeroHitPointHook.apply(character, poison, Defender.NONE, true, DeathMode.PLAYER, NEVER);
    assertThat(outcome.result()).isEqualTo(Result.STABLE_BY_POISON);
    assertThat(outcome.health().state()).isEqualTo(State.STABLE);
    assertThat(outcome.health().current()).isZero();
    assertThat(outcome.poisonedForOneHour()).isTrue();
  }

  @Test
  void T_67_thePoisonRiderNeedsPoisonDamageAndAnAttackerWithTheRider() {
    var bite = new DamageInstance(9, false, false, false, false);
    var piercing =
        ZeroHitPointHook.apply(
            CombatHealth.of(6, 30, 0), bite, Defender.NONE, true, DeathMode.PLAYER, NEVER);
    assertThat(piercing.health().state()).isEqualTo(State.DYING);
    var poison = new DamageInstance(9, false, false, true, false);
    var otherAttacker =
        ZeroHitPointHook.apply(
            CombatHealth.of(6, 30, 0), poison, Defender.NONE, false, DeathMode.PLAYER, NEVER);
    assertThat(otherAttacker.health().state()).isEqualTo(State.DYING);
    assertThat(otherAttacker.poisonedForOneHour()).isFalse();
  }
}
