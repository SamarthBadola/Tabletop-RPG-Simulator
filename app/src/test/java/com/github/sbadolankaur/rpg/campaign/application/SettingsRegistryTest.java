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
  void stepNineReactionSnapshotsKeepRecordedValuesAndGainSpellDefaults() {
    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    var campaignSettings = new CampaignSettings(null, null, null, mapper);
    var prior = mapper.createObjectNode();
    for (var definition : SettingsRegistry.all()) {
      prior.set(definition.key(), mapper.valueToTree(definition.defaultValue()));
    }
    prior.remove(
        java.util.List.of(
            "bonusActionSpellRule",
            "counterspellInfo",
            "missileDamageSources",
            "missileRoll",
            "areaCoverageRule",
            "counteredConcentration",
            "componentEnforcement"));
    prior.put("divineSmiteTiming", "BEFORE_ATTACK");

    var pinned = campaignSettings.pinned(prior);
    assertThat(pinned.value("divineSmiteTiming")).isEqualTo("BEFORE_ATTACK");
    assertThat(pinned.value("bonusActionSpellRule")).isEqualTo("OWN_TURN");
    assertThat(pinned.value("counterspellInfo")).isEqualTo("ALERT");
    assertThat(pinned.value("missileDamageSources")).isEqualTo("PER_DART");
    assertThat(pinned.value("missileRoll")).isEqualTo("PER_DART");
    assertThat(pinned.value("areaCoverageRule")).isEqualTo("HALF_ALL");
    assertThat(pinned.value("counteredConcentration")).isEqualTo("ENDS_OLD");
    assertThat(pinned.value("componentEnforcement")).isEqualTo("CONDITIONS_AND_COSTS");
    assertThat(SettingsRegistry.require("areaCoverageRule").tier())
        .isEqualTo(SettingsRegistry.Tier.ASK_ON_FIRST_USE);
    assertThat(SettingsRegistry.require("componentEnforcement").tier())
        .isEqualTo(SettingsRegistry.Tier.SILENT);
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
}
