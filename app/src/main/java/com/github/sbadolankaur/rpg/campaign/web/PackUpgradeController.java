package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignPackUpgrades;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/campaigns/{id}/packs")
class PackUpgradeController {
  private final CampaignPackUpgrades upgrades;
  private final CampaignCommands commands;
  private final AccountDirectory accounts;

  PackUpgradeController(
      CampaignPackUpgrades upgrades, CampaignCommands commands, AccountDirectory accounts) {
    this.upgrades = upgrades;
    this.commands = commands;
    this.accounts = accounts;
  }

  @GetMapping
  String list(@PathVariable UUID id, Principal principal, Model model) {
    populate(id, CampaignWeb.actor(accounts, principal), model);
    return "demo-pack-upgrade";
  }

  @PostMapping("/{packId}/reviews")
  String review(
      @PathVariable UUID id,
      @PathVariable UUID packId,
      @RequestParam String action,
      @RequestParam String targetVersion,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    populate(id, actor, model);
    if (!action.equals("upgrade")) throw new CampaignException(Code.VALIDATION_FAILED);
    model.addAttribute("review", upgrades.review(id, actor, packId, targetVersion));
    return CampaignWeb.htmx(request) ? "demo-pack-upgrade :: pack-review" : "demo-pack-upgrade";
  }

  @PostMapping("/{packId}/upgrades")
  Object apply(
      @PathVariable UUID id,
      @PathVariable UUID packId,
      @ModelAttribute CommandForm form,
      @RequestParam String targetVersion,
      @RequestParam String reviewHash,
      @RequestParam(required = false) List<String> confirmGroups,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    upgrades.apply(
        id,
        actor,
        packId,
        form.envelope(),
        targetVersion,
        reviewHash,
        confirmGroups == null ? Set.of() : Set.copyOf(confirmGroups));
    populate(id, actor, model);
    return CampaignWeb.region(
        request, "demo-pack-upgrade :: pack-list", "/campaigns/" + id + "/packs");
  }

  private void populate(UUID id, UUID actor, Model model) {
    model.addAttribute("versions", upgrades.list(id, actor));
    model.addAttribute("campaignId", id);
    model.addAttribute("currentVersion", upgrades.currentVersion());
    model.addAttribute("actionId", commands.newActionId());
    model.addAttribute("expectedVersion", commands.readClock(id, actor).version());
  }
}
