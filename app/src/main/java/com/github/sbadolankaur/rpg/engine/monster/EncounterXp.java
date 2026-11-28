package com.github.sbadolankaur.rpg.engine.monster;

import java.util.List;

/** The experience points of an encounter (RS-MON-044 to RS-MON-046). Nothing is awarded here. */
public final class EncounterXp {
  private EncounterXp() {}

  /**
   * A creature in the encounter. {@code createdOrObject} is true for a creature made by a spell and
   * for an object combatant such as a web, which are left out of the total (RS-MON-041).
   */
  public record Member(int xp, boolean defeated, boolean createdOrObject) {}

  /**
   * The sum of the XP of every monster that is part of the encounter, defeated or not, with no
   * multiplier and no division among the characters.
   */
  public static long total(List<Member> members) {
    long sum = 0;
    for (var member : members) {
      if (!member.createdOrObject()) sum += member.xp();
    }
    return sum;
  }
}
