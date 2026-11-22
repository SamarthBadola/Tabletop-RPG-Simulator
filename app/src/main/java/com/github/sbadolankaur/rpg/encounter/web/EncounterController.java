package com.github.sbadolankaur.rpg.encounter.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.web.CampaignWeb;
import com.github.sbadolankaur.rpg.encounter.application.CombatActions;
import com.github.sbadolankaur.rpg.encounter.application.CombatOverrides;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands;
import com.github.sbadolankaur.rpg.encounter.application.Encounters;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls.Cover;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/campaigns/{campaign}/encounters")
class EncounterController {
  private final Campaigns campaigns;
  private final CampaignCommands campaignCommands;
  private final EncounterCommands commands;
  private final Encounters encounters;
  private final CombatActions combat;
  private final CombatOverrides overrides;
  private final AccountDirectory accounts;

  EncounterController(
      Campaigns campaigns,
      CampaignCommands campaignCommands,
      EncounterCommands commands,
      Encounters encounters,
      CombatActions combat,
      CombatOverrides overrides,
      AccountDirectory accounts) {
    this.campaigns = campaigns;
    this.campaignCommands = campaignCommands;
    this.commands = commands;
    this.encounters = encounters;
    this.combat = combat;
    this.overrides = overrides;
    this.accounts = accounts;
  }

  @GetMapping
  String list(@PathVariable UUID campaign, Principal principal, Model model) {
    UUID actor = dm(campaign, principal);
    model.addAttribute("encounters", encounters.list(campaign, actor));
    model.addAttribute("campaignId", campaign);
    envelope(model, campaignCommands.readClock(campaign, actor).version());
    return "demo-encounters";
  }

  @PostMapping
  Object create(
      @PathVariable UUID campaign,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    if (form.optional("mapId") != null) throw invalid();
    var result = commands.create(campaign, actor, form.envelope(), form.optional("name"));
    return CampaignWeb.go(
        request, path(campaign, UUID.fromString(result.data().path("encounterId").asString())));
  }

  @GetMapping("/{id}")
  String view(
      @PathVariable UUID campaign, @PathVariable UUID id, Principal principal, Model model) {
    populate(campaign, id, dm(campaign, principal), model, Long.MAX_VALUE, 50);
    return "demo-encounter";
  }

  @GetMapping("/{id}/{region:turn-order|acting-panel|log|clock}")
  String region(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @PathVariable String region,
      @RequestParam(defaultValue = "9223372036854775807") long before,
      @RequestParam(defaultValue = "50") int limit,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    populate(campaign, id, dm(campaign, principal), model, before, limit);
    return CampaignWeb.htmx(request) ? "demo-encounter :: " + region : "demo-encounter";
  }

  @PostMapping("/{id}/combatants")
  Object add(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    var result =
        encounters.add(
            campaign,
            id,
            actor,
            form.envelope(),
            new Encounters.Join(
                form.uuid("characterId"),
                form.optional("monsterKey"),
                form.number("count", 1),
                form.choice("initiativeMode", RollMode.class, RollMode.NORMAL),
                form.integer("fixedInitiative"),
                "end-of-round".equals(form.optional("joinMode")),
                form.optional("conditionKey"),
                form.flag("surprised")));
    return updated(campaign, id, actor, request, model, result, "turn-order");
  }

