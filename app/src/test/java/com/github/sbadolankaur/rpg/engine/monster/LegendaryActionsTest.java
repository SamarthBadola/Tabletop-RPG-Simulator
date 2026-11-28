package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.monster.LegendaryActions.Creature;
import com.github.sbadolankaur.rpg.engine.monster.LegendaryActions.Option;
import com.github.sbadolankaur.rpg.engine.monster.LegendaryActions.Pool;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegendaryActionsTest {
  private static Creature creature(String id, int points) {
    return new Creature(id, new Pool(3, points), true, false, false);
  }

  @Test
  void WE_G5_theDragonSpendsItsPointsAndCannotActAtTheThirdTurn() {
    var pool = Pool.full(3);
    pool = pool.spend(1);
    assertThat(pool.points()).isEqualTo(2);
    pool = pool.spend(2);
    assertThat(pool.points()).isZero();
    var spent = new Creature("dragon", pool, true, false, false);
    assertThat(LegendaryActions.eligible(spent, "third-character")).isFalse();
    assertThat(
            LegendaryActions.eligible(
                new Creature("dragon", pool.refill(), true, false, false), "third-character"))
        .isTrue();
  }

  @Test
  void T_69_anOptionCostingMoreThanThePointsLeftIsNotOffered() {
    var tail = new Option("tail", "Tail Attack", 1);
    var wing = new Option("wing", "Wing Attack", 2);
    var detect = new Option("detect", "Detect", 1);
    assertThat(LegendaryActions.affordable(List.of(tail, wing, detect), new Pool(3, 1)))
        .isEqualTo(List.of(tail, detect));
    assertThat(LegendaryActions.affordable(List.of(wing), new Pool(3, 1))).isEmpty();
    try {
      new Pool(3, 1).spend(2);
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("Spent more points than the pool held");
  }

  @Test
  void RS_MON_R10_aCreatureCannotUseALegendaryActionAtTheEndOfItsOwnTurn() {
    assertThat(LegendaryActions.eligible(creature("dragon", 3), "dragon")).isFalse();
    assertThat(LegendaryActions.eligible(creature("dragon", 3), "fighter")).isTrue();
  }

  @Test
  void RS_MON_R8_anIncapacitatedCreatureGetsNoWindow() {
    var stunned = new Creature("dragon", Pool.full(3), true, true, false);
    assertThat(LegendaryActions.eligible(stunned, "fighter")).isFalse();
    var dead = new Creature("dragon", Pool.full(3), false, false, false);
    assertThat(LegendaryActions.eligible(dead, "fighter")).isFalse();
  }

  @Test
  void RS_MON_R7_aSurprisedCreatureWaitsUntilAfterItsFirstTurn() {
    var surprised = new Creature("dragon", Pool.full(3), true, false, true);
    assertThat(LegendaryActions.eligible(surprised, "fighter")).isFalse();
    var afterFirstTurn = new Creature("dragon", Pool.full(3), true, false, false);
    assertThat(LegendaryActions.eligible(afterFirstTurn, "fighter")).isTrue();
  }

  @Test
  void RS_MON_R9_twoCreaturesGetWindowsInInitiativeOrderStartingAfterTheEndedTurn() {
    var order =
        List.of(creature("a", 3), creature("fighter", 0), creature("b", 3), creature("c", 3));
    var after = LegendaryActions.windowOrder(order, "fighter");
    assertThat(after.stream().map(Creature::id).toList()).isEqualTo(List.of("b", "c", "a"));
    var afterB = LegendaryActions.windowOrder(order, "b");
    assertThat(afterB.stream().map(Creature::id).toList()).isEqualTo(List.of("c", "a"));
  }

  @Test
  void aCreatureWithNoPointsLeftGetsNoWindow() {
    var order = List.of(creature("a", 0), creature("fighter", 0), creature("b", 1));
    var windows = LegendaryActions.windowOrder(order, "fighter");
    assertThat(windows.stream().map(Creature::id).toList()).isEqualTo(List.of("b"));
  }
}
