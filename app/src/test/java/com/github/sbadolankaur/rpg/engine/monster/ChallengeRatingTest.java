package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChallengeRatingTest {
  private static ChallengeRating cr(String text) {
    return ChallengeRating.of(text);
  }

  @Test
  void proficiencyBonusFollowsTheBandsOfTheTable() {
    assertThat(cr("0").proficiencyBonus()).isEqualTo(2);
    assertThat(cr("1/8").proficiencyBonus()).isEqualTo(2);
    assertThat(cr("4").proficiencyBonus()).isEqualTo(2);
    assertThat(cr("5").proficiencyBonus()).isEqualTo(3);
    assertThat(cr("8").proficiencyBonus()).isEqualTo(3);
    assertThat(cr("9").proficiencyBonus()).isEqualTo(4);
    assertThat(cr("12").proficiencyBonus()).isEqualTo(4);
    assertThat(cr("13").proficiencyBonus()).isEqualTo(5);
    assertThat(cr("17").proficiencyBonus()).isEqualTo(6);
    assertThat(cr("21").proficiencyBonus()).isEqualTo(7);
    assertThat(cr("25").proficiencyBonus()).isEqualTo(8);
    assertThat(cr("29").proficiencyBonus()).isEqualTo(9);
    assertThat(cr("30").proficiencyBonus()).isEqualTo(9);
  }

  @Test
  void xpMatchesTheTable() {
    assertThat(cr("1/8").xp(true)).isEqualTo(25);
    assertThat(cr("1/4").xp(true)).isEqualTo(50);
    assertThat(cr("1/2").xp(true)).isEqualTo(100);
    assertThat(cr("1").xp(true)).isEqualTo(200);
    assertThat(cr("3").xp(true)).isEqualTo(700);
    assertThat(cr("5").xp(true)).isEqualTo(1800);
    assertThat(cr("9").xp(true)).isEqualTo(5000);
    assertThat(cr("17").xp(true)).isEqualTo(18000);
    assertThat(cr("27").xp(true)).isEqualTo(105000);
    assertThat(cr("30").xp(true)).isEqualTo(155000);
  }

  @Test
  void aRatingOfZeroIsWorthTenOnlyIfTheCreatureCanDealDamage() {
    assertThat(cr("0").xp(false)).isZero();
    assertThat(cr("0").xp(true)).isEqualTo(10);
  }

  @Test
  void ratingsParseOrderAndRejectNonsense() {
    assertThat(cr("1/8").compareTo(cr("1/4")) < 0).isTrue();
    assertThat(cr("1/2").compareTo(cr("1")) < 0).isTrue();
    assertThat(cr("10").compareTo(cr("9")) > 0).isTrue();
    assertThat(cr("1/2")).isEqualTo(cr("1/2"));
    assertThat(cr("17").text()).isEqualTo("17");
    for (String bad : new String[] {"", "31", "3/4", "-1", "1.5", "one"}) {
      try {
        ChallengeRating.of(bad);
      } catch (IllegalArgumentException expected) {
        continue;
      }
      throw new AssertionError("Accepted " + bad);
    }
  }
}
