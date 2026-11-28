package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.DeathMode;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-64 to T-66 through the glue that the attack action uses. */
class HookedDamageTest {
  private static final ZeroHitPointHook.Defender ZOMBIE =
      new ZeroHitPointHook.Defender(true, false);
  private static final ZeroHitPointHook.Defender TROLL = new ZeroHitPointHook.Defender(false, true);

  /** A save that records its DC and gives a fixed answer. */
  private static final class Save implements ZeroHitPointHook.SaveOutcome {
    private final boolean succeeds;
    private final List<Integer> dcs = new ArrayList<>();

    Save(boolean succeeds) {
      this.succeeds = succeeds;
    }

    @Override
    public boolean succeeds(Ability ability, int dc) {
      dcs.add(dc);
      return succeeds;
    }
  }

  private static HookedDamage.Resolution hit(
      CombatHealth before,
      Map<String, Integer> applied,
      boolean critical,
      boolean knockOut,
      ZeroHitPointHook.Defender defender,
      Regeneration.State marks,
      Save save) {
    int amount = applied.values().stream().mapToInt(Integer::intValue).sum();
    var plain = before.damage(amount, critical, DeathMode.DIE_AT_ZERO, knockOut, false);
    return HookedDamage.resolve(
        before, plain, DeathMode.DIE_AT_ZERO, applied, critical, knockOut, defender, marks, save);
  }

  @Test
  void undeadFortitudeSaveUsesTheDamageTakenAndKeepsItAtOne() {
    var save = new Save(true);
    var result =
        hit(
            CombatHealth.of(5, 22, 0),
            Map.of("slashing", 5),
            false,
            false,
            ZOMBIE,
            Regeneration.State.CLEAR,
            save);

    assertThat(save.dcs).containsExactly(10);
    assertThat(result.result()).isEqualTo(ZeroHitPointHook.Result.STAYED_UP);
    assertThat(result.damage().health().current()).isEqualTo(1);
    assertThat(result.damage().health().state()).isEqualTo(CombatHealth.State.ALIVE);
    assertThat(result.damage().damageTaken()).isEqualTo(5);
    assertThat(result.damage().hitPointsLost()).isEqualTo(4);
    assertThat(result.marks()).isNull();
  }

  @Test
  void aFailedSaveLeavesTheZombieDead() {
    var result =
        hit(
            CombatHealth.of(5, 22, 0),
            Map.of("slashing", 5),
            false,
            false,
            ZOMBIE,
            Regeneration.State.CLEAR,
            new Save(false));

    assertThat(result.damage().health().state()).isEqualTo(CombatHealth.State.DEAD);
    assertThat(result.result()).isEqualTo(ZeroHitPointHook.Result.NONE);
  }

  @Test
  void noSaveForACriticalHitOrAnyRadiantDamage() {
    var critical = new Save(true);
    var crit =
        hit(
            CombatHealth.of(5, 22, 0),
            Map.of("slashing", 5),
            true,
            false,
            ZOMBIE,
            Regeneration.State.CLEAR,
            critical);
    assertThat(critical.dcs).isEmpty();
    assertThat(crit.damage().health().state()).isEqualTo(CombatHealth.State.DEAD);

    var radiantSave = new Save(true);
    var radiant =
        hit(
            CombatHealth.of(10, 22, 0),
            Map.of("slashing", 6, "radiant", 4),
            false,
            false,
            ZOMBIE,
            Regeneration.State.CLEAR,
            radiantSave);
    assertThat(radiantSave.dcs).isEmpty();
    assertThat(radiant.damage().health().state()).isEqualTo(CombatHealth.State.DEAD);
  }

  @Test
  void temporaryHitPointsThatAbsorbEverythingNeverReachTheHook() {
    var save = new Save(true);
    var result =
        hit(
            CombatHealth.of(5, 22, 8),
            Map.of("slashing", 6),
            false,
            false,
            ZOMBIE,
            Regeneration.State.CLEAR,
            save);

    assertThat(save.dcs).isEmpty();
    assertThat(result.damage().health().current()).isEqualTo(5);
    assertThat(result.damage().health().temporary()).isEqualTo(2);
    assertThat(result.damage().absorbed()).isEqualTo(6);
  }

