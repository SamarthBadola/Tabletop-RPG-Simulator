package com.github.sbadolankaur.rpg.engine.equipment;

/** A payment that cannot be made. Nothing is spent (RS-EQP-038). */
public final class InsufficientFundsException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InsufficientFundsException(String message) {
    super(message);
  }
}
