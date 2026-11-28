package com.github.sbadolankaur.rpg.engine.monster;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;

/**
 * The zero-hit-point hook (RS-MON-013, E-117). It runs after damage is applied and before the death
 * step. Handlers run in a fixed order: handlers that keep the creature up (Undead Fortitude), then
 * handlers that delay death (Regeneration), then source riders (the giant spider's poison). The
 * first handler that applies and stops the outcome ends the chain. Instant death is checked first
 * and is never replaced (RS-DMG-010).
 */
public final class ZeroHitPointHook {
  private ZeroHitPointHook() {}

  /** The damage instance that took the creature down. The amount is after resistance. */
  public record DamageInstance(
      int amount,
      boolean critical,
      boolean radiant,
      boolean poison,
      boolean acidOrFireGotThrough) {}

  /** The traits of the creature that took the damage. */
  public record Defender(boolean undeadFortitude, boolean regeneration) {
    public static final Defender NONE = new Defender(false, false);
  }

  /** Rolls a saving throw for the hook and says whether it succeeded. */
  @FunctionalInterface
  public interface SaveOutcome {
    boolean succeeds(Ability ability, int dc);
  }

  public enum Result {
    NONE,
    STAYED_UP,
    DELAYED_DEATH,
    STABLE_BY_POISON
  }

  /**
   * The outcome. {@code saveDc} is 0 when no save was made. {@code dyingByTrait} is the mark that
   * Regeneration needs. {@code poisonedForOneHour} says the poison rider applied (poisoned and
   * paralyzed for one hour, RS-MON-027).
   */
  public record Outcome(
      CombatHealth health,
      Result result,
      int saveDc,
      boolean dyingByTrait,
      boolean poisonedForOneHour) {}

  /** True if the damage passes the maximum on top of what brings the creature to 0. */
  public static boolean isInstantDeath(CombatHealth before, int amount) {
    int absorbed = Math.min(before.temporary(), amount);
    int remaining = amount - absorbed;
    int lost = Math.min(before.current(), remaining);
    return remaining - lost >= before.maximum();
  }

  /**
   * Applies one damage instance to a creature. {@code mode} is how the creature dies at 0 (a
   * monster uses DIE_AT_ZERO, and monsters make no death saving throws, RS-MON-R5). {@code
   * poisonRider} says the attacker has the poison rider of RS-MON-027.
   */
  public static Outcome apply(
      CombatHealth before,
      DamageInstance instance,
      Defender defender,
      boolean poisonRider,
      CombatHealth.DeathMode mode,
      SaveOutcome saves) {
    if (before.state() == CombatHealth.State.DEAD) return plain(before);
    boolean instant = isInstantDeath(before, instance.amount());
    if (before.current() == 0 && mode != CombatHealth.DeathMode.PLAYER) {
      if (instance.amount() == 0) return plain(before);
      return plain(instant ? dead(before, before.temporary()) : before);
    }
    var damage = before.damage(instance.amount(), instance.critical(), mode, false, false);
    CombatHealth after = damage.health();
    boolean droppedToZero = before.current() > 0 && after.current() == 0;
    if (!droppedToZero || instant) return plain(after);

    int dc = 0;
    if (defender.undeadFortitude() && !instance.critical() && !instance.radiant()) {
      dc = 5 + instance.amount();
      if (saves.succeeds(Ability.CON, dc)) {
        var up = CombatHealth.of(1, before.maximum(), after.temporary());
        return new Outcome(up, Result.STAYED_UP, dc, false, false);
      }
    }
    if (defender.regeneration()) {
      var unconscious = CombatHealth.of(0, before.maximum(), 0);
      return new Outcome(unconscious, Result.DELAYED_DEATH, dc, true, false);
    }
    if (poisonRider && instance.poison()) {
      var stable =
          new CombatHealth(0, before.maximum(), after.temporary(), CombatHealth.State.STABLE, 0, 0);
      return new Outcome(stable, Result.STABLE_BY_POISON, dc, false, true);
    }
    return new Outcome(after, Result.NONE, dc, false, false);
  }

  private static Outcome plain(CombatHealth health) {
    return new Outcome(health, Result.NONE, 0, false, false);
  }

  private static CombatHealth dead(CombatHealth before, int temporary) {
    return new CombatHealth(
        0, before.maximum(), temporary, CombatHealth.State.DEAD, before.successes(), 3);
  }
}
