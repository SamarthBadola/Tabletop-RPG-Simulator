package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A computed integer stat. Callers supply the active modifiers for this evaluation. */
public record Stat(String key, int base, List<Modifier> modifiers) {
  public Stat {
    if (key == null || key.isBlank()) throw new IllegalArgumentException("Stat key is required");
    if (Modifier.isRatioStat(key)) {
      throw new IllegalArgumentException(
          "Ratio-valued proficiency multipliers are not integer stats");
    }
    modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
    if (modifiers.stream().anyMatch(modifier -> !modifier.targetStat().equals(key))) {
      throw new IllegalArgumentException("Every modifier must target this stat");
    }
  }

  /** Applies SET, ADD, exact MULTIPLY with one floor, MIN, then MAX. */
  public int value() {
    List<Modifier> active = strongestFromEachSource(modifiers);
    long current = base;

    List<Modifier> setters = operations(active, ModOp.SET);
    setters.sort(Comparator.comparingLong(Modifier::appliedOrder));
    for (Modifier modifier : setters) current = integerValue(modifier);

    for (Modifier modifier : operations(active, ModOp.ADD)) {
      current = Math.addExact(current, integerValue(modifier));
    }

    long numerator = 1;
    long denominator = 1;
    for (Modifier modifier : operations(active, ModOp.MULTIPLY)) {
      numerator = Math.multiplyExact(numerator, modifier.value().numerator());
      denominator = Math.multiplyExact(denominator, modifier.value().denominator());
    }
    long multiplied = Math.multiplyExact(current, numerator);
    current = Math.floorDiv(multiplied, denominator);

    for (Modifier modifier : operations(active, ModOp.MIN)) {
      current = Math.min(current, integerValue(modifier));
    }
    for (Modifier modifier : operations(active, ModOp.MAX)) {
      current = Math.max(current, integerValue(modifier));
    }
    return Math.toIntExact(current);
  }

  static List<Modifier> strongestFromEachSource(List<Modifier> modifiers) {
    Map<String, Modifier> strongest = new LinkedHashMap<>();
    for (Modifier modifier : modifiers) {
      strongest.merge(
          modifier.stackKey(),
          modifier,
          (current, candidate) ->
              Comparator.comparingInt(Modifier::potency)
                          .thenComparingLong(Modifier::appliedOrder)
                          .compare(candidate, current)
                      > 0
                  ? candidate
                  : current);
    }
    return new ArrayList<>(strongest.values());
  }

  private static List<Modifier> operations(List<Modifier> modifiers, ModOp operation) {
    return new ArrayList<>(
        modifiers.stream().filter(modifier -> modifier.op() == operation).toList());
  }

  private static int integerValue(Modifier modifier) {
    if (!modifier.value().isInteger()) {
      throw new IllegalStateException("An integer stat has a non-integer operation value");
    }
    return modifier.value().numerator();
  }
}
