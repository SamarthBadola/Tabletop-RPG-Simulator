package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    return breakdown().value();
  }

  /** Returns the result and the SET selected by the modifier precedence rules. */
  public ModifierBreakdown<Integer> breakdown() {
    List<Modifier> active = strongestFromEachSource(modifiers);
    long current = base;

    Optional<Modifier> winningSet = winningSet(active);
    if (winningSet.isPresent()) current = integerValue(winningSet.orElseThrow());

    for (Modifier modifier : operations(active, ModOp.ADD)) {
      current = Math.addExact(current, integerValue(modifier));
    }

    List<Modifier> multipliers = operations(active, ModOp.MULTIPLY);
    if (current == 0
        || multipliers.stream().anyMatch(modifier -> modifier.value().numerator() == 0)) {
      current = 0;
    } else {
      long numerator = 1;
      long denominator = 1;
      for (Modifier modifier : multipliers) {
        numerator = Math.multiplyExact(numerator, modifier.value().numerator());
        denominator = Math.multiplyExact(denominator, modifier.value().denominator());
      }
      current = Math.floorDiv(Math.multiplyExact(current, numerator), denominator);
    }

    for (Modifier modifier : operations(active, ModOp.MIN)) {
      current = Math.min(current, integerValue(modifier));
    }
    for (Modifier modifier : operations(active, ModOp.MAX)) {
      current = Math.max(current, integerValue(modifier));
    }
    return new ModifierBreakdown<>(
        Math.toIntExact(current), winningSet, overriddenSets(modifiers, winningSet));
  }

  /** Audit view used to explain which active SET won and which remain suppressed. */
  public record ModifierBreakdown<T>(
      T value, Optional<Modifier> winningSet, List<Modifier> overriddenSets) {
    public ModifierBreakdown {
      Objects.requireNonNull(winningSet, "winningSet");
      overriddenSets = List.copyOf(Objects.requireNonNull(overriddenSets, "overriddenSets"));
    }
  }

  static List<Modifier> strongestFromEachSource(List<Modifier> modifiers) {
    Map<String, Modifier> strongest = new LinkedHashMap<>();
    for (Modifier modifier : modifiers) {
      strongest.merge(
          modifier.stackKey(),
          modifier,
          (current, candidate) ->
              Comparator.comparingInt(Modifier::potency)
                          .thenComparingLong(Modifier::applicationSequence)
                          .compare(candidate, current)
                      > 0
                  ? candidate
                  : current);
    }
    return List.copyOf(strongest.values());
  }

  static Optional<Modifier> winningSet(List<Modifier> active) {
    return active.stream()
        .filter(modifier -> modifier.op() == ModOp.SET)
        .max(Comparator.comparingLong(Modifier::applicationSequence));
  }

  static List<Modifier> overriddenSets(List<Modifier> modifiers, Optional<Modifier> winningSet) {
    return modifiers.stream()
        .filter(modifier -> modifier.op() == ModOp.SET && !modifier.equals(winningSet.orElse(null)))
        .toList();
  }

  static List<Modifier> operations(List<Modifier> modifiers, ModOp operation) {
    return modifiers.stream().filter(modifier -> modifier.op() == operation).toList();
  }

  private static int integerValue(Modifier modifier) {
    if (!modifier.value().isInteger()) {
      throw new IllegalStateException("An integer stat has a non-integer operation value");
    }
    return modifier.value().numerator();
  }
}
