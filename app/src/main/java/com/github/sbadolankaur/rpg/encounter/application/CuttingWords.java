package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.combat.BardicInspiration;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Disclosure;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Offer;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

final class CuttingWords {
  static final String FEATURE = "srd-5.1:cutting-words";
  static final String POOL = "srd-5.1:bardic-inspiration-uses";
  static final Option OPTION =
      new Option("cutting-words", "Cutting Words", true, "bardic-inspiration", 1);

  private CuttingWords() {}

  static boolean susceptible(Set<String> conditions, Set<String> immunities) {
    return !conditions.contains("srd-5.1:deafened") && !immunities.contains("srd-5.1:charmed");
  }

  static List<Offer> offers(Context context, UUID rollerId, Step step) {
    var roller = context.creature(rollerId);
    if (!susceptible(roller.conditions(), roller.immunities())) return List.of();
    return context.entries.values().stream()
        .filter(Encounters::present)
        .filter(entry -> !rollerId.equals(entry.id()))
        .map(entry -> context.creature(entry.id()))
        .filter(bard -> bard.features().contains(FEATURE) && CombatActions.canReact(bard))
        .filter(
            bard ->
                CombatConditions.canSee(
                    bard.conditions(),
                    roller.conditions(),
                    roller.entry().tactics().hiddenFrom(bard.entry().id())))
        .filter(
            bard ->
                bard.resources().containsKey(POOL)
                    && bard.resources().get(POOL) < bard.resourceLimit(POOL)
                    && !context.alwaysPass(bard, FEATURE))
        .map(bard -> new Offer(step, bard.entry().id(), bard.initiative(), 0, OPTION))
        .toList();
  }

  static RollResult lowerRoll(
      Context context,
      UUID roller,
      Point point,
      RollResult base,
      Function<RollResult, Boolean> success) {
    Step step = point == Point.AFTER_DAMAGE_ROLL ? Step.DEFENDING_DAMAGE : Step.DEFENDING_RESULT;
    if (offers(context, roller, step).isEmpty()) return base;
    String mode = context.setting("rollDecisionInfo", String.class);
    var result = new RollResult[] {base};
    context
        .reactions()
        .windowWithDisclosure(
            point,
            () -> offers(context, roller, step),
            () -> Disclosure.forRoll(mode, result[0], success.apply(result[0])),
            (id, option) -> {
              var bard = context.creature(id);
              context.reveal(
                  id, bard.owner(), Disclosure.forRoll(mode, result[0], success.apply(result[0])));
              context.resource(bard, POOL, bard.resources().get(POOL) + 1);
              context.update(bard.entry().tactics(bard.entry().tactics().spendReaction()));
              var penalty = die(context.random(), bard.classLevel("srd-5.1:bard"));
              context.dice("Cutting Words", id, penalty);
              result[0] = result[0].withBonus(penalty);
              context.dm("ReactionUsed", Map.of("deciderId", id, "optionKey", option));
            });
    return result[0];
  }

  static RollResult die(com.github.sbadolankaur.rpg.engine.dice.RandomSource random, int level) {
    int faces = BardicInspiration.grant(level, 0).dieFaces();
    return RollPlan.builder()
        .penaltyDice("Cutting Words", FEATURE, DiceExpression.parse("1d" + faces))
        .build()
        .roll(random);
  }

  static void lowerDamage(Context context, UUID roller, Map<String, Integer> damage) {
    if (offers(context, roller, Step.DEFENDING_DAMAGE).isEmpty()) return;
    int total = damage.values().stream().mapToInt(value -> Math.max(0, value)).sum();
    var base = new RollResult(List.of(), total, null, RollMode.NORMAL);
    int reduction =
        total - lowerRoll(context, roller, Point.AFTER_DAMAGE_ROLL, base, roll -> null).total();
    for (var entry : damage.entrySet()) {
      int applied = Math.min(reduction, Math.max(0, entry.getValue()));
      entry.setValue(entry.getValue() - applied);
      reduction -= applied;
      if (reduction == 0) break;
    }
  }
}
