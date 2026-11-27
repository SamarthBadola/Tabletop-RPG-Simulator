package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.Objects;
import java.util.Set;

/**
 * A weapon as the option builder reads it. Ranges are in feet and 0 means none. {@code
 * thrownNormal} and {@code thrownLong} are the range when the weapon is thrown, {@code normalRange}
 * and {@code longRange} the range of a weapon that fires ammunition. {@code versatileDice} is null
 * when the weapon is not versatile.
 */
public record WeaponSpec(
    String id,
    WeaponCategory category,
    boolean ranged,
    String damageDice,
    String damageType,
    String versatileDice,
    Set<WeaponProperty> properties,
    int normalRange,
    int longRange,
    int thrownNormal,
    int thrownLong,
    String ammunitionType,
    SpecialRule special,
    int magicBonus,
    boolean silvered) {
  public WeaponSpec {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(special, "special");
    properties = Set.copyOf(properties);
  }

  public boolean has(WeaponProperty property) {
    return properties.contains(property);
  }

  /** A weapon with a +N bonus counts as magical (RS-EQP-030). */
  public boolean magical() {
    return magicBonus > 0;
  }
}
