package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.persistence.CommandStore;
import com.github.sbadolankaur.rpg.platform.InstanceMac;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CampaignCommands {
  private final Campaigns campaigns;
  private final CommandStore store;
  private final InstanceMac mac;
  private final JsonMapper json;

  public CampaignCommands(
      Campaigns campaigns, CommandStore store, InstanceMac mac, JsonMapper json) {
    this.campaigns = campaigns;
    this.store = store;
    this.mac = mac;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public Clock readClock(UUID campaign, UUID actor) {
    campaigns.requireMember(campaign, actor);
    return store.clock(campaign, false);
  }

  public UUID newActionId() {
    return ServerIds.at(OffsetDateTime.now(ZoneOffset.UTC).toInstant());
  }

  @Transactional
  public Reply run(
      UUID campaign,
      UUID actor,
      Envelope envelope,
      Kind kind,
      Object body,
      Function<Clock, Change> mutation) {
    campaigns.requireDm(campaign, actor);
    return execute(campaign, actor, envelope, kind, body, mutation);
  }

  @Transactional
  public Reply runForMember(
      UUID campaign,
      UUID actor,
      Envelope envelope,
      Kind kind,
      Object body,
      Function<Clock, Change> mutation) {
    campaigns.requireMember(campaign, actor);
    return execute(campaign, actor, envelope, kind, body, mutation);
  }

  private Reply execute(
      UUID campaign,
      UUID actor,
      Envelope envelope,
      Kind kind,
      Object body,
      Function<Clock, Change> mutation) {
    Objects.requireNonNull(envelope.actionId());
    Clock clock = store.clock(campaign, true);
    byte[] hash = mac.sign("campaign-action:" + kind.name(), json.writeValueAsString(body));
    var previous = store.action(campaign, envelope.actionId());
    if (previous.isPresent()) {
      var action = previous.get();
      if (!actor.equals(action.actor()) || !MessageDigest.isEqual(hash, action.hash())) {
        throw new CampaignException(CampaignException.Code.IDEMPOTENCY_KEY_CONFLICT);
      }
      return new Reply(clock.version(), json.readTree(action.result()), true);
    }
    if (clock.version() != envelope.expectedVersion()) {
      throw new CampaignException(CampaignException.Code.CAMPAIGN_VERSION_CONFLICT);
    }
    Change change = mutation.apply(clock);
    long next = Math.incrementExact(clock.version());
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    store.saveAction(
        campaign,
        envelope.actionId(),
        actor,
        hash,
        kind.name(),
        json.writeValueAsString(change.result()),
        json.writeValueAsString(change.settingsRead()),
        now);
    if (change.event() != null) {
      store.addEvent(
          campaign,
          next,
          envelope.actionId(),
          change.event(),
          change.gameSeconds(),
          change.audience().name(),
          json.writeValueAsString(change.payload()),
          now);
    }
    store.updateClock(campaign, change.gameSeconds(), next);
    return new Reply(next, json.valueToTree(change.result()), false);
  }

  @Transactional(readOnly = true)
  public java.util.List<CommandStore.EventSummary> recentEvents(UUID campaign, UUID actor) {
    var member = campaigns.requireMember(campaign, actor);
    return store.recentEvents(campaign, actor, member.role() == CampaignView.Role.DM);
  }

  public enum Kind {
    SETUP,
    SESSION_START,
    SESSION_PAUSE,
    SESSION_RESUME,
    SESSION_END,
    CLOCK_ADVANCE,
    INVITE_REVOKE,
    SCENE_CREATE,
    SCENE_EDIT,
    SCENE_ACTIVATE,
    SETTING_DECIDE,
    SETTING_RESET,
    SETTING_MODE,
    CHARACTER_ROLL,
    CHARACTER_CREATE,
    CHARACTER_EDIT,
    CHARACTER_LEVEL_GRANT,
    CHARACTER_LEVEL_UP,
    PACK_UPGRADE
  }

  public enum Audience {
    DM,
    ALL
  }

  public record Clock(long gameSeconds, long version) {}

  public record Envelope(UUID actionId, long expectedVersion) {}

  public record Reply(long version, JsonNode data, boolean replay) {}

  public record Change(
      long gameSeconds,
      String event,
      Audience audience,
      Object payload,
      Object result,
      Map<String, Object> settingsRead) {
    public Change(
        long gameSeconds, String event, Audience audience, Object payload, Object result) {
      this(gameSeconds, event, audience, payload, result, Map.of());
    }

    public Change withSettingsRead(Map<String, Object> reads) {
      return new Change(gameSeconds, event, audience, payload, result, Map.copyOf(reads));
    }

    public static Change at(
        Clock clock, String event, Audience audience, Object payload, Object result) {
      return new Change(clock.gameSeconds(), event, audience, payload, result);
    }

    public static Change unchanged(Clock clock, Object result) {
      return at(clock, null, Audience.DM, Map.of(), result);
    }
  }
}
