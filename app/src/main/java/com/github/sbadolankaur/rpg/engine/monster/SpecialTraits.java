package com.github.sbadolankaur.rpg.engine.monster;

import java.util.Collection;

/**
 * The traits that change what happens at 0 hit points, found by the name printed on the stat block.
 *
 * <p>TODO(Step 12, item 5): stat block traits are text only for now. When the roster gets its
 * structured traits, read the mechanic from the trait data and delete the names below.
 */
public final class SpecialTraits {
  public static final String UNDEAD_FORTITUDE = "Undead Fortitude";
  public static final String REGENERATION = "Regeneration";

  private SpecialTraits() {}

  /** The zero-hit-point traits of a creature, from the names of its traits. */
  public static ZeroHitPointHook.Defender defender(Collection<String> traitNames) {
    return new ZeroHitPointHook.Defender(
        traitNames.contains(UNDEAD_FORTITUDE), traitNames.contains(REGENERATION));
  }
}
