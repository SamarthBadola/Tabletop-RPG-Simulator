package com.github.sbadolankaur.rpg.engine.equipment;

/** A creature has two hands (RS-EQP-011). */
public enum Hand {
  MAIN,
  OFF;

  public Hand other() {
    return this == MAIN ? OFF : MAIN;
  }
}
