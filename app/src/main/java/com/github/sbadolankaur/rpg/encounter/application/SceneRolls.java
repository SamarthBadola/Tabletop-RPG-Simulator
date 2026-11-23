package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Audience;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Change;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Clock;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Kind;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignSettings;
import com.github.sbadolankaur.rpg.campaign.application.CampaignView;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.persistence.CommandStore;
import com.github.sbadolankaur.rpg.campaign.persistence.PlayStore;
import com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore;
import com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore.Window;
import com.github.sbadolankaur.rpg.character.application.CharacterSheets.View;
import com.github.sbadolankaur.rpg.character.application.Characters;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore.CombatMetadata;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.AnswerKind;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.ReactionAnswer;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.ReactionView;
import com.github.sbadolankaur.rpg.encounter.persistence.EncounterStore;
import com.github.sbadolankaur.rpg.engine.combat.BardicInspiration;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.Exhaustion;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.D20Test;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Continuation;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Disclosure;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Offer;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SceneRolls {
  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SceneRolls.class);
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final CampaignSettings settings;
  private final PlayStore scenes;
  private final CharacterStore store;
  private final Characters characters;
  private final EncounterStore encounters;
  private final ReactionStore reactions;
  private final CommandStore receipts;
  private final EncounterDice random;
  private final JsonMapper json;
  private final MeterRegistry metrics;

  SceneRolls(
      Campaigns campaigns,
      CampaignCommands commands,
      CampaignSettings settings,
      PlayStore scenes,
      CharacterStore store,
      Characters characters,
      EncounterStore encounters,
      ReactionStore reactions,
      CommandStore receipts,
      EncounterDice random,
      JsonMapper json,
      MeterRegistry metrics) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.settings = settings;
    this.scenes = scenes;
    this.store = store;
    this.characters = characters;
    this.encounters = encounters;
    this.reactions = reactions;
    this.receipts = receipts;
    this.random = random;
    this.json = json;
    this.metrics = metrics;
  }

  public Reply start(UUID campaign, UUID scene, UUID actor, Envelope envelope, Test request) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.SCENE_TEST,
        new SceneTest(scene, request),
        clock -> {
          if (reactions.forScene(campaign, scene).isPresent())
            throw new CampaignException(Code.REACTION_PENDING);
          if (scenes.scenes(campaign).stream().noneMatch(item -> scene.equals(item.id())))
            throw new CampaignException(Code.NOT_FOUND);
          if (scenes
              .latestSession(campaign)
              .filter(session -> session.state() == CampaignView.SessionState.ACTIVE)
              .isEmpty()) throw new CampaignException(Code.SESSION_NOT_ACTIVE);
          if (encounters.list(campaign).stream()
              .anyMatch(
                  table ->
                      switch (table.phase()) {
                        case INITIATIVE, ACTIVE -> true;
                        default -> false;
                      })) throw new CampaignException(Code.ENCOUNTER_ACTIVE);

          var subjects =
              request.subjectIds().stream()
                  .map(id -> subject(campaign, actor, id, request, clock.gameSeconds()))
                  .toList();
          var bards =
              request.save()
                  ? List.<Bard>of()
                  : characters.list(campaign, actor).stream()
                      .filter(
                          sheet ->
                              sheet.features().stream()
                                  .anyMatch(feature -> CuttingWords.FEATURE.equals(feature.key())))
                      .map(sheet -> bard(sheet, clock.gameSeconds()))
                      .filter(java.util.Objects::nonNull)
                      .filter(bard -> subjects.stream().anyMatch(subject -> canCut(bard, subject)))
                      .toList();
          var frozen = new java.util.LinkedHashSet<>(request.subjectIds());
          bards.forEach(bard -> frozen.add(bard.id()));
          if (frozen.stream().anyMatch(id -> reactions.protectsCharacter(campaign, id)))
            throw new CampaignException(Code.REACTION_PENDING);
          var keys = new ArrayList<String>();
          if (request.dc() != null && subjects.stream().anyMatch(subject -> !subject.forced()))
            keys.add("autoOutcomeOnNat20Checks");
          if (subjects.stream()
                  .anyMatch(subject -> !subject.forced() && subject.inspiration() != null)
              || !bards.isEmpty()) keys.add("rollDecisionInfo");
          settings.requireDecisions(campaign, actor, keys);
          var effective = settings.effective(campaign, actor);
          var read = new LinkedHashMap<String, String>();
          keys.forEach(key -> read.put(key, effective.get(key, String.class)));
          var saved =
              new Saved(
                  request, List.copyOf(frozen), subjects, read, Continuation.empty(), 0, bards);
          return resolve(campaign, scene, envelope, clock, saved, null, null);
        });
  }

  public boolean owns(UUID campaign, UUID window, UUID action) {
    if (reactions.findSceneWindow(campaign, window).isPresent()) return true;
    return receipts
        .action(campaign, action)
        .map(receipt -> json.readTree(receipt.result()))
        .filter(result -> result.has("sceneId"))
        .filter(result -> window.toString().equals(result.path("reactionWindowId").asString()))
        .isPresent();
  }

  public Reply answer(
      UUID campaign, UUID windowId, UUID actor, Envelope envelope, ReactionAnswer answer) {
    if (!campaigns.isMember(campaign, actor)
        || campaigns.requireMember(campaign, actor).role() != CampaignView.Role.DM) {
      metrics.counter("reaction.denied").increment();
      LOG.warn("reaction.denied campaign={} window={} principal={}", campaign, windowId, actor);
      throw new CampaignException(Code.REACTION_NOT_PERMITTED);
    }
    try {
      return commands.run(
          campaign,
          actor,
          envelope,
          Kind.SCENE_REACTION,
          new SceneAnswer(windowId, answer),
          clock -> {
            var window =
                reactions
                    .findSceneWindow(campaign, windowId)
                    .orElseThrow(() -> new CampaignException(Code.REACTION_OPTION_INVALID));
            if (answer.kind() == AnswerKind.CANCEL) {
              if (answer.reason() != null
                  && answer.reason().codePointCount(0, answer.reason().length()) > 200)
                throw EncounterCommands.invalid();
              reactions.close(window);
              var result = new LinkedHashMap<String, Object>();
              result.put("sceneId", window.scene());
              result.put("reactionWindowId", window.id());
              result.put("outcome", "cancelled");
              if (answer.reason() != null) result.put("reason", answer.reason());
              return Change.at(clock, "ActionCancelled", Audience.DM, result, result);
            }
            var prompt = window.prompt();
            if (!prompt.decider().equals(answer.deciderId())
                || prompt.followUp() != (answer.kind() == AnswerKind.FOLLOW_UP)
                || !prompt.permits(answer.optionKey()))
              throw new CampaignException(Code.REACTION_OPTION_INVALID);
            return resolve(
                campaign,
                window.scene(),
                envelope,
                clock,
                json.treeToValue(window.continuation(), Saved.class),
                window,
                answer.optionKey());
          });
    } catch (CampaignException error) {
      if (error.code() == Code.CAMPAIGN_VERSION_CONFLICT)
        throw new CampaignException(Code.ENCOUNTER_VERSION_CONFLICT);
      throw error;
    }
  }

  @Transactional(readOnly = true)
  public SceneView view(UUID campaign, UUID scene, UUID actor) {
    campaigns.requireDm(campaign, actor);
    var location =
        scenes.scenes(campaign).stream()
            .filter(item -> scene.equals(item.id()))
            .findFirst()
            .orElseThrow(() -> new CampaignException(Code.NOT_FOUND));
    return new SceneView(
        location.name(),
        commands.readClock(campaign, actor).version(),
        reactions.forScene(campaign, scene).map(ReactionView::of).orElse(null),
        characters.list(campaign, actor).stream()
            .map(sheet -> new Participant(sheet.id(), sheet.name()))
            .toList());
  }

  private Subject subject(UUID campaign, UUID actor, UUID id, Test request, long seconds) {
    var sheet = characters.sheet(campaign, actor, id);
    var metadata = store.combatMetadata(id);
    var conditions = conditions(sheet, metadata, seconds);
    var ability = sheet.abilities().get(request.ability());
    boolean forced =
        request.save() && CombatConditions.forcedSaveFailure(conditions, request.ability());
    boolean disadvantage =
        request.save()
            ? Exhaustion.disadvantagesAttacksAndSaves(metadata.exhaustion())
            : Exhaustion.disadvantagesChecks(metadata.exhaustion());
    disadvantage |=
        request.save()
            && request.ability() == Ability.DEX
            && conditions.contains("srd-5.1:restrained");
    int bonus =
        request.save()
            ? ability.save()
            : request.skill() == null
                ? ability.modifier()
                    + (sheet.features().stream()
                            .anyMatch(feature -> "srd-5.1:jack-of-all-trades".equals(feature.key()))
                        ? sheet.proficiency() / 2
                        : 0)
                : sheet.skills().getOrDefault(request.skill(), ability.modifier());
    var die = metadata.inspiration();
    return new Subject(
        id,
        bonus,
        forced,
        disadvantage,
        die != null && die.active(seconds) ? die : null,
        conditions);
  }

  private Bard bard(View sheet, long seconds) {
    var uses =
        sheet.resources().stream()
            .filter(resource -> CuttingWords.POOL.equals(resource.key()))
            .findFirst()
            .orElse(null);
    if (uses == null
        || uses.used() >= uses.maximum()
        || store.alwaysPassReaction(sheet.id(), CuttingWords.FEATURE)) return null;
    var conditions = conditions(sheet, store.combatMetadata(sheet.id()), seconds);
    if (CombatConditions.incapacitated(conditions) || conditions.contains("srd-5.1:turned"))
      return null;
    int level =
        sheet.classes().stream()
            .filter(classLine -> "srd-5.1:bard".equals(classLine.key()))
            .findFirst()
            .orElseThrow()
            .level();
    return new Bard(sheet.id(), level, uses.used(), conditions);
  }

  private static Set<String> conditions(View sheet, CombatMetadata metadata, long seconds) {
    var conditions = new HashSet<>(sheet.baseConditionKeys());
    for (var condition : metadata.effects()) {
      if (!condition.end().expired(seconds, 0, null, ConditionInstance.Boundary.CLOCK))
        conditions.add(condition.key());
    }
    if (sheet.currentHp() == 0) conditions.add(CombatConditions.UNCONSCIOUS);
    return Set.copyOf(conditions);
  }

  private static boolean canCut(Bard bard, Subject subject) {
    return !bard.id().equals(subject.id())
        && !subject.forced()
        && CuttingWords.susceptible(subject.conditions(), Set.of())
        && CombatConditions.canSee(bard.conditions(), subject.conditions(), false);
  }

  private Change resolve(
      UUID campaign,
      UUID scene,
      Envelope envelope,
      Clock clock,
      Saved saved,
      Window previous,
      String option) {
    var audit = new ArrayList<SceneEvent>();
    var continuation =
        previous == null ? saved.replay() : saved.replay().answer(previous.prompt(), option);
    var outcome =
        ReactionReplay.run(
            saved.test().save() ? "save" : "check",
            continuation,
            random,
            replay -> test(saved, replay, audit));
    var payload = new LinkedHashMap<String, Object>();
    payload.put("sceneId", scene);
    payload.put("events", List.copyOf(audit.subList(saved.audited(), audit.size())));
    if (previous != null) {
      payload.put("reactionWindowId", previous.id());
      payload.put(
          "decision",
          Map.of(
              "deciderId",
              previous.prompt().decider(),
              "choice",
              option == null ? "pass" : option));
    }
    if (outcome.pending() == null) {
      outcome.completed().consumed().forEach(id -> store.setBardicInspiration(id, null));
      outcome
          .completed()
          .spent()
          .forEach((id, used) -> store.resource(id, CuttingWords.POOL, used));
      if (previous != null) reactions.close(previous);
      payload.put("outcomes", outcome.completed().results());
      return Change.at(clock, "SceneTestResolved", Audience.DM, payload, payload)
          .withSettingsRead(Map.copyOf(saved.settings()));
    }

    var now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID id = previous == null ? ServerIds.at(now.toInstant()) : previous.id();
    var next =
        new Saved(
            saved.test(),
            saved.subjectIds(),
            saved.subjects(),
            saved.settings(),
            outcome.continuation(),
            audit.size(),
            saved.bards());
    var window =
        new Window(
            id,
            campaign,
            null,
            previous == null ? envelope.actionId() : previous.openedBy(),
            outcome.pending(),
            json.valueToTree(next),
            clock.version() + 1,
            previous == null ? now : previous.createdAt(),
            scene);
    if (previous == null) reactions.open(window);
    else reactions.advance(previous, window);
    payload.put("reactionWindowId", id);
    var result =
        Map.of(
            "sceneId",
            scene,
            "reactionWindowId",
            id,
            "awaitingReaction",
            id,
            "reaction",
            ReactionView.of(window));
    return Change.at(clock, "ReactionOpened", Audience.DM, payload, result)
        .withSettingsRead(Map.copyOf(saved.settings()));
  }

  private static Completed test(Saved saved, ReactionReplay replay, List<SceneEvent> audit) {
    var outcomes = new ArrayList<TestResult>();
    var consumed = new ArrayList<UUID>();
    var spent = new LinkedHashMap<UUID, Integer>();
    for (var subject : saved.subjects()) {
      if (subject.forced()) {
        outcomes.add(new TestResult(subject.id(), null, true, false));
        continue;
      }
      var roll =
          CombatRolls.d20(
              "Ability modifier",
              subject.bonus(),
              subject.disadvantage() ? RollMode.DISADVANTAGE : RollMode.NORMAL,
              replay.random());
      audit.add(new SceneEvent("DiceRolled", subject.id(), roll));
      if (subject.inspiration() != null) {
        var disclosure =
            Disclosure.forRoll(saved.settings().get("rollDecisionInfo"), roll, result(saved, roll));
        audit.add(new SceneEvent("ReactionRollRevealed", subject.id(), disclosure));
        String answer =
            replay.choose(
                saved.test().save() ? Point.AFTER_SAVE_ROLL : Point.AFTER_CHECK_ROLL,
                Step.ACTING_RESULT,
                subject.id(),
                List.of(CombatFeatures.INSPIRATION_OPTION),
                disclosure);
        if (answer != null) {
          var bonus = subject.inspiration().roll(replay.random());
          audit.add(new SceneEvent("DiceRolled", subject.id(), bonus));
          roll = roll.withBonus(bonus);
          consumed.add(subject.id());
        }
      }
      if (!saved.test().save() && !saved.bards().isEmpty()) {
        var current = new RollResult[] {roll};
        var bards = saved.bards().stream().filter(bard -> canCut(bard, subject)).toList();
        String mode = saved.settings().get("rollDecisionInfo");
        replay.windowWithDisclosure(
            Point.AFTER_CHECK_ROLL,
            () ->
                bards.stream()
                    .filter(bard -> !spent.containsKey(bard.id()))
                    .map(
                        bard ->
                            new Offer(Step.DEFENDING_RESULT, bard.id(), 0, 0, CuttingWords.OPTION))
                    .toList(),
            () -> Disclosure.forRoll(mode, current[0], result(saved, current[0])),
            (id, option) -> {
              var bard =
                  bards.stream()
                      .filter(candidate -> candidate.id().equals(id))
                      .findFirst()
                      .orElseThrow();
              audit.add(
                  new SceneEvent(
                      "ReactionRollRevealed",
                      id,
                      Disclosure.forRoll(mode, current[0], result(saved, current[0]))));
              var penalty = CuttingWords.die(replay.random(), bard.level());
              audit.add(new SceneEvent("DiceRolled", id, penalty));
              current[0] = current[0].withBonus(penalty);
              spent.put(id, bard.used() + 1);
            });
        roll = current[0];
      }
      outcomes.add(new TestResult(subject.id(), roll, false, result(saved, roll)));
    }
    return new Completed(outcomes, consumed, spent);
  }

  private static Boolean result(Saved saved, RollResult roll) {
    if (saved.test().dc() == null) return null;
    var natural = D20Test.NaturalMode.valueOf(saved.settings().get("autoOutcomeOnNat20Checks"));
    return D20Test.resolve(
            roll,
            saved.test().dc(),
            saved.test().save() ? D20Test.Kind.SAVING_THROW : D20Test.Kind.ABILITY_CHECK,
            natural)
        .success();
  }

  public record Test(
      Ability ability, String skill, Integer dc, boolean save, List<UUID> subjectIds) {
    public Test {
      subjectIds = List.copyOf(subjectIds);
      if (ability == null
          || subjectIds.isEmpty()
          || subjectIds.size() > 30
          || new HashSet<>(subjectIds).size() != subjectIds.size()
          || save && (dc == null || skill != null)
          || dc != null && (dc < 1 || dc > 40)) throw EncounterCommands.invalid();
    }
  }

  public record SceneView(
      String name, long version, ReactionView reaction, List<Participant> subjects) {}

  public record Participant(UUID id, String name) {}

  private record SceneTest(UUID sceneId, Test test) {}

  private record SceneAnswer(UUID windowId, ReactionAnswer answer) {}

  private record Subject(
      UUID id,
      int bonus,
      boolean forced,
      boolean disadvantage,
      BardicInspiration inspiration,
      Set<String> conditions) {
    Subject {
      conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
    }
  }

  private record Bard(UUID id, int level, int used, Set<String> conditions) {}

  private record Saved(
      Test test,
      List<UUID> subjectIds,
      List<Subject> subjects,
      Map<String, String> settings,
      Continuation replay,
      int audited,
      List<Bard> bards) {
    Saved {
      bards = bards == null ? List.of() : List.copyOf(bards);
    }
  }

  private record TestResult(
      UUID subjectId, RollResult roll, boolean automaticFailure, Boolean success) {}

  private record Completed(
      List<TestResult> results, List<UUID> consumed, Map<UUID, Integer> spent) {}

  private record SceneEvent(String type, UUID subjectId, Object data) {}
}
