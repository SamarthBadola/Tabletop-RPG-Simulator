package com.github.sbadolankaur.rpg.engine.spell;

/** Casts on one creature's turn. A new turn starts with an empty history. */
public record SpellTurn(boolean bonusActionCast, boolean otherActionCast, boolean reactionCast) {
  public enum CastingTime {
    ACTION,
    BONUS_ACTION,
    REACTION
  }

  public enum Rule {
    OWN_TURN,
    ANY_TURN,
    REACTION_EXEMPT,
    OFF
  }

  public static SpellTurn start() {
    return new SpellTurn(false, false, false);
  }

  public boolean permits(Rule rule, CastingTime time, int spellLevel, boolean ownTurn) {
    if (rule == Rule.OFF || !ownTurn && rule != Rule.ANY_TURN) return true;
    if (rule == Rule.REACTION_EXEMPT && time == CastingTime.REACTION) return true;
    if (bonusActionCast) return actionCantrip(time, spellLevel);
    return time != CastingTime.BONUS_ACTION
        || !(otherActionCast || reactionCast && rule != Rule.REACTION_EXEMPT);
  }

  public SpellTurn record(CastingTime time, int spellLevel, boolean ownTurn) {
    if (!ownTurn || actionCantrip(time, spellLevel)) return this;
    return switch (time) {
      case BONUS_ACTION -> new SpellTurn(true, otherActionCast, reactionCast);
      case REACTION -> new SpellTurn(bonusActionCast, otherActionCast, true);
      case ACTION -> new SpellTurn(bonusActionCast, true, reactionCast);
    };
  }

  private static boolean actionCantrip(CastingTime time, int spellLevel) {
    return time == CastingTime.ACTION && spellLevel == 0;
  }
}
