package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.monster.LairActions.History;
import com.github.sbadolankaur.rpg.engine.monster.LairActions.Owner;
import com.github.sbadolankaur.rpg.engine.monster.LairActions.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;

class LairActionsTest {
  private static final Owner OWNER = new Owner("dragon", true, false, false);

  @Test
  void T_70_oneLairEntryExistsWhileTheFightIsInTheLair() {
    assertThat(LairActions.entries(List.of(OWNER), true)).isEqualTo(List.of("dragon"));
    assertThat(LairActions.entries(List.of(OWNER), false)).isEmpty();
  }

  @Test
  void RS_MON_R11_theEntryIsRemovedWhenTheOwnerDies() {
    var dead = new Owner("dragon", false, false, false);
    assertThat(LairActions.entries(List.of(dead), true)).isEmpty();
  }

  @Test
  void RS_MON_R12_theLairEntryLosesTiesAtCountTwenty() {
    var order =
        LairActions.order(
            List.of(
                new Slot("lair", LairActions.INITIATIVE, true),
                new Slot("character", 20, false),
                new Slot("fast", 22, false),
                new Slot("slow", 5, false)));
    assertThat(order.stream().map(Slot::id).toList())
        .isEqualTo(List.of("fast", "character", "lair", "slow"));
  }

  @Test
  void T_70_itNeedsTheLairFlagAndAnOwnerThatCanAct() {
    assertThat(LairActions.canUse(OWNER, true, History.NONE, 1, "quake", false)).isTrue();
    assertThat(LairActions.canUse(OWNER, false, History.NONE, 1, "quake", false)).isFalse();
    var stunned = new Owner("dragon", true, true, false);
    assertThat(LairActions.canUse(stunned, true, History.NONE, 1, "quake", false)).isFalse();
  }

  @Test
  void T_70_aSurprisedOwnerWaitsUntilAfterItsFirstTurn() {
    var surprised = new Owner("dragon", true, false, true);
    assertThat(LairActions.canUse(surprised, true, History.NONE, 1, "quake", false)).isFalse();
    var after = new Owner("dragon", true, false, false);
    assertThat(LairActions.canUse(after, true, History.NONE, 2, "quake", false)).isTrue();
  }

  @Test
  void T_70_atMostOnePerRoundAndNoRepeatWhereTheStatBlockSaysSo() {
    var usedRoundOne = new History(1, "quake");
    assertThat(LairActions.canUse(OWNER, true, usedRoundOne, 1, "fog", false)).isFalse();
    assertThat(LairActions.canUse(OWNER, true, usedRoundOne, 2, "quake", true)).isFalse();
    assertThat(LairActions.canUse(OWNER, true, usedRoundOne, 2, "quake", false)).isTrue();
    assertThat(LairActions.canUse(OWNER, true, usedRoundOne, 2, "fog", true)).isTrue();
    assertThat(LairActions.canUse(OWNER, true, usedRoundOne, 3, "quake", true)).isTrue();
  }
}
