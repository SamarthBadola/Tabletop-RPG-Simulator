package com.github.sbadolankaur.rpg.engine.monster;

/**
 * Legendary Resistance (N/day) (RS-MON-032, RS-MON-033). When the creature fails a saving throw it
 * can choose to succeed instead and spend a use. The uses come back on a long rest. The choice
 * changes the outcome of the save, not the roll.
 */
public final class LegendaryResistance {
  private LegendaryResistance() {}

  /** The uses a creature has left today. */
  public record Uses(int maximum, int remaining) {
    public Uses {
      if (maximum < 1 || remaining < 0 || remaining > maximum) {
        throw new IllegalArgumentException("Invalid Legendary Resistance uses");
      }
    }

    public static Uses full(int maximum) {
      return new Uses(maximum, maximum);
    }

    public Uses afterLongRest() {
      return full(maximum);
    }
  }

  /** How a failed save came about. */
  public enum Failure {
    /** The roll missed the DC. The offer appears at AFTER_SAVE_FAIL (RS-REA-013). */
    ROLLED,
    /** A condition made the save fail with no roll, so only this offer is made (RS-MON-033). */
    AUTOMATIC
  }

  /**
   * Whether the DM is offered Legendary Resistance. An automatic failure is offered only when the
   * setting legendaryResistanceOverridesAutoFail is on (RS-MON-R13). With no uses left there is no
   * offer.
   */
  public static boolean isOffered(Uses uses, Failure failure, boolean overridesAutoFail) {
    if (uses.remaining() < 1) return false;
    return failure == Failure.ROLLED || overridesAutoFail;
  }

  /** The use after the DM's choice. Declining keeps the use. */
  public static Uses choose(Uses uses, boolean useIt) {
    if (!useIt) return uses;
    if (uses.remaining() < 1) throw new IllegalStateException("No Legendary Resistance left");
    return new Uses(uses.maximum(), uses.remaining() - 1);
  }
}
