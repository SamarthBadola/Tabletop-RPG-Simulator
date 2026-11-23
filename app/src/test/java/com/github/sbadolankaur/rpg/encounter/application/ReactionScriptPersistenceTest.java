package com.github.sbadolankaur.rpg.encounter.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Continuation;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReactionScriptPersistenceTest {
  private static final Option BOOST = new Option("boost", "Spend a use", false, "pool", 1);
  private static final UUID DECIDER = new UUID(0, 1);
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void serializedPausesMatchOnePassForGeneratedRollsAndDecisions() {
    for (int seed = 0; seed < 64; seed++) {
      var selector = new Random(seed);
      var choices = List.of(selector.nextBoolean(), selector.nextBoolean(), selector.nextBoolean());
      var expected = uninterrupted(seed, choices);
      assertThat(paused(seed, choices)).isEqualTo(expected);
      assertThat(paused(seed, choices)).isEqualTo(paused(seed, choices));
    }
  }

  private List<Event> paused(long seed, List<Boolean> choices) {
    var saved = Continuation.empty();
    for (int position = 0; ; position++) {
      var fresh = new Random(seed);
      for (var draw : saved.draws())
        assertThat(fresh.nextInt(draw.bound())).isEqualTo(draw.value());
      var result =
          ReactionReplay.run(
              "generated", saved, fresh::nextInt, replay -> evaluate(replay, choices.size()));
      if (result.pending() == null) return result.completed();
      assertThat(result.pending().sequence()).isEqualTo(position);
      var answered =
          result
              .continuation()
              .answer(result.pending(), choices.get(position) ? BOOST.key() : null);
      saved = json.readValue(json.writeValueAsBytes(answered), Continuation.class);
    }
  }

  private static List<Event> uninterrupted(long seed, List<Boolean> choices) {
    var dice = new Random(seed);
    var events = new ArrayList<Event>();
    for (boolean use : choices) {
      var d20 = dice.nextInt(20) + 1;
      int bonus = use ? dice.nextInt(6) + 1 : 0;
      events.add(new Event(d20, bonus, use ? 1 : 0));
    }
    return events;
  }

  private static List<Event> evaluate(ReactionReplay replay, int count) {
    var events = new ArrayList<Event>();
    for (int i = 0; i < count; i++) {
      int base = replay.random().nextInt(20) + 1;
      var answer =
          replay.choose(Point.AFTER_CHECK_ROLL, Step.ACTING_RESULT, DECIDER, List.of(BOOST), null);
      int bonus = answer == null ? 0 : replay.random().nextInt(6) + 1;
      events.add(new Event(base, bonus, answer == null ? 0 : 1));
    }
    return events;
  }

  private record Event(int base, int bonus, int spent) {}
}
