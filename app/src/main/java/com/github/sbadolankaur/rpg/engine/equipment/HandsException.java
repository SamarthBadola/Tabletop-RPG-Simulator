package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.List;

/** An equip that the hands cannot allow. It names what is in the way (RS-EQP-011). */
public final class HandsException extends RuntimeException {
  private final List<String> blockingItemIds;

  public HandsException(String message, List<String> blockingItemIds) {
    super(message);
    this.blockingItemIds = List.copyOf(blockingItemIds);
  }

  public List<String> blockingItemIds() {
    return blockingItemIds;
  }
}
