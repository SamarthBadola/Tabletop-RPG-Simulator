package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Audience;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Change;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Clock;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Kind;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.SettingsRegistry.Definition;
import com.github.sbadolankaur.rpg.campaign.persistence.SettingsStore;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CampaignSettings {
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final SettingsStore store;
  private final JsonMapper json;

  public CampaignSettings(
      Campaigns campaigns, CampaignCommands commands, SettingsStore store, JsonMapper json) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.store = store;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public Panel panel(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    var decisions = store.decisions(campaign);
    var rows =
        SettingsRegistry.all().stream().map(definition -> row(definition, decisions)).toList();
    var setup = store.setup(campaign);
    return new Panel(setup.step(), setup.mode(), rows);
  }

  public Reply decide(UUID campaign, UUID actor, Envelope envelope, String key, String value) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.SETTING_DECIDE,
        new Input(key, value),
        clock ->
            change(
                campaign,
                actor,
                clock,
                SettingsRegistry.require(key),
                value,
                false,
                "PANEL",
                null));
  }

  public Reply reset(UUID campaign, UUID actor, Envelope envelope, String key) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.SETTING_RESET,
        key,
        clock ->
            change(
                campaign, actor, clock, SettingsRegistry.require(key), null, true, "PANEL", null));
  }

  public Reply mode(UUID campaign, UUID actor, Envelope envelope, String mode) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.SETTING_MODE,
        mode,
        clock -> {
          if (!java.util.Set.of("CONFIGURE_NOW", "DEFAULTS_ONLY", "ASK_AS_NEEDED").contains(mode)) {
            throw new CampaignException(CampaignException.Code.SETTING_INVALID);
          }
          String oldMode = store.setup(campaign).mode();
          store.mode(campaign, mode);
          var payload =
              Map.of("oldValue", oldMode, "newValue", mode, "decidedBy", actor, "source", "PANEL");
          return Change.at(clock, "SettingModeChanged", Audience.DM, payload, Map.of("mode", mode))
              .withSettingsRead(Map.of("settingsMode", oldMode));
        });
  }

  public Reply setup(
      UUID campaign, UUID actor, Envelope envelope, int step, boolean skip, String value) {
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.SETUP,
        new SetupInput(step, skip, value),
        clock -> {
          if (step < 1 || step > SettingsRegistry.WIZARD_KEYS.size() || (skip && step != 1)) {
            throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
          }
          if (store.setup(campaign).step() != step) {
            throw new CampaignException(CampaignException.Code.CAMPAIGN_VERSION_CONFLICT);
          }
          int next = skip ? SettingsRegistry.WIZARD_KEYS.size() + 1 : step + 1;
          store.setupStep(campaign, next);
          if (skip) {
            return Change.unchanged(clock, Map.of("step", next));
          }
          Definition definition =
              SettingsRegistry.require(SettingsRegistry.WIZARD_KEYS.get(step - 1));
          return change(campaign, actor, clock, definition, value, false, "WIZARD", next);
        });
  }

  private Change change(
      UUID campaign,
      UUID actor,
      Clock clock,
      Definition definition,
      String value,
      boolean reset,
      String source,
      Integer setupStep) {
    Object prior = snapshot(campaign).value(definition.key());
    Object next = reset ? definition.defaultValue() : definition.parse(value);
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    if (reset) {
      store.reset(campaign, definition.key());
    } else {
      store.save(
          campaign,
          definition.key(),
          json.writeValueAsString(next),
          source,
          actor,
          now,
          clock.gameSeconds());
    }
    var payload =
        Map.of(
            "key",
            definition.key(),
            "oldValue",
            prior,
            "newValue",
            next,
            "decidedBy",
            actor,
            "source",
            source,
            "decidedAt",
            now.toString(),
            "isDefault",
            reset);
    Object result = setupStep == null ? payload : Map.of("step", setupStep, "setting", payload);
    return Change.at(clock, "SettingDecided", Audience.DM, payload, result)
        .withSettingsRead(Map.of(definition.key(), prior));
  }

  @Transactional(readOnly = true)
  public SettingsView effective(UUID campaign, UUID actor) {
    campaigns.requireMember(campaign, actor);
    return snapshot(campaign);
  }

  public void requireDecisions(UUID campaign, UUID actor, List<String> used) {
    campaigns.requireDm(campaign, actor);
    if (used.isEmpty() || !"ASK_AS_NEEDED".equals(store.setup(campaign).mode())) return;
    var decisions = store.decisions(campaign);
    var missing =
        used.stream()
            .distinct()
            .filter(
                key ->
                    SettingsRegistry.require(key).tier() == SettingsRegistry.Tier.ASK_ON_FIRST_USE)
            .filter(key -> !decisions.containsKey(key))
            .toList();
    if (!missing.isEmpty()) {
      throw new CampaignException(
          CampaignException.Code.SETTING_DECISION_REQUIRED, Map.of("settingKeys", missing));
    }
  }

  private SettingsView snapshot(UUID campaign) {
    var decisions = store.decisions(campaign);
    Map<String, Object> values = new LinkedHashMap<>();
    for (Definition definition : SettingsRegistry.all()) {
      values.put(definition.key(), definition.parse(row(definition, decisions).text()));
    }
    return new SettingsView(values);
  }

  private SettingRow row(Definition definition, Map<String, SettingsStore.Decision> decisions) {
    var decision = decisions.get(definition.key());
    if (decision == null) {
      return new SettingRow(
          definition, json.valueToTree(definition.defaultValue()), true, null, null, null);
    }
    return new SettingRow(
        definition,
        json.readTree(decision.valueJson()),
        false,
        decision.decidedBy(),
        decision.decidedAt(),
        decision.source());
  }

  private record Input(String key, String value) {}

  private record SetupInput(int step, boolean skip, String value) {}

  public record Panel(int setupStep, String mode, List<SettingRow> rows) {}

  public record SettingRow(
      Definition definition,
      JsonNode value,
      boolean isDefault,
      String decidedBy,
      OffsetDateTime decidedAt,
      String source) {
    public String text() {
      return value.isString() ? value.asString() : value.toString();
    }
  }
}
