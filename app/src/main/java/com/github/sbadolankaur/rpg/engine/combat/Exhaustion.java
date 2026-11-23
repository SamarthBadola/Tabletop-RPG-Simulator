package com.github.sbadolankaur.rpg.engine.combat;

public final class Exhaustion {
  private Exhaustion() {}

  public static boolean disadvantagesChecks(int levels) {
    return levels >= 1;
  }

  public static int speed(int base, int levels) {
    return levels >= 5 ? 0 : levels >= 2 ? base / 2 : base;
  }

  public static boolean disadvantagesAttacksAndSaves(int levels) {
    return levels >= 3;
  }

  public static int maximumHp(int base, int levels) {
    return levels >= 4 ? Math.max(1, base / 2) : base;
  }

  public static boolean dead(int levels) {
    return levels >= 6;
  }
}
