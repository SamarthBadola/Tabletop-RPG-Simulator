package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.encounter.application.CombatActions.Attack;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls.Cover;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.monster.LegendaryActions;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Offer;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The legendary action window at the end of another creature's turn (RS-MON-030, RS-MON-031). The
 * window opens for each legendary creature in initiative order, starting after the creature whose
 * turn ended. A creature uses at most one option per window, and the option costs points.
 */
final class MonsterLegendary {
  private static final String KEY = "legendary:";

  private MonsterLegendary() {}

  /** One option on a stat block, with its cost and the attacks it makes (names of actions). */
  record LegendaryOption(String name, int cost, List<String> attacks) {}

  static void window(Context context, UUID ended) {
    var replay = context.reactions();
    if (replay == null) return;
    replay.window(
        Point.ON_TURN_END,
        () -> offers(context, ended),
        null,
        (decider, key) -> use(context, decider, key));
  }

  /** The options of a monster's legendary block. */
  static List<LegendaryOption> options(Map<String, ?> definition) {
    var result = new ArrayList<LegendaryOption>();
    if (definition.get("legendaryActions") instanceof Map<?, ?> block
        && block.get("entries") instanceof List<?> entries) {
      for (var entry : entries) {
        if (!(entry instanceof Map<?, ?> fields) || !(fields.get("name") instanceof String name))
          continue;
        int cost = fields.get("cost") instanceof Number number ? number.intValue() : 1;
        var attacks = new ArrayList<String>();
        if (fields.get("attacks") instanceof List<?> listed) {
          for (var attack : listed) if (attack instanceof String text) attacks.add(text);
        }
        result.add(new LegendaryOption(name, cost, attacks));
      }
    }
    return List.copyOf(result);
  }

  private static List<LegendaryOption> definition(Context context, Entry entry) {
    if (entry.monster() == null) return List.of();
    return context
        .content
        .find("monster", entry.monster())
        .map(definition -> options(definition.fields()))
        .orElse(List.of());
  }

  static List<Offer> offers(Context context, UUID ended) {
    var order =
        context.entries.values().stream()
            .filter(Encounters::present)
            .sorted(Encounters.ORDER)
            .toList();
    var creatures = new ArrayList<LegendaryActions.Creature>();
    for (var entry : order) {
      int uses = MonsterHooks.legendaryUses(context, entry.monster());
      if (uses < 1) continue;
      var creature = context.creature(entry.id());
      creatures.add(
          new LegendaryActions.Creature(
              entry.id().toString(),
              new LegendaryActions.Pool(uses, Math.min(uses, entry.legendaryPoints())),
              creature.vitals().health().state() != CombatHealth.State.DEAD,
              creature.incapacitated(),
              entry.tactics().surprised()));
    }
    var offers = new ArrayList<Offer>();
    int priority = 0;
    for (var creature : LegendaryActions.windowOrder(creatures, ended.toString())) {
      var entry = context.entries.get(UUID.fromString(creature.id()));
      var listed = definition(context, entry);
      var choices = new ArrayList<LegendaryActions.Option>();
      for (int index = 0; index < listed.size(); index++) {
        var option = listed.get(index);
        choices.add(
            new LegendaryActions.Option(
                KEY + index,
                "Legendary action: " + option.name() + " (cost " + option.cost() + ")",
                option.cost()));
      }
      for (var option : LegendaryActions.affordable(choices, creature.pool())) {
        offers.add(
            new Offer(
                Step.DEFENDING_RESULT,
                entry.id(),
                entry.initiative() == null ? 0 : entry.initiative(),
                priority,
                new Option(option.key(), option.label(), false, "legendary", option.cost())));
      }
      priority++;
    }
    return List.copyOf(offers);
  }

  private static void use(Context context, UUID decider, String key) {
    var entry = context.entries.get(decider);
    var listed = definition(context, entry);
    var chosen = listed.get(Integer.parseInt(key.substring(KEY.length())));
    context.update(entry.legendary(entry.legendaryPoints() - chosen.cost()));
    context.dm(
        "LegendaryActionUsed",
        Map.of("combatantId", decider, "option", chosen.name(), "cost", chosen.cost()));
    if (chosen.attacks().isEmpty()) return;
    var attacker = context.creature(decider);
    var weapon =
        attacker.attacks().stream()
            .filter(option -> option.label().equals(chosen.attacks().getFirst()))
            .findFirst();
    if (weapon.isEmpty()) return;
    var targets = new ArrayList<Option>();
    for (var other : context.entries.values()) {
      if (!Encounters.present(other) || other.id().equals(decider)) continue;
      var creature = context.creature(other.id());
      if (creature.vitals().health().state() == CombatHealth.State.DEAD) continue;
      targets.add(new Option("target:" + other.id(), creature.name(), false, null, 0));
    }
    if (targets.isEmpty()) return;
    String picked =
        context.reactions().followUp(Point.ON_TURN_END, Step.DEFENDING_RESULT, decider, targets);
    var attack =
        new Attack(
            decider,
            UUID.fromString(picked.substring("target:".length())),
            weapon.get().key(),
            weapon.get().longRange(),
            Cover.NONE,
            RollMode.NORMAL,
            false,
            false,
            false,
            false,
            false,
            false,
            List.of(),
            null,
            null);
    context
        .reactions()
        .nested(
            "legendary-attack",
            () -> {
              CombatActions.resolveAttack(
                  context, attack, context.creature(decider), weapon.get(), true);
              return null;
            });
  }
}
