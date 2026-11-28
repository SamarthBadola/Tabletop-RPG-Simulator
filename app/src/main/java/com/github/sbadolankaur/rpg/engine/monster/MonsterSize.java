package com.github.sbadolankaur.rpg.engine.monster;

/** A creature's size and the hit die that goes with it (RS-MON-003). */
public enum MonsterSize {
  TINY(4),
  SMALL(6),
  MEDIUM(8),
  LARGE(10),
  HUGE(12),
  GARGANTUAN(20);

  private final int hitDie;

  MonsterSize(int hitDie) {
    this.hitDie = hitDie;
  }

  public int hitDie() {
    return hitDie;
  }
}