  @PostMapping("/{id}/combatants/{combatant}/removals")
  Object remove(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @PathVariable UUID combatant,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        encounters.remove(campaign, id, actor, new Fields(fields).envelope(), combatant),
        "turn-order");
  }

  @PostMapping("/{id}/start")
  Object start(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        encounters.start(
            campaign, id, actor, form.envelope(), Set.copyOf(form.ids("surprisedIds"))),
        "workspace");
  }

  @PostMapping("/{id}/initiative-ties")
  Object ties(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        encounters.ties(
            campaign,
            id,
            actor,
            form.envelope(),
            new Encounters.Tie(form.ids("order"), form.flag("rollOff"))),
        "workspace");
  }

  @PostMapping("/{id}/end-turn")
  Object endTurn(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        encounters.endTurn(campaign, id, actor, form.envelope(), form.uuid("actorId")),
        "workspace");
  }

  @PostMapping("/{id}/death-saves")
  Object deathSave(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        encounters.deathSave(campaign, id, actor, form.envelope(), form.uuid("actorId")),
        "workspace");
  }

  @PostMapping("/{id}/end")
  Object finish(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        encounters.finish(campaign, id, actor, new Fields(fields).envelope()),
        "workspace");
  }

  @PostMapping("/{id}/attacks")
  Object attack(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    var result =
        combat.attack(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatActions.Attack(
                form.uuid("actorId"),
                form.uuid("targetId"),
                form.optional("attackKey"),
                form.number("distanceFeet", 5),
                form.choice("cover", Cover.class, Cover.NONE),
                form.choice("rollMode", RollMode.class, RollMode.NORMAL),
                form.flag("knockOut"),
                form.flag("attackerObscured"),
                form.flag("targetObscured"),
                form.flag("nearbyHostile"),
                form.flag("fearSourceVisible"),
                form.flag("targetAbsent")));
    return updated(campaign, id, actor, request, model, result, "workspace");
  }

  @PostMapping("/{id}/dashes")
  Object dash(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return simple(campaign, id, fields, principal, request, model, CombatActions.Simple.DASH);
  }

  @PostMapping("/{id}/dodges")
  Object dodge(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return simple(campaign, id, fields, principal, request, model, CombatActions.Simple.DODGE);
  }

  @PostMapping("/{id}/disengages")
  Object disengage(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return simple(campaign, id, fields, principal, request, model, CombatActions.Simple.DISENGAGE);
  }

  @PostMapping("/{id}/hides")
  Object hide(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return simple(campaign, id, fields, principal, request, model, CombatActions.Simple.HIDE);
  }

  @PostMapping("/{id}/stands")
  Object stand(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return simple(campaign, id, fields, principal, request, model, CombatActions.Simple.STAND);
  }

  @PostMapping("/{id}/drops-prone")
  Object prone(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return simple(campaign, id, fields, principal, request, model, CombatActions.Simple.DROP_PRONE);
  }

  private Object simple(
      UUID campaign,
      UUID id,
      MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model,
      CombatActions.Simple action) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        combat.simple(campaign, id, actor, form.envelope(), form.uuid("actorId"), action),
        "workspace");
  }

  @PostMapping("/{id}/helps")
  Object help(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    UUID recipient = form.uuid("recipientId");
    UUID target = form.uuid("targetId");
    if (recipient == null) {
      recipient = target;
      target = null;
    }
    var result =
        combat.help(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatActions.Help(
                form.uuid("actorId"), recipient, target, form.number("distanceFeet", 5)));
    return updated(campaign, id, actor, request, model, result, "workspace");
  }

  @PostMapping("/{id}/readies")
  Object ready(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    var result =
        combat.ready(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatActions.Ready(
                form.uuid("actorId"), form.optional("trigger"), form.optional("actionKind")));
    return updated(campaign, id, actor, request, model, result, "workspace");
  }

  @PostMapping("/{id}/object-uses")
  Object objectUse(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    var result =
        combat.objectUse(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatActions.ObjectUse(
                form.uuid("actorId"),
                java.util.Objects.toString(form.optional("note"), ""),
                form.flag("usesAction")));
    return updated(campaign, id, actor, request, model, result, "workspace");
  }

  @PostMapping("/{id}/grapples")
  Object grapple(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return contest(
        campaign, id, fields, principal, request, model, CombatActions.ContestKind.GRAPPLE);
  }

  @PostMapping("/{id}/shoves")
  Object shove(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return contest(
        campaign,
        id,
        fields,
        principal,
        request,
        model,
        "push".equals(new Fields(fields).optional("shoveKind"))
            ? CombatActions.ContestKind.SHOVE_PUSH
            : CombatActions.ContestKind.SHOVE_PRONE);
  }

  @PostMapping("/{id}/grapple-escapes")
  Object escape(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return contest(
        campaign, id, fields, principal, request, model, CombatActions.ContestKind.ESCAPE);
  }

  private Object contest(
      UUID campaign,
      UUID id,
      MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model,
      CombatActions.ContestKind kind) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        combat.contest(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatActions.Contest(
                form.uuid("actorId"),
                form.uuid("targetId"),
                form.number("distanceFeet", 5),
                kind,
                form.flag("acrobatics"))),
        "workspace");
  }

  @PostMapping("/{id}/stabilizations")
  Object stabilize(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    return updated(
        campaign,
        id,
        actor,
        request,
        model,
        combat.firstAid(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatActions.Help(
                form.uuid("actorId"), form.uuid("targetId"), null, form.number("distanceFeet", 5))),
        "workspace");
  }

  @PostMapping("/{id}/saving-throws")
  Object save(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return check(campaign, id, fields, principal, request, model, true);
  }

  @PostMapping("/{id}/ability-checks")
  Object check(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    return check(campaign, id, fields, principal, request, model, false);
  }

  private Object check(
      UUID campaign,
      UUID id,
      MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model,
      boolean save) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    var result =
        combat.check(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatActions.Check(
                form.uuid("actorId"),
                form.ids(save ? "targetIds" : "subjectIds"),
                form.ability(),
                form.optional("skillKey"),
                form.integer("dc"),
                save));
    return updated(campaign, id, actor, request, model, result, "workspace");
  }

  @GetMapping("/{id}/overrides/new")
  String override(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    populate(campaign, id, actor, model, Long.MAX_VALUE, 50);
    model.addAttribute(
        "preview",
        overrides.preview(
            campaign,
            id,
            actor,
            form.choice("kind", CombatOverrides.Kind.class, null),
            form.uuid("combatantId"),
            form.uuid("entryId")));
    return CampaignWeb.htmx(request) ? "demo-encounter :: override-panel" : "demo-encounter";
  }

  @PostMapping("/{id}/overrides")
  Object apply(
      @PathVariable UUID campaign,
      @PathVariable UUID id,
      @RequestParam MultiValueMap<String, String> fields,
      Principal principal,
      HttpServletRequest request,
      Model model) {
    UUID actor = dm(campaign, principal);
    var form = new Fields(fields);
    String visibility = form.optional("visibility");
    if (visibility != null && !Set.of("players", "quiet").contains(visibility)) throw invalid();
    var result =
        overrides.apply(
            campaign,
            id,
            actor,
            form.envelope(),
            new CombatOverrides.Correction(
                form.choice("kind", CombatOverrides.Kind.class, null),
                form.uuid("combatantId"),
                form.uuid("entryId"),
                form.integer("amount"),
                form.optional("conditionKey"),
                form.uuid("sourceId"),
                form.optional("resourceKey"),
                form.optional("reason"),
                "quiet".equals(visibility)));
    return updated(campaign, id, actor, request, model, result, "workspace");
  }

  private Object updated(
      UUID campaign,
      UUID id,
      UUID actor,
      HttpServletRequest request,
      Model model,
      Reply result,
      String fragment) {
    if (CampaignWeb.htmx(request)) {
      populate(campaign, id, actor, model, Long.MAX_VALUE, 50);
      model.addAttribute("result", result.data());
      model.addAttribute("replay", result.replay());
    }
    return CampaignWeb.region(request, "demo-encounter :: " + fragment, path(campaign, id));
  }

  private void populate(UUID campaign, UUID id, UUID actor, Model model, long before, int limit) {
    var view = encounters.view(campaign, id, actor, before, limit);
    model.addAttribute("fight", view);
    model.addAttribute(
        "acting",
        view.combatants().stream()
            .filter(row -> row.id().equals(view.table().acting()))
            .findFirst()
            .orElse(null));
    model.addAttribute("campaignId", campaign);
    model.addAttribute("commandPath", path(campaign, id));
    model.addAttribute("gameSeconds", campaignCommands.readClock(campaign, actor).gameSeconds());
    model.addAttribute("conditions", new java.util.TreeMap<>(CombatConditions.names()));
    model.addAttribute("covers", Cover.values());
    model.addAttribute("abilities", Ability.values());
    envelope(model, view.table().version());
  }

  private void envelope(Model model, long version) {
    model.addAttribute("actionId", campaignCommands.newActionId());
    model.addAttribute("expectedVersion", version);
  }

  private UUID dm(UUID campaign, Principal principal) {
    UUID actor = CampaignWeb.actor(accounts, principal);
    campaigns.requireDm(campaign, actor);
    return actor;
  }

  private static String path(UUID campaign, UUID id) {
    return "/campaigns/" + campaign + "/encounters/" + id;
  }

  private static CampaignException invalid() {
    return new CampaignException(Code.VALIDATION_FAILED);
  }

  private record Fields(MultiValueMap<String, String> values) {
    String optional(String key) {
      var list = values.get(key);
      if (list == null || list.isEmpty()) return null;
      if (list.size() != 1) throw invalid();
      return list.getFirst().isBlank() ? null : list.getFirst().strip();
    }

    UUID uuid(String key) {
      return parse(optional(key), UUID::fromString);
    }

    List<UUID> ids(String key) {
      return values.getOrDefault(key, List.of()).stream()
          .map(
              raw -> {
                if (raw.isBlank()) throw invalid();
                return parse(raw, UUID::fromString);
              })
          .toList();
    }

    Integer integer(String key) {
      return parse(optional(key), Integer::valueOf);
    }

    int number(String key, int defaultValue) {
      Integer value = integer(key);
      return value == null ? defaultValue : value;
    }

    <T extends Enum<T>> T choice(String key, Class<T> type, T defaultValue) {
      var token = optional(key);
      return token == null
          ? defaultValue
          : parse(token, raw -> Enum.valueOf(type, raw.toUpperCase(Locale.ROOT).replace('-', '_')));
    }

    Ability ability() {
      var value = optional("ability");
      return value == null || value.length() < 3
          ? null
          : parse(value.substring(0, 3), raw -> Ability.valueOf(raw.toUpperCase(Locale.ROOT)));
    }

    boolean flag(String key) {
      String value = optional(key);
      return value != null && (value.equals("true") || value.equals("on"));
    }

    Envelope envelope() {
      var id = uuid("actionId");
      Long version = parse(optional("expectedVersion"), Long::valueOf);
      if (id == null || version == null || version < 0) throw invalid();
      return new Envelope(id, version);
    }

    private static <T> T parse(String raw, Function<String, T> parser) {
      if (raw == null) return null;
      try {
        return parser.apply(raw);
      } catch (IllegalArgumentException bad) {
        throw invalid();
      }
    }
  }
}
