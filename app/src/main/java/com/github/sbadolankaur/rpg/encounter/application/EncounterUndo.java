package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Table;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Private preimages for a single action. The original rolls and events remain in the audit log. */
record EncounterUndo(
    Table table,
    long secondsBefore,
    long secondsAfter,
    long clockVersionAfter,
    List<Change> changes,
    boolean publicChange,
    Map<UUID, String> privateChanges) {

  boolean visible() {
    return publicChange || !privateChanges.isEmpty();
  }

  void restore(Context context) {
    if (secondsBefore != secondsAfter
        && (secondsAfter != context.seconds || clockVersionAfter != context.clockVersion)) {
      throw new CampaignException(Code.ENCOUNTER_VERSION_CONFLICT);
    }
    for (Change change : changes) {
      if (!Objects.equals(change.after(), context.snapshot(change.id()))) {
        throw new CampaignException(Code.ENCOUNTER_VERSION_CONFLICT);
      }
    }
    context.disableUndo();
    for (Change change : changes) {
      if (change.before() == null) context.remove(change.id());
    }
    for (Change change : changes) {
      var before = change.before();
      if (before == null) continue;
      if (change.after() == null) context.add(before.entry());
      else context.update(before.entry());
      if (before.entry().character() == null || change.after() == null) continue;
      var after = change.after();
      if (!before.vitals().equals(after.vitals()))
        context.vitals(context.creature(change.id()), before.vitals());
      if (!before.equipment().equals(after.equipment()))
        context.restoreEquipment(context.creature(change.id()), before.equipment());
      before
          .resources()
          .forEach(
              (key, used) -> {
                if (!Objects.equals(used, after.resources().get(key)))
                  context.resource(context.creature(change.id()), key, used);
              });
    }
    context.table = table;
    if (secondsBefore != secondsAfter) context.seconds = secondsBefore;
  }

  static EncounterUndo of(
      Context context,
      Table before,
      long elapsed,
      Map<UUID, Snapshot> originals,
      boolean publicOutput) {
    var changes =
        originals.entrySet().stream()
            .map(
                entry ->
                    new Change(entry.getKey(), entry.getValue(), context.snapshot(entry.getKey())))
            .filter(change -> !Objects.equals(change.before(), change.after()))
            .toList();
    var privateChanges = new LinkedHashMap<UUID, String>();
    boolean visible =
        publicOutput
            || before.phase() != context.table.phase()
            || before.round() != context.table.round()
            || !Objects.equals(before.acting(), context.table.acting());
    for (var change : changes) {
      var first = change.before();
      var second = change.after();
      if (first == null || second == null) {
        visible |=
            first == null ? Encounters.present(second.entry()) : Encounters.present(first.entry());
        continue;
      }
      visible |=
          Encounters.present(first.entry()) != Encounters.present(second.entry())
              || !Objects.equals(first.entry().initiative(), second.entry().initiative())
              || first.entry().tactics().surprised() != second.entry().tactics().surprised()
              || !presentConditions(first).equals(presentConditions(second))
              || (first.owner() != null
                  && !first.vitals().health().equals(second.vitals().health()));
      if (first.owner() != null
          && (!first.resources().equals(second.resources())
              || !first.equipment().equals(second.equipment())
              || first.vitals().exhaustion() != second.vitals().exhaustion())) {
        privateChanges.put(first.owner(), first.name());
      }
    }
    return new EncounterUndo(
        before,
        elapsed,
        context.seconds,
        context.clockVersion + (elapsed == context.seconds ? 0 : 1),
        changes,
        visible,
        Map.copyOf(privateChanges));
  }

  private static java.util.Set<String> presentConditions(Snapshot snapshot) {
    var keys = new java.util.HashSet<>(snapshot.vitals().conditions());
    snapshot.vitals().effects().forEach(effect -> keys.add(effect.key()));
    snapshot.entry().tactics().conditions().forEach(effect -> keys.add(effect.key()));
    return keys;
  }

  record Change(UUID id, Snapshot before, Snapshot after) {}

  record Snapshot(
      Entry entry,
      String name,
      UUID owner,
      Vitals vitals,
      Map<String, Integer> resources,
      Map<UUID, String> equipment) {}
}
