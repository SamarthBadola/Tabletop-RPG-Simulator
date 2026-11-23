package com.github.sbadolankaur.rpg.encounter.application;

import static com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.invalid;

import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Stealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.CheckRules;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class CombatStealth {
  private CombatStealth() {}

  static void hide(Context context, Creature hider, List<UUID> obscured, List<UUID> opponents) {
    UUID id = hider.entry().id();
    if (hider.incapacitated()
        || obscured.contains(id)
        || new HashSet<>(obscured).size() != obscured.size()) throw invalid();

    var observers =
        context.entries.values().stream()
            .filter(Encounters::present)
            .filter(entry -> !entry.id().equals(id))
            .map(entry -> context.creature(entry.id()))
            .filter(observer -> !observer.incapacitated())
            .toList();
    var eligible = observers.stream().map(observer -> observer.entry().id()).toList();
    if (!eligible.containsAll(obscured) || !eligible.containsAll(opponents)) throw invalid();

    var roll =
        CombatRolls.d20(
            "Stealth",
            hider.skills().getOrDefault("stealth", hider.abilities().get(Ability.DEX)),
            CombatActions.checkSources(context, hider, "stealth").mode(),
            context.random());
    var hidden = new TreeSet<UUID>();
    var ties = new TreeSet<UUID>();
    var noticed = new TreeSet<UUID>();
    for (Creature observer : observers) {
      UUID observerId = observer.entry().id();
      boolean canSee =
          CombatConditions.canSee(
              observer.conditions(), hider.conditions(), obscured.contains(observerId));
      int passive =
          CheckRules.passive(
              observer.passivePerceptionBonus(),
              CombatActions.checkSources(context, observer, "perception").mode());
      if (canSee || passive > roll.total()) noticed.add(observerId);
      else if (passive == roll.total()) ties.add(observerId);
      else hidden.add(observerId);
    }
    var attempt = new Stealth(roll.total(), hidden, noticed, ties, new HashSet<>(opponents));
    context.update(hider.entry().tactics(hider.entry().tactics().stealth(attempt)));
    context.dice("Hide", id, roll);
    record(context, hider.entry(), attempt, "HideResolved");
  }

  static void resolveTies(Context context, Creature hider, List<UUID> noticed) {
    var entry = hider.entry();
    var attempt = entry.tactics().stealth();
    if (!Encounters.present(entry)
        || attempt == null
        || attempt.ties().isEmpty()
        || !attempt.ties().containsAll(noticed)
        || new HashSet<>(noticed).size() != noticed.size()) throw invalid();
    var hidden = new TreeSet<>(attempt.hiddenFrom());
    var seen = new TreeSet<>(attempt.noticedBy());
    for (UUID observer : attempt.ties()) {
      if (noticed.contains(observer)) {
        seen.add(observer);
        hidden.remove(observer);
      } else hidden.add(observer);
    }
    var decided = new Stealth(attempt.total(), hidden, seen, Set.of(), attempt.opponents());
    context.update(entry.tactics(entry.tactics().stealth(decided)));
    record(context, entry, decided, "StealthTiesResolved");
  }

  static boolean search(
      Context context, Creature subject, Creature observer, int perception, Boolean noticedOnTie) {
    var entry = subject.entry();
    var attempt = entry.tactics().stealth();
    if (attempt == null || !attempt.hiddenFrom().contains(observer.entry().id())) throw invalid();
    if (perception == attempt.total() && noticedOnTie == null) {
      var ties = new TreeSet<>(attempt.ties());
      ties.add(observer.entry().id());
      var pending =
          new Stealth(
              attempt.total(),
              attempt.hiddenFrom(),
              attempt.noticedBy(),
              ties,
              attempt.opponents());
      context.update(entry.tactics(entry.tactics().stealth(pending)));
      record(context, entry, pending, "SearchTie");
      return false;
    }
    boolean detected =
        perception > attempt.total()
            || perception == attempt.total() && Boolean.TRUE.equals(noticedOnTie);
    if (detected)
      context.update(
          entry.tactics(entry.tactics().stealth(attempt.detectedBy(observer.entry().id()))));
    context.dm(
        "SearchResolved",
        Map.of("hiderId", entry.id(), "observerId", observer.entry().id(), "detected", detected));
    return detected;
  }

  static Set<UUID> suggestions(Iterable<Entry> entries) {
    var hidden = new TreeSet<UUID>();
    var noticed = new TreeSet<UUID>();
    for (var entry : entries) {
      var attempt = entry.tactics().stealth();
      if (!Encounters.present(entry) || attempt == null) continue;
      for (UUID observer : attempt.opponents()) {
        if (attempt.hiddenFrom().contains(observer)) hidden.add(observer);
        if (attempt.noticedBy().contains(observer) || attempt.ties().contains(observer))
          noticed.add(observer);
      }
    }
    hidden.removeAll(noticed);
    return hidden;
  }

  private static void record(Context context, Entry entry, Stealth attempt, String type) {
    var payload = Map.of("combatantId", entry.id(), "stealth", attempt);
    context.dm(type, payload);
    context.result("stealth", attempt);
    if (context.table.phase() == EncounterState.Phase.SETUP)
      context.result("suggestedSurprisedIds", suggestions(context.entries.values()));
  }
}
