package com.github.sbadolankaur.rpg.engine.equipment;

/** A payment that cannot be made. Nothing is spent (RS-EQP-038). */
public final class InsufficientFundsException extends RuntimeException {
  public InsufficientFundsException(String message) {
    super(message);
  }
}
