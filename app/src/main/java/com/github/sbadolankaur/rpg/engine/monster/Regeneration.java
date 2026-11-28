package com.github.sbadolankaur.rpg.engine.monster;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;

/**
 * Regeneration and delayed death (RS-MON-015). The troll does not die at 0 hit points: it falls
 * unconscious and is marked as dying by trait. At the start of its turn it regains 10 hit points if
 * the trait works, and dies if it does not. Acid or fire damage that gets through its defenses
 * stops the trait from working at the start of its next turn.
 */
public final class Regeneration {
  public static final int AMOUNT = 10;

  private Regeneration() {}

  /** The marks the trait needs. {@code suppressed} lasts until the start of the next turn. */
  public record State(boolean dyingByTrait, boolean suppressed) {
    public static final State CLEAR = new State(false, false);
  }

  /** What the start of the turn did. */
  public record Turn(CombatHealth health, State state, int regained) {}

  /** Acid or fire damage counts only if at least 1 point remains after resistance and immunity. */
  public static boolean suppresses(int acidOrFireDamageAfterResistance) {
    return acidOrFireDamageAfterResistance >= 1;
  }

  /** Records damage taken since the last turn. */
  public static State afterDamage(State state, boolean acidOrFireGotThrough) {
    return acidOrFireGotThrough ? new State(state.dyingByTrait(), true) : state;
  }

  /** Healing above 0 ends the dying mark (RS-MON-R6). */
  public static State afterHealing(State state, CombatHealth health) {
    return health.current() > 0 ? new State(false, state.suppressed()) : state;
  }

  /** The START_OF_OWN_TURN step of the trait (RS-MON-036). */
  public static Turn startOfTurn(CombatHealth health, State state) {
    if (health.state() == CombatHealth.State.DEAD) return new Turn(health, State.CLEAR, 0);
    if (state.suppressed()) {
      if (state.dyingByTrait()) {
        var dead =
            new CombatHealth(
                0, health.maximum(), 0, CombatHealth.State.DEAD, health.successes(), 3);
        return new Turn(dead, State.CLEAR, 0);
      }
      return new Turn(health, State.CLEAR, 0);
    }
    if (!state.dyingByTrait() && health.current() >= health.maximum()) {
      return new Turn(health, State.CLEAR, 0);
    }
    var healed = health.heal(AMOUNT);
    return new Turn(healed, State.CLEAR, healed.current() - health.current());
  }
}
