package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.monster.EncounterXp.Member;
import java.util.List;
import org.junit.jupiter.api.Test;

class EncounterXpTest {
  @Test
  void T_78_theWorkedExampleIsFourGoblinsAndAHobgoblin() {
    var goblin = ChallengeRating.of("1/4").xp(true);
    var hobgoblin = ChallengeRating.of("1/2").xp(true);
    var members =
        List.of(
            new Member(goblin, false, false),
            new Member(goblin, false, false),
            new Member(goblin, false, false),
            new Member(goblin, false, false),
            new Member(hobgoblin, false, false));
    assertThat(EncounterXp.total(members)).isEqualTo(300L);
  }

  @Test
  void T_78_defeatedMonstersAndLateArrivalsStillCount() {
    var members = List.of(new Member(50, true, false), new Member(100, false, false));
    assertThat(EncounterXp.total(members)).isEqualTo(150L);
  }

  @Test
  void T_78_createdCreaturesAndObjectsAreLeftOut() {
    var members =
        List.of(
            new Member(200, false, false),
            new Member(450, false, true),
            new Member(0, false, true));
    assertThat(EncounterXp.total(members)).isEqualTo(200L);
  }

  @Test
  void anEmptyEncounterIsWorthNothing() {
    assertThat(EncounterXp.total(List.of())).isZero();
  }
}
