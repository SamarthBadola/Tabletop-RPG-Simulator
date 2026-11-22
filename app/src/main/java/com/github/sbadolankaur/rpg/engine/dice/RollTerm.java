package com.github.sbadolankaur.rpg.engine.dice;

import java.util.List;

public record RollTerm(
    String label,
    String source,
    int sign,
    String expression,
    List<DieRoll> dice,
    int fixed,
    int subtotal,
    boolean doublesOnCrit) {
  public RollTerm {
    dice = List.copyOf(dice);
  }
}
