package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.monster.MonsterHp.Mode;
import org.junit.jupiter.api.Test;

class MonsterHpTest {
  private static HitDice dice(MonsterSize size, int count, int constitutionModifier) {
    return HitDice.withConstitutionModifier(size, count, constitutionModifier);
  }

  @Test
  void T_62_theAverageIsRoundedDownForTheStatBlocksThatWereRead() {
    assertThat(dice(MonsterSize.LARGE, 7, 3).average()).isEqualTo(59);
    assertThat(dice(MonsterSize.MEDIUM, 9, 0).average()).isEqualTo(40);
    assertThat(dice(MonsterSize.LARGE, 8, 5).average()).isEqualTo(84);
    assertThat(dice(MonsterSize.MEDIUM, 3, 3).average()).isEqualTo(22);
    assertThat(dice(MonsterSize.MEDIUM, 5, 1).average()).isEqualTo(27);
  }

  @Test
  void theHitDieFollowsTheSize() {
    assertThat(MonsterSize.TINY.hitDie()).isEqualTo(4);
    assertThat(MonsterSize.SMALL.hitDie()).isEqualTo(6);
    assertThat(MonsterSize.MEDIUM.hitDie()).isEqualTo(8);
    assertThat(MonsterSize.LARGE.hitDie()).isEqualTo(10);
    assertThat(MonsterSize.HUGE.hitDie()).isEqualTo(12);
    assertThat(MonsterSize.GARGANTUAN.hitDie()).isEqualTo(20);
  }

  @Test
  void theExpressionIsWrittenAsOnTheStatBlock() {
    assertThat(dice(MonsterSize.LARGE, 7, 3).expression()).isEqualTo("7d10+21");
    assertThat(dice(MonsterSize.MEDIUM, 9, 0).expression()).isEqualTo("9d8");
    assertThat(dice(MonsterSize.MEDIUM, 2, -1).expression()).isEqualTo("2d8-2");
  }

  @Test
  void T_63_averageGivesThePrintedAverageAndRollsNothing() {
    var start = MonsterHp.start(dice(MonsterSize.MEDIUM, 9, 0), Mode.AVERAGE, new ForcedDice());
    assertThat(start.hitPoints()).isEqualTo(40);
    assertThat(start.roll()).isNull();
  }

  @Test
  void T_63_rolledRollsTheDiceOnceAndKeepsTheRoll() {
    var start = MonsterHp.start(dice(MonsterSize.MEDIUM, 2, 1), Mode.ROLLED, new ForcedDice(5, 7));
    assertThat(start.hitPoints()).isEqualTo(5 + 7 + 2);
    assertThat(start.roll()).isNotNull();
    assertThat(start.roll().total()).isEqualTo(14);
  }

  @Test
  void T_63_aRollBelowOneBecomesOne() {
    var start = MonsterHp.start(dice(MonsterSize.MEDIUM, 1, -5), Mode.ROLLED, new ForcedDice(2));
    assertThat(start.roll().total()).isEqualTo(-3);
    assertThat(start.hitPoints()).isEqualTo(1);
  }

  @Test
  void T_63_monstersFromOneDefinitionRollSeparately() {
    var source = new ForcedDice(2, 8, 4, 4);
    var definition = dice(MonsterSize.MEDIUM, 2, 0);
    var first = MonsterHp.start(definition, Mode.ROLLED, source);
    var second = MonsterHp.start(definition, Mode.ROLLED, source);
    assertThat(first.hitPoints()).isEqualTo(10);
    assertThat(second.hitPoints()).isEqualTo(8);
  }
}
