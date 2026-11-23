package com.github.sbadolankaur.rpg.engine.combat;

import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AdvantageSources;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** Shared condition behavior. The encounter supplies the sources and situation of each roll. */
public final class CombatConditions {
  public static final String PRONE = "srd-5.1:prone";
  public static final String UNCONSCIOUS = "srd-5.1:unconscious";
  public static final String GRAPPLED = "srd-5.1:grappled";

  private static final Map<String, String> NAMES =
      Map.ofEntries(
          Map.entry(PRONE, "Prone"),
          Map.entry(UNCONSCIOUS, "Unconscious"),
          Map.entry(GRAPPLED, "Grappled"),
          Map.entry("srd-5.1:blinded", "Blinded"),
          Map.entry("srd-5.1:charmed", "Charmed"),
          Map.entry("srd-5.1:deafened", "Deafened"),
          Map.entry("srd-5.1:frightened", "Frightened"),
          Map.entry("srd-5.1:incapacitated", "Incapacitated"),
          Map.entry("srd-5.1:invisible", "Invisible"),
          Map.entry("srd-5.1:paralyzed", "Paralyzed"),
          Map.entry("srd-5.1:petrified", "Petrified"),
          Map.entry("srd-5.1:poisoned", "Poisoned"),
          Map.entry("srd-5.1:restrained", "Restrained"),
          Map.entry("srd-5.1:stunned", "Stunned"));
  private static final Set<String> INCAPACITATING =
      Set.of(
          UNCONSCIOUS,
          "srd-5.1:incapacitated",
          "srd-5.1:paralyzed",
          "srd-5.1:petrified",
          "srd-5.1:stunned");
  private static final Set<String> GRANT_ATTACK_ADVANTAGE =
      Set.of(
          UNCONSCIOUS,
          "srd-5.1:blinded",
          "srd-5.1:paralyzed",
          "srd-5.1:petrified",
          "srd-5.1:restrained",
          "srd-5.1:stunned");
  private static final Set<String> ATTACK_DISADVANTAGE =
      Set.of(PRONE, "srd-5.1:blinded", "srd-5.1:poisoned", "srd-5.1:restrained");
  private static final Set<String> IMMOBILE =
      Set.of(
          UNCONSCIOUS,
          GRAPPLED,
          "srd-5.1:paralyzed",
          "srd-5.1:petrified",
          "srd-5.1:restrained",
          "srd-5.1:stunned");

  private CombatConditions() {}

  public static Map<String, String> names() {
    return NAMES;
  }

  public static boolean incapacitated(Set<String> conditions) {
    return conditions.stream().anyMatch(INCAPACITATING::contains);
  }

  public static int speed(int base, Set<String> conditions) {
    return conditions.stream().anyMatch(IMMOBILE::contains) ? 0 : base;
  }

  public static boolean automaticCritical(Set<String> target, int distance) {
    return distance <= 5 && (target.contains(UNCONSCIOUS) || target.contains("srd-5.1:paralyzed"));
  }

  public static boolean forcedSaveFailure(Set<String> conditions, Ability ability) {
    return (ability == Ability.STR || ability == Ability.DEX)
        && (conditions.contains(UNCONSCIOUS)
            || conditions.contains("srd-5.1:paralyzed")
            || conditions.contains("srd-5.1:petrified")
            || conditions.contains("srd-5.1:stunned"));
  }

  public static boolean forcedCheckFailure(Set<String> conditions, boolean sight, boolean hearing) {
    return sight && conditions.contains("srd-5.1:blinded")
        || hearing && conditions.contains("srd-5.1:deafened");
  }

  public static boolean canSee(Set<String> viewer, Set<String> target, boolean obscured) {
    return !obscured
        && !viewer.contains("srd-5.1:blinded")
        && !target.contains("srd-5.1:invisible");
  }

  public static AdvantageSources attack(
      Set<String> attacker,
      Set<String> target,
      int distance,
      boolean canSeeTarget,
      boolean targetCanSeeAttacker,
      boolean rangedThreat,
      boolean targetDodging,
      boolean helped,
      boolean frightened,
      RollMode selected) {
    var advantage = new ArrayList<String>();
    var disadvantage = new ArrayList<String>();
    target.stream().filter(GRANT_ATTACK_ADVANTAGE::contains).sorted().forEach(advantage::add);
    attacker.stream().filter(ATTACK_DISADVANTAGE::contains).sorted().forEach(disadvantage::add);
    if (target.contains(PRONE)) (distance <= 5 ? advantage : disadvantage).add("Prone target");
    if (!canSeeTarget) disadvantage.add("Unseen target");
    if (!targetCanSeeAttacker) advantage.add("Unseen attacker");
    if (rangedThreat) disadvantage.add("Hostile creature within 5 feet");
    if (targetDodging && targetCanSeeAttacker) disadvantage.add("Dodge");
    if (helped) advantage.add("Help");
    if (frightened) disadvantage.add("Frightened");
    if (selected == RollMode.ADVANTAGE) advantage.add("DM advantage");
    if (selected == RollMode.DISADVANTAGE) disadvantage.add("DM disadvantage");
    return new AdvantageSources(advantage, disadvantage);
  }
}
