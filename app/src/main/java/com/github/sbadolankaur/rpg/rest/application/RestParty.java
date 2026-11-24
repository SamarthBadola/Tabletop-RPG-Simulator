package com.github.sbadolankaur.rpg.rest.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignTimeEffects.ClockState;
import com.github.sbadolankaur.rpg.campaign.application.CampaignTimeEffects.Delta;
import com.github.sbadolankaur.rpg.character.application.CharacterSheets.View;
import com.github.sbadolankaur.rpg.character.application.Characters;
import com.github.sbadolankaur.rpg.character.application.ResourceRecharge;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Participant;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Resource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
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

  List<Participant> outsideEncounter(
      UUID campaign, UUID actor, Set<UUID> roster, ContentSet content, SettingsView settings) {
    return load(characters.combatSheets(campaign, actor, content, settings, roster), content);
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
            .map(
                row ->
                    new Resource(
                        row.key(), row.used(), ResourceRecharge.rules(content, sheet, row.key())))
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
        Set.copyOf(sheet.baseConditionKeys()),
        metadata.recoveryOrder());
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

  static ClockState clockState(Participant state) {
    var resources = new TreeMap<String, Integer>();
    state.resources().forEach(resource -> resources.put(resource.key(), resource.used()));
    return new ClockState(
        state.health(),
        state.recoveryAt(),
        state.exhaustion(),
        state.effects(),
        state.inspiration(),
        state.health().current() == 0 && state.conditions().contains(SRD + "unconscious"),
        resources,
        state.recoveryOrder());
  }

  void apply(Delta change, boolean undo) {
    var old = undo ? change.after() : change.before();
    var state = undo ? change.before() : change.after();
    var hp = state.health();
    UUID id = change.character();
    if (!old.health().equals(hp)
        || !Objects.equals(old.recoveryAt(), state.recoveryAt())
        || !old.effects().equals(state.effects())
        || !Objects.equals(old.inspiration(), state.inspiration())) {
      store.combatVitals(
          id,
          hp.current(),
          hp.temporary(),
          hp.successes(),
          hp.failures(),
          state.recoveryAt(),
          state.exhaustion(),
          state.effects(),
          state.inspiration(),
          state.recoveryOrder());
    }
    if (old.unconscious() != state.unconscious())
      store.condition(id, SRD + "unconscious", state.unconscious());
    state
        .resources()
        .forEach(
            (key, used) -> {
              if (!Objects.equals(used, old.resources().get(key))) store.resource(id, key, used);
            });
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
          state.inspiration(),
          state.recoveryOrder());
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