  @Test
  void instantDeathIsNeverReplaced() {
    var save = new Save(true);
    var result =
        hit(
            CombatHealth.of(5, 22, 0),
            Map.of("slashing", 50),
            false,
            false,
            ZOMBIE,
            Regeneration.State.CLEAR,
            save);

    assertThat(save.dcs).isEmpty();
    assertThat(result.damage().health().state()).isEqualTo(CombatHealth.State.DEAD);
  }

  @Test
  void aKnockOutBlowSkipsTheHook() {
    var save = new Save(true);
    var result =
        hit(
            CombatHealth.of(5, 22, 0),
            Map.of("bludgeoning", 5),
            false,
            true,
            ZOMBIE,
            Regeneration.State.CLEAR,
            save);

    assertThat(save.dcs).isEmpty();
    assertThat(result.damage().health().state()).isEqualTo(CombatHealth.State.STABLE);
  }

  @Test
  void aTrollAtZeroFallsUnconsciousAndIsMarkedDyingByTrait() {
    var save = new Save(true);
    var result =
        hit(
            CombatHealth.of(10, 84, 0),
            Map.of("slashing", 20),
            false,
            false,
            TROLL,
            Regeneration.State.CLEAR,
            save);

    assertThat(save.dcs).isEmpty();
    assertThat(result.result()).isEqualTo(ZeroHitPointHook.Result.DELAYED_DEATH);
    assertThat(result.damage().health().current()).isZero();
    assertThat(result.damage().health().state()).isNotEqualTo(CombatHealth.State.DEAD);
    assertThat(result.marks()).isEqualTo(new Regeneration.State(true, false));
    assertThat(result.damage().hitPointsLost()).isEqualTo(10);
  }

  @Test
  void acidOrFireThatGetsThroughSuppressesRegenerationButNothingElseDoes() {
    var alive = CombatHealth.of(50, 84, 0);
    var fire = hit(alive, Map.of("fire", 5), false, false, TROLL, Regeneration.State.CLEAR, null);
    assertThat(fire.marks()).isEqualTo(new Regeneration.State(false, true));

    var acid = hit(alive, Map.of("acid", 3), false, false, TROLL, Regeneration.State.CLEAR, null);
    assertThat(acid.marks().suppressed()).isTrue();

    var resisted =
        hit(alive, Map.of("fire", 0), false, false, TROLL, Regeneration.State.CLEAR, null);
    assertThat(resisted.marks()).isEqualTo(Regeneration.State.CLEAR);

    var slashing =
        hit(alive, Map.of("slashing", 9), false, false, TROLL, Regeneration.State.CLEAR, null);
    assertThat(slashing.marks()).isEqualTo(Regeneration.State.CLEAR);
  }

  @Test
  void aTrollAlreadyDownStaysAtZeroAndFireStillSuppresses() {
    var down = CombatHealth.of(0, 84, 0);
    var marks = new Regeneration.State(true, false);
    var result = hit(down, Map.of("fire", 4), false, false, TROLL, marks, null);

    assertThat(result.damage().health()).isEqualTo(down);
    assertThat(result.marks()).isEqualTo(new Regeneration.State(true, true));
    assertThat(result.damage().hitPointsLost()).isZero();
  }

  @Test
  void aCreatureWithoutTheTraitsKeepsItsOrdinaryResult() {
    var before = CombatHealth.of(5, 22, 0);
    var plain = before.damage(5, false, DeathMode.DIE_AT_ZERO, false, false);
    var result =
        HookedDamage.resolve(
            before,
            plain,
            DeathMode.DIE_AT_ZERO,
            Map.of("slashing", 5),
            false,
            false,
            ZeroHitPointHook.Defender.NONE,
            Regeneration.State.CLEAR,
            null);

    assertThat(result.damage()).isSameAs(plain);
    assertThat(result.marks()).isNull();
    assertThat(result.result()).isEqualTo(ZeroHitPointHook.Result.NONE);
  }
}
