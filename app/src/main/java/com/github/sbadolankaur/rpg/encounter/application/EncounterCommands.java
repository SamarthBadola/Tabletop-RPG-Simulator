package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignSettings;
import com.github.sbadolankaur.rpg.campaign.application.CampaignView;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.persistence.CommandStore;
import com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore;
import com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore.Window;
import com.github.sbadolankaur.rpg.content.ContentPacks;
import com.github.sbadolankaur.rpg.encounter.application.CombatActions.Attack;
import com.github.sbadolankaur.rpg.encounter.application.CombatActions.Check;
import com.github.sbadolankaur.rpg.encounter.application.CombatActions.ManualMove;
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Table;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.encounter.persistence.AfflictionStore;
import com.github.sbadolankaur.rpg.encounter.persistence.EncounterStore;
import com.github.sbadolankaur.rpg.engine.affliction.Affliction;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.Exhaustion;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;
import com.github.sbadolankaur.rpg.engine.kernel.HitPointRules;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Prompt;
import com.github.sbadolankaur.rpg.platform.InstanceMac;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EncounterCommands {
  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(EncounterCommands.class);
  private final Campaigns campaigns;
  private final CampaignCommands campaignCommands;
  private final CampaignSettings settings;
  private final CommandStore clocks;
  private final EncounterStore store;
  private final CombatRoster roster;
  private final InstanceMac mac;
  private final JsonMapper json;
  private final EncounterDice random;
  private final ReactionStore reactions;
  private final ContentPacks contentPacks;
  private final MeterRegistry metrics;
  private final AfflictionStore afflictions;

  EncounterCommands(
      Campaigns campaigns,
      CampaignCommands campaignCommands,
      CampaignSettings settings,
      CommandStore clocks,
      EncounterStore store,
      CombatRoster roster,
      InstanceMac mac,
      JsonMapper json,
      EncounterDice random,
      ReactionStore reactions,
      ContentPacks contentPacks,
      MeterRegistry metrics,
      AfflictionStore afflictions) {
    this.campaigns = campaigns;
    this.campaignCommands = campaignCommands;
    this.settings = settings;
    this.clocks = clocks;
    this.store = store;
    this.roster = roster;
    this.mac = mac;
    this.json = json;
    this.random = random;
    this.reactions = reactions;
    this.contentPacks = contentPacks;
    this.metrics = metrics;
    this.afflictions = afflictions;
  }

  public Reply create(UUID campaign, UUID actor, Envelope envelope, String name) {
    campaigns.requireDm(campaign, actor);
    String title = name == null ? "" : name.strip();
    if (title.isEmpty() || title.codePointCount(0, title.length()) > 80) throw invalid();
    return campaignCommands.run(
        campaign,
        actor,
        envelope,
        CampaignCommands.Kind.ENCOUNTER_CREATE,
        title,
        clock -> {
          UUID id = ServerIds.at(Instant.now());
          store.create(campaign, id, title);
          return CampaignCommands.Change.unchanged(clock, Map.of("encounterId", id));
        });
  }

  @Transactional
  Reply run(
      UUID campaign,
      UUID encounter,
      UUID actor,
      Envelope envelope,
      String command,
      Object request,
      boolean needsSession,
      Consumer<Context> resolve) {
    campaigns.requireDm(campaign, actor);
    var clock = clocks.clock(campaign, true);
    var table = require(campaign, encounter, true);
    if (envelope.actionId() == null || envelope.expectedVersion() < 0) throw invalid();
    byte[] hash = mac.sign("encounter-action:" + command, json.writeValueAsString(request));
    var previous = store.action(encounter, envelope.actionId());
    if (previous.isPresent()) {
      var saved = previous.get();
      if (!actor.equals(saved.actor()) || !MessageDigest.isEqual(hash, saved.hash())) {
        throw new CampaignException(Code.IDEMPOTENCY_KEY_CONFLICT);
      }
      return new Reply(saved.version(), saved.result(), true);
    }
    if (reactions.forEncounter(campaign, encounter).isPresent())
      throw new CampaignException(Code.REACTION_PENDING);
    if (table.version() != envelope.expectedVersion())
      throw new CampaignException(Code.ENCOUNTER_VERSION_CONFLICT);
    if (needsSession
        && campaigns.requireDm(campaign, actor).sessionState() != CampaignView.SessionState.ACTIVE)
      throw new CampaignException(Code.SESSION_NOT_ACTIVE);

    var context =
        new Context(
            table,
            actor,
            clock.gameSeconds(),
            clock.version(),
            settings.effective(campaign, actor),
            roster.content(campaign, actor),
            store.entries(encounter));
    boolean endTurn = "end-turn".equals(command);
    boolean deathSave = "death-saves".equals(command);
    if (request instanceof Attack
        || request instanceof ManualMove
        || request instanceof Check
        || endTurn
        || deathSave) {
      var pinned =
          new RecordedAction(
              request instanceof Attack attack ? attack : null,
              request instanceof ManualMove move ? move : null,
              request instanceof Check check ? check : null,
              endTurn ? (UUID) request : null,
              deathSave ? (UUID) request : null,
              json.valueToTree(context.effective.values()),
              context.content.packHashes(),
              ReactionReplay.Continuation.empty(),
              0,
              0);
      return resolveReactionAction(context, envelope, hash, pinned, null, null);
    }
    if (!"overrides".equals(command)
        || !(request instanceof CombatOverrides.Correction correction)
        || correction.kind() != CombatOverrides.Kind.UNDO_LAST) {
      CombatEffects.expire(context, null, ConditionInstance.Boundary.CLOCK);
    }
    resolve.accept(context);
    if (context.captureUndo) CombatEffects.expire(context, null, ConditionInstance.Boundary.CLOCK);
    return commit(context, envelope, hash, table, clock.gameSeconds(), 0, 0);
  }

  private Reply commit(
      Context context,
      Envelope envelope,
      byte[] hash,
      Table table,
      long secondsBefore,
      int auditedEvents,
      int auditedWarnings) {
    UUID encounter = table.id();
    long version = Math.incrementExact(table.version());
    JsonNode result = json.valueToTree(context.result);
    store.action(
        encounter,
        envelope.actionId(),
        context.actor,
        hash,
        result,
        json.valueToTree(context.settingsRead),
        version);
    int lastDmEvent = context.lastDmEvent();
    int seen = 0;
    for (int position = 0; position < context.events.size(); position++) {
      var event = context.events.get(position);
      if (event.auditedDuringPause() && seen++ < auditedEvents) continue;
      var payload = json.<tools.jackson.databind.node.ObjectNode>valueToTree(event.payload());
      if (position == lastDmEvent && context.captureUndo) {
        payload.set("actionPreimage", json.valueToTree(context.undo(table, secondsBefore)));
      }
      store.event(
          encounter,
          envelope.actionId(),
          event.gameSeconds(),
          event.round(),
          event.type(),
          event.audience(),
          event.recipient(),
          payload);
    }
    store.update(context.table, version);
    if (context.seconds != secondsBefore) {
      clocks.updateClock(
          table.campaign(), context.seconds, Math.incrementExact(context.clockVersion));
    }
    criticalWarnings(encounter, context.criticalWarnings, auditedWarnings);
    return new Reply(version, result, false);
  }

  private static void criticalWarnings(UUID encounter, List<Warning> warnings, int alreadyLogged) {
    var fresh = List.copyOf(warnings.subList(alreadyLogged, warnings.size()));
    if (!fresh.isEmpty())
      afterCommit(
          () ->
              fresh.forEach(
                  warning ->
                      LOG.warn(
                          "critical_not_applied encounter_id={} term_label={} term_source={}",
                          encounter,
                          safeLog(warning.label()),
                          safeLog(warning.source()))));
  }

  private static void afterCommit(Runnable effect) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            effect.run();
          }
        });
  }

  @Transactional(readOnly = true)
  public ReactionView reaction(UUID campaign, UUID encounter, UUID actor) {
    campaigns.requireDm(campaign, actor);
    require(campaign, encounter, false);
    return reactions.forEncounter(campaign, encounter).map(ReactionView::of).orElse(null);
  }

  @Transactional
  public Reply answer(
      UUID campaign, UUID windowId, UUID actor, Envelope envelope, ReactionAnswer answer) {
    if (!campaigns.isMember(campaign, actor)
        || campaigns.requireMember(campaign, actor).role() != CampaignView.Role.DM) {
      metrics.counter("reaction.denied").increment();
      LOG.warn("reaction.denied campaign={} window={} principal={}", campaign, windowId, actor);
      throw new CampaignException(Code.REACTION_NOT_PERMITTED);
    }
    if (envelope.actionId() == null || envelope.expectedVersion() < 0 || answer == null)
      throw invalid();
    if (answer.reason() != null
        && answer.reason().codePointCount(0, answer.reason().length()) > 200) throw invalid();
    byte[] hash = mac.sign("encounter-reaction:" + windowId, json.writeValueAsString(answer));
    var clock = clocks.clock(campaign, true);
    var receipt = store.reactionAction(campaign, windowId, envelope.actionId());
    if (receipt.isPresent()) return replay(receipt.get(), actor, hash);
    var window =
        reactions
            .findEncounterWindow(campaign, windowId)
            .orElseThrow(() -> new CampaignException(Code.REACTION_NOT_AVAILABLE));
    var table = require(campaign, window.encounter(), true);
    var previous = store.action(table.id(), envelope.actionId());
    if (previous.isPresent()) return replay(previous.get(), actor, hash);
    if (table.version() != envelope.expectedVersion()
        || window.version() != envelope.expectedVersion())
      throw new CampaignException(Code.ENCOUNTER_VERSION_CONFLICT);

    if (answer.kind() == AnswerKind.CANCEL) {
      var context =
          new Context(table, actor, clock.gameSeconds(), clock.version(), null, null, List.of());
      context.disableUndo();
      context.result("encounterId", table.id());
      context.result("reactionWindowId", window.id());
      context.result("outcome", "cancelled");
      var event = new LinkedHashMap<String, Object>();
      event.put("windowId", window.id());
      event.put("openedByActionId", window.openedBy());
      if (answer.reason() != null) event.put("reason", answer.reason());
      context.dm("ActionCancelled", event);
      closeReaction(window);
      afterCommit(() -> LOG.info("reaction.cancelled window={} principal={}", window.id(), actor));
      return commit(context, envelope, hash, table, clock.gameSeconds(), 0, 0);
    }

    var prompt = window.prompt();
    boolean followUp = answer.kind() == AnswerKind.FOLLOW_UP;
    if (!prompt.decider().equals(answer.deciderId())
        || prompt.followUp() != followUp
        || !prompt.permits(answer.optionKey()))
      throw new CampaignException(Code.REACTION_OPTION_INVALID);
    var saved = json.treeToValue(window.continuation(), RecordedAction.class);
    var context =
        new Context(
            table,
            actor,
            clock.gameSeconds(),
            clock.version(),
            settings.pinned(saved.settings()),
            contentPacks.openPinned(saved.packHashes()),
            store.entries(table.id()));
    var decision = new LinkedHashMap<String, Object>();
    decision.put("windowId", window.id());
    decision.put("deciderId", prompt.decider());
    decision.put("principalId", actor);
    decision.put("point", prompt.point());
    decision.put("choice", answer.optionKey() == null ? "pass" : answer.optionKey());
    context.dm("ReactionAnswered", decision);
    afterCommit(
        () ->
            LOG.info(
                "reaction.answered window={} decider={} option={} principal={}",
                windowId,
                prompt.decider(),
                decision.get("choice"),
                actor));
    return resolveReactionAction(context, envelope, hash, saved, window, answer.optionKey());
  }

  private static Reply replay(EncounterStore.Replay saved, UUID actor, byte[] hash) {
    if (!actor.equals(saved.actor()) || !MessageDigest.isEqual(hash, saved.hash()))
      throw new CampaignException(Code.IDEMPOTENCY_KEY_CONFLICT);
    return new Reply(saved.version(), saved.result(), true);
  }

  private Reply resolveReactionAction(
      Context context,
      Envelope envelope,
      byte[] hash,
      RecordedAction saved,
      Window window,
      String option) {
    Table table = context.table;
    long secondsBefore = context.seconds;
    var continuation =
        window == null ? saved.replay() : saved.replay().answer(window.prompt(), option);
    context.stage = true;
    context.resuming = window != null;
    var outcome =
        ReactionReplay.run(
            saved.name(),
            continuation,
            random,
            engine -> {
              context.replay = engine;
              CombatEffects.expire(context, null, ConditionInstance.Boundary.CLOCK);
              saved.resolve(context);
              CombatEffects.expire(context, null, ConditionInstance.Boundary.CLOCK);
              return context;
            });
    if (outcome.pending() == null) {
      context.flush();
      if (window != null) {
        closeReaction(window);
        context.result("reactionWindowId", window.id());
        context.result("encounterId", table.id());
      }
      for (var point : outcome.depthLimits()) {
        context.dm("ReactionDepthLimit", Map.of("point", point));
        afterCommit(
            () -> {
              metrics.counter("reaction.depth_limit").increment();
              LOG.warn("reaction.depth_limit encounter={} point={}", table.id(), point);
            });
      }
      return commit(
          context,
          envelope,
          hash,
          table,
          secondsBefore,
          saved.auditedEvents(),
          saved.auditedWarnings());
    }

    long version = Math.incrementExact(table.version());
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID id = window == null ? ServerIds.at(now.toInstant()) : window.id();
    int audited = (int) context.events.stream().filter(PendingEvent::auditedDuringPause).count();
    var record =
        new RecordedAction(
            saved.action(),
            saved.move(),
            saved.check(),
            saved.endTurn(),
            saved.deathSave(),
            saved.settings(),
            saved.packHashes(),
            outcome.continuation(),
            audited,
            context.criticalWarnings.size());
    var next =
        new Window(
            id,
            table.campaign(),
            table.id(),
            window == null ? envelope.actionId() : window.openedBy(),
            outcome.pending(),
            json.valueToTree(record),
            version,
            window == null ? now : window.createdAt());
    JsonNode result =
        json.valueToTree(
            Map.of(
                "reactionWindowId",
                id,
                "encounterId",
                table.id(),
                "awaitingReaction",
                id,
                "reaction",
                ReactionView.of(next)));
    store.action(
        table.id(),
        envelope.actionId(),
        context.actor,
        hash,
        result,
        json.valueToTree(context.settingsRead),
        version);
    int seen = 0;
    for (var event : context.events) {
      if (event.auditedDuringPause() && seen++ >= saved.auditedEvents()
          || "ReactionAnswered".equals(event.type())) {
        store.event(
            table.id(),
            envelope.actionId(),
            event.gameSeconds(),
            event.round(),
            event.type(),
            event.audience(),
            event.recipient(),
            json.valueToTree(event.payload()));
      }
    }
    store.event(
        table.id(),
        envelope.actionId(),
        secondsBefore,
        table.round(),
        "ReactionOpened",
        "DM",
        null,
        json.valueToTree(
            Map.of(
                "windowId",
                id,
                "point",
                next.prompt().point(),
                "depth",
                next.prompt().frames().size())));
    if (window == null) reactions.open(next);
    else reactions.advance(window, next);
    store.update(table, version);
    criticalWarnings(table.id(), context.criticalWarnings, saved.auditedWarnings());
    afterCommit(
        () -> {
          metrics.counter("reaction.opened").increment();
          LOG.info(
              "reaction.opened encounter={} window={} point={}",
              table.id(),
              id,
              next.prompt().point());
        });
    return new Reply(version, result, false);
  }

  private void closeReaction(Window window) {
    reactions.close(window);
    afterCommit(
        () ->
            metrics
                .timer("reaction.open_seconds")
                .record(Duration.between(window.createdAt(), OffsetDateTime.now(ZoneOffset.UTC))));
  }

  private record RecordedAction(
      Attack action,
      ManualMove move,
      Check check,
      UUID endTurn,
      UUID deathSave,
      JsonNode settings,
      List<String> packHashes,
      ReactionReplay.Continuation replay,
      int auditedEvents,
      int auditedWarnings) {
    String name() {
      if (action != null) return "attack";
      if (move != null) return "move";
      if (check != null) return check.save() ? "save" : "check";
      return endTurn != null ? "end-turn" : "death-save";
    }

    void resolve(Context context) {
      if (action != null) CombatActions.resolveAttack(context, action);
      else if (move != null) CombatActions.resolveMove(context, move);
      else if (check != null) CombatActions.resolveCheck(context, check);
      else if (endTurn != null) Encounters.resolveEndTurn(context, endTurn);
      else Encounters.resolveExplicitDeathSave(context, deathSave);
    }
  }

  public record ReactionView(UUID id, long version, Prompt prompt) {
    static ReactionView of(Window window) {
      return new ReactionView(window.id(), window.version(), window.prompt());
    }
  }

  public enum AnswerKind {
    ANSWER,
    FOLLOW_UP,
    CANCEL
  }

  public record ReactionAnswer(AnswerKind kind, UUID deciderId, String optionKey, String reason) {
    public ReactionAnswer {
      if (kind == null
          || (kind == AnswerKind.CANCEL
              ? deciderId != null || optionKey != null
              : deciderId == null
                  || reason != null
                  || kind == AnswerKind.FOLLOW_UP && optionKey == null)) throw invalid();
    }
  }

  private static String safeLog(String text) {
    return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
  }

  Table require(UUID campaign, UUID encounter, boolean lock) {
    return store
        .find(campaign, encounter, lock)
        .orElseThrow(() -> new CampaignException(Code.NOT_FOUND));
  }

  static CampaignException invalid() {
    return new CampaignException(Code.VALIDATION_FAILED);
  }

  final class Context {
    Table table;
    long seconds;
    final long clockVersion;
    private final Map<UUID, EncounterUndo.Snapshot> originals = new LinkedHashMap<>();
    private boolean captureUndo = true;
    private final UUID actor;
    private final SettingsView effective;
    final ContentSet content;
    final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Creature> profiles = new HashMap<>();
    private final Map<UUID, Vitals> pendingVitals = new HashMap<>();
    private final Map<UUID, Map<Integer, Integer>> pendingSlots = new HashMap<>();
    private final List<Runnable> writes = new ArrayList<>();
    private boolean stage;
    private boolean resuming;
    private ReactionReplay replay;
    private final Map<String, Object> settingsRead = new LinkedHashMap<>();
    private final List<PendingEvent> events = new ArrayList<>();
    private final List<Warning> criticalWarnings = new ArrayList<>();
    private final List<JsonNode> rolls = new ArrayList<>();
    private final Map<String, Object> result = new LinkedHashMap<>();

    Context(
        Table table,
        UUID actor,
        long seconds,
        long clockVersion,
        SettingsView effective,
        ContentSet content,
        List<Entry> entries) {
      this.table = table;
      this.actor = actor;
      this.seconds = seconds;
      this.clockVersion = clockVersion;
      this.effective = effective;
      this.content = content;
      entries.forEach(entry -> this.entries.put(entry.id(), entry));
    }

    RandomSource random() {
      return replay == null ? random : replay.random();
    }

    ReactionReplay reactions() {
      return replay;
    }

    void preflight(List<String> keys) {
      if (!resuming) settings.requireDecisions(table.campaign(), actor, keys);
    }

    private void write(Runnable operation) {
      if (stage) writes.add(operation);
      else operation.run();
    }

    private void flush() {
      writes.forEach(Runnable::run);
      writes.clear();
      pendingVitals.clear();
      pendingSlots.clear();
      profiles.clear();
      stage = false;
    }

    <T> T setting(String key, Class<T> type) {
      Object value = effective.value(key);
      settingsRead.put(key, value);
      return type.cast(value);
    }

    Creature creature(UUID id) {
      Entry entry = entries.get(id);
      if (entry == null) throw new CampaignException(Code.NOT_FOUND);
      var profile =
          profiles.computeIfAbsent(
              id, key -> roster.load(table.campaign(), actor, entry, content, effective));
      if (!stage) return profile;
      var vitals =
          entry.character() == null
              ? entry.npcVitals()
              : pendingVitals.getOrDefault(id, profile.vitals());
      return profile.withState(entry, vitals);
    }

    String creatureType(Creature creature) {
      String monster = creature.entry().monster();
      return monster == null
          ? "humanoid"
          : String.valueOf(content.find("monster", monster).orElseThrow().fields().get("type"));
    }

    Creature acting(UUID id) {
      if (table.phase() != EncounterState.Phase.ACTIVE || id == null || !id.equals(table.acting()))
        throw new CampaignException(Code.NOT_YOUR_TURN);
      return creature(id);
    }

    Creature canAct(UUID id) {
      var creature = acting(id);
      if (creature.incapacitated() || creature.entry().tactics().surprised())
        throw new CampaignException(Code.NO_ACTIONS_LEFT);
      return creature;
    }

    private void remember(UUID id) {
      if (captureUndo && !originals.containsKey(id)) originals.put(id, snapshot(id));
    }

    EncounterUndo.Snapshot snapshot(UUID id) {
      if (!entries.containsKey(id)) return null;
      var creature = creature(id);
      return new EncounterUndo.Snapshot(
          creature.entry(),
          creature.name(),
          creature.owner(),
          creature.vitals(),
          creature.resources(),
          equipment(creature),
          spellSlots(creature),
          afflictions(id));
    }

    void disableUndo() {
      captureUndo = false;
    }

    int lastDmEvent() {
      for (int i = events.size() - 1; i >= 0; i--) {
        if (events.get(i).audience().equals("DM")) return i;
      }
      return -1;
    }

    Encounters.Recap recap() {
      var fallen = new LinkedHashMap<UUID, String>();
      var resources = new java.util.TreeMap<String, Integer>();
      for (var preimage : store.committedPreimages(table.id())) {
        var action = json.treeToValue(preimage, EncounterUndo.class);
        for (var change : action.changes()) {
          var before = change.before();
          var after = change.after();
          if (before == null || after == null) continue;
          if (before.vitals().health().current() > 0 && after.vitals().health().current() == 0)
            fallen.put(change.id(), after.name());
          after
              .resources()
              .forEach(
                  (key, used) -> {
                    int spent = used - before.resources().getOrDefault(key, 0);
                    if (spent > 0) resources.merge(key, spent, Integer::sum);
                  });
        }
      }
      for (var id : entries.keySet()) {
        var creature = creature(id);
        if (creature.vitals().health().current() == 0) fallen.putIfAbsent(id, creature.name());
      }
      return new Encounters.Recap(table.round(), List.copyOf(fallen.values()), resources);
    }

    EncounterUndo undo(Table before, long elapsed) {
      boolean publicOutput = events.stream().anyMatch(event -> event.audience().equals("ALL"));
      return EncounterUndo.of(this, before, elapsed, originals, publicOutput);
    }

    void update(Entry entry) {
      remember(entry.id());
      entries.put(entry.id(), entry);
      if (!stage) profiles.remove(entry.id());
      write(() -> store.update(entry));
    }

    void add(Entry entry) {
      remember(entry.id());
      entries.put(entry.id(), entry);
      UUID encounter = table.id();
      write(() -> store.add(encounter, entry));
    }

    void remove(UUID id) {
      remember(id);
      entries.remove(id);
      profiles.remove(id);
      UUID encounter = table.id();
      write(() -> store.remove(encounter, id));
    }

    Vitals health(Creature current, CombatHealth next) {
      return health(current, next, current.vitals().exhaustion());
    }

    void exhaustion(Creature current, int level) {
      if (level < 0 || level > 6) throw invalid();
      var old = current.vitals().health();
      int max = Exhaustion.maximumHp(current.baseMaximumHp(), level);
      int hp = HitPointRules.afterMaximumChange(old.current(), old.maximum(), max);
      var next =
          Exhaustion.dead(level)
              ? new CombatHealth(0, max, old.temporary(), State.DEAD, 0, 3)
              : new CombatHealth(
                  hp, max, old.temporary(), old.state(), old.successes(), old.failures());
      health(current, next, level);
    }

    private Vitals health(Creature current, CombatHealth next, int exhaustion) {
      remember(current.entry().id());
      var before = current.vitals();
      var conditions = new HashSet<>(before.conditions());
      Long recoveryAt = null;
      if (next.state() == State.STABLE) {
        recoveryAt = before.recoveryAt();
        if (recoveryAt == null) {
          var recoveryRoll = DiceExpression.parse("1d4").roll(random());
          dice("StableRecovery", current.entry().id(), recoveryRoll);
          recoveryAt = Math.addExact(seconds, 3600L * recoveryRoll.total());
        }
      }
      if (next.state() != State.ALIVE) {
        conditions.add(CombatConditions.UNCONSCIOUS);
        conditions.add(CombatConditions.PRONE);
        if (before.health().state() == State.ALIVE) write(() -> roster.dropHeld(current));
      } else if (before.health().state() != State.ALIVE) {
        conditions.remove(CombatConditions.UNCONSCIOUS);
      }
      var vitals =
          new Vitals(
              next, recoveryAt, conditions, before.effects(), exhaustion, before.inspiration());
      vitals(current, vitals);
      return vitals;
    }

    void vitals(Creature current, Vitals next) {
      remember(current.entry().id());
      if (current.entry().character() == null) update(current.entry().vitals(next));
      else {
        if (stage) pendingVitals.put(current.entry().id(), next);
        else profiles.remove(current.entry().id());
        write(() -> roster.writePlayer(current, next));
      }
    }

    void dm(String type, Map<String, ?> data) {
      events.add(new PendingEvent(type, "DM", null, data, seconds, table.round()));
    }

    void tell(String type, UUID recipient, String line) {
      events.add(
          new PendingEvent(
              type,
              recipient == null ? "ALL" : "PLAYERS",
              recipient,
              Map.of("text", line),
              seconds,
              table.round()));
    }

    void reveal(UUID decider, UUID recipient, ReactionReplay.Disclosure disclosure) {
      events.add(
          new PendingEvent(
              "ReactionRollRevealed",
              recipient == null ? "DM" : "PLAYERS",
              recipient,
              Map.of("deciderId", decider, "disclosure", disclosure),
              seconds,
              table.round()));
    }

    void result(String key, Object value) {
      result.put(key, value);
    }

    Map<UUID, String> equipment(Creature creature) {
      return roster.equipment(creature);
    }

    void restoreEquipment(Creature creature, Map<UUID, String> slots) {
      remember(creature.entry().id());
      write(() -> roster.restoreEquipment(creature, slots));
      profiles.remove(creature.entry().id());
    }

    Map<UUID, Affliction> afflictions(UUID combatant) {
      return afflictions.forCombatant(combatant);
    }

    void affliction(UUID combatant, Affliction affliction) {
      remember(combatant);
      afflictions.update(combatant, affliction);
    }

    Map<Integer, Integer> spellSlots(Creature creature) {
      var staged = pendingSlots.get(creature.entry().id());
      return staged == null ? roster.spentSlots(creature) : staged;
    }

    void spellSlot(Creature creature, int level, int used) {
      UUID id = creature.entry().id();
      remember(id);
      if (stage) {
        var slots = new HashMap<>(spellSlots(creature));
        if (used == 0) slots.remove(level);
        else slots.put(level, used);
        pendingSlots.put(id, Map.copyOf(slots));
      }
      write(() -> roster.spellSlot(creature, level, used));
    }

    void resource(Creature creature, String key, int used) {
      remember(creature.entry().id());
      write(() -> roster.setResource(creature, key, used));
      profiles.remove(creature.entry().id());
    }

    void dice(String purpose, UUID creature, RollResult roll) {
      var data = json.<tools.jackson.databind.node.ObjectNode>valueToTree(roll);
      for (int i = 0; i < roll.terms().size(); i++) {
        var term = roll.terms().get(i);
        if (term.criticalNotApplied()) {
          ((tools.jackson.databind.node.ObjectNode) data.path("terms").get(i))
              .put("critical_not_applied", true);
          criticalWarnings.add(new Warning(term.label(), term.source()));
        }
      }
      dm("DiceRolled", Map.of("purpose", purpose, "combatantId", creature, "roll", data));
      rolls.add(data);
      result("rolls", rolls);
    }
  }

  private record PendingEvent(
      String type,
      String audience,
      UUID recipient,
      Map<String, ?> payload,
      long gameSeconds,
      int round) {
    boolean auditedDuringPause() {
      return type.equals("DiceRolled") || type.equals("ReactionRollRevealed");
    }
  }

  private record Warning(String label, String source) {}
}
