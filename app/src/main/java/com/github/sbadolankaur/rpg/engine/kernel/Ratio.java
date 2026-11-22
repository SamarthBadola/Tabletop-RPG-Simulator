package com.github.sbadolankaur.rpg.engine.kernel;

/** An exact rational number with a positive denominator and reduced terms. */
public record Ratio(int numerator, int denominator) implements Comparable<Ratio> {
  public static final Ratio ZERO = new Ratio(0, 1);
  public static final Ratio ONE = new Ratio(1, 1);

  public Ratio {
    if (denominator <= 0) {
      throw new IllegalArgumentException("Ratio denominator must be positive");
    }
    long divisor = gcd(Math.abs((long) numerator), denominator);
    numerator = Math.toIntExact(numerator / divisor);
    denominator = Math.toIntExact(denominator / divisor);
  }

  public static Ratio of(int integer) {
    return new Ratio(integer, 1);
  }

  public boolean isInteger() {
    return denominator == 1;
  }

  public Ratio multiply(Ratio other) {
    long productNumerator = Math.multiplyExact((long) numerator, other.numerator);
    long productDenominator = Math.multiplyExact((long) denominator, other.denominator);
    return new Ratio(Math.toIntExact(productNumerator), Math.toIntExact(productDenominator));
  }

  public Ratio add(Ratio other) {
    long first = Math.multiplyExact((long) numerator, other.denominator);
    long second = Math.multiplyExact((long) other.numerator, denominator);
    long sum = Math.addExact(first, second);
    long commonDenominator = Math.multiplyExact((long) denominator, other.denominator);
    return new Ratio(Math.toIntExact(sum), Math.toIntExact(commonDenominator));
  }

  @Override
  public int compareTo(Ratio other) {
    long first = Math.multiplyExact((long) numerator, other.denominator);
    long second = Math.multiplyExact((long) other.numerator, denominator);
    return Long.compare(first, second);
  }

  /** Multiplies an integer and floors the exact result toward negative infinity. */
  public int floorProduct(int value) {
    long product = Math.multiplyExact((long) value, numerator);
    return Math.toIntExact(Math.floorDiv(product, denominator));
  }

  private static long gcd(long first, long second) {
    long a = first;
    long b = second;
    while (b != 0) {
      long remainder = a % b;
      a = b;
      b = remainder;
    }
    return a == 0 ? 1 : a;
  }
}
