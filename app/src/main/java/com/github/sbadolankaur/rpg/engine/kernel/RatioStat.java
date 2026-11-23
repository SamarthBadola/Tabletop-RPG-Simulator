package com.github.sbadolankaur.rpg.engine.kernel;

import static com.github.sbadolankaur.rpg.engine.kernel.Stat.operations;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    return breakdown().value();
  }

  public Stat.ModifierBreakdown<Ratio> breakdown() {
    List<Modifier> active = Stat.strongestFromEachSource(modifiers);
    Optional<Modifier> winningSet = Stat.winningSet(active);
    Ratio current = winningSet.map(Modifier::value).orElse(base);
    for (Modifier modifier : operations(active, ModOp.ADD)) current = current.add(modifier.value());
    for (Modifier modifier : operations(active, ModOp.MULTIPLY))
      current = current.multiply(modifier.value());
    for (Modifier modifier : operations(active, ModOp.MIN))
      if (current.compareTo(modifier.value()) > 0) current = modifier.value();
    for (Modifier modifier : operations(active, ModOp.MAX))
      if (current.compareTo(modifier.value()) < 0) current = modifier.value();
    return new Stat.ModifierBreakdown<>(
        current, winningSet, Stat.overriddenSets(modifiers, winningSet));
  }
}
