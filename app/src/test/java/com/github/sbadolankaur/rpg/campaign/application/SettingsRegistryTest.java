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
  void monsterSettingsDefaultToAveragesAndAutoFailOverride() {
    var hp = SettingsRegistry.require("monsterHp");
    assertThat(hp.defaultValue()).isEqualTo("AVERAGE");
    assertThat(hp.tier()).isEqualTo(SettingsRegistry.Tier.STRUCTURAL);
    assertThat(hp.parse("ROLLED")).isEqualTo("ROLLED");
    assertThatThrownBy(() -> hp.parse("MAX")).isInstanceOf(CampaignException.class);
    var legendary = SettingsRegistry.require("legendaryResistanceOverridesAutoFail");
    assertThat(legendary.defaultValue()).isEqualTo(true);
    assertThat(legendary.tier()).isEqualTo(SettingsRegistry.Tier.ASK_ON_FIRST_USE);
    assertThat(legendary.parse("false")).isEqualTo(false);
  }
}
