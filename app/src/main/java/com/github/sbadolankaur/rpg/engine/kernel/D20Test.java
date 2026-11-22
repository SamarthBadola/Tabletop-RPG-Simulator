package com.github.sbadolankaur.rpg.engine.kernel;

import com.github.sbadolankaur.rpg.engine.dice.RollResult;
import java.util.Objects;

/** Resolves the Part A outcome of an ability check or saving throw. */
public final class D20Test {
  private D20Test() {}

  public static Result resolve(RollResult roll, int target, Kind kind, NaturalMode naturalMode) {
    Objects.requireNonNull(roll, "roll");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(naturalMode, "naturalMode");
    Integer natural = roll.naturalD20();
    if (natural == null || natural < 1 || natural > 20) {
      throw new IllegalArgumentException("The roll must contain a natural d20 result");
    }

    AutomaticOutcome automaticOutcome = AutomaticOutcome.NONE;
    if (naturalMode.appliesTo(kind)) {
      if (natural == 20) automaticOutcome = AutomaticOutcome.SUCCESS;
      if (natural == 1) automaticOutcome = AutomaticOutcome.FAILURE;
    }
    boolean success =
        switch (automaticOutcome) {
          case SUCCESS -> true;
          case FAILURE -> false;
          case NONE -> roll.total() >= target;
        };
    return new Result(roll.total(), target, natural, success, automaticOutcome);
  }

  public enum Kind {
    ABILITY_CHECK,
    SAVING_THROW
  }

  public enum NaturalMode {
    OFF,
    CHECKS_ONLY,
    SAVES_ONLY,
    CHECKS_AND_SAVES;

    private boolean appliesTo(Kind kind) {
      return this == CHECKS_AND_SAVES
          || (this == CHECKS_ONLY && kind == Kind.ABILITY_CHECK)
          || (this == SAVES_ONLY && kind == Kind.SAVING_THROW);
    }
  }

  public enum AutomaticOutcome {
    NONE,
    SUCCESS,
    FAILURE
  }

  /** A forced save carries its cause and resolves without creating or drawing a d20. */
  public record SaveOverride(AutomaticOutcome outcome, String source) {
    public SaveOverride {
      Objects.requireNonNull(outcome, "outcome");
      if (outcome == AutomaticOutcome.NONE || source == null || source.isBlank()) {
        throw new IllegalArgumentException("A forced save requires an outcome and source");
      }
    }

    public boolean success() {
      return outcome == AutomaticOutcome.SUCCESS;
    }
  }

  public record Result(
      int total, int target, int naturalD20, boolean success, AutomaticOutcome automaticOutcome) {
    public Result {
      Objects.requireNonNull(automaticOutcome, "automaticOutcome");
    }
  }
}
