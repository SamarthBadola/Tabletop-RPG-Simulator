package com.github.sbadolankaur.rpg.engine.equipment;

/** The five coins, with their value in copper (RS-EQP-037). */
public enum Denomination {
  CP(1),
  SP(10),
  EP(50),
  GP(100),
  PP(1000);

  private final int copper;

  Denomination(int copper) {
    this.copper = copper;
  }

  public int copper() {
    return copper;
  }
}
