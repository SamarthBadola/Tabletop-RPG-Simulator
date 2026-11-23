package com.github.sbadolankaur.rpg.engine.character;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollTerm;
import java.util.List;

public enum AbilityGeneration {
  STANDARD_ARRAY,
  POINT_BUY,
  ROLL_4D6_DROP_LOWEST,
  ROLL_3D6,
  MANUAL;

  private static final List<Integer> STANDARD = List.of(8, 10, 12, 13, 14, 15);
  private static final int[] COST = {0, 1, 2, 3, 4, 5, 7, 9};

  public boolean rolled() {
    return this == ROLL_4D6_DROP_LOWEST || this == ROLL_3D6;
  }

  public RolledScores roll(RandomSource random) {
    if (!rolled()) throw new IllegalArgumentException("This method does not roll");
    String term = this == ROLL_4D6_DROP_LOWEST ? "4d6dl1" : "3d6";
    var result =
        DiceExpression.parse(String.join("+", java.util.Collections.nCopies(6, term))).roll(random);
    return new RolledScores(
        result.terms().stream().map(RollTerm::subtotal).toList(), result.terms());
  }

  public void validate(List<Integer> scores, List<Integer> rolledScores, boolean dm) {
    if (scores.size() != 6 || scores.stream().anyMatch(value -> value < 1 || value > 30)) {
      throw new IllegalArgumentException("Six valid ability scores are required");
    }
    var sorted = scores.stream().sorted().toList();
    boolean valid =
        switch (this) {
          case STANDARD_ARRAY -> sorted.equals(STANDARD);
          case POINT_BUY ->
              sorted.getFirst() >= 8
                  && sorted.getLast() <= 15
                  && sorted.stream().mapToInt(value -> COST[value - 8]).sum() <= 27;
          case ROLL_4D6_DROP_LOWEST, ROLL_3D6 ->
              sorted.equals(rolledScores.stream().sorted().toList());
          case MANUAL -> dm;
        };
    if (!valid)
      throw new IllegalArgumentException("The scores do not match their generation method");
  }

  public record RolledScores(List<Integer> scores, List<RollTerm> terms) {
    public RolledScores {
      scores = List.copyOf(scores);
      terms = List.copyOf(terms);
    }
  }
}
