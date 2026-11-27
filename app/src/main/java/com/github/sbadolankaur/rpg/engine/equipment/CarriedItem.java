package com.github.sbadolankaur.rpg.engine.equipment;

/**
 * A thing that is worn, held or carried. Weights are in hundredths of a pound, so that a quarter
 * pound and 50 coins to the pound are exact. A container that hides its contents (a Bag of Holding,
 * RS-EQP-043) counts only its own weight.
 */
public record CarriedItem(String id, long weight, long contentsWeight, boolean hidesContents) {
  public CarriedItem {
    if (weight < 0 || contentsWeight < 0)
      throw new IllegalArgumentException("Weights cannot be negative");
  }

  public static CarriedItem of(String id, long weight) {
    return new CarriedItem(id, weight, 0, false);
  }

  public long counted() {
    return hidesContents ? weight : weight + contentsWeight;
  }
}
