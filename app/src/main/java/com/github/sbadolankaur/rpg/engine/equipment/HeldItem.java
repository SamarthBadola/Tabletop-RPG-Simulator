package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.Objects;

/**
 * What a hand holds, as the hands model reads it. {@code suppliesFocus} is true for a spellcasting
 * focus, a component pouch, or a shield that carries a holy symbol (RS-EQP-016).
 */
public record HeldItem(
    String id,
    Kind kind,
    boolean twoHanded,
    boolean versatile,
    boolean light,
    boolean suppliesFocus) {
  public enum Kind {
    WEAPON,
    SHIELD,
    FOCUS,
    OTHER
  }

  public HeldItem {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(kind, "kind");
  }

  public static HeldItem weapon(String id, boolean twoHanded, boolean versatile, boolean light) {
    return new HeldItem(id, Kind.WEAPON, twoHanded, versatile, light, false);
  }

  public static HeldItem shield(String id) {
    return new HeldItem(id, Kind.SHIELD, false, false, false, false);
  }

  public static HeldItem focus(String id) {
    return new HeldItem(id, Kind.FOCUS, false, false, false, true);
  }

  public static HeldItem object(String id) {
    return new HeldItem(id, Kind.OTHER, false, false, false, false);
  }
}
