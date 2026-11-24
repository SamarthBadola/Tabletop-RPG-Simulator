package com.github.sbadolankaur.rpg.rest.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.web.CampaignWeb;
import com.github.sbadolankaur.rpg.campaign.web.CommandForm;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Kind;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Preview;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.PreviewRow;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import com.github.sbadolankaur.rpg.rest.application.CampaignRests;
import com.github.sbadolankaur.rpg.rest.application.RestInput;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/campaigns/{id}/rests")
class RestController {
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final CampaignRests rests;
  private final AccountDirectory accounts;

  RestController(
      Campaigns campaigns,
      CampaignCommands commands,
      CampaignRests rests,
      AccountDirectory accounts) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.rests = rests;
    this.accounts = accounts;
  }

  @GetMapping("/new")
  String form(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "short") String kind,
      Principal principal,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    var initial = RestForm.kind(kind);
    var input =
        new RestInput(
            initial,
            rests.minimumMinutes(id, actor, initial),
            List.of(),
            Map.of(),
            Map.of(),
            false,
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            List.of());
    page(id, actor, input, null, model);
    return "demo-rests";
  }

  @PostMapping("/previews")
  String preview(
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    var input = new RestForm(fields).input();
    page(id, actor, input, rests.preview(id, actor, input), model);
    return "demo-rests :: rest-preview";
  }

  @PostMapping
  String submit(
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    var form = new RestForm(fields);
    var envelope =
        new CommandForm(
                RestForm.parse("actionId", form.single("actionId", "actionId"), UUID::fromString),
                RestForm.parse(
                    "expectedVersion",
                    form.single("expectedVersion", "expectedVersion"),
                    Long::valueOf))
            .envelope();
    var result = rests.submit(id, actor, envelope, form.input(), form.flag("confirmWarnings"));
    model.addAttribute("campaign", campaigns.requireDm(id, actor));
    model.addAttribute("result", result.data());
    model.addAttribute("version", result.version());
    return "demo-rests :: rest-result";
  }

  private void page(UUID id, UUID actor, RestInput input, Preview preview, Model model) {
    model.addAttribute("campaign", campaigns.requireDm(id, actor));
    model.addAttribute("characters", rests.characters(id, actor));
    model.addAttribute("input", input);
    model.addAttribute("longRest", input.kind() == Kind.LONG);
    model.addAttribute("preview", preview);
    var rows = new HashMap<UUID, PreviewRow>();
    if (preview != null) preview.participants().forEach(row -> rows.put(row.id(), row));
    model.addAttribute("rows", rows);
    model.addAttribute("version", commands.readClock(id, actor).version());
    model.addAttribute("ids", commands);
  }
}
