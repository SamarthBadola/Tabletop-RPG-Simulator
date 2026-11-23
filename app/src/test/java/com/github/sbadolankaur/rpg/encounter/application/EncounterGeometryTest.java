package com.github.sbadolankaur.rpg.encounter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.FieldError;
import org.junit.jupiter.api.Test;

class EncounterGeometryTest {
  @Test
  void onlyTheGridComputesAdjacencyWhenTheEncounterHasAMap() {
    for (boolean supplied : new boolean[] {true, false}) {
      assertThatThrownBy(() -> EncounterGeometry.MAPPED.validateEnemyNearTarget(supplied, true))
          .isInstanceOfSatisfying(
              CampaignException.class,
              violation -> {
                assertThat(violation.code()).isEqualTo(Code.VALIDATION_FAILED);
                assertThat(violation.fields())
                    .containsExactly(new FieldError("enemyNearTarget", "INCORRECT"));
              });
    }
    assertThatCode(() -> EncounterGeometry.MAPPED.validateEnemyNearTarget(null, true))
        .doesNotThrowAnyException();
    assertThatCode(() -> EncounterGeometry.UNMAPPED.validateEnemyNearTarget(true, true))
        .doesNotThrowAnyException();
  }

  @Test
  void mappedEncountersRefuseManualMovement() {
    assertThatThrownBy(EncounterGeometry.MAPPED::requireManualMovement)
        .isInstanceOfSatisfying(
            CampaignException.class,
            violation -> assertThat(violation.code()).isEqualTo(Code.MANUAL_MOVE_NOT_ALLOWED));
    assertThatCode(EncounterGeometry.UNMAPPED::requireManualMovement).doesNotThrowAnyException();
  }
}
