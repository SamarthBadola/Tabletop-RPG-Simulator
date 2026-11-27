package com.github.sbadolankaur.rpg.engine.equipment;

/** An attunement that the rules refuse (RS-EQP-032). */
public final class AttunementException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public enum Code {
    TOO_MANY_ITEMS,
    ATTUNED_TO_ANOTHER,
    DUPLICATE_DEFINITION,
    ENCOUNTER_ACTIVE,
    PREREQUISITE_NOT_MET,
    ALREADY_ATTUNED,
    CHARACTER_DEAD,
    CURSED
  }

  private final Code code;

  public AttunementException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
