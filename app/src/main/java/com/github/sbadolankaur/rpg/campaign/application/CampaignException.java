package com.github.sbadolankaur.rpg.campaign.application;

public final class CampaignException extends RuntimeException {
  private final Code code;

  public CampaignException(Code code) {
    super(code.name());
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public enum Code {
    NOT_FOUND,
    FORBIDDEN,
    VALIDATION_FAILED,
    IDEMPOTENCY_KEY_CONFLICT
  }
}
