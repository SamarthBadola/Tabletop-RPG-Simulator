package com.github.sbadolankaur.rpg.character.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignView.Role;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.web.CampaignWeb;
import com.github.sbadolankaur.rpg.campaign.web.CommandForm;
import com.github.sbadolankaur.rpg.character.application.Characters;
import com.github.sbadolankaur.rpg.character.application.Characters.Advancement;
import com.github.sbadolankaur.rpg.character.application.Characters.Creation;
import com.github.sbadolankaur.rpg.character.application.Characters.Edit;
import com.github.sbadolankaur.rpg.character.application.Characters.Step;
import com.github.sbadolankaur.rpg.engine.character.AbilityGeneration;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
@RequestMapping("/campaigns/{id}/characters")
class CharacterController {
  private final Characters characters;
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final AccountDirectory accounts;

  CharacterController(
      Characters characters,
      Campaigns campaigns,
      CampaignCommands commands,
      AccountDirectory accounts) {
    this.characters = characters;
    this.campaigns = campaigns;
    this.commands = commands;
    this.accounts = accounts;
  }

  @GetMapping
  String list(@PathVariable UUID id, Principal principal, Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    var member = campaigns.requireMember(id, actor);
    model.addAttribute("campaign", member);
    model.addAttribute("characters", characters.list(id, actor));
    model.addAttribute("dm", member.role() == Role.DM);
    return "demo-characters";
  }

  @GetMapping("/new")
  String start(@PathVariable UUID id, Principal principal, Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    return creation(
        id,
        actor,
        new Fields(new org.springframework.util.LinkedMultiValueMap<>()).creation(),
        Step.SPECIES,
        model,
        null);
  }

  @PostMapping
  Object create(
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> parameters,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireMember(id, actor);
    var input = new Fields(parameters);
    var form = input.creation();
    Step step = input.enumValue(Step.class, "step");
    if (step == null) throw invalid();

    if (input.flag("rollSet")) {
      if (step != Step.ABILITIES) throw invalid();
      characters.validateCreation(id, actor, form, Step.CLASS);
      var command = input.command();
      var result = characters.roll(id, actor, command.envelope(), form);
      var rolled =
          result.data().path("scores").valueStream().map(value -> value.intValue()).toList();
      String page = creation(id, actor, form, Step.ABILITIES, model, rolled);
      return CampaignWeb.htmx(request) ? page + " :: wizard" : page;
    }
    if (step == Step.REVIEW) {
      var result = characters.create(id, actor, input.command().envelope(), form);
      return CampaignWeb.go(
          request, path(id, UUID.fromString(result.data().path("characterId").asString())));
    }
    characters.validateCreation(id, actor, form, step);
    String page = creation(id, actor, form, Step.values()[step.ordinal() + 1], model, null);
    return CampaignWeb.htmx(request) ? page + " :: wizard" : page;
  }

  private String creation(
      UUID id, UUID actor, Creation form, Step stage, Model model, List<Integer> rolled) {
    var view = characters.wizard(id, actor, form);
    if (stage == Step.CLASS && form.classKey() == null && view.classes().size() == 1) {
      form =
          new Creation(
              form.speciesKey(),
              view.classes().getFirst().key(),
              null,
              form.abilityMethod(),
              form.abilityScores(),
              null,
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              null,
              List.of());
      view = characters.wizard(id, actor, form);
    }
    model.addAttribute("wizard", view);
    model.addAttribute("form", form);
    model.addAttribute("creationStep", stage.name().toLowerCase(Locale.ROOT));
    model.addAttribute("abilityNames", Ability.values());
    model.addAttribute("rolls", rolled == null ? view.rolled() : rolled);
    model.addAttribute(
        "languages",
        List.of(
            "dwarvish",
            "elvish",
            "giant",
            "gnomish",
            "goblin",
            "halfling",
            "orc",
            "abyssal",
            "celestial",
            "draconic",
            "deep_speech",
            "infernal",
            "primordial",
            "sylvan",
            "undercommon"));
    model.addAttribute("campaignId", id);
    envelope(id, actor, model);
    return "demo-character-create";
  }

