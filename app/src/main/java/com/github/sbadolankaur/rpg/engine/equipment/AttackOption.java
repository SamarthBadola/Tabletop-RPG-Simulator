package com.github.sbadolankaur.rpg.engine.equipment;

import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.util.List;

/**
 * One way to attack, with every number the roll needs (11.5). {@code damageDice} is null when the
 * attack deals fixed damage only (an unarmed strike) or none (a net). A non-null {@code
 * blockedReason} means the option is listed but cannot be used now.
 */
public record AttackOption(
    Kind kind,
    String source,
    Ability ability,
    boolean proficient,
    int attackBonus,
    String damageDice,
    int damageBonus,
    String damageType,
    Grip grip,
    int reachFeet,
    int normalRange,
    int longRange,
    String ammunitionType,
    Integer shotsPerAction,
    List<String> alwaysDisadvantage,
    boolean disadvantageWithinFiveFeet,
    String appliesCondition,
    boolean magical,
    boolean silvered,
    String blockedReason,
    List<String> notes) {
  public enum Kind {
    MELEE,
    OFF_HAND,
    RANGED,
    THROWN,
    IMPROVISED_MELEE,
    IMPROVISED_THROWN,
    UNARMED
  }

  public AttackOption {
    alwaysDisadvantage = List.copyOf(alwaysDisadvantage);
    notes = List.copyOf(notes);
  }
}
