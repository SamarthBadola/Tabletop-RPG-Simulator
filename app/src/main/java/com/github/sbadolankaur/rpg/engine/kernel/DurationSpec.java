package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Duration metadata; timed forms retain absolute game-time or round ends, never counters. */
public final class DurationSpec {
  private final DurationKind kind;
  private final Long endRound;
  private final Long endClockSeconds;
  private final String creatureKey;

  private DurationSpec(DurationKind kind, Long endRound, Long endClockSeconds, String creatureKey) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.endRound = endRound;
    this.endClockSeconds = endClockSeconds;
    this.creatureKey = creatureKey;
  }

  public static DurationSpec instant() {
    return ofKind(DurationKind.INSTANT);
  }

  /** Resolves a number of rounds to the absolute end round and the creature's turn track. */
  public static DurationSpec rounds(long amount, long currentRound, String creatureKey) {
    if (amount <= 0 || currentRound < 0) {
      throw new IllegalArgumentException(
          "Round amount must be positive and current round nonnegative");
    }
    return new DurationSpec(
        DurationKind.ROUNDS,
        Math.addExact(currentRound, amount),
        null,
        requireCreature(creatureKey));
  }

  /** Resolves a clock quantity to an absolute game-clock second. */
  public static DurationSpec clock(long amount, long secondsPerUnit, long currentClockSeconds) {
    if (amount <= 0 || secondsPerUnit <= 0 || currentClockSeconds < 0) {
      throw new IllegalArgumentException(
          "Clock amount and unit must be positive and current clock nonnegative");
    }
    long durationSeconds = Math.multiplyExact(amount, secondsPerUnit);
    return new DurationSpec(
        DurationKind.CLOCK, null, Math.addExact(currentClockSeconds, durationSeconds), null);
  }

  /** Resolves the maximum concentration time to an absolute game-clock second. */
  public static DurationSpec concentration(long maximumSeconds, long currentClockSeconds) {
    if (maximumSeconds <= 0 || currentClockSeconds < 0) {
      throw new IllegalArgumentException(
          "Concentration maximum must be positive and current clock nonnegative");
    }
    return new DurationSpec(
        DurationKind.CONCENTRATION, null, Math.addExact(currentClockSeconds, maximumSeconds), null);
  }

  /** Stores the absolute round and named creature whose turn start ends this duration. */
  public static DurationSpec untilTurnStart(long endRound, String creatureKey) {
    return turn(DurationKind.UNTIL_TURN_START, endRound, creatureKey);
  }

  /** Stores the absolute round and named creature whose turn end ends this duration. */
  public static DurationSpec untilTurnEnd(long endRound, String creatureKey) {
    return turn(DurationKind.UNTIL_TURN_END, endRound, creatureKey);
  }

  public static DurationSpec untilDispelled() {
    return ofKind(DurationKind.UNTIL_DISPELLED);
  }

  public static DurationSpec untilShortOrLongRest() {
    return ofKind(DurationKind.UNTIL_SHORT_OR_LONG_REST);
  }

  public static DurationSpec untilLongRest() {
    return ofKind(DurationKind.UNTIL_LONG_REST);
  }

  public static DurationSpec untilUsed() {
    return ofKind(DurationKind.UNTIL_USED);
  }

  public static DurationSpec permanent() {
    return ofKind(DurationKind.PERMANENT);
  }

  public static DurationSpec whileEquipped() {
    return ofKind(DurationKind.WHILE_EQUIPPED);
  }

  public static DurationSpec whileAttuned() {
    return ofKind(DurationKind.WHILE_ATTUNED);
  }

  public DurationKind kind() {
    return kind;
  }

  public OptionalLong endRound() {
    return endRound == null ? OptionalLong.empty() : OptionalLong.of(endRound);
  }

  public OptionalLong endClockSeconds() {
    return endClockSeconds == null ? OptionalLong.empty() : OptionalLong.of(endClockSeconds);
  }

  public Optional<String> creatureKey() {
    return Optional.ofNullable(creatureKey);
  }

  /** Converts the remaining rounds to the fixed six-second rules clock when combat ends. */
  public DurationSpec asClockWhenEncounterEnds(long currentClockSeconds, long remainingRounds) {
    if (kind != DurationKind.ROUNDS) {
      throw new IllegalStateException("Only round durations convert when an encounter ends");
    }
    if (remainingRounds < 0) {
      throw new IllegalArgumentException("Remaining rounds cannot be negative");
    }
    if (currentClockSeconds < 0) {
      throw new IllegalArgumentException("Current clock cannot be negative");
    }
    long remainingSeconds = Math.multiplyExact(remainingRounds, (long) GameClock.SECONDS_PER_ROUND);
    return new DurationSpec(
        DurationKind.CLOCK, null, Math.addExact(currentClockSeconds, remainingSeconds), null);
  }

  /** INSTANT effects are resolved immediately and are never persisted as active durations. */
  public boolean isStoredActive() {
    return kind != DurationKind.INSTANT;
  }

  private static DurationSpec turn(DurationKind kind, long endRound, String creatureKey) {
    if (endRound < 0) throw new IllegalArgumentException("End round cannot be negative");
    return new DurationSpec(kind, endRound, null, requireCreature(creatureKey));
  }

  private static String requireCreature(String creatureKey) {
    if (creatureKey == null || creatureKey.isBlank()) {
      throw new IllegalArgumentException("A named creature is required for turn durations");
    }
    return creatureKey;
  }

  private static DurationSpec ofKind(DurationKind kind) {
    return new DurationSpec(kind, null, null, null);
  }
}
