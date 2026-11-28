package com.github.sbadolankaur.rpg.encounter.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

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

  @Test
  void aMultiattackGivesOneSwingPerListedAttack() {
    var json = JsonMapper.builder().build();
    var owlbear =
        json.readTree(
            """
            {"actions":[{"name":"Multiattack","attacks":["Beak","Claws"]},{"name":"Beak"}]}
            """);
    var goblin = json.readTree("{\"actions\":[{\"name\":\"Scimitar\"}]}");

    assertThat(MonsterHooks.attacksPerAction(owlbear)).isEqualTo(2);
    assertThat(MonsterHooks.attacksPerAction(goblin)).isEqualTo(1);
    assertThat(MonsterHooks.attacksPerAction(json.readTree("{}"))).isEqualTo(1);
  }
}
