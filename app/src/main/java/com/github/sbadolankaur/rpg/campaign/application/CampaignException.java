package com.github.sbadolankaur.rpg.campaign.application;

public final class CampaignException extends RuntimeException {
  private final Code code;
  private final long retryAfter;

  public CampaignException(Code code) {
    this(code, 0);
  }

  public CampaignException(Code code, long retryAfter) {
    super(code.name());
    this.code = code;
    this.retryAfter = retryAfter;
  }

  public Code code() {
    return code;
  }

  public long retryAfter() {
    return retryAfter;
  }

  public enum Code {
    NOT_FOUND,
    FORBIDDEN,
    CSRF_INVALID,
    VALIDATION_FAILED,
    IDEMPOTENCY_KEY_CONFLICT,
    INVITE_INVALID,
    RATE_LIMITED,
    CAMPAIGN_VERSION_CONFLICT,
    SESSION_NOT_ACTIVE,
    SETTING_INVALID
  }
}