  @GetMapping("/{characterId}")
  String sheet(
      @PathVariable UUID id, @PathVariable UUID characterId, Principal principal, Model model) {
    return sheet(id, CampaignWeb.actor(accounts, principal), characterId, model);
  }

  private String sheet(UUID id, UUID actor, UUID characterId, Model model) {
    model.addAttribute("sheet", characters.sheet(id, actor, characterId));
    model.addAttribute("campaignId", id);
    envelope(id, actor, model);
    return "demo-character-sheet";
  }

  @PostMapping("/{characterId}/edits")
  Object edit(
      @PathVariable UUID id,
      @PathVariable UUID characterId,
      Principal principal,
      @RequestParam MultiValueMap<String, String> parameters,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    characters.sheet(
        id, actor, characterId); // Authorize before interpreting another character's inputs.
    var input = new Fields(parameters);
    characters.edit(id, actor, characterId, input.command().envelope(), input.edit());
    sheet(id, actor, characterId, model);
    return CampaignWeb.region(request, "demo-character-sheet :: sheet", path(id, characterId));
  }

  @PostMapping("/{characterId}/level-grants")
  Object grant(
      @PathVariable UUID id,
      @PathVariable UUID characterId,
      Principal principal,
      @RequestParam MultiValueMap<String, String> parameters,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(id, actor);
    var input = new Fields(parameters);
    characters.grant(
        id, actor, characterId, input.command().envelope(), input.optional("classKey"));
    sheet(id, actor, characterId, model);
    return CampaignWeb.region(request, "demo-character-sheet :: sheet", path(id, characterId));
  }

  @GetMapping("/{characterId}/level-ups")
  String levelForm(
      @PathVariable UUID id, @PathVariable UUID characterId, Principal principal, Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    return level(
        id,
        actor,
        characterId,
        "class",
        new Advancement(null, null, null, Map.of(), null, false, null, List.of(), List.of()),
        model);
  }

