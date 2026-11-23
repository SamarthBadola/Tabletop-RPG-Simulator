package com.github.sbadolankaur.rpg.engine.combat;

import java.util.Objects;

/** Hit points, the temporary buffer and the zero-hit-point state machine. */
public record CombatHealth(
    int current, int maximum, int temporary, State state, int successes, int failures) {
  public CombatHealth {
    Objects.requireNonNull(state);
    if (maximum < 1
        || current < 0
        || current > maximum
        || temporary < 0
        || (current > 0) != (state == State.ALIVE)
        || successes < 0
        || successes > 2
        || failures < 0
        || failures > 3
        || (state == State.DEAD) != (failures == 3)
        || ((state == State.ALIVE || state == State.STABLE) && (successes != 0 || failures != 0))) {
      throw new IllegalArgumentException("Inconsistent combat health");
    }
  }

  public enum State {
    ALIVE,
    DYING,
    STABLE,
    DEAD
  }

  public enum DeathMode {
    PLAYER,
    DIE_AT_ZERO,
    STABLE_AT_ZERO
  }

  public static CombatHealth of(int current, int maximum, int temporary) {
    return new CombatHealth(
        current, maximum, temporary, current == 0 ? State.DYING : State.ALIVE, 0, 0);
  }

  public static int damageAmount(
      int rolled, int flat, boolean halved, boolean resistant, boolean vulnerable, boolean immune) {
    if (immune) return 0;
    long amount = Math.max(0L, (long) rolled + flat);
    if (halved) amount /= 2;
    if (resistant) amount /= 2;
    if (vulnerable) amount *= 2;
    return Math.toIntExact(amount);
  }

  public Damage damage(
      int amount,
      boolean critical,
      DeathMode mode,
      boolean knockOut,
      boolean temporaryAbsorbsFailure) {
    if (amount < 0) throw new IllegalArgumentException("Damage must be nonnegative");
    Objects.requireNonNull(mode);
    if (amount == 0 || state == State.DEAD) return new Damage(this, 0, 0, 0);
    int absorbed = Math.min(temporary, amount);
    int remaining = amount - absorbed;
    int lost = Math.min(current, remaining);
    int nextTemporary = temporary - absorbed;
    CombatHealth next;
    if (current > lost) {
      next = of(current - lost, maximum, nextTemporary);
    } else if (remaining - lost >= maximum) {
      next = dead(nextTemporary);
    } else if (current > 0) {
      next =
          knockOut || mode == DeathMode.STABLE_AT_ZERO
              ? new CombatHealth(0, maximum, nextTemporary, State.STABLE, 0, 0)
              : mode == DeathMode.DIE_AT_ZERO ? dead(nextTemporary) : of(0, maximum, nextTemporary);
    } else {
      int nextFailures =
          failures + ((remaining == 0 && temporaryAbsorbsFailure) ? 0 : critical ? 2 : 1);
      next =
          nextFailures >= 3
              ? dead(nextTemporary)
              : new CombatHealth(0, maximum, nextTemporary, State.DYING, successes, nextFailures);
    }
    return new Damage(next, amount, absorbed, lost);
  }

  public CombatHealth deathSave(int natural, int total) {
    if (state != State.DYING || natural < 1 || natural > 20) {
      throw new IllegalArgumentException("Only a dying creature can roll a death save");
    }
    if (natural == 20) return of(1, maximum, temporary);
    if (natural == 1 || total < 10) {
      int next = failures + (natural == 1 ? 2 : 1);
      return next >= 3
          ? dead(temporary)
          : new CombatHealth(0, maximum, temporary, State.DYING, successes, next);
    }
    return successes == 2
        ? stabilize()
        : new CombatHealth(0, maximum, temporary, State.DYING, successes + 1, failures);
  }

  public CombatHealth stabilize() {
    return state != State.DYING
        ? this
        : new CombatHealth(0, maximum, temporary, State.STABLE, 0, 0);
  }

  public CombatHealth heal(int amount) {
    if (amount < 0) throw new IllegalArgumentException("Healing must be nonnegative");
    if (amount == 0 || state == State.DEAD) return this;
    return of((int) Math.min(maximum, (long) current + amount), maximum, temporary);
  }

  public CombatHealth temporary(int amount) {
    if (amount < 0) throw new IllegalArgumentException("Temporary HP must be nonnegative");
    return new CombatHealth(
        current, maximum, Math.max(temporary, amount), state, successes, failures);
  }

  private CombatHealth dead(int nextTemporary) {
    return new CombatHealth(0, maximum, nextTemporary, State.DEAD, successes, 3);
  }

  public record Damage(CombatHealth health, int damageTaken, int absorbed, int hitPointsLost) {}
}
