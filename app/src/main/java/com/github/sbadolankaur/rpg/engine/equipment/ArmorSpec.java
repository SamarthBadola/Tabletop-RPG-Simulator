package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.Objects;

/**
 * Worn body armor as the engine reads it. {@code strengthMinimum} 0 means none. The +N of magic
 * armor is not here; it comes in as a modifier so that the breakdown can show it.
 */
public record ArmorSpec(
    String id,
    ArmorCategory category,
    int baseAc,
    DexterityRule dexterityRule,
    int strengthMinimum,
    boolean stealthDisadvantage) {
  public ArmorSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(dexterityRule, "dexterityRule");
    if (baseAc < 0 || strengthMinimum < 0) {
      throw new IllegalArgumentException("Armor values cannot be negative");
    }
  }
}
