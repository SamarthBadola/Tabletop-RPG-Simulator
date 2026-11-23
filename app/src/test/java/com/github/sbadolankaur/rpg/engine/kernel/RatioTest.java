package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RatioTest {
  @Test
  void ratioIsReducedAndHasAPositiveDenominator() {
    assertThat(new Ratio(2, 4)).isEqualTo(new Ratio(1, 2));
    assertThat(new Ratio(0, 17)).isEqualTo(Ratio.ZERO);
    assertThatThrownBy(() -> new Ratio(1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Ratio(1, -2)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void multiplicationIsExactAndDoesNotUseFloatingPoint() {
    assertThat(new Ratio(1, 2).multiply(new Ratio(3, 1))).isEqualTo(new Ratio(3, 2));
    assertThat(new Ratio(1, 2).multiply(new Ratio(1, 2))).isEqualTo(new Ratio(1, 4));
    assertThatThrownBy(() -> new Ratio(Integer.MAX_VALUE, 1).multiply(new Ratio(2, 1)))
        .isInstanceOf(ArithmeticException.class);
  }
}
