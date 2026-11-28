package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.monster.HookedDamage;
import com.github.sbadolankaur.rpg.engine.monster.Regeneration;
import com.github.sbadolankaur.rpg.engine.monster.SpecialTraits;
import com.github.sbadolankaur.rpg.engine.monster.ZeroHitPointHook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** The glue between a spawned monster and the zero-hit-point hook (Step 12). */
final class MonsterHooks {
  private MonsterHooks() {}

  /** The names of the traits on a monster definition. */
  static List<String> traitNames(Map<String, ?> definition) {
    var names = new ArrayList<String>();
    if (definition.get("traits") instanceof List<?> traits) {
      for (var trait : traits) {
        if (trait instanceof Map<?, ?> fields && fields.get("name") instanceof String name) {
          names.add(name);
        }
      }
    }
    return names;
  }

  /** The zero-hit-point traits of a creature. Players have none. */
  static ZeroHitPointHook.Defender defender(Context context, Creature creature) {
    String monster = creature.entry().monster();
    if (monster == null) return ZeroHitPointHook.Defender.NONE;
    return context
        .content
        .find("monster", monster)
        .map(definition -> SpecialTraits.defender(traitNames(definition.fields())))
        .orElse(ZeroHitPointHook.Defender.NONE);
  }

  /** Stores the Regeneration marks after the creature's hit points were written. */
  static void writeMarks(Context context, UUID combatant, HookedDamage.Resolution resolution) {
    var marks = resolution.marks();
    if (marks == null) return;
    var entry = context.entries.get(combatant);
    if (entry.dyingByTrait() != marks.dyingByTrait()
        || entry.regenSuppressed() != marks.suppressed()) {
      context.update(entry.marks(marks.dyingByTrait(), marks.suppressed()));
    }
  }

  /**
   * The start-of-turn step of Regeneration (RS-MON-036). A creature without the trait is left
   * alone. The step runs before the death-save check, so a monster held at 0 by the trait never
   * makes a death save.
   */
  static void startOfTurn(Context context, Creature creature) {
    if (!defender(context, creature).regeneration()) return;
    var entry = creature.entry();
    var before = creature.vitals().health();
    var turn =
        Regeneration.turnStart(
            before, new Regeneration.State(entry.dyingByTrait(), entry.regenSuppressed()));
    var id = entry.id();
    if (!turn.health().equals(before)) {
      context.health(creature, turn.health());
      if (turn.health().state()
          == com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State.DEAD) {
        context.dm("RegenerationFailed", Map.of("combatantId", id));
      } else {
        context.dm("Regenerated", Map.of("combatantId", id, "regained", turn.regained()));
      }
    }
    var current = context.entries.get(id);
    if (current.dyingByTrait() != turn.state().dyingByTrait()
        || current.regenSuppressed() != turn.state().suppressed()) {
      context.update(current.marks(turn.state().dyingByTrait(), turn.state().suppressed()));
    }
  }

  /**
   * How many attacks the Attack action gives a monster: the attacks its Multiattack lists, or one.
   * The listed attacks are counted, and the player or DM still picks which one each swing uses.
   */
  static int attacksPerAction(JsonNode monster) {
    int most = 1;
    for (var action : monster.path("actions")) {
      most = Math.max(most, action.path("attacks").size());
    }
    return most;
  }
}
