package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.List;

/**
 * What each audience sees of an item (RS-EQP-035, E-109, C-F9). Unidentified items show their
 * mundane look to players. Hidden fields are null or empty in the view, so that nothing about them
 * can leak. The engine reads the real item, so the effects still apply.
 */
public final class PlayerViewFilter {
  private PlayerViewFilter() {}

  public enum Audience {
    DM,
    PLAYER
  }

  /** Everything the DM knows about an item. */
  public record ItemFacts(
      String name,
      String mundaneName,
      boolean identified,
      int magicBonus,
      boolean requiresAttunement,
      List<String> effects,
      Integer charges) {
    public ItemFacts {
      effects = List.copyOf(effects);
    }
  }

  /** What one audience may see. A null or empty field is hidden. */
  public record ItemView(
      String name,
      Integer magicBonus,
      Boolean requiresAttunement,
      List<String> effects,
      Integer charges) {
    public ItemView {
      effects = List.copyOf(effects);
    }
  }

  public static ItemView view(ItemFacts item, Audience audience, boolean hideUnidentified) {
    if (audience == Audience.DM || item.identified() || !hideUnidentified) {
      return new ItemView(
          item.name(),
          item.magicBonus(),
          item.requiresAttunement(),
          item.effects(),
          item.charges());
    }
    return new ItemView(item.mundaneName(), null, null, List.of(), null);
  }
}
