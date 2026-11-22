package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class RollBonusTest {
  @Test
  void repeatedBlessAppliesOnceAndDifferentSourcesStack() {
    RollBonus earlier = bonus("Bless", "caster one", "bless", 1, "d4");
    RollBonus later = bonus("Bless", "caster two", "bless", 2, "d4");
    RollBonus inspiration = bonus("Bardic Inspiration", "bard", "inspiration", 3, "d6");
    var applying = RollBonus.select(List.of(earlier, later, inspiration));
    assertThat(applying).containsExactly(later, inspiration);
    assertThat(RollBonus.select(List.of(earlier, inspiration)))
        .containsExactly(earlier, inspiration);

    var plan = RollPlan.builder().d20(RollMode.NORMAL);
    applying.forEach(bonus -> bonus.addTo(plan));
    var dice = new ForcedDice(12, 3, 5);
    var result = plan.build().roll(dice);

    assertThat(result.total()).isEqualTo(20);
    assertThat(result.terms().get(1).source()).isEqualTo("caster two");
    dice.assertExhausted();
  }

  private static RollBonus bonus(
      String label, String source, String key, long sequence, String die) {
    return new RollBonus(
        label,
        source,
        key,
        1,
        sequence,
        DurationSpec.permanent(),
        new RollBonus.Dice(DiceExpression.parse(die), false));
  }
}