  @PostMapping("/{characterId}/level-ups")
  Object advance(
      @PathVariable UUID id,
      @PathVariable UUID characterId,
      Principal principal,
      @RequestParam MultiValueMap<String, String> parameters,
      HttpServletRequest request,
      Model model) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    var input = new Fields(parameters);
    characters.sheet(id, actor, characterId);
    Advancement form = input.advancement();
    String stage = input.optional("step");
    if (stage == null) throw invalid();
    if (stage.equals("review")) {
      characters.advance(id, actor, characterId, input.command().envelope(), form);
      sheet(id, actor, characterId, model);
      return CampaignWeb.region(request, "demo-character-sheet :: sheet", path(id, characterId));
    }
    String next =
        switch (stage) {
          case "class" -> "hit-points";
          case "hit-points" -> "ability-scores";
          case "ability-scores" -> "review";
          default -> throw invalid();
        };
    String page = level(id, actor, characterId, next, form, model);
    return CampaignWeb.htmx(request) ? page + " :: wizard" : page;
  }

  private String level(
      UUID campaign, UUID actor, UUID character, String stage, Advancement form, Model model) {
    var plan = characters.levelPlan(campaign, actor, character, form);
    if (stage.equals("review")
        && plan.improvementRequired()
        && form.improvements().isEmpty()
        && form.featKey() == null) throw invalid();
    model.addAttribute("plan", plan);
    model.addAttribute("form", form);
    model.addAttribute("levelStep", stage);
    model.addAttribute("campaignId", campaign);
    model.addAttribute("characterId", character);
    model.addAttribute("abilityNames", Ability.values());
    envelope(campaign, actor, model);
    return "demo-character-level";
  }

  private void envelope(UUID id, UUID actor, Model model) {
    model.addAttribute("actionId", commands.newActionId());
    model.addAttribute("expectedVersion", commands.readClock(id, actor).version());
  }

  private static String path(UUID campaign, UUID id) {
    return "/campaigns/" + campaign + "/characters/" + id;
  }

  private static CampaignException invalid() {
    return new CampaignException(Code.VALIDATION_FAILED);
  }

  private record Fields(MultiValueMap<String, String> values) {
    String optional(String name) {
      String raw = values.getFirst(name);
      return raw == null || raw.isBlank() ? null : raw.strip();
    }

    List<String> many(String name) {
      List<String> raw =
          values.getOrDefault(name, List.of()).stream()
              .map(String::strip)
              .filter(value -> !value.isEmpty())
              .toList();
      if (raw.size() > 100 || raw.stream().anyMatch(value -> value.length() > 200)) throw invalid();
      return raw;
    }

    boolean flag(String key) {
      return "true".equalsIgnoreCase(optional(key)) || "on".equalsIgnoreCase(optional(key));
    }

    <T extends Enum<T>> T enumValue(Class<T> type, String key) {
      String raw = optional(key);
      if (raw == null) return null;
      try {
        return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException error) {
        throw invalid();
      }
    }

    Integer integer(String key) {
      String raw = optional(key);
      return raw == null ? null : integerValue(raw);
    }

    private static int integerValue(String text) {
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException error) {
        throw invalid();
      }
    }

    private static UUID uuid(String raw) {
      try {
        return raw == null ? null : UUID.fromString(raw);
      } catch (IllegalArgumentException error) {
        throw invalid();
      }
    }

    CommandForm command() {
      String version = optional("expectedVersion");
      try {
        return new CommandForm(
            uuid(optional("actionId")), version == null ? null : Long.parseLong(version));
      } catch (NumberFormatException error) {
        throw invalid();
      }
    }

    Creation creation() {
      return new Creation(
          optional("speciesKey"),
          optional("classKey"),
          optional("fightingStyleKey"),
          enumValue(AbilityGeneration.class, "abilityMethod"),
          many("abilityScores").stream().map(Fields::integerValue).toList(),
          optional("backgroundKey"),
          many("skillChoices"),
          many("skillReplacements"),
          many("languageChoices"),
          many("equipmentChoices"),
          optional("name"),
          many("expertiseChoices"));
    }

    Advancement advancement() {
      var changes = new EnumMap<Ability, Integer>(Ability.class);
      for (int i = 0; i < 2; i++) {
        String base = "abilityImprovements[" + i + "].";
        Ability ability = enumValue(Ability.class, base + "ability");
        Integer amount = integer(base + "amount");
        if (ability == null && amount == null) continue;
        if (ability == null || amount == null || changes.putIfAbsent(ability, amount) != null)
          throw invalid();
      }
      return new Advancement(
          optional("classKey"),
          optional("subclassKey"),
          optional("hitPointMethod"),
          Map.copyOf(changes),
          optional("featKey"),
          flag("acknowledgeLoss"),
          optional("fightingStyleKey"),
          many("expertiseChoices"),
          many("bonusSkills"));
    }

    Edit edit() {
      var slots = new LinkedHashMap<UUID, String>();
      for (String entry : many("equipment")) {
        String[] parts = entry.split("=", -1);
        if (parts.length != 2) throw invalid();
        if (parts[1].isEmpty()) continue;
        if (slots.putIfAbsent(uuid(parts[0]), parts[1]) != null) throw invalid();
      }
      return new Edit(
          optional("section"),
          optional("name"),
          integer("currentHp"),
          integer("temporaryHp"),
          Map.copyOf(slots),
          many("abilityScores").stream().map(Fields::integerValue).toList(),
          optional("conditionKey"),
          flag("addCondition"),
          optional("resourceKey"),
          integer("used"),
          optional("itemId") == null ? null : uuid(optional("itemId")),
          integer("cost"),
          flag("useElectrum"));
    }
  }
}
