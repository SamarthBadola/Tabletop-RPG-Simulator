package com.github.sbadolankaur.rpg.engine.combat;

import com.github.sbadolankaur.rpg.engine.kernel.DurationKind;
import com.github.sbadolankaur.rpg.engine.kernel.GameClock;
import java.util.Objects;
import java.util.UUID;

/** One source of a condition. Its end is an absolute deadline or an event. */
public record ConditionInstance(String key, UUID source, End end) {
  public ConditionInstance {
    Objects.requireNonNull(key);
    if (end == null) end = End.untilDispelled();
  }

  public ConditionInstance(String key, UUID source) {
    this(key, source, End.untilDispelled());
  }

  public boolean sameSource(ConditionInstance other) {
    return key.equals(other.key) && Objects.equals(source, other.source);
  }

  public enum Boundary {
    CLOCK,
    START,
    END
  }

  public record End(DurationKind kind, Long round, Long seconds, UUID creature) {
    public End {
      Objects.requireNonNull(kind);
      boolean turn =
          kind == DurationKind.ROUNDS
              || kind == DurationKind.UNTIL_TURN_START
              || kind == DurationKind.UNTIL_TURN_END;
      boolean clock = kind == DurationKind.CLOCK || kind == DurationKind.CONCENTRATION;
      if (kind == DurationKind.INSTANT
          || turn != (round != null)
          || turn != (creature != null)
          || clock != (seconds != null)
          || (round != null && round < 0)
          || (seconds != null && seconds < 0)) {
        throw new IllegalArgumentException("An active condition requires a valid end");
      }
    }

    public static End untilDispelled() {
      return new End(DurationKind.UNTIL_DISPELLED, null, null, null);
    }

    public static End clock(long seconds) {
      return new End(DurationKind.CLOCK, null, seconds, null);
    }

    public static End turn(DurationKind kind, long round, UUID creature) {
      return new End(kind, round, null, creature);
    }

    public boolean expired(long elapsed, int currentRound, UUID acting, Boundary boundary) {
      if (seconds != null) return elapsed >= seconds;
      if (round == null || !creature.equals(acting) || currentRound < round) return false;
      return kind == DurationKind.UNTIL_TURN_END
          ? boundary == Boundary.END
          : boundary == Boundary.START;
    }

    public End afterEncounter(long elapsed, int currentRound) {
      return switch (kind) {
        case ROUNDS ->
            clock(
                Math.addExact(
                    elapsed, GameClock.secondsForRounds(Math.max(0, round - currentRound))));
        case UNTIL_TURN_START, UNTIL_TURN_END -> clock(elapsed);
        default -> this;
      };
    }
  }
}
