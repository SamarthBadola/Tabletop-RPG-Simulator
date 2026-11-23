package com.github.sbadolankaur.rpg.encounter.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.web.CampaignWeb;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.AnswerKind;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.ReactionAnswer;
import com.github.sbadolankaur.rpg.encounter.web.EncounterController.Fields;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/campaigns/{campaign}/reactions/{windowId}")
class ReactionController {
  private final EncounterCommands commands;
  private final EncounterController encounters;
  private final AccountDirectory accounts;

  ReactionController(
      EncounterCommands commands, EncounterController encounters, AccountDirectory accounts) {
    this.commands = commands;
    this.encounters = encounters;
    this.accounts = accounts;
  }

  @PostMapping("/{route:answers|follow-ups|cancellations}")
  Object decide(
      @PathVariable UUID campaign,
      @PathVariable UUID windowId,
      @PathVariable String route,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    var form = new Fields(fields);
    var answer =
        switch (route) {
          case "answers" -> answer(form);
          case "follow-ups" -> {
            String option = form.optional("optionKey");
            if (option == null)
              throw new CampaignException(CampaignException.Code.REACTION_OPTION_INVALID);
            yield new ReactionAnswer(AnswerKind.FOLLOW_UP, form.uuid("deciderId"), option, null);
          }
          case "cancellations" ->
              new ReactionAnswer(AnswerKind.CANCEL, null, null, form.optional("reason"));
          default -> throw new IllegalStateException("Unexpected reaction route");
        };
    var reply = commands.answer(campaign, windowId, actor, form.envelope(), answer);
    UUID encounter = UUID.fromString(reply.data().path("encounterId").asString());
    return encounters.updated(campaign, encounter, actor, request, model, reply, "workspace");
  }

  private static ReactionAnswer answer(Fields form) {
    String choice = form.optional("choice");
    String option = form.optional("optionKey");
    if (("use".equals(choice) && option == null)
        || ("pass".equals(choice) && option != null)
        || !("use".equals(choice) || "pass".equals(choice))) {
      throw new CampaignException(CampaignException.Code.REACTION_OPTION_INVALID);
    }
    return new ReactionAnswer(AnswerKind.ANSWER, form.uuid("deciderId"), option, null);
  }
}
