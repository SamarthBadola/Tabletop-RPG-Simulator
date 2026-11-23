package com.github.sbadolankaur.rpg.engine.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Replays every recorded draw before consuming and recording fresh randomness. */
public final class ReplayingRandomSource implements RandomSource {
  private final List<Draw> previous;
  private final ArrayList<Draw> draws = new ArrayList<>();
  private final RandomSource fresh;

  public ReplayingRandomSource(List<Draw> previous, RandomSource fresh) {
    this.previous = List.copyOf(previous);
    this.fresh = Objects.requireNonNull(fresh);
  }

  @Override
  public int nextInt(int bound) {
    if (bound < 1) throw new IllegalArgumentException("A draw needs a positive bound");
    var index = draws.size();
    var draw =
        index < previous.size() ? previous.get(index) : new Draw(bound, fresh.nextInt(bound));
    if (draw.bound() != bound)
      throw new IllegalStateException("Recorded die size differs on replay");
    draws.add(draw);
    return draw.value();
  }

  public List<Draw> draws() {
    if (draws.size() < previous.size())
      throw new IllegalStateException("The action did not replay all its recorded dice");
    return List.copyOf(draws);
  }

  public record Draw(int bound, int value) {
    public Draw {
      if (bound < 1 || value < 0 || value >= bound)
        throw new IllegalArgumentException("A recorded draw is outside its bounds");
    }
  }
}
