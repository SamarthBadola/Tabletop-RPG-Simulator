package com.github.sbadolankaur.rpg.engine.equipment;

import com.github.sbadolankaur.rpg.engine.kernel.AbilityScores;
import java.util.Map;
import java.util.Set;

/**
 * What the option builder needs to know about the attacker: scores, proficiency, size, the hands,
 * whether the attacker is mounted (a DM-set state until mounts exist, E-127) and the ammunition
 * stacks by type.
 */
public record AttackerState(
    AbilityScores scores,
    int proficiencyBonus,
    CreatureSize size,
    Set<WeaponCategory> categoryProficiencies,
    Set<String> weaponProficiencies,
    HandState hands,
    boolean mounted,
    Map<String, Integer> ammunition) {
  public AttackerState {
    categoryProficiencies = Set.copyOf(categoryProficiencies);
    weaponProficiencies = Set.copyOf(weaponProficiencies);
    ammunition = Map.copyOf(ammunition);
  }
}
