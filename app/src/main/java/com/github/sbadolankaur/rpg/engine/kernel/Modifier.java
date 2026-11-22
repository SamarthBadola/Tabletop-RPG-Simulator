package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.Objects;

/** Immutable stat modifier, including stacking identity and potency. */
public record Modifier(
    String targetStat,
    ModOp op,
    Ratio value,
    String activeWhen,
    DurationSpec duration,
    String stackKey,
    String source,
    int potency,
    long appliedOrder) {
  public Modifier {
    requireText(targetStat, "targetStat");
    Objects.requireNonNull(op, "op");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(duration, "duration");
    if (duration.kind() == DurationKind.INSTANT) {
      throw new IllegalArgumentException("An instant effect is never stored as an active modifier");
    }
    requireText(stackKey, "stackKey");
    requireText(source, "source");
    if (activeWhen != null && activeWhen.isBlank()) {
      throw new IllegalArgumentException("activeWhen must be absent or non-blank");
    }
    if (potency < 0 || appliedOrder < 0) {
      throw new IllegalArgumentException("Potency and applied order cannot be negative");
    }
    if (isIntegerStat(targetStat)
        && (op == ModOp.SET || op == ModOp.ADD || op == ModOp.MIN || op == ModOp.MAX)
        && !value.isInteger()) {
      throw new IllegalArgumentException(op + " on an integer stat requires an integer operand");
    }
    if (op == ModOp.MULTIPLY && value.numerator() < 0) {
      throw new IllegalArgumentException("A multiplier cannot be negative");
    }
  }

  /** Only skill proficiency multiplier keys have a ratio-valued target. */
  public static boolean isRatioStat(String targetStat) {
    return targetStat.startsWith("skill:") && targetStat.endsWith(":prof_multiplier");
  }

  public static boolean isIntegerStat(String targetStat) {
    return !isRatioStat(targetStat);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must be non-blank");
    }
  }
}
