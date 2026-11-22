package com.github.sbadolankaur.rpg.engine.kernel;

/** The six fixed ability scores used by the ruleset. */
public enum Ability {
  STR,
  DEX,
  CON,
  INT,
  WIS,
  CHA;

  /** The content registry's stat key for this ability. */
  public String statKey() {
    return switch (this) {
      case STR -> "ability:strength";
      case DEX -> "ability:dexterity";
      case CON -> "ability:constitution";
      case INT -> "ability:intelligence";
      case WIS -> "ability:wisdom";
      case CHA -> "ability:charisma";
    };
  }
}
