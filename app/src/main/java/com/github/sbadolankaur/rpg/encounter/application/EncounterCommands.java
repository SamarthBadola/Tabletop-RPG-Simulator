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
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Table;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.encounter.persistence.EncounterStore;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import com.github.sbadolankaur.rpg.platform.InstanceMac;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.security.MessageDigest;
import java.time.Instant;
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

  EncounterCommands(
      Campaigns campaigns,
      CampaignCommands campaignCommands,
      CampaignSettings settings,
      CommandStore clocks,
      EncounterStore store,
      CombatRoster roster,
      InstanceMac mac,
      JsonMapper json,
      EncounterDice random) {
    this.campaigns = campaigns;
    this.campaignCommands = campaignCommands;
    this.settings = settings;
    this.clocks = clocks;
    this.store = store;
    this.roster = roster;
    this.mac = mac;
    this.json = json;
    this.random = random;
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
            settings.effective(campaign, actor),
            roster.content(campaign, actor),
            store.entries(encounter));
    resolve.accept(context);
    long version = Math.incrementExact(table.version());
    JsonNode result = json.valueToTree(context.result);
    store.action(
        encounter,
        envelope.actionId(),
        actor,
        hash,
        result,
        json.valueToTree(context.settingsRead),
        version);
    for (var event : context.events) {
      store.event(
          encounter,
          envelope.actionId(),
          event.gameSeconds(),
          event.round(),
          event.type(),
          event.audience(),
          event.recipient(),
          json.valueToTree(event.payload()));
    }
    store.update(context.table, version);
    if (context.seconds != clock.gameSeconds()) {
      clocks.updateClock(campaign, context.seconds, Math.incrementExact(clock.version()));
    }
    if (!context.criticalWarnings.isEmpty()) {
      var warnings = List.copyOf(context.criticalWarnings);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              warnings.forEach(
                  warning ->
                      LOG.warn(
                          "critical_not_applied encounter_id={} term_label={} term_source={}",
                          encounter,
                          safeLog(warning.label()),
                          safeLog(warning.source())));
            }
          });
    }
    return new Reply(version, result, false);
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
    private final UUID actor;
    private final SettingsView effective;
    final ContentSet content;
    final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Creature> profiles = new HashMap<>();
    private final Map<String, Object> settingsRead = new LinkedHashMap<>();
    private final List<PendingEvent> events = new ArrayList<>();
    private final List<Warning> criticalWarnings = new ArrayList<>();
    private final List<JsonNode> rolls = new ArrayList<>();
    private final Map<String, Object> result = new LinkedHashMap<>();

    Context(
        Table table,
        UUID actor,
        long seconds,
        SettingsView effective,
        ContentSet content,
        List<Entry> entries) {
      this.table = table;
      this.actor = actor;
      this.seconds = seconds;
      this.effective = effective;
      this.content = content;
      entries.forEach(entry -> this.entries.put(entry.id(), entry));
    }

    RandomSource random() {
      return random;
    }

    void preflight(List<String> keys) {
      settings.requireDecisions(table.campaign(), actor, keys);
    }

    <T> T setting(String key, Class<T> type) {
      Object value = effective.value(key);
      settingsRead.put(key, value);
      return type.cast(value);
    }

    Creature creature(UUID id) {
      Entry entry = entries.get(id);
      if (entry == null) throw new CampaignException(Code.NOT_FOUND);
      return profiles.computeIfAbsent(
          id, key -> roster.load(table.campaign(), actor, entry, content));
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

    void update(Entry entry) {
      entries.put(entry.id(), entry);
      profiles.remove(entry.id());
      store.update(entry);
    }

    void add(Entry entry) {
      entries.put(entry.id(), entry);
      store.add(table.id(), entry);
    }

    void remove(UUID id) {
      entries.remove(id);
      profiles.remove(id);
      store.remove(table.id(), id);
    }

    Vitals health(Creature current, CombatHealth next) {
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
        if (before.health().state() == State.ALIVE) roster.dropHeld(current);
      } else if (before.health().state() != State.ALIVE) {
        conditions.remove(CombatConditions.UNCONSCIOUS);
      }
      var vitals = new Vitals(next, recoveryAt, conditions);
      vitals(current, vitals);
      return vitals;
    }

    void vitals(Creature current, Vitals next) {
      if (current.entry().character() == null) update(current.entry().vitals(next));
      else {
        roster.writePlayer(current, next);
        profiles.remove(current.entry().id());
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

    void result(String key, Object value) {
      result.put(key, value);
    }

    Map<UUID, String> equipment(Creature creature) {
      return roster.equipment(creature);
    }

    void restoreEquipment(Creature creature, Map<UUID, String> slots) {
      roster.restoreEquipment(creature, slots);
      profiles.remove(creature.entry().id());
    }

    void resource(Creature creature, String key, int used) {
      roster.setResource(creature, key, used);
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
      int round) {}

  private record Warning(String label, String source) {}
}
