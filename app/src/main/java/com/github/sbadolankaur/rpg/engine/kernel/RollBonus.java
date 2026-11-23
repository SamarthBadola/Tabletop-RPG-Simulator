package com.github.sbadolankaur.rpg.engine.kernel;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An applicable modifier to one roll. The caller owns the active bonuses and their expiry. */
public record RollBonus(
    String label,
    String source,
    String stackKey,
    int potency,
    long applicationSequence,
    DurationSpec duration,
    Amount amount) {
  public RollBonus {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(source, "source");
    if (stackKey == null || stackKey.isBlank()) {
      throw new IllegalArgumentException("A roll bonus needs a stack key");
    }
    Objects.requireNonNull(duration, "duration");
    Objects.requireNonNull(amount, "amount");
  }

  /** Selects the applying bonuses without changing the list of active instances. */
  public static List<RollBonus> select(List<RollBonus> applicable) {
    Comparator<RollBonus> priority =
        Comparator.comparingInt(RollBonus::potency)
            .thenComparingLong(RollBonus::applicationSequence);
    Map<String, RollBonus> selected = new LinkedHashMap<>();
    for (RollBonus bonus : applicable) {
      selected.merge(bonus.stackKey(), bonus, (a, b) -> priority.compare(a, b) < 0 ? b : a);
    }
    return List.copyOf(selected.values());
  }

  public void addTo(RollPlan.Builder plan) {
    switch (amount) {
      case Fixed fixed -> plan.fixed(label, source, fixed.value());
      case Dice dice -> {
        if (dice.penalty()) {
          plan.penaltyDice(label, source, dice.expression());
        } else {
          plan.bonusDice(label, source, dice.expression());
        }
      }
    }
  }

  public sealed interface Amount permits Fixed, Dice {}

  public record Fixed(int value) implements Amount {}

  public record Dice(DiceExpression expression, boolean penalty) implements Amount {
    public Dice {
      Objects.requireNonNull(expression, "expression");
    }
  }
}
