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

  /** The size as a stat block writes it, such as "medium". */
  public static MonsterSize fromKey(String key) {
    if (key == null) throw new IllegalArgumentException("A size is required");
    return switch (key) {
      case "tiny" -> TINY;
      case "small" -> SMALL;
      case "medium" -> MEDIUM;
      case "large" -> LARGE;
      case "huge" -> HUGE;
      case "gargantuan" -> GARGANTUAN;
      default -> throw new IllegalArgumentException("Unknown size: " + key);
    };
  }

  public int hitDie() {
    return hitDie;
  }
}
