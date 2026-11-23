package com.github.sbadolankaur.rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.engine.content.ContentExpression;
import com.github.sbadolankaur.rpg.engine.content.ContentExpression.Type;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentExpressionTest {
  @Test
  void arithmeticIsExactAndPredicatesShortCircuit() {
    assertThat(formula("-3 / 2").evaluate(Map.of(), bound -> 0)).isEqualTo(-2L);
    assertThat(formula("round_div(-3, 2)").evaluate(Map.of(), bound -> 0)).isEqualTo(-1L);
    assertThat(formula("if(2 < 3, max(2, 3), 1 / 0)").evaluate(Map.of(), bound -> 0)).isEqualTo(3L);
    assertThatThrownBy(() -> formula("1 / 0").evaluate(Map.of(), bound -> 0))
        .hasMessageContaining("EXPRESSION_ERROR");
  }

  @Test
  void retainsDiceCanonicalFormAndEvaluatesDynamicGroupsFromLeftToRight() {
    assertThat(formula("2d20kh1 + 5").canonical()).isEqualTo("2d20kh1+5");
    var bounds = new ArrayList<Integer>();
    var roll = ContentExpression.parse("1d6 + (slot_level - 1)d8", Map.of("slot_level", Type.INT));
    assertThat(
            roll.evaluate(
                Map.of("slot_level", 3L),
                bound -> {
                  bounds.add(bound);
                  return 0;
                }))
        .isEqualTo(3L);
    assertThat(bounds).containsExactly(6, 8, 8);
    assertThatThrownBy(() -> formula("(2d6)*2")).isInstanceOf(IllegalArgumentException.class);
  }

  private static ContentExpression formula(String text) {
    return ContentExpression.parse(text, Map.of());
  }
}
