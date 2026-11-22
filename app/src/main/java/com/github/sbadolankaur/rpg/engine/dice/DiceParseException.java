package com.github.sbadolankaur.rpg.engine.dice;

public final class DiceParseException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final DiceErrorCode code;
  private final int position;
  private final String token;
  private final String limitName;
  private final Integer limitValue;
  private final String field;

  public DiceParseException(
      DiceErrorCode code,
      int position,
      String token,
      String field,
      String limitName,
      Integer limitValue) {
    super(code + " at position " + position + (field == null ? "" : " (" + field + ")"));
    this.code = code;
    this.position = position;
    this.token = token;
    this.field = field;
    this.limitName = limitName;
    this.limitValue = limitValue;
  }

  public DiceErrorCode code() {
    return code;
  }

  public int position() {
    return position;
  }

  public String token() {
    return token;
  }

  public String field() {
    return field;
  }

  public String limitName() {
    return limitName;
  }

  public Integer limitValue() {
    return limitValue;
  }
}
