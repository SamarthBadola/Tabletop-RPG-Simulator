package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A ratio-valued stat, currently reserved for skill proficiency multipliers. */
public record RatioStat(String key, Ratio base, List<Modifier> modifiers) {
  public RatioStat {
    if (!Modifier.isRatioStat(key)) {
      throw new IllegalArgumentException("Only skill proficiency multiplier keys are ratio-valued");
    }
    Objects.requireNonNull(base, "base");
    modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
    if (modifiers.stream().anyMatch(modifier -> !modifier.targetStat().equals(key))) {
      throw new IllegalArgumentException("Every modifier must target this ratio stat");
    }
    if (modifiers.stream()
        .anyMatch(
            modifier -> modifier.op() == ModOp.ADVANTAGE || modifier.op() == ModOp.DISADVANTAGE)) {
      throw new IllegalArgumentException("Advantage operations are not ratio stat operations");
    }
  }

  /** Applies the same operation order as integer stats, preserving exact rational values. */
  public Ratio value() {
    List<Modifier> active = Stat.strongestFromEachSource(modifiers);
    Ratio current = base;
    for (Modifier modifier : operations(active, ModOp.SET)) current = modifier.value();
    for (Modifier modifier : operations(active, ModOp.ADD)) current = current.add(modifier.value());
    for (Modifier modifier : operations(active, ModOp.MULTIPLY))
      current = current.multiply(modifier.value());
    for (Modifier modifier : operations(active, ModOp.MIN))
      if (current.compareTo(modifier.value()) > 0) current = modifier.value();
    for (Modifier modifier : operations(active, ModOp.MAX))
      if (current.compareTo(modifier.value()) < 0) current = modifier.value();
    return current;
  }

  private static List<Modifier> operations(List<Modifier> modifiers, ModOp operation) {
    List<Modifier> result =
        modifiers.stream().filter(modifier -> modifier.op() == operation).toList();
    if (operation == ModOp.SET) {
      List<Modifier> sorted = new ArrayList<>(result);
      sorted.sort(java.util.Comparator.comparingLong(Modifier::appliedOrder));
      return sorted;
    }
    return result;
  }
}
