package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignPlay;
import com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.ClockUnit;
import com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.Transition;
import com.github.sbadolankaur.rpg.campaign.application.CampaignView;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.persistence.PlayStore.Light;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Locale;
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
class PlayController {
  private final CampaignPlay play;
  private final CampaignCommands commands;
  private final Campaigns campaigns;
  private final AccountDirectory accounts;

  PlayController(
      CampaignPlay play,
      CampaignCommands commands,
      Campaigns campaigns,
      AccountDirectory accounts) {
    this.play = play;
    this.commands = commands;
    this.campaigns = campaigns;
    this.accounts = accounts;
  }

  @GetMapping
  String home(@PathVariable UUID id, Principal principal, Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    workspace(id, actor, model);
    model.addAttribute("hasRulesContent", !campaigns.contentFor(id, actor).packHashes().isEmpty());
    return "demo-campaign";
  }

  @PostMapping("/sessions")
  Object start(
      @PathVariable UUID id,
      @ModelAttribute CommandForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(id, principal);
    validate(errors);
    var reply = play.start(id, actor, form.envelope());
    workspace(id, actor, model);
    model.addAttribute("sessionResult", reply.data());
    return CampaignWeb.region(request, "demo-campaign :: session-bar", "/campaigns/" + id);
  }

  @PostMapping("/sessions/{sessionId}/{transition:pause|resume|end}")
  Object transition(
      @PathVariable UUID id,
      @PathVariable UUID sessionId,
      @PathVariable String transition,
      @ModelAttribute CommandForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(id, principal);
    validate(errors);
    Transition action = Transition.valueOf(transition.toUpperCase(Locale.ROOT));
    var reply = play.transition(id, actor, form.envelope(), sessionId, action);
    workspace(id, actor, model);
    model.addAttribute("sessionResult", reply.data());
    return CampaignWeb.region(request, "demo-campaign :: session-bar", "/campaigns/" + id);
  }

  @PostMapping("/clock/advances")
  Object advance(
      @PathVariable UUID id,
      @ModelAttribute ClockForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(id, principal);
    validate(errors);
    if (form.amount() == null || form.unit() == null) throw invalid();
    var reply =
        play.advance(
            id,
            actor,
            new CommandForm(form.actionId(), form.expectedVersion()).envelope(),
            form.amount(),
            parse(ClockUnit.class, form.unit()));
    workspace(id, actor, model);
    model.addAttribute("clockResult", reply.data());
    return CampaignWeb.region(request, "demo-campaign :: clock", "/campaigns/" + id);
  }

  @GetMapping("/scenes")
  String scenes(@PathVariable UUID id, Principal principal, Model model) {
    sceneList(id, dm(id, principal), model);
    return "demo-scenes";
  }

  @PostMapping({"/scenes", "/scenes/{sceneId}/edits"})
  Object saveScene(
      @PathVariable UUID id,
      @PathVariable(required = false) UUID sceneId,
      @ModelAttribute SceneForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(id, principal);
    validate(errors);
    String name = form.name() == null ? "" : form.name().strip();
    if (name.isEmpty()
        || name.length() > 80
        || (form.notes() != null && form.notes().length() > 2000)) {
      throw invalid();
    }
    play.saveScene(
        id,
        actor,
        new CommandForm(form.actionId(), form.expectedVersion()).envelope(),
        sceneId,
        new CampaignPlay.SceneInput(name, form.notes(), parse(Light.class, form.light())));
    sceneList(id, actor, model);
    return CampaignWeb.region(request, "demo-scenes :: scene-list", "/campaigns/" + id + "/scenes");
  }

  @PostMapping("/scenes/{sceneId}/activations")
  Object activateScene(
      @PathVariable UUID id,
      @PathVariable UUID sceneId,
      @ModelAttribute CommandForm form,
      BindingResult errors,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(id, principal);
    validate(errors);
    play.activateScene(id, actor, form.envelope(), sceneId);
    sceneList(id, actor, model);
    return CampaignWeb.region(request, "demo-scenes :: scene-list", "/campaigns/" + id + "/scenes");
  }

  private UUID dm(UUID id, Principal principal) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    return actor;
  }

  private void workspace(UUID id, UUID actor, Model model) {
    var view = play.workspace(id, actor);
    model.addAttribute("campaign", view.campaign());
    model.addAttribute("clock", view.clock());
    model.addAttribute("gameSession", view.session());
    model.addAttribute("scene", view.scene());
    model.addAttribute("isDm", view.campaign().role() == CampaignView.Role.DM);
    model.addAttribute("events", commands.recentEvents(id, actor));
    model.addAttribute("version", view.clock().version());
    model.addAttribute("ids", commands);
  }

  private void sceneList(UUID id, UUID actor, Model model) {
    model.addAttribute("campaign", campaigns.requireDm(id, actor));
    model.addAttribute("scenes", play.scenes(id, actor));
    model.addAttribute("version", commands.readClock(id, actor).version());
    model.addAttribute("ids", commands);
  }

  private static void validate(BindingResult errors) {
    if (errors.hasErrors()) throw invalid();
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value) {
    if (value == null) throw invalid();
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw invalid();
    }
  }

  private static CampaignException invalid() {
    return new CampaignException(CampaignException.Code.VALIDATION_FAILED);
  }

  record ClockForm(UUID actionId, Long expectedVersion, Long amount, String unit) {}

  record SceneForm(UUID actionId, Long expectedVersion, String name, String notes, String light) {}
}
