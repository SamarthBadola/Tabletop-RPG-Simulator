package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.monster.HitDice;
import com.github.sbadolankaur.rpg.engine.monster.MonsterHp;
import com.github.sbadolankaur.rpg.engine.monster.MonsterSize;
import tools.jackson.databind.JsonNode;

/**
 * Turns a monster definition into the hit points of one spawned monster (RS-MON-012, E-114, E-115).
 * The definition is only read: each spawned monster gets its own hit points, so several monsters
 * from one definition roll separately.
 */
final class MonsterSpawner {
  private MonsterSpawner() {}

  /**
   * AVERAGE uses the average printed in the stat block. ROLLED rolls the printed hit dice once; the
   * caller records that roll in the event log.
   */
  static MonsterHp.Start startingHitPoints(
      JsonNode monster, MonsterHp.Mode mode, RandomSource source) {
    JsonNode hp = monster.path("hp");
    if (mode == MonsterHp.Mode.AVERAGE) {
      return new MonsterHp.Start(hp.path("average").asInt(), null);
    }
    try {
      var dice =
          HitDice.parse(
              MonsterSize.fromKey(monster.path("size").asString()), hp.path("dice").asString());
      return MonsterHp.start(dice, mode, source);
    } catch (IllegalArgumentException unreadable) {
      throw EncounterCommands.invalid();
    }
  }
}
