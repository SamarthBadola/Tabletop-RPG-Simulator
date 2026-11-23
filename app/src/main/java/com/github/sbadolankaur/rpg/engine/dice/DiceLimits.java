package com.github.sbadolankaur.rpg.engine.dice;

/** The single source of limits for dice expressions and plans. */
public record DiceLimits(
    int minDieSize,
    int maxDieSize,
    int maxDicePerGroup,
    int maxTotalDice,
    int maxDiceAfterCriticalDoubling,
    int maxTerms,
    int maxExpressionLength,
    int maxIntegerLiteral) {

  public static final DiceLimits DEFAULT =
      new DiceLimits(2, 1000, 100, 200, 400, 20, 200, 1_000_000);

  public DiceLimits {
    if (minDieSize < 1
        || maxDieSize < minDieSize
        || maxDicePerGroup < 1
        || maxTotalDice < 1
        || maxDiceAfterCriticalDoubling < maxTotalDice
        || maxTerms < 1
        || maxExpressionLength < 1
        || maxIntegerLiteral < 0) {
      throw new IllegalArgumentException("Dice limits are inconsistent");
    }
  }
}
