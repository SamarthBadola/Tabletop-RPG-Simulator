package com.github.sbadolankaur.rpg.engine.monster;

/**
 * Rust Metal and Antennae (RS-MON-026, E-125). Corrosion is a counter on the item instance. Magical
 * items are unaffected, and so is anything that is not metal.
 */
public final class ItemCorrosion {
  public static final int WEAPON_DESTROYED_AT = 5;

  private ItemCorrosion() {}

  /** The new penalty and whether the item is destroyed. */
  public record Result(int penalty, boolean destroyed) {}

  /** A nonmagical metal weapon that hits the monster takes -1 after it deals damage. */
  public static Result weaponHit(int penalty, boolean magical, boolean metal) {
    if (magical || !metal) return new Result(penalty, false);
    int next = penalty + 1;
    return new Result(next, next >= WEAPON_DESTROYED_AT);
  }

  /** The damage of a corroded weapon: the roll minus the penalty, never below 0. */
  public static int damageAfterPenalty(int rolled, int penalty) {
    return Math.max(0, rolled - penalty);
  }

  /** Nonmagical metal ammunition that hits is destroyed after it deals damage. */
  public static boolean ammunitionDestroyed(boolean magical, boolean metal) {
    return !magical && metal;
  }

  /** The armor class that metal armor offers after corrosion. */
  public static int armorOffers(int baseArmorClass, int penalty) {
    return baseArmorClass - penalty;
  }

  /** Armor is destroyed when it offers AC 10. */
  public static boolean armorDestroyed(int baseArmorClass, int penalty) {
    return armorOffers(baseArmorClass, penalty) <= 10;
  }

  /** A metal shield is destroyed when it offers +0. */
  public static boolean shieldDestroyed(int baseBonus, int penalty) {
    return baseBonus - penalty <= 0;
  }

  /** Antennae corrode only a nonmagical ferrous metal object. */
  public static boolean antennaeAffect(boolean magical, boolean ferrousMetal) {
    return !magical && ferrousMetal;
  }

  /** A worn or carried object lets its owner make a Dexterity saving throw to avoid the touch. */
  public static boolean ownerMayAvoid(boolean wornOrCarried) {
    return wornOrCarried;
  }
}
