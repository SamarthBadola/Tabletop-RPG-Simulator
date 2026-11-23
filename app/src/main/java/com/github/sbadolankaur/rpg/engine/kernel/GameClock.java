package com.github.sbadolankaur.rpg.engine.kernel;

/** Fixed rules-time conversions. This class never reads or advances real-world time. */
public final class GameClock {
  public static final int SECONDS_PER_ROUND = 6;
  public static final int ROUNDS_PER_MINUTE = 10;
  public static final int ROUNDS_PER_HOUR = 600;

  private GameClock() {}

  public static long secondsForRounds(long rounds) {
    if (rounds < 0) throw new IllegalArgumentException("Rounds cannot be negative");
    return Math.multiplyExact(rounds, SECONDS_PER_ROUND);
  }

  public static long roundsForMinutes(long minutes) {
    if (minutes < 0) throw new IllegalArgumentException("Minutes cannot be negative");
    return Math.multiplyExact(minutes, ROUNDS_PER_MINUTE);
  }
}
