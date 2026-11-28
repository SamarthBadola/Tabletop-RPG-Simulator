package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpecialTraitsTest {
  @Test
  void findsEachTraitByItsPrintedName() {
    assertThat(SpecialTraits.defender(List.of("Undead Fortitude")))
        .isEqualTo(new ZeroHitPointHook.Defender(true, false));
    assertThat(SpecialTraits.defender(List.of("Keen Smell", "Regeneration")))
        .isEqualTo(new ZeroHitPointHook.Defender(false, true));
  }

  @Test
  void aCreatureWithoutThemHasNone() {
    assertThat(SpecialTraits.defender(List.of())).isEqualTo(ZeroHitPointHook.Defender.NONE);
    assertThat(SpecialTraits.defender(List.of("regeneration", "Pack Tactics")))
        .isEqualTo(ZeroHitPointHook.Defender.NONE);
  }
}
