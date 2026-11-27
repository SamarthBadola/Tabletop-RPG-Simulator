package com.github.sbadolankaur.rpg.character.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CoinsTest {
  @Test
  void aStartingPurseIsSplitIntoGoldSilverAndCopper() {
    var coins = CharacterStore.Coins.fromCopper(12_345);
    assertThat(coins.gold()).isEqualTo(123);
    assertThat(coins.silver()).isEqualTo(4);
    assertThat(coins.copper()).isEqualTo(5);
    assertThat(coins.totalCopper()).isEqualTo(12_345L);
  }

  @Test
  void totalValueCountsEveryDenomination() {
    assertThat(new CharacterStore.Coins(1, 1, 1, 1, 1).totalCopper())
        .isEqualTo(1 + 10 + 50 + 100 + 1000L);
  }

  @Test
  void negativeCountsAreRefused() {
    assertThatThrownBy(() -> new CharacterStore.Coins(-1, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CharacterStore.Coins.fromCopper(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
