package com.github.sbadolankaur.rpg.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SettingsRegistryTest {
  @Test
  void everyDefinitionHasAValidDefaultAndUniqueKey() {
    assertThat(SettingsRegistry.all())
        .extracting(SettingsRegistry.Definition::key)
        .doesNotHaveDuplicates();
    for (var definition : SettingsRegistry.all()) {
      assertThat(definition.parse(definition.defaultValue().toString()))
          .isEqualTo(definition.defaultValue());
      assertThat(definition.explanation()).isNotBlank();
    }
    assertThat(SettingsRegistry.WIZARD_KEYS.stream().map(SettingsRegistry::require))
        .allMatch(definition -> definition.tier() == SettingsRegistry.Tier.STRUCTURAL);
  }

  @Test
  void valuesKeepTheirTypesAndUnrecognizedSettingsAreRejected() {
    assertThat(SettingsRegistry.require("roundAdvanceSeconds").parse("0")).isEqualTo(0L);
    assertThat(SettingsRegistry.require("feats.enabled").parse("false")).isEqualTo(false);
    assertThatThrownBy(() -> SettingsRegistry.require("dawnTime").parse("24:00"))
        .isInstanceOf(CampaignException.class);
    assertThatThrownBy(() -> SettingsRegistry.require("roundAdvanceSeconds").parse("-1"))
        .isInstanceOf(CampaignException.class);
    assertThatThrownBy(() -> SettingsRegistry.require("campaignId"))
        .isInstanceOf(CampaignException.class);
  }

  @Test
  void theEquipmentSettingsExistWithTheSpecDefaults() {
    assertThat(SettingsRegistry.require("encumbranceRule").defaultValue()).isEqualTo("OFF");
    assertThat(SettingsRegistry.require("encumbranceRule").tier())
        .isEqualTo(SettingsRegistry.Tier.STRUCTURAL);
    assertThat(SettingsRegistry.require("objectInteraction").defaultValue())
        .isEqualTo("ONE_PER_TURN");
    assertThat(SettingsRegistry.require("armorChangeInEncounter").defaultValue())
        .isEqualTo("BLOCKED");
    assertThat(SettingsRegistry.require("ammunitionRecovery").defaultValue()).isEqualTo("HALF");
    assertThat(SettingsRegistry.require("unidentifiedItems").defaultValue()).isEqualTo("HIDDEN");
    assertThat(SettingsRegistry.require("ammunitionRecovery").parse("NONE")).isEqualTo("NONE");
    assertThatThrownBy(() -> SettingsRegistry.require("unidentifiedItems").parse("MAYBE"))
        .isInstanceOf(CampaignException.class);
  }
}
