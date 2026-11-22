package com.github.sbadolankaur.rpg.encounter.application;

import static com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.invalid;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.FieldError;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Phase;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.encounter.persistence.EncounterStore;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CombatOverrides {
  private final Campaigns campaigns;
  private final EncounterCommands commands;
  private final EncounterStore store;
  private final CombatRoster roster;
  private final JsonMapper json;

  CombatOverrides(
      Campaigns campaigns,
      EncounterCommands commands,
      EncounterStore store,
      CombatRoster roster,
      JsonMapper json) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.store = store;
    this.roster = roster;
    this.json = json;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Preview preview(
      UUID campaign, UUID encounter, UUID actor, Kind kind, UUID combatant, UUID entryId) {
    campaigns.requireDm(campaign, actor);
    var table = commands.require(campaign, encounter, false);
    if (table.phase() != Phase.ACTIVE) throw invalid();
    if (kind == null) throw invalid();
    Kind actual = kind;
    boolean undoAttack = false;
    if (kind == Kind.UNDO_LAST) {
      var event = latest(encounter, entryId);
      undoAttack = event.type().equals("AttackResolved");
      if (undoAttack) {
        combatant = attackUndo(event).target().id();
        actual = Kind.SET_HIT_POINTS;
      } else {
        var previous = before(event);
        combatant = previous.entry().id();
        actual = previous.kind();
      }
    }
    final UUID id = combatant;
    var entry =
        store.entries(encounter).stream()
            .filter(Encounters::present)
            .filter(row -> row.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new CampaignException(Code.NOT_FOUND));
    var creature = roster.load(campaign, actor, entry, roster.content(campaign, actor));
    return new Preview(
        creature.name(),
        current(actual, creature),
        !visible(actual, creature),
        undoAttack ? "The DM undid the last attack." : sentence(actual, creature),
        combatant,
        entryId,
        kind,
        creature.resources().keySet());
  }

  public Reply apply(
      UUID campaign, UUID encounter, UUID actor, Envelope envelope, Correction form) {
    return commands.run(
        campaign,
        encounter,
        actor,
        envelope,
        "overrides",
        form,
        true,
        context -> {
          if (context.table.phase() != Phase.ACTIVE
              || form.reason() == null
              || form.reason().isBlank()
              || form.reason().codePointCount(0, form.reason().length()) > 200
              || form.kind() == null) throw invalid();
          if (form.kind() == Kind.UNDO_LAST) {
            var event = latest(encounter, form.entryId());
            if (event.type().equals("AttackResolved")) {
              undoAttack(context, form, attackUndo(event));
              return;
            }
            var previous = before(event);
            var target = context.creature(previous.entry().id());
            audience(form.quiet(), previous.kind(), target);
            context.update(previous.entry());
            context.vitals(context.creature(target.entry().id()), previous.vitals());
            context.restoreEquipment(target, previous.equipment());
            if (previous.resourceUsed() != null)
              context.resource(target, previous.resource(), previous.resourceUsed());
            context.dm(
                "CorrectionUndone",
                Map.of("entryId", form.entryId(), "reason", form.reason(), "quiet", form.quiet()));
            publish(context, previous.kind(), target, form.quiet());
            return;
          }
          var target = context.creature(form.target());
          if (!Encounters.present(target.entry())) throw new CampaignException(Code.NOT_FOUND);
          audience(form.quiet(), form.kind(), target);
          if (form.entryId() != null && !store.eventExists(encounter, form.entryId()))
            throw invalid();
          var snapshot =
              new Before(
                  form.kind(),
                  target.entry(),
                  target.vitals(),
                  context.equipment(target),
                  form.resource(),
                  form.resource() == null ? null : target.resources().get(form.resource()));
          change(context, target, form);

          Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("before", snapshot);
          payload.put("reason", form.reason());
          payload.put("entryId", form.entryId());
          payload.put("quiet", form.quiet());
          payload.put("after", current(form.kind(), context.creature(target.entry().id())));
          context.dm("CorrectionApplied", payload);
          publish(context, form.kind(), target, form.quiet());
        });
  }

  private void change(Context context, Creature target, Correction form) {
    switch (form.kind()) {
      case SET_HIT_POINTS, ADJUST_HIT_POINTS -> {
        if (form.amount() == null) throw invalid();
        var health = target.vitals().health();
        long next =
            form.kind() == Kind.SET_HIT_POINTS
                ? form.amount()
                : (long) health.current() + form.amount();
        if (next < 0 || next > health.maximum()) throw invalid();
        if (next != health.current())
          context.health(target, CombatHealth.of((int) next, health.maximum(), health.temporary()));
      }
      case ADD_CONDITION ->
          CombatActions.addCondition(
              context, target, Encounters.conditionKey(form.condition()), form.source());
      case REMOVE_CONDITION ->
          CombatActions.removeCondition(context, target, Encounters.conditionKey(form.condition()));
      case SET_RESOURCE -> {
        if (form.amount() == null || form.resource() == null) throw invalid();
        context.resource(target, form.resource(), form.amount());
      }
      case SET_INITIATIVE -> {
        if (form.amount() == null) throw invalid();
        context.update(target.entry().initiative(form.amount(), null));
      }
      case UNDO_LAST -> throw invalid();
    }
  }

  private EncounterStore.Event latest(UUID encounter, UUID entryId) {
    if (entryId == null) throw invalid();
    return store
        .latestReversible(encounter)
        .filter(row -> row.id().equals(entryId))
        .orElseThrow(() -> new CampaignException(Code.ENCOUNTER_VERSION_CONFLICT));
  }

  private Before before(EncounterStore.Event event) {
    return json.treeToValue(event.payload().path("before"), Before.class);
  }

  private CombatActions.AttackUndo attackUndo(EncounterStore.Event event) {
    return json.treeToValue(event.payload().path("undo"), CombatActions.AttackUndo.class);
  }

  private static void undoAttack(Context context, Correction form, CombatActions.AttackUndo undo) {
    var target = context.creature(undo.target().id());
    boolean playerSawDamage = undo.hit() && target.owner() != null;
    if (playerSawDamage) audience(form.quiet(), Kind.SET_HIT_POINTS, target);
    context.update(undo.attacker());
    context.update(undo.target());
    if (undo.helper() != null) context.update(undo.helper());
    if (undo.hit()) {
      context.vitals(context.creature(undo.target().id()), undo.targetVitals());
      context.restoreEquipment(target, undo.equipment());
    }
    var table = context.table;
    context.table = table.position(table.phase(), table.round(), table.acting(), undo.budget());
    context.dm(
        "ActionUndone",
        Map.of("entryId", form.entryId(), "reason", form.reason(), "quiet", form.quiet()));
    if (playerSawDamage) {
      var restored = undo.targetVitals().health();
      context.tell(
          "PlayerCorrection",
          null,
          "The DM undid the last attack. "
              + target.name()
              + " now has "
              + restored.current()
              + " of "
              + restored.maximum()
              + " hit points.");
    }
  }

  private static void audience(boolean quiet, Kind kind, Creature creature) {
    if (quiet && visible(kind, creature))
      throw new CampaignException(new FieldError("visibility", "INCORRECT"));
  }

  private static boolean visible(Kind kind, Creature creature) {
    return switch (kind) {
      case SET_INITIATIVE, ADD_CONDITION, REMOVE_CONDITION -> true;
      case SET_HIT_POINTS, ADJUST_HIT_POINTS, SET_RESOURCE -> creature.owner() != null;
      case UNDO_LAST -> throw invalid();
    };
  }

  private static void publish(Context context, Kind kind, Creature previous, boolean quiet) {
    if (quiet || !visible(kind, previous)) return;
    var after = context.creature(previous.entry().id());
    String line = sentence(kind, after);
    if (kind == Kind.SET_HIT_POINTS || kind == Kind.ADJUST_HIT_POINTS) {
      var hp = after.vitals().health();
      line +=
          " " + after.name() + " now has " + hp.current() + " of " + hp.maximum() + " hit points.";
    }
    context.tell("PlayerCorrection", kind == Kind.SET_RESOURCE ? after.owner() : null, line);
  }

  private static String sentence(Kind kind, Creature creature) {
    String label =
        switch (kind) {
          case SET_HIT_POINTS, ADJUST_HIT_POINTS -> "hit points";
          case ADD_CONDITION, REMOVE_CONDITION -> "conditions";
          case SET_RESOURCE -> "resources";
          case SET_INITIATIVE -> "initiative";
          case UNDO_LAST -> throw invalid();
        };
    return "The DM corrected " + creature.name() + "'s " + label + ".";
  }

  private static String current(Kind kind, Creature creature) {
    return switch (kind) {
      case SET_HIT_POINTS, ADJUST_HIT_POINTS ->
          Integer.toString(creature.vitals().health().current());
      case ADD_CONDITION, REMOVE_CONDITION ->
          String.join(", ", creature.conditions().stream().sorted().toList());
      case SET_RESOURCE -> creature.resources().toString();
      case SET_INITIATIVE -> Objects.toString(creature.entry().initiative(), "");
      case UNDO_LAST -> throw invalid();
    };
  }

  public enum Kind {
    SET_HIT_POINTS,
    ADJUST_HIT_POINTS,
    ADD_CONDITION,
    REMOVE_CONDITION,
    SET_RESOURCE,
    SET_INITIATIVE,
    UNDO_LAST
  }

  public record Correction(
      Kind kind,
      UUID target,
      UUID entryId,
      Integer amount,
      String condition,
      UUID source,
      String resource,
      String reason,
      boolean quiet) {}

  public record Preview(
      String name,
      String currentValue,
      boolean quietAllowed,
      String playersText,
      UUID combatant,
      UUID entryId,
      Kind kind,
      java.util.Set<String> resourceKeys) {}

  private record Before(
      Kind kind,
      Entry entry,
      Vitals vitals,
      Map<UUID, String> equipment,
      String resource,
      Integer resourceUsed) {}
}
