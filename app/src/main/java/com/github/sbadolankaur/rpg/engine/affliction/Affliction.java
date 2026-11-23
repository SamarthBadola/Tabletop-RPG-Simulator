package com.github.sbadolankaur.rpg.engine.affliction;

import java.util.UUID;

public record Affliction(
    UUID id, Kind kind, State state, Long endsAt, Long endedAt, String endReason) {
  public enum Kind {
    MADNESS_SHORT,
    MADNESS_LONG,
    MADNESS_INDEFINITE,
    DISEASE,
    POISON
  }

  public enum State {
    ACTIVE,
    SUPPRESSED,
    ENDED
  }

  public boolean canLayOnHandsCure(long now) {
    return state == State.ACTIVE
        && (endsAt == null || now < endsAt)
        && (kind == Kind.DISEASE || kind == Kind.POISON);
  }

  public Affliction cure(long now) {
    return new Affliction(id, kind, State.ENDED, endsAt, now, "CURED");
  }
}
