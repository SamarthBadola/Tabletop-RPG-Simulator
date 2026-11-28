package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.monster.Recharge.Ability;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RechargeTest {
  private static final Ability WEB = new Ability("web", 5);
  private static final Ability BREATH = new Ability("breath", 5);

  @Test
  void T_68_aSpentAbilityRollsAtTheStartOfTheTurnAndRechargesOnTheStatedNumbers() {
    var five = Recharge.startOfTurn(List.of(WEB), Set.of("web"), new ForcedDice(5));
    assertThat(five.spent()).isEmpty();
    assertThat(five.rolls().get(0).recharged()).isTrue();
    var six = Recharge.startOfTurn(List.of(WEB), Set.of("web"), new ForcedDice(6));
    assertThat(six.spent()).isEmpty();
    var four = Recharge.startOfTurn(List.of(WEB), Set.of("web"), new ForcedDice(4));
    assertThat(four.spent()).isEqualTo(Set.of("web"));
    assertThat(four.rolls().get(0).die()).isEqualTo(4);
  }

  @Test
  void T_68_anAbilityThatIsNotSpentRollsNothing() {
    var turn = Recharge.startOfTurn(List.of(WEB), Set.of(), new ForcedDice());
    assertThat(turn.rolls()).isEmpty();
  }

  @Test
  void RS_MON_R15_twoSpentAbilitiesRollSeparately() {
    var turn =
        Recharge.startOfTurn(List.of(WEB, BREATH), Set.of("web", "breath"), new ForcedDice(6, 2));
    assertThat(turn.rolls()).hasSize(2);
    assertThat(turn.spent()).isEqualTo(Set.of("breath"));
  }

  @Test
  void T_68_aShortOrLongRestRechargesBoth() {
    assertThat(Recharge.onRest()).isEmpty();
  }

  @Test
  void anAbilityThatIsStillSpentCannotBeUsedAgain() {
    var spent = Recharge.spend(Set.of(), "web");
    assertThat(spent).isEqualTo(Set.of("web"));
    try {
      Recharge.spend(spent, "web");
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("A spent ability was used again");
  }
}
