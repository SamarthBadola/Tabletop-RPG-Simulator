package com.github.sbadolankaur.rpg.engine.monster;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;

/**
 * Swarms (RS-MON-023, PB-G5, E-121). A swarm of Tiny creatures takes the space of a Medium creature
 * and shares space with other creatures. It cannot regain hit points or gain temporary hit points,
 * and its bite has reach 0 and does less damage at half its hit points or fewer.
 */
public final class Swarm {
  public static final int REACH_FEET = 0;

  /** Entering a swarm's space costs movement as difficult terrain (RS-MOV-004). */
  public static final int ENTERING_COST_MULTIPLIER = 2;

  private Swarm() {}

  /** A swarm gains nothing from healing (RS-MON-R16). */
  public static CombatHealth heal(CombatHealth health, int amount) {
    if (amount < 0) throw new IllegalArgumentException("Healing must be nonnegative");
    return health;
  }

  /** A swarm gains no temporary hit points (RS-MON-R16). */
  public static CombatHealth temporary(CombatHealth health, int amount) {
    if (amount < 0) throw new IllegalArgumentException("Temporary HP must be nonnegative");
    return health;
  }

  /** The bite uses its lower damage when the swarm has half its maximum or fewer (WE-G6). */
  public static String biteDamage(int current, int maximum, String normal, String reduced) {
    return current * 2L <= maximum ? reduced : normal;
  }

  /** Movement cost, in feet, of entering the swarm's space. */
  public static int enteringCostFeet(int feet) {
    return feet * ENTERING_COST_MULTIPLIER;
  }

  /** Leaving a swarm's space is leaving a reach-0 threat, so it can provoke (RS-MON-R17). */
  public static boolean leavingProvokes() {
    return true;
  }
}
