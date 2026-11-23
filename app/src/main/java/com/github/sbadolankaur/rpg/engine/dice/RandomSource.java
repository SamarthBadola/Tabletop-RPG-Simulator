package com.github.sbadolankaur.rpg.engine.dice;

@FunctionalInterface
public interface RandomSource {
  /** Returns a value in [0, bound). */
  int nextInt(int bound);
}
