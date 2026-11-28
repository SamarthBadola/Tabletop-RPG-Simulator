package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MonsterTraitsTest {

  private static final Adjacency.Distance GRID =
      (a, b) -> a.equals("w2") ? 5 : a.equals("w3") ? 10 : 30;

  @Test
  void anAllyWithinFiveFeetGivesPackTactics() {
    var answer =
        Adjacency.allyNextToTarget("w1", "pc", List.of(new Adjacency.Ally("w2", false)), GRID);
    assertThat(answer).isEqualTo(Adjacency.Answer.YES);
    assertThat(TraitRules.packTacticsAdvantage(true)).isTrue();
  }

  @Test
  void anIncapacitatedOrDistantAllyDoesNotCount() {
    assertThat(
            Adjacency.allyNextToTarget("w1", "pc", List.of(new Adjacency.Ally("w2", true)), GRID))
        .isEqualTo(Adjacency.Answer.NO);
    assertThat(
            Adjacency.allyNextToTarget("w1", "pc", List.of(new Adjacency.Ally("w3", false)), GRID))
        .isEqualTo(Adjacency.Answer.NO);
  }

  @Test
  void theAttackerIsNeverItsOwnAlly() {
    assertThat(
            Adjacency.allyNextToTarget("w2", "pc", List.of(new Adjacency.Ally("w2", false)), GRID))
        .isEqualTo(Adjacency.Answer.NO);
  }

  @Test
  void withoutAGridTheDmIsAsked() {
    var answer =
        Adjacency.allyNextToTarget(
            "w1", "pc", List.of(new Adjacency.Ally("w2", false)), (a, b) -> null);
    assertThat(answer).isEqualTo(Adjacency.Answer.ASK_DM);
    assertThat(Adjacency.confirmed(answer, true)).isTrue();
    assertThat(Adjacency.confirmed(answer, false)).isFalse();
  }

  @Test
  void martialAdvantageIsOncePerTurn() {
    assertThat(TraitRules.martialAdvantageApplies(true, true, false)).isTrue();
    assertThat(TraitRules.martialAdvantageApplies(true, true, true)).isFalse();
    assertThat(TraitRules.martialAdvantageApplies(false, true, false)).isFalse();
    assertThat(TraitRules.martialAdvantageApplies(true, false, false)).isFalse();
  }

  @Test
  void surpriseAttackIsRoundOneAgainstASurprisedTarget() {
    assertThat(TraitRules.surpriseAttackApplies(true, 1, true)).isTrue();
    assertThat(TraitRules.surpriseAttackApplies(true, 2, true)).isFalse();
    assertThat(TraitRules.surpriseAttackApplies(true, 1, false)).isFalse();
  }

  @Test
  void rampageNeedsAMeleeKillOnItsTurnAndABonusAction() {
    assertThat(TraitRules.rampageOffered(true, true, true, true)).isTrue();
    assertThat(TraitRules.rampageOffered(true, true, true, false)).isFalse();
    assertThat(TraitRules.rampageOffered(false, true, true, true)).isFalse();
    assertThat(TraitRules.rampageOffered(true, false, true, true)).isFalse();
  }

  @Test
  void aSwarmGainsNeitherHealingNorTemporaryHitPoints() {
    var swarm = CombatHealth.of(11, 24, 0);
    assertThat(Swarm.heal(swarm, 8)).isEqualTo(swarm);
    assertThat(Swarm.temporary(swarm, 8)).isEqualTo(swarm);
  }

  @Test
  void aSwarmBitesWeakerAtHalfHitPointsOrFewer() {
    assertThat(Swarm.biteDamage(11, 24, "2d6", "1d6")).isEqualTo("1d6");
    assertThat(Swarm.biteDamage(12, 24, "2d6", "1d6")).isEqualTo("1d6");
    assertThat(Swarm.biteDamage(13, 24, "2d6", "1d6")).isEqualTo("2d6");
  }

  @Test
  void aSwarmHasReachZeroAndEnteringItsSpaceIsDifficult() {
    assertThat(Swarm.REACH_FEET).isEqualTo(0);
    assertThat(Swarm.enteringCostFeet(5)).isEqualTo(10);
    assertThat(Swarm.leavingProvokes()).isTrue();
  }

  private static Forms.State mimic() {
    var object = new Forms.Form("object", 12, Map.of("walk", 0), Set.of());
    var trueForm = new Forms.Form("true", 12, Map.of("walk", 15), Set.of("bite", "pseudopod"));
    return new Forms.State(List.of(object, trueForm), "true", "object");
  }

  @Test
  void aFormGatesItsActionsAndKeepsOthersOpen() {
    var state = mimic();
    assertThat(state.actionAvailable("bite")).isFalse();
    assertThat(state.actionAvailable("shove")).isTrue();
    var revealed = state.change("true");
    assertThat(revealed.actionAvailable("bite")).isTrue();
    assertThat(revealed.currentForm().speeds().get("walk")).isEqualTo(15);
  }

  @Test
  void aShapechangerReturnsToItsTrueFormOnDeath() {
    var state = mimic();
    assertThat(state.onDeath().current()).isEqualTo("true");
  }

  @Test
  void adhesiveGrapplesHugeOrSmallerOnlyInObjectForm() {
    assertThat(Forms.Adhesive.grapples(true, 4, 5)).isTrue();
    assertThat(Forms.Adhesive.grapples(true, 6, 5)).isFalse();
    assertThat(Forms.Adhesive.grapples(false, 3, 5)).isFalse();
    assertThat(Forms.Adhesive.escapeHasDisadvantage()).isTrue();
    assertThat(Forms.Adhesive.attackHasAdvantage(true)).isTrue();
  }

  @Test
  void aWeaponCorrodesByOneEachHitAndBreaksAtFive() {
    int penalty = 0;
    ItemCorrosion.Result result = null;
    for (int i = 0; i < 5; i++) {
      result = ItemCorrosion.weaponHit(penalty, false, true);
      penalty = result.penalty();
      assertThat(result.destroyed()).isEqualTo(i == 4);
    }
    assertThat(penalty).isEqualTo(5);
    assertThat(ItemCorrosion.damageAfterPenalty(3, 4)).isEqualTo(0);
    assertThat(ItemCorrosion.damageAfterPenalty(7, 2)).isEqualTo(5);
  }

  @Test
  void magicalOrNonMetalItemsAreUnaffected() {
    assertThat(ItemCorrosion.weaponHit(2, true, true).penalty()).isEqualTo(2);
    assertThat(ItemCorrosion.weaponHit(2, false, false).penalty()).isEqualTo(2);
    assertThat(ItemCorrosion.ammunitionDestroyed(true, true)).isFalse();
    assertThat(ItemCorrosion.ammunitionDestroyed(false, true)).isTrue();
    assertThat(ItemCorrosion.antennaeAffect(true, true)).isFalse();
    assertThat(ItemCorrosion.antennaeAffect(false, true)).isTrue();
  }

  @Test
  void armorAndShieldsLoseOnePerTouchUntilDestroyed() {
    assertThat(ItemCorrosion.armorOffers(16, 3)).isEqualTo(13);
    assertThat(ItemCorrosion.armorDestroyed(16, 5)).isFalse();
    assertThat(ItemCorrosion.armorDestroyed(16, 6)).isTrue();
    assertThat(ItemCorrosion.shieldDestroyed(2, 1)).isFalse();
    assertThat(ItemCorrosion.shieldDestroyed(2, 2)).isTrue();
    assertThat(ItemCorrosion.ownerMayAvoid(true)).isTrue();
  }

  @Test
  void aGroupRollsInitiativeOnceAndSingleCreaturesRollTheirOwn() {
    var group =
        new CreatureCreation.Spec(
            "wolf",
            4,
            CreatureCreation.Controller.DM,
            CreatureCreation.Join.GROUP_ROLLS_ONCE,
            true);
    var single =
        new CreatureCreation.Spec(
            "wolf", 1, CreatureCreation.Controller.DM, CreatureCreation.Join.OWN_INITIATIVE, true);
    assertThat(CreatureCreation.initiativeRolls(group)).isEqualTo(1);
    assertThat(CreatureCreation.initiativeRolls(single)).isEqualTo(1);
  }

  @Test
  void createdCreaturesAreRemovedWithoutDyingAndGiveNoXp() {
    var spec =
        new CreatureCreation.Spec(
            "skeleton",
            1,
            CreatureCreation.Controller.DM,
            CreatureCreation.Join.OWN_INITIATIVE,
            true);
    assertThat(CreatureCreation.atZeroHitPoints(spec))
        .isEqualTo(CreatureCreation.Ending.REMOVED_WITHOUT_DYING);
    assertThat(CreatureCreation.whenEffectEnds())
        .isEqualTo(CreatureCreation.Ending.REMOVED_WITHOUT_DYING);
    assertThat(CreatureCreation.joinAt(false)).isEqualTo(CreatureCreation.JoinAt.NEXT_ROUND_START);
    assertThat(EncounterXp.total(List.of(CreatureCreation.asEncounterMember(50, true))))
        .isEqualTo(0L);
  }
}
