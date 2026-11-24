package com.github.sbadolankaur.rpg.engine.spell;

/** Level bands shared by damaging cantrips which gain one die at each milestone. */
public final class SpellScaling {
  private SpellScaling() {}

  public static int cantripDice(int characterLevel) {
    if (characterLevel < 1) throw new IllegalArgumentException("Character level must be positive");
    if (characterLevel < 5) return 1;
    if (characterLevel < 11) return 2;
    if (characterLevel < 17) return 3;
    return 4;
  }
}
