package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.engine.combat.BardicInspiration;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Event;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Campaign-wide time effects applied transactionally by the clock's callers. */
public interface CampaignTimeEffects {
  CampaignCommands.Change advance(UUID campaign, UUID actor, long start, long seconds);

  Sweep outsideEncounter(
      UUID campaign,
      UUID actor,
      Set<UUID> roster,
      long start,
      long seconds,
      SettingsView settings,
      ContentSet content,
      RandomSource random);

  void apply(List<Delta> deltas, boolean undo);

  record Sweep(List<Delta> deltas, List<Event> events) {}

  record Delta(UUID character, ClockState before, ClockState after) {}

  record ClockState(
      CombatHealth health,
      Long recoveryAt,
      int exhaustion,
      List<ConditionInstance> effects,
      BardicInspiration inspiration,
      boolean unconscious,
      Map<String, Integer> resources) {}
}
