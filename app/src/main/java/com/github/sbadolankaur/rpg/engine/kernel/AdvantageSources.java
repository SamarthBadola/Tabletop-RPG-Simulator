package com.github.sbadolankaur.rpg.engine.kernel;

import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import java.util.List;

/** The applicable advantage and disadvantage sources for one d20 test. */
public record AdvantageSources(List<String> advantages, List<String> disadvantages) {
  public AdvantageSources {
    advantages = List.copyOf(advantages);
    disadvantages = List.copyOf(disadvantages);
  }

  /** Multiple sources of one kind do not stack; at least one of each kind cancels. */
  public RollMode mode() {
    if (!advantages.isEmpty() && !disadvantages.isEmpty()) return RollMode.NORMAL;
    if (!advantages.isEmpty()) return RollMode.ADVANTAGE;
    if (!disadvantages.isEmpty()) return RollMode.DISADVANTAGE;
    return RollMode.NORMAL;
  }
}
