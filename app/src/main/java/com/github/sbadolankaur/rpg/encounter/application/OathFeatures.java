package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.ProficiencyBonus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class OathFeatures {
  private static final String SACRED = CombatFeatures.SACRED_WEAPON;
  private static final String TURNED = "srd-5.1:turned";
  private static final Set<String> WEAPON_SLOTS = Set.of("MAIN_HAND", "BOTH_HANDS");

  private OathFeatures() {}

  static void sacredWeapon(Context context, Creature wielder) {
    var held = heldWeapon(context, wielder);
    if (held == null
        || wielder.attacks().stream().allMatch(attack -> "unarmed".equals(attack.key())))
      throw CombatFeatures.invalidInput("featureKey");
    CombatActions.spend(context, com.github.sbadolankaur.rpg.engine.combat.TurnBudget::action);
    long endsAt = Math.addExact(context.seconds, 60);
    CombatEffects.add(
        context, wielder, new ConditionInstance(SACRED, held, ConditionInstance.End.clock(endsAt)));
    context.dm(
        "SacredWeaponActivated",
        Map.of(
            "combatantId",
            wielder.entry().id(),
            "itemId",
            held,
            "endsAt",
            endsAt,
            "brightFeet",
            20,
            "dimFeet",
            20));
  }

  static UUID heldWeapon(Context context, Creature wielder) {
    return context.equipment(wielder).entrySet().stream()
        .filter(item -> WEAPON_SLOTS.contains(item.getValue()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  static boolean sacredActive(Context context, Creature wielder) {
    if (!wielder.conditions().contains(SACRED)
        || wielder.conditions().contains(CombatConditions.UNCONSCIOUS)) return false;
    UUID held = heldWeapon(context, wielder);
    return held != null
        && wielder.vitals().effects().stream()
            .anyMatch(effect -> SACRED.equals(effect.key()) && held.equals(effect.source()));
  }

  static void turnUnholy(Context context, Creature paladin, List<UUID> ids) {
    if (ids.isEmpty()) throw CombatFeatures.invalidInput("targetIds");
    UUID actor = paladin.entry().id();
    for (UUID id : ids) {
      if (!context.entries.containsKey(id)
          || id.equals(actor)
          || !Encounters.present(context.entries.get(id)))
        throw CombatFeatures.invalidInput("targetIds");
      var target = context.creature(id);
      String type = context.creatureType(target);
      boolean hears = !target.conditions().contains("srd-5.1:deafened");
      boolean sees =
          CombatConditions.canSee(
              target.conditions(), paladin.conditions(), paladin.entry().tactics().hiddenFrom(id));
      if (!("fiend".equals(type) || "undead".equals(type)) || !hears && !sees)
        throw CombatFeatures.invalidInput("targetIds");
    }
    CombatActions.spend(context, com.github.sbadolankaur.rpg.engine.combat.TurnBudget::action);
    int level = paladin.classLevels().values().stream().mapToInt(Integer::intValue).sum();
    int dc = 8 + ProficiencyBonus.forLevel(level) + paladin.abilities().get(Ability.CHA);
    var roll =
        new CombatActions.Check(
            actor,
            ids,
            Ability.WIS,
            null,
            dc,
            true,
            Map.of(),
            null,
            null,
            false,
            false,
            false,
            null);
    var outcomes = CombatActions.resolveCheck(context, roll);
    var end = ConditionInstance.End.clock(Math.addExact(context.seconds, 60));
    for (var outcome : outcomes) {
      if (Boolean.FALSE.equals(outcome.get("success"))) {
        UUID id = (UUID) outcome.get("combatantId");
        CombatEffects.add(context, context.creature(id), new ConditionInstance(TURNED, actor, end));
        context.dm("CreatureTurned", Map.of("combatantId", id, "sourceId", actor));
      }
    }
  }
}
