package com.github.sbadolankaur.rpg.campaign.application;

import java.util.List;
import java.util.Map;

public final class CampaignException extends RuntimeException {
  private final Code code;
  private final long retryAfter;
  private final List<FieldError> fields;
  private final Map<String, ?> details;

  public CampaignException(Code code) {
    this(code, 0);
  }

  public CampaignException(Code code, long retryAfter) {
    this(code, retryAfter, List.of(), Map.of());
  }

  public CampaignException(FieldError field) {
    this(Code.VALIDATION_FAILED, 0, List.of(field), Map.of());
  }

  public CampaignException(Code code, Map<String, ?> details) {
    this(code, 0, List.of(), details);
  }

  private CampaignException(
      Code code, long retryAfter, List<FieldError> fields, Map<String, ?> details) {
    super(code.name());
    this.code = code;
    this.retryAfter = retryAfter;
    this.fields = fields;
    this.details = Map.copyOf(details);
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

  public Map<String, ?> details() {
    return details;
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
    PACK_REVIEW_STALE,
    ENCOUNTER_ACTIVE,
    ENCOUNTER_STARTED,
    ENCOUNTER_VERSION_CONFLICT,
    REACTION_PENDING,
    MANUAL_MOVE_NOT_ALLOWED,
    REACTION_OPTION_INVALID,
    REACTION_NOT_AVAILABLE,
    REACTION_NOT_PERMITTED,
    FEATURE_INPUT_INVALID,
    FEATURE_NOT_AVAILABLE,
    FEATURE_USES_EXHAUSTED,
    NOT_YOUR_TURN,
    NO_ACTIONS_LEFT,
    TARGET_OUT_OF_RANGE,
    TOTAL_COVER,
    INVALID_TARGET,
    REST_NOT_ALLOWED,
    REST_TOO_SHORT,
    HIT_DICE_UNAVAILABLE,
    SETTING_DECISION_REQUIRED
  }
}
