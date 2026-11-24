package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.ArrayList;
import java.util.List;

/** Fixed rules-time conversions. This class never reads or advances real-world time. */
public final class GameClock {
  public static final int SECONDS_PER_ROUND = 6;
  public static final int ROUNDS_PER_MINUTE = 10;
  public static final int ROUNDS_PER_HOUR = 600;
  public static final int SECONDS_PER_DAY = 86_400;
  public static final long MAX_ADVANCE = 366L * SECONDS_PER_DAY;

  private GameClock() {}

  public static long secondsForRounds(long rounds) {
    if (rounds < 0) throw new IllegalArgumentException("Rounds cannot be negative");
    return Math.multiplyExact(rounds, SECONDS_PER_ROUND);
  }

  public static int timeOfDay(long seconds, int startTimeOfDay) {
    if (seconds < 0 || startTimeOfDay < 0 || startTimeOfDay >= SECONDS_PER_DAY)
      throw new IllegalArgumentException("Invalid game time");
    return (int) ((seconds % SECONDS_PER_DAY + startTimeOfDay) % SECONDS_PER_DAY);
  }

  public static Advance advance(long start, long duration, int startTimeOfDay, int dawn) {
    if (duration < 0 || duration > MAX_ADVANCE || dawn < 0 || dawn >= SECONDS_PER_DAY)
      throw new IllegalArgumentException("Invalid clock advance");
    int current = timeOfDay(start, startTimeOfDay);
    long end = Math.addExact(start, duration);
    long untilDawn = Math.floorMod(dawn - current - 1, SECONDS_PER_DAY) + 1L;
    var crossings = new ArrayList<Long>();
    for (long offset = untilDawn; offset <= duration; offset += SECONDS_PER_DAY)
      crossings.add(start + offset);
    return new Advance(start, end, timeOfDay(end, startTimeOfDay), crossings);
  }

  public record Advance(long start, long end, int timeOfDaySeconds, List<Long> dawns) {
    public Advance {
      dawns = List.copyOf(dawns);
    }
  }

  public static long roundsForMinutes(long minutes) {
    if (minutes < 0) throw new IllegalArgumentException("Minutes cannot be negative");
    return Math.multiplyExact(minutes, ROUNDS_PER_MINUTE);
  }
}
