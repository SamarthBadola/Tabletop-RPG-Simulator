package com.github.sbadolankaur.rpg.engine.dice;

import java.security.SecureRandom;
import java.util.Objects;

/** Production random source backed by SecureRandom. */
public final class SecureRandomSource implements RandomSource {
  private final SecureRandom random;

  public SecureRandomSource() {
    this(new SecureRandom());
  }

  public SecureRandomSource(SecureRandom random) {
    this.random = Objects.requireNonNull(random);
  }

  @Override
  public int nextInt(int bound) {
    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
    return random.nextInt(bound);
  }
}
