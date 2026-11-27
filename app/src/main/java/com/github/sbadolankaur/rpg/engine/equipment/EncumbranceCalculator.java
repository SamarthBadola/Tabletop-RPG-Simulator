package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.List;

/**
 * Carrying capacity and the effects of weight (RS-EQP-039 to RS-EQP-043). Weights are in hundredths
 * of a pound. A Tiny creature has half the capacity and a Large or larger one has double. Used only
 * when the rule is not OFF.
 */
public final class EncumbranceCalculator {
  private static final long COIN_WEIGHT = 2; // 50 coins to the pound is 2 hundredths each
  private static final long UNIT = 100;

  private EncumbranceCalculator() {}

  public enum Level {
    NONE,
    ENCUMBERED,
    HEAVILY_ENCUMBERED
  }

  /** The speed and roll penalties that follow from the weight. */
  public record Result(
      Level level,
      int speedPenalty,
      boolean disadvantageOnPhysicalRolls,
      long totalWeight,
      long capacity) {}

  /** Thrown when a STANDARD or VARIANT limit refuses to take on more weight. */
  public static final class OverCapacityException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final long weight;
    private final long limit;

    OverCapacityException(long weight, long limit) {
      super("Carrying " + weight + " hundredths of a pound would pass the limit of " + limit);
      this.weight = weight;
      this.limit = limit;
    }

    public long weight() {
      return weight;
    }

    public long limit() {
      return limit;
    }
  }

  /** Fifteen times the Strength score in pounds, halved for Tiny and doubled for Large or more. */
  public static long capacity(int strengthScore, CreatureSize size) {
    return scaled(15L * strengthScore * UNIT, size);
  }

  /** What the creature can push, drag or lift: thirty times the Strength score. */
  public static long liftLimit(int strengthScore, CreatureSize size) {
    return scaled(30L * strengthScore * UNIT, size);
  }

  public static long totalWeight(List<CarriedItem> items, long coinCount) {
    long total = coinCount * COIN_WEIGHT;
    for (var item : items) total += item.counted();
    return total;
  }

  public static Result compute(
      EncumbranceRule rule,
      int strengthScore,
      CreatureSize size,
      List<CarriedItem> items,
      long coinCount) {
    long total = totalWeight(items, coinCount);
    long capacity = capacity(strengthScore, size);
    if (rule != EncumbranceRule.VARIANT) return new Result(Level.NONE, 0, false, total, capacity);
    long encumbered = scaled(5L * strengthScore * UNIT, size);
    long heavily = scaled(10L * strengthScore * UNIT, size);
    if (total > heavily) return new Result(Level.HEAVILY_ENCUMBERED, 20, true, total, capacity);
    if (total > encumbered) return new Result(Level.ENCUMBERED, 10, false, total, capacity);
    return new Result(Level.NONE, 0, false, total, capacity);
  }

  /** Refuses weight that would pass the hard limit of STANDARD and VARIANT. OFF never refuses. */
  public static void checkCanCarry(
      EncumbranceRule rule,
      int strengthScore,
      CreatureSize size,
      long currentWeight,
      long addedWeight) {
    if (rule == EncumbranceRule.OFF) return;
    long limit = capacity(strengthScore, size);
    if (currentWeight + addedWeight > limit) {
      throw new OverCapacityException(currentWeight + addedWeight, limit);
    }
  }

  private static long scaled(long value, CreatureSize size) {
    return switch (size) {
      case TINY -> value / 2;
      case SMALL, MEDIUM -> value;
      case LARGE, HUGE, GARGANTUAN -> value * 2;
    };
  }
}
