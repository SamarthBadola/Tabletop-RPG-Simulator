package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.AttackOption;
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class DivineSmite {
  static final String SETTING = "divineSmiteTiming";
  private static final String FEATURE = "srd-5.1:divine-smite";

  private DivineSmite() {}

  static List<Integer> available(Context context, Creature actor) {
    int level = actor.classLevel("srd-5.1:paladin");
    if (level < 2 || !actor.features().contains(FEATURE)) return List.of();
    var used = context.spellSlots(actor);
    return IntStream.rangeClosed(1, 5)
        .filter(slot -> used.getOrDefault(slot, 0) < count(level, slot))
        .boxed()
        .toList();
  }

  private static int count(int level, int slot) {
    return switch (slot) {
      case 1 -> level < 3 ? 2 : level < 5 ? 3 : 4;
      case 2 -> level < 5 ? 0 : level < 7 ? 2 : 3;
      case 3 -> level < 9 ? 0 : level < 11 ? 2 : 3;
      case 4 -> level < 13 ? 0 : level < 15 ? 1 : level < 17 ? 2 : 3;
      case 5 -> level < 17 ? 0 : level < 19 ? 1 : 2;
      default -> 0;
    };
  }

  static String validate(
      Context context, AttackOption weapon, Integer declared, List<Integer> slots) {
    if (slots.isEmpty() && declared == null) return null;
    String timing = context.setting(SETTING, String.class);
    if (declared != null
        && (!"BEFORE_ATTACK".equals(timing) || !melee(weapon) || !slots.contains(declared)))
      throw EncounterCommands.invalid();
    return timing;
  }

  static RollPlan onConfirmedHit(
      Context context,
      Creature actor,
      Creature target,
      AttackOption weapon,
      Integer declared,
      String timing,
      List<Integer> slots) {
    if (slots.isEmpty() || !melee(weapon)) return null;
    Integer selected = declared;
    if ("AFTER_HIT".equals(timing)) {
      var options =
          slots.stream()
              .map(
                  slot ->
                      new Option(
                          slot.toString(),
                          "Divine Smite with level " + slot + " slot",
                          false,
                          "spellSlot",
                          1))
              .toList();
      String answer =
          context
              .reactions()
              .choose(Point.AFTER_HIT, Step.ACTING_DAMAGE, actor.entry().id(), options, null);
      if (answer == null) return null;
      selected = Integer.valueOf(answer);
    }
    if (selected == null) return null;
    int used = context.spellSlots(actor).getOrDefault(selected, 0);
    context.spellSlot(actor, selected, used + 1);
    String type = context.creatureType(target);
    var plan = CombatRolls.divineSmite(selected, "undead".equals(type) || "fiend".equals(type));
    context.dm("DivineSmiteUsed", Map.of("actorId", actor.entry().id(), "slotLevel", selected));
    return plan;
  }

  private static boolean melee(AttackOption weapon) {
    return !weapon.ranged() && !"unarmed".equals(weapon.key());
  }
}
