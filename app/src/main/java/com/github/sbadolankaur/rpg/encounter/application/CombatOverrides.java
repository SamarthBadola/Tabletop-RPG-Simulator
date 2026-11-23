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
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance.End;
import com.github.sbadolankaur.rpg.engine.kernel.DurationKind;
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
    if (kind == null || (table.phase() != Phase.ACTIVE && kind != Kind.UNDO_LAST)) throw invalid();
    Kind actual = kind;
    boolean undoAttack = false;
    if (kind == Kind.UNDO_LAST) {
      var event = latest(encounter, entryId);
      if (event.payload().has("actionPreimage")) {
        var undo = preimage(event);
        return new Preview(
            table.name(),
            event.type(),
            !undo.visible(),
            undoSentence(event),
            null,
            entryId,
            kind,
            java.util.Set.of());
      }
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
          if ((context.table.phase() != Phase.ACTIVE && form.kind() != Kind.UNDO_LAST)
              || form.reason() == null
              || form.reason().isBlank()
              || form.reason().codePointCount(0, form.reason().length()) > 200
              || form.kind() == null) throw invalid();
          if (form.kind() == Kind.UNDO_LAST) {
            var event = latest(encounter, form.entryId());
            if (event.payload().has("actionPreimage")) {
              var undo = preimage(event);
              if (form.quiet() && undo.visible())
                throw new CampaignException(new FieldError("visibility", "INCORRECT"));
              if (undo.table().phase() == Phase.ACTIVE && store.hasRunning(campaign, encounter))
                throw new CampaignException(Code.ENCOUNTER_ACTIVE);
              undo.restore(context);
              context.dm(
                  "ActionUndone",
                  Map.of(
                      "entryId",
                      form.entryId(),
                      "reason",
                      form.reason(),
                      "quiet",
                      !undo.visible()));
              announceUndo(context, event, undo);
              return;
            }
            context.disableUndo();
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
          String previousValue = current(form.kind(), target);
          change(context, target, form);

          Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("combatantId", target.entry().id());
          payload.put("kind", form.kind());
          payload.put("before", previousValue);
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
          CombatEffects.add(
              context,
              target,
              new ConditionInstance(
                  Encounters.conditionKey(context, form.condition()),
                  form.source(),
                  conditionEnd(context, target, form)));
      case REMOVE_CONDITION ->
          CombatEffects.remove(
              context, target, Encounters.conditionKey(context, form.condition()), form.source());
      case SET_EXHAUSTION -> {
        if (form.amount() == null || form.amount() < 0 || form.amount() > 6) throw invalid();
        context.exhaustion(target, form.amount());
      }
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

  private static End conditionEnd(Context context, Creature target, Correction form) {
    DurationKind kind = form.duration() == null ? DurationKind.UNTIL_DISPELLED : form.duration();
    UUID creature = form.durationCreature() == null ? target.entry().id() : form.durationCreature();
    return switch (kind) {
      case INSTANT -> throw invalid();
      case CLOCK, CONCENTRATION ->
          new End(kind, null, Math.addExact(context.seconds, durationAmount(form)), null);
      case ROUNDS -> {
        context.creature(creature);
        yield End.turn(kind, (long) context.table.round() + durationAmount(form), creature);
      }
      case UNTIL_TURN_START, UNTIL_TURN_END -> {
        var turn = context.creature(creature).entry();
        if (!Encounters.present(turn)) throw invalid();
        long current = context.table.round();
        boolean currentEnd =
            kind == DurationKind.UNTIL_TURN_END && creature.equals(context.table.acting());
        yield End.turn(
            kind,
            !currentEnd && turn.tactics().lastTurn() >= current ? current + 1 : current,
            creature);
      }
      default -> new End(kind, null, null, null);
    };
  }

  private static int durationAmount(Correction form) {
    if (form.durationAmount() == null || form.durationAmount() < 1) throw invalid();
    return form.durationAmount();
  }

  private EncounterStore.Event latest(UUID encounter, UUID entryId) {
    if (entryId == null) throw invalid();
    return store
        .latestReversible(encounter)
        .filter(row -> row.id().equals(entryId))
        .orElseThrow(() -> new CampaignException(Code.ENCOUNTER_VERSION_CONFLICT));
  }

  private EncounterUndo preimage(EncounterStore.Event event) {
    return json.treeToValue(event.payload().path("actionPreimage"), EncounterUndo.class);
  }

  private static String undoSentence(EncounterStore.Event event) {
    return event.type().equals("AttackResolved")
        ? "The DM undid the last attack."
        : "The DM undid the last action.";
  }

  private static void announceUndo(
      Context context, EncounterStore.Event event, EncounterUndo undo) {
    if (undo.publicChange()) {
      var line = new StringBuilder(undoSentence(event));
      for (var change : undo.changes()) {
        var before = change.before();
        var after = change.after();
        if (before == null
            || after == null
            || before.owner() == null
            || before.vitals().health().equals(after.vitals().health())) continue;
        var hp = before.vitals().health();
        line.append(" ")
            .append(before.name())
            .append(" now has ")
            .append(hp.current())
            .append(" of ")
            .append(hp.maximum())
            .append(" hit points.");
      }
      context.tell("PlayerCorrection", null, line.toString());
    }
    undo.privateChanges()
        .forEach(
            (owner, name) ->
                context.tell(
                    "PlayerCorrection",
                    owner,
                    undoSentence(event) + " " + name + "'s private state was restored."));
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
      case SET_HIT_POINTS, ADJUST_HIT_POINTS, SET_RESOURCE, SET_EXHAUSTION ->
          creature.owner() != null;
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
    UUID recipient =
        kind == Kind.SET_RESOURCE || kind == Kind.SET_EXHAUSTION ? after.owner() : null;
    context.tell("PlayerCorrection", recipient, line);
  }

  private static String sentence(Kind kind, Creature creature) {
    String label =
        switch (kind) {
          case SET_HIT_POINTS, ADJUST_HIT_POINTS -> "hit points";
          case ADD_CONDITION, REMOVE_CONDITION -> "conditions";
          case SET_RESOURCE -> "resources";
          case SET_EXHAUSTION -> "exhaustion";
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
      case SET_EXHAUSTION -> Integer.toString(creature.vitals().exhaustion());
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
    SET_EXHAUSTION,
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
      boolean quiet,
      DurationKind duration,
      Integer durationAmount,
      UUID durationCreature) {}

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
