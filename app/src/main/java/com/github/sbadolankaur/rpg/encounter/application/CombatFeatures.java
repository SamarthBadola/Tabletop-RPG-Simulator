package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.affliction.Affliction;
import com.github.sbadolankaur.rpg.engine.combat.BardicInspiration;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.combat.TurnBudget;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Disclosure;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CombatFeatures {
  public static final String ACTION_SURGE = "srd-5.1:action-surge";
  public static final String BARDIC_INSPIRATION = "srd-5.1:bardic-inspiration";
  public static final String LAY_ON_HANDS = "srd-5.1:lay-on-hands";

  private CombatFeatures() {}

  static void use(Context context, Use form) {
    var actor = context.canAct(form.actorId());
    var feature =
        context
            .content
            .find("feature", form.featureKey())
            .filter(value -> actor.features().contains(value.key()))
            .orElseThrow(() -> new CampaignException(Code.FEATURE_NOT_AVAILABLE));
    if (!(feature.fields().get("cost") instanceof Map<?, ?> cost)
        || !(cost.get("resource") instanceof String pool)
        || !actor.resources().containsKey(pool))
      throw new CampaignException(Code.FEATURE_NOT_AVAILABLE);
    int used = actor.resources().get(pool);
    int remaining = actor.resourceLimit(pool) - used;
    if (remaining <= 0 && !LAY_ON_HANDS.equals(feature.key()))
      throw new CampaignException(Code.FEATURE_USES_EXHAUSTED);

    int spent =
        switch (feature.key()) {
          case ACTION_SURGE -> {
            CombatActions.spend(context, TurnBudget::surge);
            yield 1;
          }
          case BARDIC_INSPIRATION -> {
            inspire(context, actor, form.targetId());
            yield 1;
          }
          case LAY_ON_HANDS -> layOnHands(context, form, remaining);
          default -> throw new CampaignException(Code.FEATURE_NOT_AVAILABLE);
        };
    context.resource(actor, pool, used + spent);
    context.dm(
        "FeatureUsed",
        Map.of(
            "actorId",
            form.actorId(),
            "featureKey",
            feature.key(),
            "resourceKey",
            pool,
            "usesRemaining",
            remaining - spent));
    context.result("actorId", form.actorId());
    context.result("action", "feature-use");
    context.result("outcome", "used");
  }

  private static int layOnHands(Context context, Use form, int remaining) {
    UUID id = form.targetId();
    if (id == null
        || !context.entries.containsKey(id)
        || !Encounters.present(context.entries.get(id))) throw invalidInput("targetId");
    var target = context.creature(id);
    String type = context.creatureType(target);
    if ("undead".equals(type)
        || "construct".equals(type)
        || target.vitals().health().state() == State.DEAD) throw invalidInput("targetId");
    if (form.healPoints() == null || form.healPoints() < 0) throw invalidInput("healPoints");
    long cost = form.healPoints() + 5L * form.cureIds().size();
    if (cost < 1 || cost > remaining) throw invalidInput("healPoints");
    var instances = context.afflictions(id);
    var cures = new ArrayList<Affliction>();
    for (UUID selected : form.cureIds()) {
      var instance = instances.get(selected);
      if (instance == null || !instance.canLayOnHandsCure(context.seconds))
        throw invalidInput("cureIds");
      cures.add(instance);
    }

    CombatActions.spend(context, TurnBudget::action);
    var health = target.vitals().health();
    if (form.healPoints() > 0) {
      health = health.heal(form.healPoints());
      context.health(target, health);
      if (target.owner() != null)
        context.tell(
            "OwnHitPointsChanged",
            target.owner(),
            target.name() + " now has " + health.current() + " hit points.");
    }
    for (var instance : cures) {
      context.affliction(id, instance.cure(context.seconds));
      CombatEffects.removeAfflictionEffects(context, context.creature(id), instance.id());
      context.dm(
          "AfflictionEnded",
          Map.of("combatantId", id, "afflictionId", instance.id(), "reason", "CURED"));
    }
    context.result("targetId", id);
    context.result("hitPointsAfter", health);
    context.result("cured", form.cureIds());
    return (int) cost;
  }

  private static void inspire(Context context, Creature actor, UUID targetId) {
    if (targetId == null
        || targetId.equals(actor.entry().id())
        || !context.entries.containsKey(targetId)) throw invalidInput("targetId");
    var target = CombatActions.target(context, targetId);
    var held = target.vitals().inspiration();
    if (held != null && held.active(context.seconds)) throw invalidInput("targetId");
    CombatActions.spend(context, TurnBudget::bonusAction);
    var die = BardicInspiration.grant(actor.classLevel("srd-5.1:bard"), context.seconds);
    context.vitals(target, target.vitals().inspiration(die));
    context.dm(
        "BardicInspirationGranted",
        Map.of(
            "actorId",
            actor.entry().id(),
            "targetId",
            targetId,
            "dieFaces",
            die.dieFaces(),
            "endsAt",
            die.endsAt()));
  }

  static RollResult inspireRoll(
      Context context, Creature creature, RollResult roll, Point point, Boolean success) {
    var die = creature.vitals().inspiration();
    if (die == null || !die.active(context.seconds)) return roll;
    var disclosure =
        switch (context.setting("rollDecisionInfo", String.class)) {
          case "TOTAL_SHOWN" -> new Disclosure(null, roll.total(), null);
          case "NATURAL_DIE_ONLY" -> new Disclosure(roll.naturalD20(), null, null);
          case "OUTCOME_SHOWN" -> new Disclosure(roll.naturalD20(), roll.total(), success);
          default -> throw new IllegalStateException("Unknown roll disclosure mode");
        };
    UUID decider = creature.entry().id();
    context.reveal(decider, creature.owner(), disclosure);
    String option =
        context
            .reactions()
            .choose(
                point,
                Step.ACTING_RESULT,
                decider,
                List.of(new Option("bardic-inspiration", "Use Bardic Inspiration", false, null, 0)),
                disclosure);
    if (option == null) return roll;
    var bonus =
        RollPlan.builder()
            .dice(
                "Bardic Inspiration",
                BARDIC_INSPIRATION,
                DiceExpression.parse("1d" + die.dieFaces()),
                false)
            .build()
            .roll(context.random());
    context.dice("Bardic Inspiration", decider, bonus);
    var current = context.creature(decider);
    context.vitals(current, current.vitals().inspiration(null));
    return roll.withBonus(bonus);
  }

  public static CampaignException invalidInput(String field) {
    return new CampaignException(Code.FEATURE_INPUT_INVALID, Map.of("field", field));
  }

  public record Use(
      UUID actorId, String featureKey, UUID targetId, Integer healPoints, List<UUID> cureIds) {
    public Use {
      if (cureIds.size() > 20 || cureIds.stream().distinct().count() != cureIds.size())
        throw invalidInput("cureIds");
      cureIds = cureIds.stream().sorted().toList();
    }
  }
}
