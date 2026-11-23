package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignSettings;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.application.SettingsRegistry;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/campaigns/{id}")
class SettingsController {
  private final CampaignSettings settings;
  private final CampaignCommands commands;
  private final Campaigns campaigns;
  private final AccountDirectory accounts;

  SettingsController(
      CampaignSettings settings,
      CampaignCommands commands,
      Campaigns campaigns,
      AccountDirectory accounts) {
    this.settings = settings;
    this.commands = commands;
    this.campaigns = campaigns;
    this.accounts = accounts;
  }

  @GetMapping("/setup")
  Object setup(@PathVariable UUID id, Principal principal, Model model) {
    var panel = populate(id, CampaignWeb.actor(accounts, principal), model);
    return step(id, panel, model);
  }

  @PostMapping("/setup")
  Object setupAction(
      @PathVariable UUID id,
      @ModelAttribute SettingsForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    validate(errors);
    if (form.step() == null
        || form.step() < 1
        || form.step() > SettingsRegistry.WIZARD_KEYS.size()) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }
    String key = SettingsRegistry.WIZARD_KEYS.get(form.step() - 1);
    boolean skip = Boolean.TRUE.equals(form.skip());
    settings.setup(
        id, actor, form.envelope(), form.step(), skip, skip ? null : request.getParameter(key));
    var panel = populate(id, actor, model);
    if (panel.setupStep() > SettingsRegistry.WIZARD_KEYS.size()) {
      return CampaignWeb.go(request, "/campaigns/" + id + "/players");
    }
    step(id, panel, model);
    return CampaignWeb.region(
        request, "demo-campaign-setup :: setup", "/campaigns/" + id + "/setup");
  }

  @GetMapping("/settings")
  String settings(@PathVariable UUID id, Principal principal, Model model) {
    populate(id, CampaignWeb.actor(accounts, principal), model);
    return "demo-settings";
  }

  @PostMapping({"/settings/{key}/decisions", "/settings/{key}/resets"})
  Object settingAction(
      @PathVariable UUID id,
      @PathVariable String key,
      @ModelAttribute SettingsForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    validate(errors);
    if (request.getServletPath().endsWith("/resets")
        || request.getRequestURI().endsWith("/resets")) {
      settings.reset(id, actor, form.envelope(), key);
    } else {
      settings.decide(id, actor, form.envelope(), key, form.value());
    }
    var panel = populate(id, actor, model);
    model.addAttribute("singleRow", true);
    model.addAttribute(
        "row",
        panel.rows().stream()
            .filter(item -> item.definition().key().equals(key))
            .findFirst()
            .orElseThrow());
    return CampaignWeb.region(request, "demo-setting-row :: row", "/campaigns/" + id + "/settings");
  }

  @PostMapping("/settings/mode")
  Object mode(
      @PathVariable UUID id,
      @ModelAttribute SettingsForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    validate(errors);
    settings.mode(id, actor, form.envelope(), form.mode());
    populate(id, actor, model);
    return CampaignWeb.region(
        request, "demo-settings :: settings-mode", "/campaigns/" + id + "/settings");
  }

  private CampaignSettings.Panel populate(UUID id, UUID actor, Model model) {
    model.addAttribute("campaign", campaigns.requireDm(id, actor));
    model.addAttribute("version", commands.readClock(id, actor).version());
    model.addAttribute("ids", commands);
    var panel = settings.panel(id, actor);
    model.addAttribute("panel", panel);
    return panel;
  }

  private static Object step(UUID id, CampaignSettings.Panel panel, Model model) {
    if (panel.setupStep() > SettingsRegistry.WIZARD_KEYS.size()) {
      return CampaignWeb.seeOther("/campaigns/" + id + "/players");
    }
    String key = SettingsRegistry.WIZARD_KEYS.get(panel.setupStep() - 1);
    model.addAttribute(
        "row",
        panel.rows().stream()
            .filter(item -> item.definition().key().equals(key))
            .findFirst()
            .orElseThrow());
    return "demo-campaign-setup";
  }

  private static void validate(BindingResult errors) {
    if (errors.hasErrors()) throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
  }

  record SettingsForm(
      UUID actionId, Long expectedVersion, String value, String mode, Integer step, Boolean skip) {
    CampaignCommands.Envelope envelope() {
      return new CommandForm(actionId, expectedVersion).envelope();
    }
  }
}
