package com.github.sbadolankaur.rpg.campaign.application;

import java.util.UUID;

public record CampaignView(UUID id, String name, Role role, SessionState sessionState) {
  public enum Role {
    DM,
    PLAYER
  }

  public enum SessionState {
    NONE,
    ACTIVE,
    PAUSED,
    ENDED
  }
}
