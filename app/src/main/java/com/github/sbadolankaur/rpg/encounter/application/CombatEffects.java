package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.combat.ClockExpiry;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance.Boundary;
import com.github.sbadolankaur.rpg.engine.kernel.AdvantageSources;
import com.github.sbadolankaur.rpg.engine.kernel.DurationKind;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Event;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class CombatEffects {
  private CombatEffects() {}

  static void add(Context context, Creature creature, ConditionInstance effect) {
    if (creature.immunities().contains(effect.key())) return;
    var state = creature.vitals();
    if (effect.source() == null && effect.end().kind() == DurationKind.UNTIL_DISPELLED) {
      var conditions = new HashSet<>(state.conditions());
      if (conditions.add(effect.key())) context.vitals(creature, state.conditions(conditions));
      return;
    }
    var active = new ArrayList<>(state.effects());
    active.removeIf(effect::sameSource);
    active.add(effect.created(context.nextEffectCreationOrder()));
    context.vitals(creature, state.effects(active));
  }

  static void remove(Context context, Creature creature, String key, UUID source) {
    var entry = creature.entry();
    var old = entry.tactics().conditions();
    var retained =
        old.stream()
            .filter(effect -> !matches(effect.key(), effect.source(), key, source))
            .toList();
    if (!old.equals(retained)) context.update(entry.tactics(entry.tactics().conditions(retained)));

    var state = creature.vitals();
    var effects =
        state.effects().stream()
            .filter(effect -> !matches(effect.key(), effect.source(), key, source))
            .toList();
    var conditions = new HashSet<>(state.conditions());
    if (source == null) conditions.remove(key);
    var next = state.effects(effects).conditions(conditions);
    if (!state.equals(next)) context.vitals(context.creature(entry.id()), next);
  }

  static void removeAfflictionEffects(Context context, Creature creature, UUID source) {
    var entry = creature.entry();
    var old = entry.tactics().conditions();
    var remaining = old.stream().filter(effect -> !source.equals(effect.source())).toList();
    if (!old.equals(remaining))
      context.update(entry.tactics(entry.tactics().conditions(remaining)));
    var vitals = creature.vitals();
    var effects =
        vitals.effects().stream().filter(effect -> !source.equals(effect.source())).toList();
    if (!effects.equals(vitals.effects()))
      context.vitals(context.creature(entry.id()), vitals.effects(effects));
  }

  private static boolean matches(String key, UUID source, String selected, UUID selectedSource) {
    return key.equals(selected)
        && (selectedSource == null || Objects.equals(source, selectedSource));
  }

  private record Timer(UUID combatant, ClockExpiry expiry) {}

  static List<Event> expireClock(Context context) {
    var timers = new ArrayList<Timer>();
    for (var entry : context.entries.values()) {
      if (!Encounters.present(entry)
          || entry.character() == null
              && entry.npcVitals().effects().isEmpty()
              && entry.npcVitals().inspiration() == null
              && entry.npcVitals().recoveryAt() == null) continue;
      var vitals = context.creature(entry.id()).vitals();
      for (var expiry :
          ClockExpiry.due(
              context.seconds,
              vitals.effects(),
              vitals.inspiration(),
              vitals.recoveryAt(),
              vitals.recoveryOrder())) {
        timers.add(new Timer(entry.id(), expiry));
      }
    }
    timers.sort(java.util.Comparator.comparing(Timer::expiry, ClockExpiry.ORDER));
    return timers.stream().map(timer -> expire(context, timer)).toList();
  }

  private static Event expire(Context context, Timer timer) {
    var creature = context.creature(timer.combatant());
    var vitals = creature.vitals();
    var expiry = timer.expiry();
    UUID id = timer.combatant();
    String type;
    Map<String, Object> payload;
    switch (expiry.kind()) {
      case CONDITION -> {
        var remaining = new ArrayList<>(vitals.effects());
        remaining.remove(expiry.effect());
        context.vitals(creature, vitals.effects(remaining));
        type = "ConditionsExpired";
        payload = Map.of("combatantId", id, "conditions", List.of(expiry.effect().key()));
      }
      case INSPIRATION -> {
        context.vitals(creature, vitals.inspiration(null));
        type = "BardicInspirationExpired";
        payload = Map.of("combatantId", id);
      }
      case STABLE_RECOVERY -> {
        context.health(creature, vitals.health().heal(1));
        type = "StableRecovered";
        payload = Map.of("combatantId", id);
      }
      default -> throw new IllegalStateException("Unknown clock expiry");
    }
    return new Event(type, expiry.deadline(), null, payload, expiry.creationOrder());
  }

  static void expire(Context context, UUID acting, Boundary boundary) {
    context.recordClockEvents(expireClock(context));
    for (var id : List.copyOf(context.entries.keySet())) {
      var entry = context.entries.get(id);
      if (!Encounters.present(entry)) continue;
      if (entry.character() == null
          && entry.npcVitals().effects().isEmpty()
          && !entry.tactics().dodging()) continue;
      var creature = context.creature(id);
      if (entry.tactics().dodging() && (creature.speed() == 0 || creature.incapacitated())) {
        var flags = creature.entry().tactics();
        context.update(
            creature
                .entry()
                .tactics(
                    flags.flags(
                        false,
                        flags.hidden(),
                        flags.helping(),
                        flags.helpedTarget(),
                        flags.readied())));
        creature = context.creature(id);
      }
      var state = creature.vitals();
      var current = creature;
      var effects =
          state.effects().stream()
              .filter(
                  effect ->
                      !effect
                              .end()
                              .expired(context.seconds, context.table.round(), acting, boundary)
                          && !endedGrapple(context, effect)
                          && !(CombatFeatures.SACRED_WEAPON.equals(effect.key())
                              && !OathFeatures.sacredActive(context, current)))
              .toList();
      if (!effects.equals(state.effects())) {
        context.vitals(creature, state.effects(effects));
        context.dm(
            "ConditionsExpired",
            Map.of(
                "combatantId",
                id,
                "conditions",
                state.effects().stream()
                    .filter(effect -> !effects.contains(effect))
                    .map(ConditionInstance::key)
                    .distinct()
                    .toList()));
      }
    }
  }

  static AdvantageSources rollSources(
      Context context,
      Creature holder,
      String roll,
      String direction,
      Integer distance,
      Boolean seesOther) {
    var advantages = new ArrayList<String>();
    var disadvantages = new ArrayList<String>();
    for (String key : holder.conditions().stream().sorted().toList()) {
      var definition = context.content.find("condition", key).orElse(null);
      if (definition == null || !(definition.fields().get("parts") instanceof List<?> parts))
        continue;
      for (var value : parts) {
        if (!(value instanceof Map<?, ?> part)
            || !"advantage_source".equals(part.get("type"))
            || !roll.equals(part.get("roll"))
            || !direction.equals(part.get("direction"))) continue;
        if (part.get("maxDistance") instanceof Number max
            && (distance == null || distance > max.longValue())) continue;
        if (part.get("minDistanceExclusive") instanceof Number min
            && (distance == null || distance <= min.longValue())) continue;
        if (part.get("lineOfSight") instanceof Boolean sight && !sight.equals(seesOther)) continue;
        ("advantage".equals(part.get("mode")) ? advantages : disadvantages).add(definition.name());
      }
    }
    return new AdvantageSources(advantages, disadvantages);
  }

  private static boolean endedGrapple(Context context, ConditionInstance effect) {
    if (!CombatConditions.GRAPPLED.equals(effect.key()) || effect.source() == null) return false;
    var source = context.entries.get(effect.source());
    return source == null
        || !Encounters.present(source)
        || context.creature(source.id()).incapacitated();
  }

  static void leaveEncounter(Context context, Creature creature) {
    var state = creature.vitals();
    var effects =
        state.effects().stream()
            .map(
                effect ->
                    effect.until(
                        effect.end().afterEncounter(context.seconds, context.table.round())))
            .filter(
                effect ->
                    !effect
                        .end()
                        .expired(context.seconds, context.table.round(), null, Boundary.CLOCK))
            .toList();
    if (!effects.equals(state.effects())) context.vitals(creature, state.effects(effects));
  }
}
