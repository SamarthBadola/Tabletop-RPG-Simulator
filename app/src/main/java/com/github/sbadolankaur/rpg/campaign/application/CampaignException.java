package com.github.sbadolankaur.rpg.campaign.application;

import java.util.List;

public final class CampaignException extends RuntimeException {
  private final Code code;
  private final long retryAfter;
  private final List<FieldError> fields;

  public CampaignException(Code code) {
    this(code, 0);
  }

  public CampaignException(Code code, long retryAfter) {
    this(code, retryAfter, List.of());
  }

  public CampaignException(FieldError field) {
    this(Code.VALIDATION_FAILED, 0, List.of(field));
  }

  private CampaignException(Code code, long retryAfter, List<FieldError> fields) {
    super(code.name());
    this.code = code;
    this.retryAfter = retryAfter;
    this.fields = fields;
  }

  public Code code() {
    return code;
  }

  public long retryAfter() {
    return retryAfter;
  }

  public List<FieldError> fields() {
    return fields;
  }

  public record FieldError(String field, String code) {}

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
    SETTING_INVALID,
    PACK_NOT_ENABLED,
    CONTENT_INVALID,
    PACK_REVIEW_STALE
  }
}
