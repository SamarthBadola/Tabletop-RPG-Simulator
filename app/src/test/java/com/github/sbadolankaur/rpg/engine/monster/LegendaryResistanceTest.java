package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.monster.LegendaryResistance.Failure;
import com.github.sbadolankaur.rpg.engine.monster.LegendaryResistance.Uses;
import org.junit.jupiter.api.Test;

class LegendaryResistanceTest {
  @Test
  void T_71_aRolledFailureOffersItAndAUseIsSpent() {
    var uses = Uses.full(3);
    assertThat(LegendaryResistance.isOffered(uses, Failure.ROLLED, true)).isTrue();
    assertThat(LegendaryResistance.choose(uses, true).remaining()).isEqualTo(2);
  }

  @Test
  void T_71_decliningKeepsTheUse() {
    var uses = Uses.full(3);
    assertThat(LegendaryResistance.choose(uses, false)).isEqualTo(uses);
  }

  @Test
  void T_71_theUsesReturnOnALongRest() {
    var spent = new Uses(3, 0);
    assertThat(spent.afterLongRest()).isEqualTo(Uses.full(3));
  }

  @Test
  void T_71_withNoUsesLeftNothingIsOffered() {
    var none = new Uses(3, 0);
    assertThat(LegendaryResistance.isOffered(none, Failure.ROLLED, true)).isFalse();
    assertThat(LegendaryResistance.isOffered(none, Failure.AUTOMATIC, true)).isFalse();
    try {
      LegendaryResistance.choose(none, true);
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("A use was spent with none left");
  }

  @Test
  void RS_MON_R14_anAutomaticFailureOpensTheOfferWhenTheSettingIsOn() {
    assertThat(LegendaryResistance.isOffered(Uses.full(3), Failure.AUTOMATIC, true)).isTrue();
  }

  @Test
  void RS_MON_R13_anAutomaticFailureStandsWhenTheSettingIsOff() {
    assertThat(LegendaryResistance.isOffered(Uses.full(3), Failure.AUTOMATIC, false)).isFalse();
    assertThat(LegendaryResistance.isOffered(Uses.full(3), Failure.ROLLED, false)).isTrue();
  }
}
