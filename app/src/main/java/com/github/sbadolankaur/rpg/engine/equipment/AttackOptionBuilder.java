package com.github.sbadolankaur.rpg.engine.equipment;

import com.github.sbadolankaur.rpg.engine.equipment.AttackOption.Kind;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the attack options for the current state (E-100, RS-EQP-018 to RS-EQP-030). It runs when
 * an attack is declared. Part B resolves the option the player picks.
 */
public final class AttackOptionBuilder {
  private static final int REACH = 5;
  private static final String IMPROVISED_DICE = "1d4";

  private AttackOptionBuilder() {}

  /**
   * @param catalog the weapon definitions, by the id that a hand or carried item uses
   * @param carriedThrowables ids of carried (not held) weapons that can be thrown
   */
  public static List<AttackOption> build(
      AttackerState attacker, Map<String, WeaponSpec> catalog, List<String> carriedThrowables) {
    var options = new ArrayList<AttackOption>();
    var hands = attacker.hands();
    var seen = new ArrayList<String>();

    for (var hand : Hand.values()) {
      var held = hands.in(hand);
      if (held == null || seen.contains(held.id())) continue;
      seen.add(held.id());
      var weapon = catalog.get(held.id());
      if (held.kind() == HeldItem.Kind.WEAPON && weapon != null) {
        weaponOptions(attacker, hand, weapon, options);
      } else if (held.kind() != HeldItem.Kind.SHIELD) {
        options.add(improvised(attacker, Kind.IMPROVISED_MELEE, held.id(), 5, 0, 0));
        options.add(improvised(attacker, Kind.IMPROVISED_THROWN, held.id(), 0, 20, 60));
      }
    }
    for (var id : carriedThrowables) {
      var weapon = catalog.get(id);
      if (weapon != null && weapon.has(WeaponProperty.THROWN) && !seen.contains(id)) {
        options.add(thrown(attacker, weapon));
      }
    }
    options.add(unarmed(attacker));
    return options;
  }

  private static void weaponOptions(
      AttackerState attacker, Hand hand, WeaponSpec weapon, List<AttackOption> out) {
    var hands = attacker.hands();
    if (!weapon.ranged()) {
      var grips = hands.grips(hand);
      for (var grip : new Grip[] {Grip.ONE_HANDED, Grip.TWO_HANDED}) {
        if (!grips.contains(grip)) continue;
        String dice =
            grip == Grip.TWO_HANDED && weapon.versatileDice() != null
                ? weapon.versatileDice()
                : weapon.damageDice();
        out.add(melee(attacker, hand, weapon, grip, dice));
      }
      if (weapon.has(WeaponProperty.THROWN)) out.add(thrown(attacker, weapon));
      if (!weapon.has(WeaponProperty.THROWN)) {
        out.add(improvised(attacker, Kind.IMPROVISED_THROWN, weapon.id(), 0, 20, 60));
      }
    } else if (weapon.has(WeaponProperty.AMMUNITION)) {
      out.add(ranged(attacker, weapon));
      out.add(improvised(attacker, Kind.IMPROVISED_MELEE, weapon.id(), REACH, 0, 0));
    } else if (weapon.has(WeaponProperty.THROWN)) {
      out.add(thrown(attacker, weapon));
    }
  }

  private static AttackOption melee(
      AttackerState a, Hand hand, WeaponSpec w, Grip grip, String dice) {
    var ability = meleeAbility(a, w);
    int mod = a.scores().modifier(ability);
    boolean offHand =
        hand == Hand.OFF && a.hands().twoWeaponFighting() && w.has(WeaponProperty.LIGHT);
    int damageMod = offHand ? Math.min(0, mod) : mod;
    boolean proficient = proficient(a, w);
    var notes = new ArrayList<String>();
    var disadvantage = new ArrayList<String>();
    String blocked = null;
    if (w.has(WeaponProperty.HEAVY) && smallOrTiny(a))
      disadvantage.add("Heavy weapon used by a Small or Tiny creature");
    if (w.special() == SpecialRule.LANCE) {
      notes.add("Needs two hands unless mounted");
      if (!a.mounted() && !a.hands().isFree(hand.other()))
        blocked = "A lance needs two hands when not mounted";
    }
    if (offHand)
      notes.add(
          "Off-hand attack: the ability modifier is not added to damage unless it is negative");
    return new AttackOption(
        offHand ? Kind.OFF_HAND : Kind.MELEE,
        w.id(),
        ability,
        proficient,
        mod + (proficient ? a.proficiencyBonus() : 0) + w.magicBonus(),
        dice,
        damageMod + w.magicBonus(),
        w.damageType(),
        grip,
        w.has(WeaponProperty.REACH) ? REACH + 5 : REACH,
        0,
        0,
        null,
        null,
        disadvantage,
        w.special() == SpecialRule.LANCE,
        null,
        w.magical(),
        w.silvered(),
        blocked,
        notes);
  }

