package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Audience;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Change;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Kind;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignView.SessionState;
import com.github.sbadolankaur.rpg.campaign.persistence.PlayStore;
import com.github.sbadolankaur.rpg.campaign.persistence.PlayStore.Light;
import com.github.sbadolankaur.rpg.campaign.persistence.PlayStore.Scene;
import com.github.sbadolankaur.rpg.campaign.persistence.PlayStore.Session;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignPlay {
  private static final long MAX_ADVANCE = 366 * 86400L;
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final PlayStore store;
  private final com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore reactions;

  public CampaignPlay(
      Campaigns campaigns,
      CampaignCommands commands,
      PlayStore store,
      com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore reactions) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.store = store;
    this.reactions = reactions;
  }

  @Transactional(readOnly = true)
  public Workspace workspace(UUID campaign, UUID actor) {
    var view = campaigns.requireMember(campaign, actor);
    return new Workspace(
        view,
        commands.readClock(campaign, actor),
        store.latestSession(campaign).orElse(null),
        store.currentScene(campaign, view.role() == CampaignView.Role.DM).orElse(null));
  }

  @Transactional(readOnly = true)
  public List<Scene> scenes(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    return store.scenes(campaign);
  }

  public Reply start(UUID campaign, UUID actor, Envelope envelope) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.SESSION_START,
        Map.of(),
        clock -> {
          var latest = store.latestSession(campaign);
          if (latest.isPresent() && latest.get().state() != SessionState.ENDED) {
            throw new CampaignException(CampaignException.Code.CAMPAIGN_VERSION_CONFLICT);
          }
          var now = OffsetDateTime.now(ZoneOffset.UTC);
          UUID id = ServerIds.at(now.toInstant());
          long number = latest.map(Session::number).orElse(0L) + 1;
          store.addSession(campaign, id, number, now);
          var session = sessionData(id, number, SessionState.ACTIVE, now);
          return Change.at(clock, "SessionStarted", Audience.ALL, session, session);
        });
  }

  public Reply transition(
      UUID campaign, UUID actor, Envelope envelope, UUID id, Transition transition) {
    return commands.run(
        campaign,
        actor,
        envelope,
        transition.kind,
        id,
        clock -> {
          var session =
              store
                  .session(campaign, id)
                  .orElseThrow(() -> new CampaignException(CampaignException.Code.NOT_FOUND));
          if (session.state() == SessionState.ENDED
              || (transition == Transition.PAUSE && session.state() != SessionState.ACTIVE)
              || (transition == Transition.RESUME && session.state() != SessionState.PAUSED)) {
            throw new CampaignException(CampaignException.Code.CAMPAIGN_VERSION_CONFLICT);
          }
          var end = transition == Transition.END ? OffsetDateTime.now(ZoneOffset.UTC) : null;
          store.updateSession(campaign, id, transition.state, end);
          if (transition == Transition.END) {
            store.clearScene(campaign);
          }
          var result = sessionData(id, session.number(), transition.state, session.startedAt());
          return Change.at(clock, transition.event, Audience.ALL, result, result);
        });
  }

  public Reply advance(UUID campaign, UUID actor, Envelope envelope, long amount, ClockUnit unit) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.CLOCK_ADVANCE,
        new Advance(amount, unit),
        clock -> {
          requireActive(campaign);
          if (amount < 1
              || amount > MAX_ADVANCE / unit.seconds
              || clock.gameSeconds() > Long.MAX_VALUE - amount * unit.seconds) {
            throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
          }
          long next = clock.gameSeconds() + amount * unit.seconds;
          var payload = Map.of("previousGameSeconds", clock.gameSeconds(), "gameTimeSeconds", next);
          return new Change(
              next,
              "ClockAdvanced",
              Audience.ALL,
              payload,
              Map.of("gameTimeSeconds", next, "effectsEnded", List.of()));
        });
  }

  public Reply saveScene(UUID campaign, UUID actor, Envelope envelope, UUID id, SceneInput input) {
    Kind kind = id == null ? Kind.SCENE_CREATE : Kind.SCENE_EDIT;
    return commands.run(
        campaign,
        actor,
        envelope,
        kind,
        new SceneEdit(id, input),
        clock -> {
          UUID result;
          if (id == null) {
            var now = OffsetDateTime.now(ZoneOffset.UTC);
            result = ServerIds.at(now.toInstant());
            store.addScene(campaign, result, input.name(), input.notes(), input.light(), now);
          } else {
            if (reactions.forScene(campaign, id).isPresent())
              throw new CampaignException(CampaignException.Code.REACTION_PENDING);
            if (!store.editScene(campaign, id, input.name(), input.notes(), input.light())) {
              throw new CampaignException(CampaignException.Code.NOT_FOUND);
            }
            result = id;
          }
          var data = Map.of("sceneId", result, "name", input.name());
          return Change.at(
              clock, id == null ? "SceneCreated" : "SceneEdited", Audience.DM, data, data);
        });
  }

  public Reply activateScene(UUID campaign, UUID actor, Envelope envelope, UUID id) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.SCENE_ACTIVATE,
        id,
        clock -> {
          var session = requireActive(campaign);
          store.clearScene(campaign);
          if (!store.activateScene(campaign, id, session.id())) {
            throw new CampaignException(CampaignException.Code.NOT_FOUND);
          }
          var data = Map.of("sceneId", id);
          return Change.at(clock, "SceneActivated", Audience.ALL, data, data);
        });
  }

  private Session requireActive(UUID campaign) {
    return store
        .latestSession(campaign)
        .filter(session -> session.state() == SessionState.ACTIVE)
        .orElseThrow(() -> new CampaignException(CampaignException.Code.SESSION_NOT_ACTIVE));
  }

  private static Map<String, Object> sessionData(
      UUID id, long number, SessionState state, OffsetDateTime start) {
    return Map.of(
        "sessionId", id, "number", number, "state", state.name(), "startedAt", start.toString());
  }

  public enum Transition {
    PAUSE(Kind.SESSION_PAUSE, SessionState.PAUSED, "SessionPaused"),
    RESUME(Kind.SESSION_RESUME, SessionState.ACTIVE, "SessionResumed"),
    END(Kind.SESSION_END, SessionState.ENDED, "SessionEnded");

    private final Kind kind;
    private final SessionState state;
    private final String event;

    Transition(Kind kind, SessionState state, String event) {
      this.kind = kind;
      this.state = state;
      this.event = event;
    }
  }

  public enum ClockUnit {
    MINUTES(60),
    HOURS(3600),
    DAYS(86400);
    private final int seconds;

    ClockUnit(int seconds) {
      this.seconds = seconds;
    }
  }

  private record Advance(long amount, ClockUnit unit) {}

  private record SceneEdit(UUID id, SceneInput input) {}

  public record SceneInput(String name, String notes, Light light) {}

  public record Workspace(
      CampaignView campaign, CampaignCommands.Clock clock, Session session, Scene scene) {}
}
