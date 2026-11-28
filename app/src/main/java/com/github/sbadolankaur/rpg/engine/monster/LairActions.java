package com.github.sbadolankaur.rpg.engine.monster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lair actions (RS-MON-034, E-120). A creature with lair actions uses one at initiative count 20,
 * which loses all ties, and only while the fight is marked as in its lair. The LAIR entry is not a
 * combatant: it never takes damage and has no turn options but the owner's lair actions. No SRD 5.1
 * stat block has lair actions, so the rules run on test definitions.
 */
public final class LairActions {
  public static final int INITIATIVE = 20;

  private LairActions() {}

  /** The owner of a lair, as the entry sees it. */
  public record Owner(
      String id, boolean alive, boolean incapacitated, boolean surprisedBeforeFirstTurn) {}

  /** What the owner has used so far. */
  public record History(int lastRound, String lastOption) {
    public static final History NONE = new History(0, null);
  }

  /** A place in the initiative order. A lair slot is never a creature. */
  public record Slot(String id, int count, boolean lair) {}

  /**
   * The LAIR entries of the encounter. There is one for each owner that is alive, while the fight
   * is in the lair. An entry is removed when its owner dies (RS-MON-R11).
   */
  public static List<String> entries(List<Owner> owners, boolean inLair) {
    var ids = new ArrayList<String>();
    if (!inLair) return List.of();
    for (var owner : owners) if (owner.alive()) ids.add(owner.id());
    return List.copyOf(ids);
  }

  /** Initiative order: highest count first, and a LAIR entry goes after everyone at its count. */
  public static List<Slot> order(List<Slot> slots) {
    var sorted = new ArrayList<>(slots);
    sorted.sort(
        Comparator.comparingInt(Slot::count).reversed().thenComparing(slot -> slot.lair() ? 1 : 0));
    return List.copyOf(sorted);
  }

  /**
   * Whether the owner can use a lair action this round. The owner must be able to act, and if it
   * was surprised it waits until after its first turn. It uses at most one per round, and an option
   * that cannot repeat is not used two rounds in a row.
   */
  public static boolean canUse(
      Owner owner,
      boolean inLair,
      History history,
      int round,
      String option,
      boolean cannotRepeat) {
    if (!inLair || !owner.alive() || owner.incapacitated() || owner.surprisedBeforeFirstTurn()) {
      return false;
    }
    if (history.lastRound() == round) return false;
    return !(cannotRepeat
        && history.lastRound() == round - 1
        && option.equals(history.lastOption()));
  }
}
