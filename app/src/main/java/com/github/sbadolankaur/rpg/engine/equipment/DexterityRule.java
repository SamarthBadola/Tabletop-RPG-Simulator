package com.github.sbadolankaur.rpg.engine.equipment;

/** How armor uses the Dexterity modifier: all of it, at most +2, or not at all (RS-EQP-001). */
public enum DexterityRule {
  FULL,
  CAPPED,
  NONE;

  /** The part of the modifier that counts. A negative modifier still reduces light and medium. */
  public int apply(int dexterityModifier) {
    return switch (this) {
      case FULL -> dexterityModifier;
      case CAPPED -> Math.min(dexterityModifier, 2);
      case NONE -> 0;
    };
  }
}
