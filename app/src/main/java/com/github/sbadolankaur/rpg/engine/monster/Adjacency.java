package com.github.sbadolankaur.rpg.engine.monster;

import java.util.List;

/**
 * "Within 5 feet of a creature" (RS-MON-018, E-122). The distance comes from the grid of the
 * pure-Java engine, between footprints and with the campaign's diagonal rule. An ally counts only
 * if it is not incapacitated, which includes unconscious and dead. With no grid the engine asks the
 * DM to confirm the condition and records the answer.
 */
public final class Adjacency {
  public static final int REACH_FEET = 5;

  private Adjacency() {}

  /** The distance in feet between two creatures, or null when there is no grid to measure on. */
  @FunctionalInterface
  public interface Distance {
    Integer feet(String first, String second);
  }

  /** An ally of the attacker. */
  public record Ally(String id, boolean incapacitated) {}

  /** What the engine concludes, or that it must ask. */
  public enum Answer {
    YES,
    NO,
    ASK_DM
  }

  /**
   * Whether at least one ally of the attacker is within 5 feet of the target and not incapacitated.
   * The attacker itself is never its own ally.
   */
  public static Answer allyNextToTarget(
      String attacker, String target, List<Ally> allies, Distance distance) {
    boolean measured = true;
    for (var ally : allies) {
      if (ally.id().equals(attacker) || ally.id().equals(target) || ally.incapacitated()) continue;
      Integer feet = distance.feet(ally.id(), target);
      if (feet == null) {
        measured = false;
      } else if (feet <= REACH_FEET) {
        return Answer.YES;
      }
    }
    return measured ? Answer.NO : Answer.ASK_DM;
  }

  /** Turns the DM's recorded answer into the result when there was no grid (RS-MON-R22). */
  public static boolean confirmed(Answer answer, boolean dmConfirms) {
    return switch (answer) {
      case YES -> true;
      case NO -> false;
      case ASK_DM -> dmConfirms;
    };
  }
}
