package com.github.sbadolankaur.rpg.rest.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Audience;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Change;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Event;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignSettings;
import com.github.sbadolankaur.rpg.campaign.application.CampaignTimeEffects;
import com.github.sbadolankaur.rpg.campaign.application.CampaignView.SessionState;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore;
import com.github.sbadolankaur.rpg.character.application.CharacterSheets.View;
import com.github.sbadolankaur.rpg.encounter.application.EncounterDice;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Phase;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.encounter.persistence.EncounterStore;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.kernel.GameClock;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Kind;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Participant;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Preview;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Request;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Resolution;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Settings;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignRests implements CampaignTimeEffects {
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final CampaignSettings settings;
  private final ReactionStore reactions;
  private final EncounterStore encounters;
  private final RestParty party;
  private final EncounterDice random;

  CampaignRests(
      Campaigns campaigns,
      CampaignCommands commands,
      CampaignSettings settings,
      ReactionStore reactions,
      EncounterStore encounters,
      RestParty party,
      EncounterDice random) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.settings = settings;
    this.reactions = reactions;
    this.encounters = encounters;
    this.party = party;
    this.random = random;
  }

  @Transactional(readOnly = true)
  public List<View> characters(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    return party.sheets(campaign, actor);
  }

  @Transactional(readOnly = true)
  public int minimumMinutes(UUID campaign, UUID actor, Kind kind) {
    campaigns.requireDm(campaign, actor);
    var rules = restRules(settings.effective(campaign, actor));
    return kind == Kind.LONG ? rules.longMinutes() : rules.shortMinutes();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PreviewResult preview(UUID campaign, UUID actor, RestInput input) {
    campaigns.requireDm(campaign, actor);
    var clock = commands.readClock(campaign, actor);
    return new PreviewResult(
        prepare(campaign, actor, clock.gameSeconds(), input).preview(), clock.version());
  }

  public record PreviewResult(Preview preview, long version) {}

  public Reply submit(
      UUID campaign, UUID actor, Envelope envelope, RestInput input, boolean confirmWarnings) {
    return commands.run(
        campaign,
        actor,
        envelope,
        CampaignCommands.Kind.REST,
        new Submission(input, confirmWarnings),
        clock -> {
          var prepared = prepare(campaign, actor, clock.gameSeconds(), input);
          if (!confirmWarnings && prepared.preview().requiresConfirmation())
            throw RestInput.invalid("confirmWarnings", "REQUIRED");
          var result =
              RestResolver.resolve(
                  clock.gameSeconds(),
                  prepared.request(),
                  prepared.party(),
                  prepared.rules(),
                  random);
          party.save(prepared.party(), result.participants());
          var report =
              Map.of(
                  "gameTimeSeconds", result.end(),
                  "timeOfDaySeconds",
                      GameClock.timeOfDay(result.end(), prepared.rules().startTimeOfDay()),
                  "events", result.events(),
                  "interruptionOutcome", prepared.preview().interruptionOutcome());
          return change(result, report, prepared.reads(), Set.of());
        });
  }

  private Prepared prepare(UUID campaign, UUID actor, long start, RestInput input) {
    if (reactions.campaignHasWindow(campaign)) throw new CampaignException(Code.REACTION_PENDING);
    if (encounters.hasRunning(campaign)) throw new CampaignException(Code.ENCOUNTER_ACTIVE);
    if (campaigns.requireDm(campaign, actor).sessionState() != SessionState.ACTIVE)
      throw new CampaignException(Code.SESSION_NOT_ACTIVE);
    if (input.durationMinutes() < 1
        || input.durationMinutes() > GameClock.MAX_ADVANCE / 60
        || start > Long.MAX_VALUE - input.durationMinutes() * 60L)
      throw RestInput.invalid("durationMinutes", "OUT_OF_RANGE");

    var effective = settings.effective(campaign, actor);
    var rules = restRules(effective);
    boolean interruptedLong =
        input.interrupted() && input.kind() == Kind.LONG && input.durationMinutes() >= 60;
    boolean shortBenefit =
        input.kind() == Kind.SHORT && !input.interrupted()
            || interruptedLong && rules.interruptedLongRest();
    var request = input.toRequest(input.kind() == Kind.SHORT || interruptedLong);
    var participants = participants(campaign, actor);
    Preview preview;
    try {
      preview = RestResolver.preview(start, request, participants, rules);
    } catch (RestResolver.Rejected failure) {
      if (failure.field() != null) throw RestInput.invalid(failure.field(), failure.fieldCode());
      Code code =
          switch (failure.problem()) {
            case NOT_FOUND -> Code.NOT_FOUND;
            case REST_NOT_ALLOWED -> Code.REST_NOT_ALLOWED;
            case REST_TOO_SHORT -> Code.REST_TOO_SHORT;
            case HIT_DICE_UNAVAILABLE -> Code.HIT_DICE_UNAVAILABLE;
            case INVALID_CHOICES -> Code.VALIDATION_FAILED;
          };
      throw new CampaignException(code, Map.of("participantIds", failure.participants()));
    }
    var reads = new ArrayList<>(List.of("restVariant", "startTimeOfDay", "dawnTime"));
    if ("CUSTOM".equals(effective.value("restVariant")))
      reads.addAll(List.of("shortRestMinutes", "longRestMinutes", "longRestIntervalHours"));
    if (interruptedLong) reads.add("interruptedLongRestGivesShortRest");
    if (shortBenefit
        && request.plans().stream()
            .anyMatch(
                plan ->
                    plan.spendUntil() != null
                        || plan.spendCount() != null && plan.spendCount() > 0))
      reads.add("hitDiceMethod");
    settings.requireDecisions(campaign, actor, reads);
    return new Prepared(request, participants, rules, preview, readValues(effective, reads));
  }

  @Override
  public Change advance(UUID campaign, UUID actor, long start, long seconds) {
    var effective = settings.effective(campaign, actor);
    var rules = restRules(effective);
    var characters = participants(campaign, actor);
    var combat =
        encounters.list(campaign).stream()
            .filter(table -> table.phase() == Phase.ACTIVE || table.phase() == Phase.INITIATIVE)
            .findFirst()
            .orElse(null);
    var npcs =
        combat == null
            ? List.<Entry>of()
            : encounters.entries(combat.id()).stream()
                .filter(entry -> entry.present() && entry.character() == null)
                .toList();
    var clockCreatures = new ArrayList<>(characters);
    npcs.stream().map(CampaignRests::npcTimers).forEach(clockCreatures::add);
    var result = RestResolver.advance(start, seconds, clockCreatures, rules, random);
    party.save(characters, result.participants().subList(0, characters.size()));
    boolean npcChanged = false;
    for (int i = 0; i < npcs.size(); i++) {
      var old = npcs.get(i);
      var next = result.participants().get(characters.size() + i);
      npcChanged |= saveNpcClockEffects(old, next);
    }
    if (npcChanged) encounters.update(combat, Math.incrementExact(combat.version()));
    var ended =
        result.events().stream().filter(event -> event.type().equals("EffectExpired")).toList();
    var report =
        Map.of(
            "gameTimeSeconds",
            result.end(),
            "effectsEnded",
            ended,
            "timeOfDaySeconds",
            GameClock.timeOfDay(result.end(), rules.startTimeOfDay()));
    var npcIds = npcs.stream().map(Entry::id).collect(java.util.stream.Collectors.toSet());
    return change(
        result, report, readValues(effective, List.of("startTimeOfDay", "dawnTime")), npcIds);
  }

  private static Participant npcTimers(Entry npc) {
    var vitals = npc.npcVitals();
    return new Participant(
        npc.id(),
        vitals.health(),
        vitals.health().maximum(),
        0,
        vitals.exhaustion(),
        null,
        vitals.recoveryAt(),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        vitals.effects(),
        vitals.inspiration(),
        0,
        0,
        null,
        vitals.conditions(),
        vitals.recoveryOrder());
  }

  private boolean saveNpcClockEffects(Entry npc, Participant state) {
    var conditions = new java.util.HashSet<>(state.conditions());
    if (npc.npcVitals().health().current() == 0 && state.health().current() > 0)
      conditions.remove(CombatConditions.UNCONSCIOUS);
    var next =
        new Vitals(
            state.health(),
            state.recoveryAt(),
            conditions,
            state.effects(),
            state.exhaustion(),
            state.inspiration(),
            state.recoveryOrder());
    if (next.equals(npc.npcVitals())) return false;
    encounters.update(npc.vitals(next));
    return true;
  }

  @Override
  public Sweep outsideEncounter(
      UUID campaign,
      UUID actor,
      Set<UUID> roster,
      long start,
      long seconds,
      SettingsView pinned,
      ContentSet content,
      RandomSource dice) {
    var before = party.outsideEncounter(campaign, actor, roster, content, pinned);
    var result = RestResolver.advance(start, seconds, before, restRules(pinned), dice);
    var deltas = new ArrayList<Delta>();
    for (int i = 0; i < before.size(); i++) {
      var old = RestParty.clockState(before.get(i));
      var next = RestParty.clockState(result.participants().get(i));
      if (!old.equals(next)) deltas.add(new Delta(before.get(i).id(), old, next));
    }
    return new Sweep(List.copyOf(deltas), result.events());
  }

  @Override
  public void apply(List<Delta> deltas, boolean undo) {
    deltas.forEach(delta -> party.apply(delta, undo));
  }

  private List<Participant> participants(UUID campaign, UUID actor) {
    return party.load(party.sheets(campaign, actor), campaigns.contentFor(campaign, actor));
  }

  private static Change change(
      Resolution result, Object report, Map<String, Object> reads, Set<UUID> npcs) {
    var events =
        result.events().stream()
            .map(
                event -> {
                  var payload = new LinkedHashMap<>(event.data());
                  if (event.character() != null)
                    payload.put(
                        npcs.contains(event.character()) ? "combatantId" : "characterId",
                        event.character());
                  var audience =
                      switch (event.type()) {
                        case "ClockAdvanced",
                            "RestStarted",
                            "RestCompleted",
                            "RestInterrupted",
                            "DawnReached" ->
                            Audience.ALL;
                        default -> Audience.DM;
                      };
                  return new Event(event.type(), event.seconds(), audience, payload);
                })
            .toList();
    return new Change(result.end(), null, Audience.DM, Map.of(), report, reads, events);
  }

  private static Map<String, Object> readValues(SettingsView effective, List<String> keys) {
    var reads = new LinkedHashMap<String, Object>();
    keys.forEach(key -> reads.put(key, effective.value(key)));
    return reads;
  }

  private static Settings restRules(SettingsView effective) {
    int shortMinutes = 60;
    int longMinutes = 480;
    int intervalHours = 24;
    switch (effective.get("restVariant", String.class)) {
      case "STANDARD" -> {}
      case "EPIC_HEROISM" -> {
        shortMinutes = 5;
        longMinutes = 60;
        intervalHours = 1;
      }
      case "GRITTY_REALISM" -> {
        shortMinutes = 480;
        longMinutes = 10080;
        intervalHours = 168;
      }
      case "CUSTOM" -> {
        shortMinutes = Math.toIntExact(effective.get("shortRestMinutes", Long.class));
        longMinutes = Math.toIntExact(effective.get("longRestMinutes", Long.class));
        intervalHours = Math.toIntExact(effective.get("longRestIntervalHours", Long.class));
      }
      default -> throw new IllegalStateException("Unknown rest variant");
    }
    return new Settings(
        shortMinutes,
        longMinutes,
        intervalHours,
        effective.get("interruptedLongRestGivesShortRest", Boolean.class),
        "FIXED".equals(effective.value("hitDiceMethod")),
        LocalTime.parse(effective.get("startTimeOfDay", String.class)).toSecondOfDay(),
        LocalTime.parse(effective.get("dawnTime", String.class)).toSecondOfDay());
  }

  private record Submission(RestInput input, boolean confirmWarnings) {}

  private record Prepared(
      Request request,
      List<Participant> party,
      Settings rules,
      Preview preview,
      Map<String, Object> reads) {}
}
