package com.github.sbadolankaur.rpg.engine.monster;

/** Which monster actions can be made as an opportunity attack (RS-MON-010, E-133). */
public final class MonsterActions {
  private MonsterActions() {}

  public enum Kind {
    MELEE_WEAPON_ATTACK,
    UNARMED_STRIKE,
    MULTIATTACK,
    BREATH_WEAPON,
    SPELL,
    OTHER
  }

  /** An opportunity attack is one melee weapon attack or unarmed strike, never a composite. */
  public static boolean canBeOpportunityAttack(Kind kind) {
    return kind == Kind.MELEE_WEAPON_ATTACK || kind == Kind.UNARMED_STRIKE;
  }
}
