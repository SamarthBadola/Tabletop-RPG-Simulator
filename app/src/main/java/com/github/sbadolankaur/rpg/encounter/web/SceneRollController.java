package com.github.sbadolankaur.rpg.encounter.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.web.CampaignWeb;
import com.github.sbadolankaur.rpg.encounter.application.SceneRolls;
import com.github.sbadolankaur.rpg.encounter.web.EncounterController.Fields;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/campaigns/{campaign}/scenes/{scene}")
class SceneRollController {
  private final SceneRolls rolls;
  private final CampaignCommands commands;
  private final AccountDirectory accounts;

  SceneRollController(SceneRolls rolls, CampaignCommands commands, AccountDirectory accounts) {
    this.rolls = rolls;
    this.commands = commands;
    this.accounts = accounts;
  }

  @GetMapping
  String view(
      @PathVariable UUID campaign, @PathVariable UUID scene, Principal principal, Model model) {
    populate(campaign, scene, CampaignWeb.actor(accounts, principal), model);
    return "demo-scene-tests";
  }

  @GetMapping("/reaction")
  String reaction(
      @PathVariable UUID campaign,
      @PathVariable UUID scene,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    populate(campaign, scene, CampaignWeb.actor(accounts, principal), model);
    return CampaignWeb.htmx(request) ? "demo-encounter :: reaction" : "demo-scene-tests";
  }

  @PostMapping("/{route:ability-checks|saving-throws}")
  Object start(
      @PathVariable UUID campaign,
      @PathVariable UUID scene,
      @PathVariable String route,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    var form = new Fields(fields);
    boolean save = "saving-throws".equals(route);
    var test =
        new SceneRolls.Test(
            form.ability(),
            form.optional("skillKey"),
            form.integer("dc"),
            save,
            form.ids(save ? "targetIds" : "subjectIds"));
    return updated(
        campaign,
        scene,
        actor,
        request,
        model,
        rolls.start(campaign, scene, actor, form.envelope(), test));
  }

  Object updated(
      UUID campaign, UUID scene, UUID actor, HttpServletRequest request, Model model, Reply reply) {
    if (CampaignWeb.htmx(request)) {
      populate(campaign, scene, actor, model);
      model.addAttribute("result", reply.data());
      model.addAttribute("replay", reply.replay());
    }
    return CampaignWeb.region(request, "demo-scene-tests :: workspace", path(campaign, scene));
  }

  private void populate(UUID campaign, UUID scene, UUID actor, Model model) {
    var view = rolls.view(campaign, scene, actor);
    model.addAttribute("view", view);
    model.addAttribute("commandPath", path(campaign, scene));
    model.addAttribute("abilities", Ability.values());
    model.addAttribute("reaction", view.reaction());
    model.addAttribute("actionId", commands.newActionId());
    model.addAttribute("expectedVersion", view.version());
    if (view.reaction() != null) {
      model.addAttribute(
          "reactionPath", "/campaigns/" + campaign + "/reactions/" + view.reaction().id());
      model.addAttribute(
          "reactionDecider",
          view.subjects().stream()
              .filter(subject -> subject.id().equals(view.reaction().prompt().decider()))
              .map(SceneRolls.Participant::name)
              .findFirst()
              .orElse("Creature"));
    }
  }

  private static String path(UUID campaign, UUID scene) {
    return "/campaigns/" + campaign + "/scenes/" + scene;
  }
}
