package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.application.FormTokens;
import com.github.sbadolankaur.rpg.campaign.application.InviteAttempts;
import com.github.sbadolankaur.rpg.campaign.application.Invites;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
class InviteController {
  private final Invites invites;
  private final InviteAttempts attempts;
  private final Campaigns campaigns;
  private final AccountDirectory accounts;
  private final FormTokens tokens;
  private final CampaignCommands commands;

  InviteController(
      Invites invites,
      InviteAttempts attempts,
      Campaigns campaigns,
      AccountDirectory accounts,
      FormTokens tokens,
      CampaignCommands commands) {
    this.invites = invites;
    this.attempts = attempts;
    this.campaigns = campaigns;
    this.accounts = accounts;
    this.tokens = tokens;
    this.commands = commands;
  }

  @GetMapping("/join")
  String joinForm(Principal principal, HttpSession session, Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    model.addAttribute(
        "formToken", tokens.issue(CampaignWeb.formSession(session), "join:" + actor));
    return "demo-join";
  }

  @PostMapping("/join")
  Object join(
      @Valid @ModelAttribute JoinForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      HttpSession session) {
    if (errors.hasErrors()) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }
    UUID actor = CampaignWeb.actor(accounts, principal);
    String code = Objects.toString(form.inviteCode(), "").toUpperCase(Locale.ROOT);
    String location =
        attempts.check(
            request.getRemoteAddr(),
            () ->
                tokens.createOnce(
                    CampaignWeb.formSession(session),
                    "join:" + actor,
                    form.formToken(),
                    code,
                    () -> "/campaigns/" + invites.join(actor, code)));
    return CampaignWeb.go(request, location);
  }

  @GetMapping("/campaigns/{id}/players")
  String players(
      @PathVariable UUID id,
      Principal principal,
      HttpServletRequest request,
      HttpSession session,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    populate(id, actor, request, session, model);
    return "demo-players";
  }

  @PostMapping("/campaigns/{id}/invites")
  String issue(
      @PathVariable UUID id,
      @Valid @ModelAttribute IssueForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      HttpSession session,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    if (errors.hasErrors()) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }
    int uses = form.maxUses() == null ? 1 : form.maxUses();
    int days = form.expiresInDays() == null ? 7 : form.expiresInDays();
    var submission =
        tokens.execute(
            CampaignWeb.formSession(session),
            purpose(id, actor),
            form.formToken(),
            uses + ":" + days,
            () -> new FormTokens.Created<>(null, invites.issue(id, actor, uses, days)));
    populate(id, actor, request, session, model);
    model.addAttribute("inviteCode", submission.firstResponseValue());
    model.addAttribute("shownOnce", submission.replay());
    return "demo-players :: invites";
  }

  @PostMapping("/campaigns/{id}/invites/{inviteId}/revocations")
  Object revoke(
      @PathVariable UUID id,
      @PathVariable UUID inviteId,
      @ModelAttribute CommandForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      HttpSession session,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    if (errors.hasErrors()) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }
    invites.revoke(id, actor, inviteId, form.envelope());
    populate(id, actor, request, session, model);
    return CampaignWeb.region(request, "demo-players :: invites", "/campaigns/" + id + "/players");
  }

  private void populate(
      UUID id, UUID actor, HttpServletRequest request, HttpSession session, Model model) {
    model.addAttribute("campaign", campaigns.requireDm(id, actor));
    model.addAttribute("invites", invites.list(id, actor));
    model.addAttribute("members", invites.members(id, actor));
    model.addAttribute(
        "formToken", tokens.issue(CampaignWeb.formSession(session), purpose(id, actor)));
    model.addAttribute("version", commands.readClock(id, actor).version());
    model.addAttribute("ids", commands);
    String host = request.getServerName();
    model.addAttribute(
        "localAddress",
        host.equalsIgnoreCase("localhost")
            || host.equals("127.0.0.1")
            || host.equals("[::1]")
            || host.equals("::1"));
    String addressHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    String joinAddress =
        request.getScheme()
            + "://"
            + addressHost
            + ":"
            + request.getServerPort()
            + request.getContextPath()
            + "/register";
    model.addAttribute("joinAddress", joinAddress);
    model.addAttribute("qr", JoinAddressQr.encode(joinAddress));
  }

  private static String purpose(UUID id, UUID actor) {
    return "invite:" + id + ":" + actor;
  }

  record JoinForm(String inviteCode, @NotNull UUID formToken) {}

  record IssueForm(
      @Min(1) @Max(20) Integer maxUses,
      @Min(1) @Max(30) Integer expiresInDays,
      @NotNull UUID formToken) {}
}
