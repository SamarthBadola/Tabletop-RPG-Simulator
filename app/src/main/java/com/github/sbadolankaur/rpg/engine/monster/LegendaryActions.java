package com.github.sbadolankaur.rpg.engine.monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Legendary actions (RS-MON-030, RS-MON-031). A legendary creature has a pool of points that
 * refills at the start of its own turn. At the end of another creature's turn it may spend points
 * on one option. Using one is not an action, bonus action or reaction.
 */
public final class LegendaryActions {
  private LegendaryActions() {}

  /** The points the creature has this round. */
  public record Pool(int maximum, int points) {
    public Pool {
      if (maximum < 1 || points < 0 || points > maximum) {
        throw new IllegalArgumentException("Invalid legendary action points");
      }
    }

    public static Pool full(int maximum) {
      return new Pool(maximum, maximum);
    }

    /** Pays for an option. */
    public Pool spend(int cost) {
      if (cost < 1 || cost > points)
        throw new IllegalStateException("Not enough legendary action points");
      return new Pool(maximum, points - cost);
    }

    /** The start of the creature's own turn regains all its points (RS-MON-036). */
    public Pool refill() {
      return full(maximum);
    }
  }

  /** An option on the stat block and what it costs. */
  public record Option(String key, String label, int cost) {
    public Option {
      if (key == null || key.isBlank() || label == null || label.isBlank() || cost < 1) {
        throw new IllegalArgumentException("Invalid legendary action option");
      }
    }
  }

  /**
   * A legendary creature as the window sees it. {@code surprisedBeforeFirstTurn} is true while the
   * creature was surprised and has not yet had its first turn (RS-MON-R7).
   */
  public record Creature(
      String id,
      Pool pool,
      boolean alive,
      boolean incapacitated,
      boolean surprisedBeforeFirstTurn) {}

  /** Whether the creature may use a legendary action at the end of the given turn. */
  public static boolean eligible(Creature creature, String endedTurnOf) {
    return creature.alive()
        && !creature.incapacitated()
        && !creature.surprisedBeforeFirstTurn()
        && creature.pool().points() > 0
        && !creature.id().equals(endedTurnOf);
  }

  /** The options the creature can pay for with the points it has. */
  public static List<Option> affordable(List<Option> options, Pool pool) {
    return options.stream().filter(option -> option.cost() <= pool.points()).toList();
  }

  /**
   * The creatures that get a window when a turn ends, in order. The windows open one at a time in
   * initiative order, starting after the creature whose turn ended (RS-MON-031, RS-MON-R9). The
   * list is every legendary creature in the fight, in initiative order.
   */
  public static List<Creature> windowOrder(List<Creature> initiativeOrder, String endedTurnOf) {
    int size = initiativeOrder.size();
    int start = 0;
    for (int index = 0; index < size; index++) {
      if (initiativeOrder.get(index).id().equals(endedTurnOf)) {
        start = index + 1;
        break;
      }
    }
    var ordered = new ArrayList<Creature>();
    for (int offset = 0; offset < size; offset++) {
      var creature = initiativeOrder.get((start + offset) % size);
      if (eligible(creature, endedTurnOf)) ordered.add(creature);
    }
    return List.copyOf(ordered);
  }
}
