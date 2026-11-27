package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.equipment.Charges.Rules;
import com.github.sbadolankaur.rpg.engine.equipment.Charges.State;
import org.junit.jupiter.api.Test;

class ChargesTest {
  /**
   * The Wand of Magic Missiles: 1d6 + 1 at dawn, and a d20 on the last charge that destroys on a 1.
   */
  private static final Rules WAND = new Rules("1d6", 1, 20, 1);

  @Test
  void T_57_theWandSpendsOneChargeAtLevel1AndOneMoreForEachLevelAbove() {
    assertThat(Charges.magicMissileCost(1)).isEqualTo(1);
    assertThat(Charges.magicMissileCost(3)).isEqualTo(3);
    var result =
        Charges.spend(new State(7, 7, false), Charges.magicMissileCost(3), WAND, new ForcedDice());
    assertThat(result.state().current()).isEqualTo(4);
    assertThat(result.destroyRoll()).isNull();
  }

  @Test
  void T_57_dawnReturnsOneD6PlusOneUpToTheMaximum() {
    assertThat(Charges.rechargeAtDawn(new State(2, 7, false), WAND, new ForcedDice(3)).current())
        .isEqualTo(6);
    assertThat(Charges.rechargeAtDawn(new State(5, 7, false), WAND, new ForcedDice(6)).current())
        .isEqualTo(7);
  }

  @Test
  void T_57_theLastChargeRollsADieAndOnlyAOneDestroysTheWand() {
    var gone = Charges.spend(new State(1, 7, false), 1, WAND, new ForcedDice(1));
    assertThat(gone.destroyedNow()).isTrue();
    assertThat(gone.state().destroyed()).isTrue();
    var kept = Charges.spend(new State(1, 7, false), 1, WAND, new ForcedDice(2));
    assertThat(kept.destroyedNow()).isFalse();
    assertThat(kept.state().current()).isZero();
    assertThat(kept.destroyRoll()).isEqualTo(2);
  }

  @Test
  void T_57_aDestroyedWandCannotBeUsedOrRecharged() {
    var dead = new State(0, 7, true);
    assertThat(Charges.rechargeAtDawn(dead, WAND, new ForcedDice(6))).isEqualTo(dead);
    try {
      Charges.spend(dead, 1, WAND, new ForcedDice());
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("A destroyed wand was used");
  }

  @Test
  void T_57_spendingMoreThanTheWandHoldsIsRefused() {
    try {
      Charges.spend(new State(2, 7, false), 3, WAND, new ForcedDice());
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("Too many charges were spent");
  }

  @Test
  void anItemWithNoDestroyRuleNeverRollsOnItsLastCharge() {
    var result =
        Charges.spend(new State(1, 3, false), 1, new Rules("1d4", 0, 0, 0), new ForcedDice());
    assertThat(result.state().current()).isZero();
    assertThat(result.destroyedNow()).isFalse();
  }
}
