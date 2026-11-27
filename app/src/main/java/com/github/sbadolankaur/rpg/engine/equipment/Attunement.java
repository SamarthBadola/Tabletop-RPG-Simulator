package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.ArrayList;
import java.util.List;

/**
 * Attunement start and end (RS-EQP-032, E-105). Attuning is a downtime action that advances the
 * game clock by one hour. The engine ends attunement by itself when an ending condition shows in
 * the state, and the DM decides the rest.
 */
public final class Attunement {
  public static final int MAX_ITEMS = 3;
  public static final long ATTUNE_SECONDS = 3600;

  /** The item has been more than 100 feet away for this long (24 hours). */
  public static final long AWAY_SECONDS = 24 * 3600;

  private Attunement() {}

  /** The character at the moment of the attempt. */
  public record Snapshot(
      String creatureId, boolean alive, boolean encounterActive, List<AttunedItem> attuned) {
    public Snapshot {
      attuned = List.copyOf(attuned);
    }
  }

  /** The item to attune to. {@code attunedTo} is the creature it is attuned to now, if any. */
  public record Candidate(
      String instanceId, String definitionId, String attunedTo, boolean prerequisitesMet) {}

  /** The new list of attuned items and the time that the attunement took. */
  public record Result(List<AttunedItem> attuned, long clockAdvanceSeconds) {}

  public static Result attune(Snapshot who, Candidate item) {
    if (!who.alive())
      throw new AttunementException(
          AttunementException.Code.CHARACTER_DEAD, "The character is dead");
    if (who.encounterActive()) {
      throw new AttunementException(
          AttunementException.Code.ENCOUNTER_ACTIVE, "Attuning must be done outside an encounter");
    }
    if (!item.prerequisitesMet()) {
      throw new AttunementException(
          AttunementException.Code.PREREQUISITE_NOT_MET,
          "The character does not meet the item's prerequisite");
    }
    for (var held : who.attuned()) {
      if (held.instanceId().equals(item.instanceId())) {
        throw new AttunementException(
            AttunementException.Code.ALREADY_ATTUNED, "Already attuned to this item");
      }
    }
    if (item.attunedTo() != null && !item.attunedTo().equals(who.creatureId())) {
      throw new AttunementException(
          AttunementException.Code.ATTUNED_TO_ANOTHER, "The item is attuned to another creature");
    }
    for (var held : who.attuned()) {
      if (held.definitionId().equals(item.definitionId())) {
        throw new AttunementException(
            AttunementException.Code.DUPLICATE_DEFINITION,
            "Cannot attune to two copies of the same item");
      }
    }
    if (who.attuned().size() >= MAX_ITEMS) {
      throw new AttunementException(
          AttunementException.Code.TOO_MANY_ITEMS, "A character can be attuned to at most 3 items");
    }
    var next = new ArrayList<>(who.attuned());
    next.add(new AttunedItem(item.instanceId(), item.definitionId()));
    return new Result(next, ATTUNE_SECONDS);
  }

  /** Why an attunement ended by itself. */
  public enum Reason {
    CHARACTER_DIED,
    PREREQUISITE_LOST,
    TOO_FAR_FOR_24_HOURS,
    ATTUNED_TO_ANOTHER
  }

  public record Ending(String instanceId, Reason reason) {}

  /**
   * What the state shows about one attuned item. {@code attunedTo} is the item's current attuned
   * creature.
   */
  public record ItemCondition(
      AttunedItem item, boolean prerequisitesMet, long secondsAway, String attunedTo) {}

  /**
   * The attunements that end because of the state (the DM handles anything the state cannot show).
   */
  public static List<Ending> endings(Snapshot who, List<ItemCondition> conditions) {
    var out = new ArrayList<Ending>();
    for (var condition : conditions) {
      String id = condition.item().instanceId();
      if (!who.alive()) {
        out.add(new Ending(id, Reason.CHARACTER_DIED));
      } else if (!condition.prerequisitesMet()) {
        out.add(new Ending(id, Reason.PREREQUISITE_LOST));
      } else if (condition.secondsAway() >= AWAY_SECONDS) {
        out.add(new Ending(id, Reason.TOO_FAR_FOR_24_HOURS));
      } else if (condition.attunedTo() != null && !condition.attunedTo().equals(who.creatureId())) {
        out.add(new Ending(id, Reason.ATTUNED_TO_ANOTHER));
      }
    }
    return out;
  }

  /** Ending it with another short rest. A cursed item cannot be ended this way. */
  public static List<AttunedItem> endByRest(Snapshot who, String instanceId, boolean cursed) {
    if (cursed)
      throw new AttunementException(
          AttunementException.Code.CURSED, "A cursed item cannot be unattuned");
    var next = new ArrayList<>(who.attuned());
    next.removeIf(held -> held.instanceId().equals(instanceId));
    return next;
  }
}
