package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.monster.HookedDamage;
import com.github.sbadolankaur.rpg.engine.monster.SpecialTraits;
import com.github.sbadolankaur.rpg.engine.monster.ZeroHitPointHook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
}
