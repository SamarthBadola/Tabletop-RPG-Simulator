package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.FieldError;

enum EncounterGeometry {
  UNMAPPED,
  MAPPED;

  void requireManualMovement() {
    if (this == MAPPED) throw new CampaignException(Code.MANUAL_MOVE_NOT_ALLOWED);
  }

  void validateEnemyNearTarget(Boolean supplied, boolean hasSneakAttack) {
    if (supplied != null && (this == MAPPED || !hasSneakAttack))
      throw new CampaignException(new FieldError("enemyNearTarget", "INCORRECT"));
  }
}
