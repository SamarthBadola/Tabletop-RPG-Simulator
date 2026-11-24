package com.github.sbadolankaur.rpg.rest.application;

import com.github.sbadolankaur.rpg.character.application.CharacterSheets.View;
import com.github.sbadolankaur.rpg.character.application.Characters;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.content.ContentExpression;
import com.github.sbadolankaur.rpg.engine.content.ContentExpression.Type;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Participant;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Resource;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Trigger;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Component;

@Component
final class RestParty {
  private static final String SRD = "srd-5.1:";
  private final Characters characters;
  private final CharacterStore store;

  RestParty(Characters characters, CharacterStore store) {
    this.characters = characters;
    this.store = store;
  }

  List<View> sheets(UUID campaign, UUID actor) {
    return characters.list(campaign, actor);
  }

  List<Participant> load(List<View> sheets, ContentSet content) {
    return sheets.stream().map(sheet -> participant(sheet, content)).toList();
  }

  private Participant participant(View sheet, ContentSet content) {
    UUID id = sheet.id();
    var metadata = store.combatMetadata(id);
    var state =
        sheet.currentHp() > 0
            ? State.ALIVE
            : metadata.failures() == 3
                ? State.DEAD
                : metadata.recoveryAt() != null ? State.STABLE : State.DYING;
    var health =
        new CombatHealth(
            sheet.currentHp(),
            sheet.maximumHp(),
            sheet.temporaryHp(),
            state,
            state == State.DYING ? metadata.successes() : 0,
            state == State.DYING || state == State.DEAD ? metadata.failures() : 0);
    var dice = new TreeMap<Integer, Integer>();
    var spent = new TreeMap<Integer, Integer>();
    for (var die : sheet.hitDice()) {
      dice.put(die.size(), die.maximum());
      spent.put(die.size(), die.spent());
    }
    var resources =
        sheet.resources().stream()
            .map(row -> new Resource(row.key(), row.used(), recharge(content, sheet, row.key())))
            .toList();
    String recoveryKey =
        resources.stream()
            .map(Resource::key)
            .filter(
                key ->
                    key.equals(SRD + "arcane-recovery") || key.equals(SRD + "arcane-recovery-uses"))
            .findFirst()
            .orElse(null);
    return new Participant(
        id,
        health,
        sheet.baseMaximumHp(),
        sheet.abilities().get(Ability.CON).modifier(),
        metadata.exhaustion(),
        store.lastLongRest(id),
        metadata.recoveryAt(),
        dice,
        spent,
        store.spentSlots(id),
        resources,
        metadata.effects(),
        metadata.inspiration(),
        classLevel(sheet, "wizard"),
        hasFeature(sheet, "song-of-rest") ? classLevel(sheet, "bard") : 0,
        recoveryKey,
        Set.copyOf(sheet.baseConditionKeys()));
  }

  private static Map<Trigger, ToIntFunction<RandomSource>> recharge(
      ContentSet content, View sheet, String key) {
    var rules = new EnumMap<Trigger, ToIntFunction<RandomSource>>(Trigger.class);
    content
        .find("resource", key)
        .ifPresent(
            definition -> {
              if (definition.fields().get("recharge") instanceof List<?> entries) {
                for (Object entry : entries) {
                  if (entry instanceof Map<?, ?> rule) {
                    Trigger trigger = Trigger.valueOf((String) rule.get("on"));
                    rules.put(trigger, restore(sheet, rule.get("restore")));
                  }
                }
              }
            });
    if (key.equals(SRD + "bardic-inspiration-uses") && hasFeature(sheet, "font-of-inspiration"))
      rules.put(Trigger.SHORT_REST, random -> Integer.MAX_VALUE);
    return rules;
  }

  private static ToIntFunction<RandomSource> restore(View sheet, Object input) {
    if ("all".equals(input)) return random -> Integer.MAX_VALUE;
    var variables = new HashMap<String, Object>();
    variables.put("level", (long) sheet.level());
    variables.put("prof", (long) sheet.proficiency());
    variables.put("hp", (long) sheet.currentHp());
    variables.put("hp_max", (long) sheet.maximumHp());
    sheet
        .abilities()
        .forEach(
            (ability, score) -> {
              String key = ability.name().toLowerCase(java.util.Locale.ROOT);
              variables.put("mod:" + key, (long) score.modifier());
              variables.put("score:" + key, (long) score.value());
            });
    var types = new HashMap<String, Type>();
    variables.keySet().forEach(variable -> types.put(variable, Type.INT));
    var formula = ContentExpression.parse(input.toString(), types);
    return random ->
        (int) Math.clamp((Long) formula.evaluate(variables, random), 0, Integer.MAX_VALUE);
  }

  static boolean hasFeature(View sheet, String key) {
    return sheet.features().stream().anyMatch(feature -> feature.key().equals(SRD + key));
  }

  private static int classLevel(View sheet, String key) {
    return sheet.classes().stream()
        .filter(row -> row.key().equals(SRD + key))
        .mapToInt(row -> row.level())
        .findFirst()
        .orElse(0);
  }

  void save(List<Participant> before, List<Participant> after) {
    for (int i = 0; i < before.size(); i++) {
      var old = before.get(i);
      var state = after.get(i);
      if (!old.equals(state)) save(old, state);
    }
  }

  private void save(Participant old, Participant state) {
    UUID id = state.id();
    var health = state.health();
    if (!old.health().equals(health)
        || old.exhaustion() != state.exhaustion()
        || !Objects.equals(old.recoveryAt(), state.recoveryAt())
        || !old.effects().equals(state.effects())
        || !Objects.equals(old.inspiration(), state.inspiration())) {
      store.combatVitals(
          id,
          health.current(),
          health.temporary(),
          health.successes(),
          health.failures(),
          state.recoveryAt(),
          state.exhaustion(),
          state.effects(),
          state.inspiration());
      if (old.health().current() == 0 && health.current() > 0)
        store.condition(id, SRD + "unconscious", false);
    }
    if (!Objects.equals(old.lastLongRest(), state.lastLongRest()))
      store.lastLongRest(id, state.lastLongRest());
    state
        .spentHitDice()
        .forEach(
            (size, count) -> {
              if (count != old.spentHitDice().getOrDefault(size, 0))
                store.spentHitDice(id, size, count);
            });
    state
        .usedSlots()
        .forEach(
            (level, count) -> {
              if (count != old.usedSlots().getOrDefault(level, 0))
                store.spellSlot(id, level, count);
            });
    for (int i = 0; i < state.resources().size(); i++) {
      var resource = state.resources().get(i);
      if (resource.used() != old.resources().get(i).used())
        store.resource(id, resource.key(), resource.used());
    }
  }
}
