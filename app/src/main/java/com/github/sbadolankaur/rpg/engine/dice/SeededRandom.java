package com.github.sbadolankaur.rpg.engine.dice;

/** SplitMix64 with rejection sampling; its sequence is independent of JDK RNG implementations. */
public final class SeededRandom implements RandomSource {
  private long state;

  public SeededRandom(long seed) {
    state = seed;
  }

  @Override
  public int nextInt(int bound) {
    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
    long m = Integer.toUnsignedLong(bound);
    long candidate;
    long remainder;
    do {
      candidate = nextLong() >>> 1;
      remainder = candidate % m;
    } while (candidate - remainder + (m - 1) < 0L);
    return (int) remainder;
  }

  private long nextLong() {
    long z = (state += 0x9E3779B97F4A7C15L);
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
