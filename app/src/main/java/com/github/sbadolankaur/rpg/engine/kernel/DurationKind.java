package com.github.sbadolankaur.rpg.engine.kernel;

/** The closed duration vocabulary shared by the ruleset and content schema. */
public enum DurationKind {
  INSTANT,
  ROUNDS,
  CLOCK,
  CONCENTRATION,
  UNTIL_TURN_START,
  UNTIL_TURN_END,
  UNTIL_DISPELLED,
  UNTIL_SHORT_OR_LONG_REST,
  UNTIL_LONG_REST,
  UNTIL_USED,
  PERMANENT,
  WHILE_EQUIPPED,
  WHILE_ATTUNED
}
