package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import java.util.UUID;

public record CommandForm(UUID actionId, Long expectedVersion) {
  public Envelope envelope() {
    if (actionId == null
        || actionId.version() != 7
        || expectedVersion == null
        || expectedVersion < 0) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }
    return new Envelope(actionId, expectedVersion);
  }
}
