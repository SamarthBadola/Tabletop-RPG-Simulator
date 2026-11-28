package com.github.sbadolankaur.rpg.encounter.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonsterLegendaryTest {
  @Test
  void readsTheOptionsCostsAndAttacksOfALegendaryBlock() {
    var dragon =
        Map.of(
            "legendaryActions",
            Map.of(
                "uses",
                3,
                "entries",
                List.of(
                    Map.of("name", "Detect", "cost", 1),
                    Map.of("name", "Tail Attack", "cost", 1, "attacks", List.of("Tail")),
                    Map.of("name", "Wing Attack", "cost", 2))));

    var options = MonsterLegendary.options(dragon);

    assertThat(options).hasSize(3);
    assertThat(options.get(1).name()).isEqualTo("Tail Attack");
    assertThat(options.get(1).attacks()).containsExactly("Tail");
    assertThat(options.get(2).cost()).isEqualTo(2);
    assertThat(options.get(0).attacks()).isEmpty();
  }

  @Test
  void anOptionWithoutACostCostsOnePoint() {
    var block =
        Map.of("legendaryActions", Map.of("uses", 3, "entries", List.of(Map.of("name", "Detect"))));

    assertThat(MonsterLegendary.options(block).getFirst().cost()).isEqualTo(1);
    assertThat(MonsterLegendary.options(Map.of())).isEmpty();
  }
}
