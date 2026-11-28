package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HitDiceParseTest {
  @Test
  void readsDiceWithAPositiveNegativeAndNoBonus() {
    var troll = HitDice.parse(MonsterSize.LARGE, "8d10+40");
    assertThat(troll.count()).isEqualTo(8);
    assertThat(troll.bonus()).isEqualTo(40);
    assertThat(troll.average()).isEqualTo(84);

    var rat = HitDice.parse(MonsterSize.TINY, "1d4-1");
    assertThat(rat.bonus()).isEqualTo(-1);

    var plain = HitDice.parse(MonsterSize.MEDIUM, "5d8");
    assertThat(plain.bonus()).isZero();
    assertThat(plain.expression()).isEqualTo("5d8");
  }

  @Test
  void rejectsDiceThatDoNotFitTheSizeOrAreUnreadable() {
    assertThatThrownBy(() -> HitDice.parse(MonsterSize.LARGE, "8d8+40"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HitDice.parse(MonsterSize.MEDIUM, "five dice"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HitDice.parse(MonsterSize.MEDIUM, "0d8"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HitDice.parse(MonsterSize.MEDIUM, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsEverySizeAsAStatBlockWritesIt() {
    assertThat(MonsterSize.fromKey("tiny")).isEqualTo(MonsterSize.TINY);
    assertThat(MonsterSize.fromKey("gargantuan")).isEqualTo(MonsterSize.GARGANTUAN);
    assertThatThrownBy(() -> MonsterSize.fromKey("Medium"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MonsterSize.fromKey(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
