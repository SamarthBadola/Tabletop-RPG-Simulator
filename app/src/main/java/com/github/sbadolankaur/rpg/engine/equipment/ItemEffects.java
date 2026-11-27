package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gathers the bonuses of the items that count (RS-EQP-031, RS-EQP-033, E-104). Bonuses from
 * different items add, and two copies of one definition count once. Whether the item is identified
 * does not matter here: the effects apply either way (RS-EQP-035).
 */
public final class ItemEffects {
  private ItemEffects() {}

  public record Bonus(String kind, int amount) {}

  /** An item with its effects. It counts while worn or held, and attuned if it needs attunement. */
  public record ActiveItem(
      String definitionId,
      String instanceId,
      boolean wornOrHeld,
      boolean requiresAttunement,
      boolean attuned,
      List<Bonus> bonuses) {
    public ActiveItem {
      bonuses = List.copyOf(bonuses);
    }

    boolean counts() {
      return wornOrHeld && (!requiresAttunement || attuned);
    }
  }

  /** The total by kind, with one copy of each definition. */
  public static Map<String, Integer> gather(List<ActiveItem> items) {
    Set<String> seen = new HashSet<>();
    var totals = new LinkedHashMap<String, Integer>();
    for (var item : items) {
      if (!item.counts() || !seen.add(item.definitionId())) continue;
      for (var bonus : item.bonuses()) totals.merge(bonus.kind(), bonus.amount(), Integer::sum);
    }
    return Map.copyOf(totals);
  }
}
