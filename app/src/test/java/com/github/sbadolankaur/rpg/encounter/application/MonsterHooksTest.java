package com.github.sbadolankaur.rpg.encounter.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonsterHooksTest {
  @Test
  void readsTheTraitNamesOfADefinition() {
    var definition =
        Map.of(
            "traits",
            List.of(
                Map.of("name", "Regeneration", "text", "The troll regains 10 hit points."),
                Map.of("name", "Keen Smell", "text", "Advantage on Perception checks.")));

    assertThat(MonsterHooks.traitNames(definition)).containsExactly("Regeneration", "Keen Smell");
  }

  @Test
  void aDefinitionWithoutTraitsHasNoNames() {
    assertThat(MonsterHooks.traitNames(Map.of())).isEmpty();
    assertThat(MonsterHooks.traitNames(Map.of("traits", "none"))).isEmpty();
  }
}