  private static AttackOption ranged(AttackerState a, WeaponSpec w) {
    var ability = w.has(WeaponProperty.FINESSE) ? better(a) : Ability.DEX;
    int mod = a.scores().modifier(ability);
    boolean proficient = proficient(a, w);
    var disadvantage = new ArrayList<String>();
    if (w.has(WeaponProperty.HEAVY) && smallOrTiny(a))
      disadvantage.add("Heavy weapon used by a Small or Tiny creature");
    String blocked =
        a.ammunition().getOrDefault(w.ammunitionType(), 0) < 1 ? "No ammunition" : null;
    return new AttackOption(
        Kind.RANGED,
        w.id(),
        ability,
        proficient,
        mod + (proficient ? a.proficiencyBonus() : 0) + w.magicBonus(),
        w.damageDice(),
        mod + w.magicBonus(),
        w.damageType(),
        null,
        0,
        w.normalRange(),
        w.longRange(),
        w.ammunitionType(),
        w.has(WeaponProperty.LOADING) ? 1 : null,
        disadvantage,
        false,
        null,
        w.magical(),
        w.silvered(),
        blocked,
        List.of());
  }

  private static AttackOption thrown(AttackerState a, WeaponSpec w) {
    var ability = w.ranged() && !w.has(WeaponProperty.FINESSE) ? Ability.DEX : meleeAbility(a, w);
    int mod = a.scores().modifier(ability);
    boolean proficient = proficient(a, w);
    boolean net = w.special() == SpecialRule.NET;
    var notes = new ArrayList<String>();
    if (net)
      notes.add(
          "A Large or smaller creature that is hit is restrained; formless and Huge or larger creatures are unaffected");
    return new AttackOption(
        Kind.THROWN,
        w.id(),
        ability,
        proficient,
        mod + (proficient ? a.proficiencyBonus() : 0) + w.magicBonus(),
        net ? null : w.damageDice(),
        net ? 0 : mod + w.magicBonus(),
        net ? null : w.damageType(),
        null,
        0,
        w.thrownNormal(),
        w.thrownLong(),
        null,
        net || w.has(WeaponProperty.LOADING) ? 1 : null,
        List.of(),
        false,
        net ? "restrained" : null,
        w.magical(),
        w.silvered(),
        null,
        notes);
  }

  private static AttackOption improvised(
      AttackerState a, Kind kind, String source, int reach, int normal, int longRange) {
    int mod = a.scores().modifier(Ability.STR);
    return new AttackOption(
        kind,
        source,
        Ability.STR,
        false,
        mod,
        IMPROVISED_DICE,
        mod,
        "bludgeoning",
        null,
        reach,
        normal,
        longRange,
        null,
        null,
        List.of(),
        false,
        null,
        false,
        false,
        null,
        List.of("Improvised weapon: no proficiency bonus; the DM can change the damage type"));
  }

  private static AttackOption unarmed(AttackerState a) {
    int mod = a.scores().modifier(Ability.STR);
    return new AttackOption(
        Kind.UNARMED,
        "unarmed",
        Ability.STR,
        true,
        mod + a.proficiencyBonus(),
        null,
        1 + mod,
        "bludgeoning",
        null,
        REACH,
        0,
        0,
        null,
        null,
        List.of(),
        false,
        null,
        false,
        false,
        null,
        List.of());
  }

  /** Strength for melee, or the better of Strength and Dexterity for finesse (RS-EQP-019). */
  private static Ability meleeAbility(AttackerState a, WeaponSpec w) {
    return w.has(WeaponProperty.FINESSE) ? better(a) : Ability.STR;
  }

  private static Ability better(AttackerState a) {
    return a.scores().modifier(Ability.DEX) > a.scores().modifier(Ability.STR)
        ? Ability.DEX
        : Ability.STR;
  }

  private static boolean proficient(AttackerState a, WeaponSpec w) {
    return a.categoryProficiencies().contains(w.category())
        || a.weaponProficiencies().contains(w.id());
  }

  private static boolean smallOrTiny(AttackerState a) {
    return a.size() == CreatureSize.TINY || a.size() == CreatureSize.SMALL;
  }
}
