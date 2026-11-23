package com.github.sbadolankaur.rpg.engine.reaction;

import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.ReplayingRandomSource;
import com.github.sbadolankaur.rpg.engine.dice.ReplayingRandomSource.Draw;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Re-executes an action against the same inputs until it completes or reaches an unanswered prompt.
 */
public final class ReactionReplay {
  public static final int MAX_DEPTH = 6;

  private static final Comparator<Offer> OFFER_ORDER =
      Comparator.comparingInt(Offer::priority)
          .thenComparing(Comparator.comparingInt(Offer::initiative).reversed())
          .thenComparing(Offer::decider)
          .thenComparing(offer -> offer.option().key());

  private final ReplayingRandomSource random;
  private final List<Decision> decisions;
  private final List<String> frames = new ArrayList<>();
  private final List<Point> depthLimits = new ArrayList<>();
  private int consumed;

  private ReactionReplay(String action, Continuation continuation, RandomSource fresh) {
    frames.add(Objects.requireNonNull(action));
    random = new ReplayingRandomSource(continuation.draws(), fresh);
    decisions = continuation.decisions();
  }

  public static <T> Resolution<T> run(
      String name,
      Continuation continuation,
      RandomSource fresh,
      Function<ReactionReplay, T> action) {
    var replay = new ReactionReplay(name, continuation, fresh);
    T completed = null;
    Prompt pending = null;
    try {
      completed = action.apply(replay);
    } catch (AwaitingDecision pause) {
      pending = pause.prompt;
    }
    if (replay.consumed != replay.decisions.size())
      throw new IllegalStateException("The action did not replay all its recorded decisions");
    return new Resolution<>(
        completed,
        pending,
        new Continuation(replay.random.draws(), replay.decisions),
        List.copyOf(replay.depthLimits));
  }

  public RandomSource random() {
    return random;
  }

  public <T> T nested(String action, Supplier<T> resolve) {
    frames.add(Objects.requireNonNull(action));
    try {
      return resolve.get();
    } finally {
      frames.removeLast();
    }
  }

  /**
   * Asks eligible combatants in the ordered steps. After every answer the caller rebuilds offers
   * from the current rules state, so an exhausted resource or a cancelled trigger is withdrawn.
   */
  public void window(
      Point point,
      Supplier<List<Offer>> eligible,
      Disclosure disclosure,
      BiConsumer<UUID, String> use) {
    if (frames.size() > MAX_DEPTH) {
      if (!eligible.get().isEmpty()) depthLimits.add(point);
      return;
    }
    for (Step step : Step.values()) {
      var answered = new HashSet<UUID>();
      while (true) {
        var offers =
            eligible.get().stream()
                .filter(offer -> offer.step() == step && !answered.contains(offer.decider()))
                .sorted(OFFER_ORDER)
                .toList();
        if (offers.isEmpty()) break;
        var decider = offers.getFirst().decider();
        var options =
            offers.stream()
                .filter(offer -> offer.decider().equals(decider))
                .map(Offer::option)
                .toList();
        String chosen = choose(point, step, decider, options, disclosure);
        answered.add(decider);
        if (chosen != null) use.accept(decider, chosen);
      }
    }
  }

  public String choose(
      Point point, Step step, UUID decider, List<Option> options, Disclosure disclosure) {
    if (options.isEmpty()) return null;
    if (frames.size() > MAX_DEPTH) {
      depthLimits.add(point);
      return null;
    }
    return ask(point, step, decider, options, false, disclosure);
  }

  public String followUp(Point point, Step step, UUID decider, List<Option> options) {
    return ask(point, step, decider, options, true, null);
  }

  private String ask(
      Point point,
      Step step,
      UUID decider,
      List<Option> options,
      boolean followUp,
      Disclosure disclosure) {
    var prompt = new Prompt(consumed, frames, point, step, decider, options, followUp, disclosure);
    if (consumed == decisions.size()) throw new AwaitingDecision(prompt);
    var previous = decisions.get(consumed++);
    if (!prompt.equals(previous.prompt()))
      throw new IllegalStateException("The recorded prompt differs on replay");
    return previous.optionKey();
  }

  public enum Point {
    ON_MOVE_STEP,
    BEFORE_ATTACK_ROLL,
    AFTER_ATTACK_ROLL,
    AFTER_HIT,
    AFTER_DAMAGE_ROLL,
    AFTER_SAVE_ROLL,
    AFTER_SAVE_FAIL,
    AFTER_CHECK_ROLL,
    ON_CAST,
    AFTER_DAMAGE_TAKEN,
    ON_READIED_TRIGGER
  }

  public enum Step {
    ACTING_RESULT,
    DEFENDING_RESULT,
    ACTING_DAMAGE,
    DEFENDING_DAMAGE
  }

  public record Option(String key, String label, boolean reaction, String pool, int units) {
    public Option {
      if (key == null
          || key.isBlank()
          || label == null
          || label.isBlank()
          || units < 0
          || (pool == null) != (units == 0)
          || pool != null && pool.isBlank())
        throw new IllegalArgumentException("Invalid reaction option");
    }
  }

  public record Offer(Step step, UUID decider, int initiative, int priority, Option option) {
    public Offer {
      Objects.requireNonNull(step);
      Objects.requireNonNull(decider);
      Objects.requireNonNull(option);
    }
  }

  public record Disclosure(Integer natural, Integer total, Boolean success) {}

  public record Prompt(
      int sequence,
      List<String> frames,
      Point point,
      Step step,
      UUID decider,
      List<Option> options,
      boolean followUp,
      Disclosure disclosure) {
    public Prompt {
      Objects.requireNonNull(point);
      Objects.requireNonNull(step);
      Objects.requireNonNull(decider);
      frames = List.copyOf(frames);
      options = List.copyOf(options);
      if (sequence < 0
          || frames.isEmpty()
          || frames.size() > MAX_DEPTH
          || options.isEmpty()
          || options.stream().map(Option::key).distinct().count() != options.size())
        throw new IllegalArgumentException("Invalid reaction prompt");
    }

    public boolean permits(String key) {
      return key == null
          ? !followUp
          : options.stream().anyMatch(option -> option.key().equals(key));
    }
  }

  public record Decision(Prompt prompt, String optionKey) {
    public Decision {
      if (!Objects.requireNonNull(prompt).permits(optionKey))
        throw new IllegalArgumentException("The choice is not offered");
    }
  }

  public record Continuation(List<Draw> draws, List<Decision> decisions) {
    public Continuation {
      draws = List.copyOf(draws);
      decisions = List.copyOf(decisions);
    }

    public static Continuation empty() {
      return new Continuation(List.of(), List.of());
    }

    public Continuation answer(Prompt prompt, String key) {
      if (prompt.sequence() != decisions.size()) throw new IllegalArgumentException("Stale prompt");
      var next = new ArrayList<>(decisions);
      next.add(new Decision(prompt, key));
      return new Continuation(draws, next);
    }
  }

  public record Resolution<T>(
      T completed, Prompt pending, Continuation continuation, List<Point> depthLimits) {}

  private static final class AwaitingDecision extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient Prompt prompt;

    private AwaitingDecision(Prompt prompt) {
      super(null, null, false, false);
      this.prompt = prompt;
    }
  }
}
