package com.github.sbadolankaur.rpg.engine.kernel;

import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import java.util.Objects;

/** Small pure helpers for Part A contest, passive, and group-check rules. */
public final class CheckRules {
  private CheckRules() {}

  public static ContestResult contest(int totalA, int totalB) {
    return totalA > totalB
        ? ContestResult.WIN_A
        : totalB > totalA ? ContestResult.WIN_B : ContestResult.TIE;
  }

  public static int passive(int totalModifier, RollMode mode) {
    Objects.requireNonNull(mode, "mode");
    int adjustment =
        switch (mode) {
          case NORMAL -> 0;
          case ADVANTAGE -> 5;
          case DISADVANTAGE -> -5;
        };
    return Math.addExact(Math.addExact(10, totalModifier), adjustment);
  }

  public static boolean groupCheck(int successes, int members) {
    if (members < 1 || successes < 0 || successes > members) {
      throw new IllegalArgumentException(
          "Successes must be between zero and a positive group size");
    }
    return 2L * successes >= members;
  }

  public enum ContestResult {
    WIN_A,
    WIN_B,
    TIE
  }
}
