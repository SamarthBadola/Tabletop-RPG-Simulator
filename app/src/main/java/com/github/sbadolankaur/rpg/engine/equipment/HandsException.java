package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.ArrayList;
import java.util.List;

/** An equip that the hands cannot allow. It names what is in the way (RS-EQP-011). */
public final class HandsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final ArrayList<String> blockingItemIds;

  public HandsException(String message, List<String> blockingItemIds) {
    super(message);
    this.blockingItemIds = new ArrayList<>(blockingItemIds);
  }

  public List<String> blockingItemIds() {
    return List.copyOf(blockingItemIds);
  }
}
