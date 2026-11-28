package com.github.sbadolankaur.rpg.engine.monster;

/**
 * Creatures made by a spell or feature (RS-MON-037 to RS-MON-041, E-126). The engine spawns each
 * one as a combatant with its own hit points. In version 1 the DM enters every choice, so the
 * controller says whom to ask.
 */
public final class CreatureCreation {
  private CreatureCreation() {}

  public enum Controller {
    DM,
    CASTER_PLAYER
  }

  public enum Join {
    /** Each creature rolls its own initiative. */
    OWN_INITIATIVE,
    /** The spell says it shares the caster's count. */
    SHARES_CASTERS_COUNT,
    /** A group created at once rolls once and acts as one entry. */
    GROUP_ROLLS_ONCE
  }

  /** What the creating effect says. */
  public record Spec(
      String definitionId, int count, Controller controller, Join join, boolean vanishesAtZero) {
    public Spec {
      if (definitionId == null || definitionId.isBlank() || count < 1) {
        throw new IllegalArgumentException("Invalid creature creation");
      }
    }
  }

  /** How many initiative rolls the creation needs. */
  public static int initiativeRolls(Spec spec) {
    return switch (spec.join()) {
      case OWN_INITIATIVE -> spec.count();
      case SHARES_CASTERS_COUNT -> 0;
      case GROUP_ROLLS_ONCE -> 1;
    };
  }

  /** When the creatures join the order. A creature made outside an encounter is queued. */
  public enum JoinAt {
    NOW,
    NEXT_ROUND_START
  }

  public static JoinAt joinAt(boolean encounterRunning) {
    return encounterRunning ? JoinAt.NOW : JoinAt.NEXT_ROUND_START;
  }

  public enum Ending {
    /** It dies as any monster does, through the zero-hit-point hook. */
    DIES,
    /** It is removed without dying: no death saves, no hook, no death effects. */
    REMOVED_WITHOUT_DYING
  }

  /** The creature reaches 0 hit points. Either way the effect that made it does not end. */
  public static Ending atZeroHitPoints(Spec spec) {
    return spec.vanishesAtZero() ? Ending.REMOVED_WITHOUT_DYING : Ending.DIES;
  }

  /** The effect ends (concentration lost, duration over, dispelled): it is removed, not killed. */
  public static Ending whenEffectEnds() {
    return Ending.REMOVED_WITHOUT_DYING;
  }

  /** A created creature gives no XP and is left out of the encounter total (RS-MON-041). */
  public static EncounterXp.Member asEncounterMember(int xp, boolean defeated) {
    return new EncounterXp.Member(xp, defeated, true);
  }
}
