package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.application.FormTokens;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
class CampaignController {
  private final Campaigns campaigns;
  private final AccountDirectory accounts;
  private final FormTokens tokens;

  CampaignController(Campaigns campaigns, AccountDirectory accounts, FormTokens tokens) {
    this.campaigns = campaigns;
    this.accounts = accounts;
    this.tokens = tokens;
  }

  @GetMapping("/campaigns")
  String list(Principal principal, HttpSession session, Model model) {
    UUID user = actor(principal);
    model.addAttribute("items", campaigns.listFor(user));
    model.addAttribute("formToken", tokens.issue(session.getId(), purpose(user)));
    return "demo-campaigns";
  }

  @PostMapping("/campaigns")
  RedirectView create(
      @Valid @ModelAttribute CreateCampaignForm form,
      BindingResult errors,
      Principal principal,
      HttpSession session) {
    if (errors.hasErrors()) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }
    UUID user = actor(principal);
    String name = form.name().strip();
    String location =
        tokens.createOnce(
            session.getId(),
            purpose(user),
            form.formToken(),
            name,
            () -> "/campaigns/" + campaigns.create(user, name).id() + "/setup");
    var redirect = new RedirectView(location, true);
    redirect.setStatusCode(HttpStatus.SEE_OTHER);
    redirect.setExposeModelAttributes(false);
    return redirect;
  }

  @GetMapping("/campaigns/{id}")
  String view(@PathVariable UUID id, Principal principal, Model model) {
    model.addAttribute("campaign", campaigns.requireMember(id, actor(principal)));
    return "demo-campaign";
  }

  @GetMapping("/campaigns/{id}/setup")
  String setup(@PathVariable UUID id, Principal principal, Model model) {
    model.addAttribute("campaign", campaigns.requireDm(id, actor(principal)));
    return "demo-campaign-setup";
  }

  private UUID actor(Principal principal) {
    return accounts
        .idFor(principal.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  private static String purpose(UUID user) {
    return "campaign-create:" + user;
  }

  record CreateCampaignForm(@NotBlank @Size(max = 80) String name, @NotNull UUID formToken) {}
}
