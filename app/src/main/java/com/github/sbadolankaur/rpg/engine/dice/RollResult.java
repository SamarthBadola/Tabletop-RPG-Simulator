package com.github.sbadolankaur.rpg.engine.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable roll output, including the natural d20 test value when the plan contains one. */
public record RollResult(List<RollTerm> terms, int total, Integer naturalD20, RollMode mode) {
  public RollResult {
    terms = List.copyOf(terms);
    Objects.requireNonNull(mode, "mode");
  }

  /** Replaces exactly one d20-test die. The replacement is final, as specified by D-8. */
  public RollResult replaceD20(int dieIndex, RandomSource source) {
    int d20TermIndex = -1;
    for (int i = 0; i < terms.size(); i++) {
      if (terms.get(i).label().equals(RollPlan.D20_TEST_LABEL)) {
        d20TermIndex = i;
        break;
      }
    }
    if (d20TermIndex < 0) throw new IllegalStateException("This result has no d20 test die");
    RollTerm term = terms.get(d20TermIndex);
    if (dieIndex < 0 || dieIndex >= term.dice().size())
      throw new IndexOutOfBoundsException(dieIndex);
    List<DieRoll> dice = new ArrayList<>(term.dice());
    DieRoll old = dice.get(dieIndex);
    int face = draw(source, 20) + 1;
    dice.set(
        dieIndex, new DieRoll(old.rolled(), face, face, old.kept(), true, false, old.critical()));
    if (mode != RollMode.NORMAL) {
      int selected = 0;
      for (int i = 1; i < dice.size(); i++) {
        boolean better =
            mode == RollMode.ADVANTAGE
                ? dice.get(i).value() > dice.get(selected).value()
                : dice.get(i).value() < dice.get(selected).value();
        if (better) selected = i;
      }
      for (int i = 0; i < dice.size(); i++) {
        DieRoll die = dice.get(i);
        dice.set(
            i,
            new DieRoll(
                die.rolled(),
                die.rerollFace(),
                die.value(),
                i == selected,
                die.rerolled(),
                die.floored(),
                die.critical()));
      }
    }
    int subtotal = sumKept(term.sign(), dice);
    List<RollTerm> replaced = new ArrayList<>(terms);
    replaced.set(
        d20TermIndex,
        new RollTerm(
            term.label(),
            term.source(),
            term.sign(),
            term.expression(),
            dice,
            term.fixed(),
            subtotal,
            term.doublesOnCrit()));
    int newTotal = Math.addExact(total - term.subtotal(), subtotal);
    Integer natural =
        dice.stream().filter(DieRoll::kept).map(DieRoll::value).findFirst().orElse(null);
    return new RollResult(replaced, newTotal, natural, mode);
  }

  static int draw(RandomSource source, int bound) {
    int value = Objects.requireNonNull(source, "source").nextInt(bound);
    if (value < 0 || value >= bound)
      throw new IllegalStateException("RandomSource returned a value outside [0, bound)");
    return value;
  }

  private static int sumKept(int sign, List<DieRoll> dice) {
    int sum = 0;
    for (DieRoll die : dice) if (die.kept()) sum = Math.addExact(sum, die.value());
    return Math.multiplyExact(sign, sum);
  }
}
