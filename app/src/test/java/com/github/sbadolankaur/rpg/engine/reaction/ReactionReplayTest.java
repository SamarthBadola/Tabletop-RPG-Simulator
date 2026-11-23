package com.github.sbadolankaur.rpg.engine.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Continuation;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReactionReplayTest {
  private static final UUID ATTACKER = new UUID(1, 1);
  private static final UUID DEFENDER = new UUID(1, 2);
  private static final Option INSPIRE =
      new Option("inspire", "Bardic Inspiration", false, "inspiration", 1);
  private static final Option DODGE = new Option("dodge", "Uncanny Dodge", true, null, 0);
  private static final Option REROLL = new Option("reroll", "Reroll damage", false, null, 0);

  @Test
  void replayUsesOldFacesInOrderAndDrawsOnlyWhenTheDecisionNeedsMore() {
    var dice = new ForcedDice(14);
    var first = ReactionReplay.run("Attack", Continuation.empty(), dice, this::inspiredAttack);
    dice.assertExhausted();
    assertThat(first.pending().point()).isEqualTo(Point.AFTER_ATTACK_ROLL);
    assertThat(first.pending().options()).containsExactly(INSPIRE);

    var bonus = new ForcedDice(3);
    var resume = first.continuation().answer(first.pending(), "inspire");
    var complete = ReactionReplay.run("Attack", resume, bonus, this::inspiredAttack);
    bonus.assertExhausted();
    assertThat(complete.completed()).isEqualTo(20);
    assertThat(complete.pending()).isNull();
    assertThat(complete.continuation().draws())
        .extracting(draw -> draw.value() + 1)
        .containsExactly(14, 3);

    var pass = first.continuation().answer(first.pending(), null);
    var without = ReactionReplay.run("Attack", pass, new ForcedDice(), this::inspiredAttack);
    assertThat(without.completed()).isEqualTo(17);
  }

  @Test
  void followUpIsMandatoryAndReplaysBothOriginalAndReplacement() {
    var first = ReactionReplay.run("Attack", Continuation.empty(), new ForcedDice(2), this::damage);
    var next =
        ReactionReplay.run(
            "Attack",
            first.continuation().answer(first.pending(), "reroll"),
            new ForcedDice(5),
            this::damage);
    assertThat(next.pending().followUp()).isTrue();
    assertThat(next.pending().step()).isEqualTo(first.pending().step());
    assertThatThrownBy(() -> next.continuation().answer(next.pending(), null))
        .isInstanceOf(IllegalArgumentException.class);
    var result =
        ReactionReplay.run(
            "Attack",
            next.continuation().answer(next.pending(), "original"),
            new ForcedDice(),
            this::damage);
    assertThat(result.completed()).isEqualTo(2);
  }

  @Test
  void anEmptyOfferDoesNotPauseAndNestedReactionsStopAfterSix() {
    var decisions = Continuation.empty();
    for (int depth = 1; depth <= 6; depth++) {
      var pending = ReactionReplay.run("Move", decisions, new ForcedDice(), this::nested);
      assertThat(pending.pending().frames()).hasSize(depth);
      decisions = pending.continuation().answer(pending.pending(), "dodge");
    }
    var complete = ReactionReplay.run("Move", decisions, new ForcedDice(), this::nested);
    assertThat(complete.completed()).isEqualTo(6);
    assertThat(complete.depthLimits()).containsExactly(Point.ON_MOVE_STEP);

    var noOffers =
        ReactionReplay.run(
            "Move",
            Continuation.empty(),
            new ForcedDice(),
            replay ->
                replay.choose(
                    Point.ON_MOVE_STEP, Step.DEFENDING_RESULT, DEFENDER, List.of(), null));
    assertThat(noOffers.pending()).isNull();
    assertThat(noOffers.depthLimits()).isEmpty();
  }

  @Test
  void aCorruptReplayCannotSilentlyChangeTheDieSizeOrThePrompt() {
    var first =
        ReactionReplay.run(
            "Attack", Continuation.empty(), new ForcedDice(14), this::inspiredAttack);
    assertThatThrownBy(
            () ->
                ReactionReplay.run(
                    "Attack",
                    first.continuation(),
                    new ForcedDice(),
                    replay -> replay.random().nextInt(6)))
        .isInstanceOf(IllegalStateException.class);
    var answered = first.continuation().answer(first.pending(), "inspire");
    assertThatThrownBy(
            () ->
                ReactionReplay.run(
                    "Attack",
                    answered,
                    new ForcedDice(),
                    replay -> {
                      replay.random().nextInt(20);
                      return replay.choose(
                          Point.AFTER_ATTACK_ROLL,
                          Step.ACTING_RESULT,
                          DEFENDER,
                          List.of(INSPIRE),
                          null);
                    }))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void everyStepReevaluatesEligibleOptionsAndOneReactionDoesNotSpendFreeOptions() {
    var script = Continuation.empty();
    var first = ReactionReplay.run("Attack", script, new ForcedDice(), this::ordered);
    assertThat(first.pending().step()).isEqualTo(Step.DEFENDING_RESULT);
    script = first.continuation().answer(first.pending(), "shield");
    var second = ReactionReplay.run("Attack", script, new ForcedDice(), this::ordered);
    assertThat(second.pending().step()).isEqualTo(Step.ACTING_DAMAGE);
    script = second.continuation().answer(second.pending(), "smite");
    var done = ReactionReplay.run("Attack", script, new ForcedDice(), this::ordered);
    assertThat(done.completed()).containsExactly("shield", "smite");

    var pass = first.continuation().answer(first.pending(), null);
    var smite = ReactionReplay.run("Attack", pass, new ForcedDice(), this::ordered);
    var later =
        ReactionReplay.run(
            "Attack",
            smite.continuation().answer(smite.pending(), null),
            new ForcedDice(),
            this::ordered);
    assertThat(later.pending().step()).isEqualTo(Step.DEFENDING_DAMAGE);
    assertThat(later.pending().options()).containsExactly(DODGE);
  }

  private int inspiredAttack(ReactionReplay replay) {
    int base = replay.random().nextInt(20) + 4;
    var option =
        replay.choose(
            Point.AFTER_ATTACK_ROLL, Step.ACTING_RESULT, ATTACKER, List.of(INSPIRE), null);
    return option == null ? base : base + replay.random().nextInt(6) + 1;
  }

  private int damage(ReactionReplay replay) {
    int original = replay.random().nextInt(6) + 1;
    var option =
        replay.choose(Point.AFTER_DAMAGE_ROLL, Step.ACTING_DAMAGE, ATTACKER, List.of(REROLL), null);
    if (option == null) return original;
    int replacement = replay.random().nextInt(6) + 1;
    var selected =
        replay.followUp(
            Point.AFTER_DAMAGE_ROLL,
            Step.ACTING_DAMAGE,
            ATTACKER,
            List.of(
                new Option("original", "Keep " + original, false, null, 0),
                new Option("new", "Keep " + replacement, false, null, 0)));
    return selected.equals("original") ? original : replacement;
  }

  private int nested(ReactionReplay replay) {
    var option =
        replay.choose(Point.ON_MOVE_STEP, Step.DEFENDING_RESULT, DEFENDER, List.of(DODGE), null);
    return option == null ? 0 : 1 + replay.nested("Opportunity attack", () -> nested(replay));
  }

  private List<String> ordered(ReactionReplay replay) {
    var resolved = new ArrayList<String>();
    var shield = new Option("shield", "Shield", true, "slot", 1);
    var smite = new Option("smite", "Divine Smite", false, "slot", 1);
    replay.window(
        Point.AFTER_HIT,
        () -> {
          var options = new ArrayList<ReactionReplay.Offer>();
          if (!resolved.contains("shield")) {
            options.add(new ReactionReplay.Offer(Step.DEFENDING_RESULT, DEFENDER, 15, 0, shield));
            options.add(new ReactionReplay.Offer(Step.DEFENDING_DAMAGE, DEFENDER, 15, 0, DODGE));
          }
          options.add(new ReactionReplay.Offer(Step.ACTING_DAMAGE, ATTACKER, 20, 0, smite));
          return options;
        },
        null,
        (decider, value) -> resolved.add(value));
    return List.copyOf(resolved);
  }
}
