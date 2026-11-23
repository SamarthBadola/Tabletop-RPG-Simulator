package com.github.sbadolankaur.rpg.character.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Audience;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Change;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Kind;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.CampaignSettings;
import com.github.sbadolankaur.rpg.campaign.application.CampaignView.Role;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore;
import com.github.sbadolankaur.rpg.character.application.CharacterCatalog.Choice;
import com.github.sbadolankaur.rpg.character.application.CharacterCreation.EquipmentGroup;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore.ClassLevel;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore.Header;
import com.github.sbadolankaur.rpg.character.persistence.CharacterStore.State;
import com.github.sbadolankaur.rpg.engine.character.AbilityGeneration;
import com.github.sbadolankaur.rpg.engine.character.CharacterRules;
import com.github.sbadolankaur.rpg.engine.content.ContentExpression;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AbilityScores;
import com.github.sbadolankaur.rpg.engine.kernel.HitPointRules;
import com.github.sbadolankaur.rpg.engine.kernel.SettingsView;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Characters {
  private static final int FIGHTER_SCOPE_LIMIT = 5;
  private final Campaigns campaigns;
  private final CampaignCommands commands;
  private final CampaignSettings settings;
  private final CharacterStore store;
  private final ReactionStore reactions;
  private final SecureRandom random = new SecureRandom();

  Characters(
      Campaigns campaigns,
      CampaignCommands commands,
      CampaignSettings settings,
      CharacterStore store,
      ReactionStore reactions) {
    this.campaigns = campaigns;
    this.commands = commands;
    this.settings = settings;
    this.store = store;
    this.reactions = reactions;
  }

  @Transactional(readOnly = true)
  public List<CharacterSheets.View> list(UUID campaign, UUID actor) {
    boolean dm = campaigns.requireMember(campaign, actor).role() == Role.DM;
    var catalog = catalog(campaign, actor);
    var effective = settings.effective(campaign, actor);
    return store.list(campaign, actor, dm).stream()
        .map(header -> CharacterSheets.derive(store.read(header), catalog, effective, dm))
        .toList();
  }

  @Transactional(readOnly = true)
  public CharacterSheets.View sheet(UUID campaign, UUID actor, UUID id) {
    var access = access(campaign, actor, id);
    return CharacterSheets.derive(
        store.read(access.character()),
        catalog(campaign, actor),
        settings.effective(campaign, actor),
        access.dm());
  }

  @Transactional(readOnly = true)
  public CharacterSheets.View combatSheet(
      UUID campaign, UUID actor, UUID id, ContentSet content, SettingsView effective) {
    var access = access(campaign, actor, id);
    return CharacterSheets.derive(
        store.read(access.character()), new CharacterCatalog(content), effective, access.dm());
  }

  @Transactional(readOnly = true)
  public Wizard wizard(UUID campaign, UUID actor, Creation form) {
    boolean dm = campaigns.requireMember(campaign, actor).role() == Role.DM;
    var catalog = catalog(campaign, actor);
    var effective = settings.effective(campaign, actor);
    var rolled = store.rolled(campaign, actor);
    AbilityGeneration method =
        rolled.map(CharacterStore.Rolled::method).orElseGet(() -> generation(effective));
    return new Wizard(
        catalog.choices("species"),
        catalog.choices("class"),
        catalog.choices("background"),
        form.classKey() == null
            ? catalog.styles("srd-5.1:fighter")
            : startingStyles(catalog, form.classKey()),
        form.classKey() == null
            ? List.of()
            : catalog
                .require("class", form.classKey())
                .object("proficiencies")
                .object("skillChoice")
                .strings("from"),
        form.classKey() == null ? List.of() : CharacterCreation.equipment(catalog, form.classKey()),
        form.classKey() != null && catalog.require("class", form.classKey()).has("startingGold"),
        method,
        rolled.map(CharacterStore.Rolled::values).orElseGet(List::of),
        dm,
        CharacterSheets.SKILLS.keySet().stream().sorted().toList(),
        "srd-5.1:rogue".equals(form.classKey()));
  }

  public void validateCreation(UUID campaign, UUID actor, Creation form, Step through) {
    var role = campaigns.requireMember(campaign, actor).role();
    validate(
        catalog(campaign, actor),
        settings.effective(campaign, actor),
        campaign,
        actor,
        role,
        form,
        through);
  }

  public Reply roll(UUID campaign, UUID actor, Envelope envelope, Creation form) {
    campaigns.requireMember(campaign, actor);
    return commands.runForMember(
        campaign,
        actor,
        envelope,
        Kind.CHARACTER_ROLL,
        form.abilityMethod(),
        clock -> {
          if (form.abilityMethod() == null || !form.abilityMethod().rolled())
            throw incorrectAbilityMethod();
          var existing = store.rolled(campaign, actor);
          var effective = settings.effective(campaign, actor);
          if (existing.isPresent()) {
            if (existing.get().method() != form.abilityMethod()) throw incorrectAbilityMethod();
            return Change.unchanged(clock, Map.of("scores", existing.get().values()));
          }
          if (generation(effective) != form.abilityMethod()) throw incorrectAbilityMethod();
          var rolled = form.abilityMethod().roll(random::nextInt);
          store.rememberRoll(campaign, actor, form.abilityMethod(), rolled.scores());
          return Change.at(
                  clock,
                  "AbilityScoresRolled",
                  Audience.DM,
                  Map.of(
                      "player",
                      actor,
                      "method",
                      form.abilityMethod(),
                      "terms",
                      rolled.terms(),
                      "scores",
                      rolled.scores()),
                  Map.of("scores", rolled.scores()))
              .withSettingsRead(
                  Map.of(
                      "abilityScoreGenerationMethod",
                      effective.value("abilityScoreGenerationMethod")));
        });
  }

  public Reply create(UUID campaign, UUID actor, Envelope envelope, Creation form) {
    return commands.runForMember(
        campaign,
        actor,
        envelope,
        Kind.CHARACTER_CREATE,
        form,
        clock -> {
          var role = campaigns.requireMember(campaign, actor).role();
          var catalog = catalog(campaign, actor);
          var effective = settings.effective(campaign, actor);
          validate(catalog, effective, campaign, actor, role, form, Step.REVIEW);
          var base = CharacterCreation.allocate(form.abilityScores());
          var scores =
              CharacterRules.scores(base, catalog.speciesBonuses(form.speciesKey()), Map.of(), 30);
          int die = catalog.require("class", form.classKey()).number("hitDie");
          int hp = CharacterRules.maximumHp(scores, List.of(die));
          var proficiencies = proficiencies(catalog, form, effective);
          var starter =
              CharacterCreation.starter(
                  catalog, form.classKey(), form.backgroundKey(), form.equipmentChoices());
          var gold =
              starter.goldDice() == null
                  ? null
                  : DiceExpression.parse(starter.goldDice()).roll(random::nextInt);
          long copper =
              starter.backgroundCopper()
                  + (gold == null
                      ? 0L
                      : Math.multiplyExact((long) gold.total(), 100L * starter.goldMultiplier()));
          UUID id = ServerIds.at(Instant.now());
          var header =
              new Header(
                  id,
                  campaign,
                  actor,
                  name(form.name()),
                  form.speciesKey(),
                  form.backgroundKey(),
                  form.fightingStyleKey(),
                  hp,
                  0,
                  copper,
                  0);
          store.create(
              header,
              form.classKey(),
              die,
              base,
              proficiencies,
              inventory(catalog, starter.items()),
              catalog.resources(form.classKey(), 1));
          if (form.abilityMethod().rolled()) store.consumeRoll(campaign, actor);
          return Change.at(
                  clock,
                  "CharacterCreated",
                  Audience.DM,
                  Map.of(
                      "characterId",
                      id,
                      "choices",
                      form,
                      "startingCopper",
                      copper,
                      "startingGoldDice",
                      gold == null ? List.of() : gold.terms()),
                  Map.of("characterId", id))
              .withSettingsRead(
                  Map.of(
                      "abilityScoreGenerationMethod",
                      effective.value("abilityScoreGenerationMethod"),
                      "duplicateProficiencyReplacement",
                      effective.value("duplicateProficiencyReplacement")));
        });
  }

  private static List<CharacterStore.Item> inventory(
      CharacterCatalog catalog, Map<String, Integer> items) {
    var rows = new ArrayList<CharacterStore.Item>();
    items.forEach(
        (key, quantity) -> {
          String category = catalog.require("item", key).text("category");
          if (category.equals("weapon") || category.equals("armor") || category.equals("shield")) {
            for (int i = 0; i < quantity; i++) {
              rows.add(new CharacterStore.Item(ServerIds.at(Instant.now()), key, 1, null));
            }
          } else {
            rows.add(new CharacterStore.Item(ServerIds.at(Instant.now()), key, quantity, null));
          }
        });
    return List.copyOf(rows);
  }

  private void validate(
      CharacterCatalog catalog,
      SettingsView effective,
      UUID campaign,
      UUID actor,
      Role role,
      Creation form,
      Step through) {
    catalog.require("species", form.speciesKey());
    if (through.ordinal() >= Step.CLASS.ordinal()) {
      catalog.require("class", form.classKey());
      var styles = startingStyles(catalog, form.classKey());
      if (styles.isEmpty()
          ? form.fightingStyleKey() != null
          : styles.stream().noneMatch(choice -> choice.key().equals(form.fightingStyleKey())))
        throw invalid();
    }
    if (through.ordinal() >= Step.ABILITIES.ordinal()) {
      var set = store.rolled(campaign, actor);
      var method = set.map(CharacterStore.Rolled::method).orElseGet(() -> generation(effective));
      if (form.abilityMethod() != method) throw incorrectAbilityMethod();
      try {
        method.validate(
            form.abilityScores(),
            set.map(CharacterStore.Rolled::values).orElseGet(List::of),
            role == Role.DM);
      } catch (IllegalArgumentException invalid) {
        throw invalid();
      }
    }
    if (through.ordinal() >= Step.BACKGROUND.ordinal()) proficiencies(catalog, form, effective);
    if (through.ordinal() >= Step.EQUIPMENT.ordinal()) {
      CharacterCreation.starter(
          catalog, form.classKey(), form.backgroundKey(), form.equipmentChoices());
    }
    if (through == Step.REVIEW) name(form.name());
  }

  private static List<Choice> startingStyles(CharacterCatalog catalog, String classKey) {
    return catalog.level(classKey, 1).strings("grants").contains("fighting_style")
            || "srd-5.1:fighter".equals(classKey)
        ? catalog.styles(classKey)
        : List.of();
  }

  private static List<CharacterStore.Proficiency> proficiencies(
      CharacterCatalog catalog, Creation form, SettingsView effective) {
    var source =
        CharacterCreation.proficiencies(
            catalog,
            form.speciesKey(),
            form.classKey(),
            form.backgroundKey(),
            form.skillChoices(),
            form.languageChoices(),
            effective.get("duplicateProficiencyReplacement", String.class));
    var trained =
        CharacterCreation.replacements(
            source, form.skillChoices(), form.skillReplacements(), form.classKey());
    var additions = new ArrayList<>(trained);
    additions.addAll(
        expertise(
            trained,
            form.expertiseChoices(),
            "srd-5.1:rogue".equals(form.classKey()) ? 2 : 0,
            form.classKey(),
            true));
    return List.copyOf(additions);
  }

  private static List<CharacterStore.Proficiency> expertise(
      List<CharacterStore.Proficiency> trained,
      List<String> choices,
      int count,
      String source,
      boolean tools) {
    if (choices.size() != count
        || new HashSet<>(choices).size() != count
        || choices.stream()
            .anyMatch(
                key ->
                    trained.stream()
                        .noneMatch(
                            entry ->
                                entry.key().equals(key)
                                    && ("skill".equals(entry.kind())
                                        || tools
                                            && "tool".equals(entry.kind())
                                            && "thieves_tools".equals(key))))) throw invalid();
    return choices.stream()
        .map(key -> new CharacterStore.Proficiency("expertise", key, source))
        .toList();
  }

  public Reply grant(UUID campaign, UUID actor, UUID id, Envelope envelope, String classKey) {
    campaigns.requireDm(campaign, actor);
    access(campaign, actor, id);
    return commands.run(
        campaign,
        actor,
        envelope,
        Kind.CHARACTER_LEVEL_GRANT,
        new Target(id, classKey),
        clock -> {
          var state = state(campaign, actor, id);
          requireUnfrozen(campaign, id);
          var selected = selectedClass(state, classKey);
          catalog(campaign, actor).level(selected.key(), selected.level() + 1);
          if (state.rawHealth().size() >= FIGHTER_SCOPE_LIMIT) throw invalid();
          if (state.grantedClass() != null) {
            if (!state.grantedClass().equals(selected.key())) throw invalid();
            return Change.unchanged(clock, Map.of("characterId", id));
          }
          store.grant(id, selected.key(), actor);
          return Change.at(
              clock,
              "CharacterLevelGranted",
              Audience.DM,
              Map.of("characterId", id, "classKey", selected.key()),
              Map.of("characterId", id));
        });
  }

  @Transactional(readOnly = true)
  public LevelPlan levelPlan(UUID campaign, UUID actor, UUID id, Advancement request) {
    var state = state(campaign, actor, id);
    return plan(state, catalog(campaign, actor), settings.effective(campaign, actor), request);
  }

  public Reply advance(UUID campaign, UUID actor, UUID id, Envelope envelope, Advancement form) {
    access(campaign, actor, id);
    return commands.runForMember(
        campaign,
        actor,
        envelope,
        Kind.CHARACTER_LEVEL_UP,
        new LevelAction(id, form),
        clock -> {
          var state = state(campaign, actor, id);
          requireUnfrozen(campaign, id);
          if (store.inActiveEncounter(id)) throw new CampaignException(Code.ENCOUNTER_ACTIVE);
          var catalog = catalog(campaign, actor);
          var effective = settings.effective(campaign, actor);
          var plan = plan(state, catalog, effective, form);
          if (!plan.granted()) throw invalid();
          var previous = selectedClass(state, form.classKey());
          String subclass = plan.subclassRequired() ? form.subclassKey() : previous.subclass();
          if (plan.subclassRequired()
              && plan.subclasses().stream().noneMatch(choice -> choice.key().equals(subclass)))
            throw invalid();
          if (!plan.subclassRequired()
              && form.subclassKey() != null
              && !Objects.equals(subclass, form.subclassKey())) throw invalid();

          if (plan.styles().isEmpty()
              ? form.fightingStyleKey() != null
              : plan.styles().stream()
                  .noneMatch(choice -> choice.key().equals(form.fightingStyleKey())))
            throw invalid();
          boolean lore = plan.level() == 3 && "srd-5.1:college-of-lore".equals(subclass);
          if (form.bonusSkills().size() != (lore ? 3 : 0)
              || new HashSet<>(form.bonusSkills()).size() != form.bonusSkills().size()
              || !plan.newSkills().containsAll(form.bonusSkills())) throw invalid();
          var additions = new ArrayList<CharacterStore.Proficiency>();
          form.bonusSkills()
              .forEach(
                  key -> additions.add(new CharacterStore.Proficiency("skill", key, subclass)));
          var eligible = new ArrayList<>(state.proficiencies());
          eligible.addAll(additions);
          additions.addAll(
              expertise(
                  eligible,
                  form.expertiseChoices(),
                  plan.expertiseRequired() ? 2 : 0,
                  previous.key(),
                  false));

          if (!plan.hpMethods().contains(form.hitPointMethod())) throw invalid();
          if (plan.improvementRequired()
              && (form.improvements().isEmpty() == (form.featKey() == null))) throw invalid();
          if (!plan.improvementRequired()
              && (!form.improvements().isEmpty() || form.featKey() != null)) throw invalid();
          if (plan.losses().values().stream().anyMatch(loss -> loss.lost() > 0)
              && !form.acknowledgeLoss()) throw invalid();
          if (form.featKey() != null
              && plan.feats().stream().noneMatch(choice -> choice.key().equals(form.featKey())))
            throw invalid();

          int die = catalog.require("class", previous.key()).number("hitDie");
          var hpRoll =
              form.hitPointMethod().equals("roll")
                  ? DiceExpression.parse("1d" + die).roll(random::nextInt)
                  : null;
          int raw = hpRoll == null ? die / 2 + 1 : hpRoll.total();
          var oldScores = CharacterSheets.scores(state, catalog);
          var newScores = oldScores;
          var improved = new EnumMap<Ability, Integer>(Ability.class);
          for (var change : plan.losses().entrySet()) {
            if (change.getValue().applied() > 0) {
              newScores = newScores.withImprovement(change.getKey(), change.getValue().applied());
              improved.put(change.getKey(), change.getValue().applied());
            }
          }
          var rawHealth = new ArrayList<>(state.rawHealth());
          rawHealth.add(raw);
          int oldMax =
              com.github.sbadolankaur.rpg.engine.combat.Exhaustion.maximumHp(
                  CharacterRules.maximumHp(oldScores, state.rawHealth()),
                  state.combat().exhaustion());
          int newMax =
              com.github.sbadolankaur.rpg.engine.combat.Exhaustion.maximumHp(
                  CharacterRules.maximumHp(newScores, rawHealth), state.combat().exhaustion());
          int hp = HitPointRules.afterMaximumChange(state.header().hp(), oldMax, newMax);
          store.advance(
              id,
              new ClassLevel(previous.key(), subclass, plan.level()),
              rawHealth.size(),
              raw,
              hp,
              improved,
              form.featKey(),
              catalog.resources(previous.key(), plan.level(), subclass));
          if (form.fightingStyleKey() != null) store.fightingStyle(id, form.fightingStyleKey());
          store.addProficiencies(id, additions);
          return Change.at(
                  clock,
                  "CharacterLeveledUp",
                  Audience.DM,
                  Map.of(
                      "characterId",
                      id,
                      "level",
                      plan.level(),
                      "rawHitPoints",
                      raw,
                      "choices",
                      form,
                      "hitDie",
                      hpRoll == null ? List.of() : hpRoll.terms()),
                  Map.of("characterId", id))
              .withSettingsRead(
                  Map.of(
                      "hitPointMethod",
                      effective.value("hitPointMethod"),
                      "feats.enabled",
                      effective.value("feats.enabled")));
        });
  }

  private LevelPlan plan(
      State state, CharacterCatalog catalog, SettingsView effective, Advancement form) {
    ClassLevel current = selectedClass(state, form.classKey());
    int next = current.level() + 1;
    var row = catalog.level(current.key(), next);
    if (state.rawHealth().size() >= FIGHTER_SCOPE_LIMIT) throw invalid();
    var grants = row.strings("grants");
    var scores = CharacterSheets.scores(state, catalog);
    Map<Ability, AbilityScores.ImprovementPreview> losses;
    try {
      losses =
          form.improvements().isEmpty()
              ? Map.of()
              : CharacterRules.improvements(scores, form.improvements());
    } catch (IllegalArgumentException error) {
      throw invalid();
    }
    List<String> methods =
        switch (effective.get("hitPointMethod", String.class)) {
          case "FIXED" -> List.of("average");
          case "ROLLED" -> List.of("roll");
          default -> List.of("average", "roll");
        };
    List<Choice> feats =
        grants.contains("asi") && effective.get("feats.enabled", Boolean.class)
            ? catalog.choices("feat").stream()
                .filter(choice -> !state.feats().contains(choice.key()))
                .filter(choice -> prerequisite(catalog, choice.key(), scores))
                .toList()
            : List.of();
    return new LevelPlan(
        current.key(),
        next,
        current.key().equals(state.grantedClass()),
        grants.contains("subclass"),
        grants.contains("asi"),
        catalog.subclasses(current.key()),
        row.strings("features").stream().map(key -> catalog.choice("feature", key)).toList(),
        methods,
        feats,
        losses,
        grants.contains("fighting_style") ? catalog.styles(current.key()) : List.of(),
        row.strings("features").contains("srd-5.1:bard-expertise"),
        state.proficiencies().stream()
            .filter(entry -> "skill".equals(entry.kind()))
            .map(entry -> entry.key())
            .distinct()
            .sorted()
            .toList(),
        CharacterSheets.SKILLS.keySet().stream()
            .filter(
                key ->
                    state.proficiencies().stream()
                        .noneMatch(
                            entry -> "skill".equals(entry.kind()) && key.equals(entry.key())))
            .sorted()
            .toList());
  }

  private static boolean prerequisite(CharacterCatalog catalog, String feat, AbilityScores scores) {
    String rule = catalog.require("feat", feat).text("prerequisite");
    if (rule.isBlank()) return true;
    var types = new java.util.HashMap<String, ContentExpression.Type>();
    var values = new java.util.HashMap<String, Object>();
    for (var ability : Ability.values()) {
      String key = "score:" + ability.name().toLowerCase(java.util.Locale.ROOT);
      types.put(key, ContentExpression.Type.INT);
      values.put(key, (long) scores.score(ability));
    }
    return Boolean.TRUE.equals(ContentExpression.parse(rule, types).evaluate(values, bound -> 0));
  }

  private static ClassLevel selectedClass(State state, String classKey) {
    if (classKey == null && state.classes().size() == 1) return state.classes().getFirst();
    return state.classes().stream()
        .filter(level -> level.key().equals(classKey))
        .findFirst()
        .orElseThrow(Characters::invalid);
  }

  public Reply edit(UUID campaign, UUID actor, UUID id, Envelope envelope, Edit form) {
    access(campaign, actor, id);
    return commands.runForMember(
        campaign,
        actor,
        envelope,
        Kind.CHARACTER_EDIT,
        new EditAction(id, form),
        clock -> {
          var rights = access(campaign, actor, id);
          requireUnfrozen(campaign, id);
          var state = store.read(rights.character());
          var catalog = catalog(campaign, actor);
          if (form.section() == null) throw invalid();
          if (store.inActiveEncounter(id) && !form.section().equals("details")) {
            throw new CampaignException(Code.ENCOUNTER_ACTIVE);
          }
          switch (form.section()) {
            case "details" -> store.rename(id, name(form.name()));
            case "inventory" -> {
              validateEquipment(state, catalog, form.equipment());
              store.equip(id, form.equipment());
            }
            case "resources" -> {
              if (form.used() == null
                  || form.used() < 0
                  || form.used() > 1
                  || state.resources().stream()
                      .noneMatch(resource -> resource.key().equals(form.resourceKey())))
                throw invalid();
              store.resource(id, form.resourceKey(), form.used());
            }
            case "hit-points" -> {
              requireDm(rights);
              int maximum =
                  com.github.sbadolankaur.rpg.engine.combat.Exhaustion.maximumHp(
                      CharacterRules.maximumHp(
                          CharacterSheets.scores(state, catalog), state.rawHealth()),
                      state.combat().exhaustion());
              if (form.currentHp() == null
                  || form.temporaryHp() == null
                  || form.currentHp() < 0
                  || form.temporaryHp() < 0
                  || form.currentHp() > maximum) throw invalid();
              store.hitPoints(id, form.currentHp(), form.temporaryHp());
            }
            case "conditions" -> {
              requireDm(rights);
              catalog.require("condition", form.conditionKey());
              store.condition(id, form.conditionKey(), form.addCondition());
            }
            case "ability-scores" -> {
              requireDm(rights);
              var allocated = CharacterCreation.allocate(form.abilityScores());
              int oldMaximum =
                  com.github.sbadolankaur.rpg.engine.combat.Exhaustion.maximumHp(
                      CharacterRules.maximumHp(
                          CharacterSheets.scores(state, catalog), state.rawHealth()),
                      state.combat().exhaustion());
              AbilityScores updated;
              try {
                updated =
                    CharacterRules.scores(
                        allocated,
                        catalog.speciesBonuses(state.header().species()),
                        state.improvements(),
                        30);
              } catch (IllegalArgumentException error) {
                throw invalid();
              }
              int maximum =
                  com.github.sbadolankaur.rpg.engine.combat.Exhaustion.maximumHp(
                      CharacterRules.maximumHp(updated, state.rawHealth()),
                      state.combat().exhaustion());
              store.updateAbilities(id, allocated);
              store.hitPoints(
                  id,
                  HitPointRules.afterMaximumChange(state.header().hp(), oldMaximum, maximum),
                  state.header().temporaryHp());
            }
            default -> throw invalid();
          }
          return Change.at(
              clock,
              "CharacterEdited",
              Audience.DM,
              Map.of("characterId", id, "edit", form),
              Map.of("characterId", id));
        });
  }

  private static void validateEquipment(
      State state, CharacterCatalog catalog, Map<UUID, String> slots) {
    var occupied = new HashSet<String>();
    for (var assignment : slots.entrySet()) {
      var item =
          state.inventory().stream()
              .filter(row -> row.id().equals(assignment.getKey()))
              .findFirst()
              .orElseThrow(Characters::invalid);
      var definition = catalog.require("item", item.key());
      String category = definition.text("category");
      boolean weapon = category.equals("weapon");
      boolean twoHanded =
          definition.object("weaponData").strings("properties").contains("two_handed");
      String slot = assignment.getValue();
      boolean eligible =
          switch (slot) {
            case "ARMOR" -> category.equals("armor");
            case "MAIN_HAND" -> weapon && !twoHanded;
            case "OFF_HAND" -> category.equals("shield") || (weapon && !twoHanded);
            case "BOTH_HANDS" -> weapon;
            default -> false;
          };
      if (!eligible || !occupied.add(slot)) throw invalid();
    }
    if (occupied.contains("BOTH_HANDS")
        && (occupied.contains("MAIN_HAND") || occupied.contains("OFF_HAND"))) throw invalid();
  }

  private static CampaignException incorrectAbilityMethod() {
    return new CampaignException(new CampaignException.FieldError("abilityMethod", "INCORRECT"));
  }

  private static void requireDm(Access rights) {
    if (!rights.dm()) throw new CampaignException(Code.FORBIDDEN);
  }

  private Access access(UUID campaign, UUID actor, UUID id) {
    var role = campaigns.requireMember(campaign, actor).role();
    Header character =
        store.find(campaign, id).orElseThrow(() -> new CampaignException(Code.NOT_FOUND));
    if (role != Role.DM && !actor.equals(character.owner()))
      throw new CampaignException(Code.FORBIDDEN);
    return new Access(character, role == Role.DM);
  }

  private State state(UUID campaign, UUID actor, UUID id) {
    return store.read(access(campaign, actor, id).character());
  }

  private void requireUnfrozen(UUID campaign, UUID id) {
    if (reactions.protectsCharacter(campaign, id))
      throw new CampaignException(Code.REACTION_PENDING);
  }

  private CharacterCatalog catalog(UUID campaign, UUID actor) {
    return new CharacterCatalog(campaigns.contentFor(campaign, actor));
  }

  private static AbilityGeneration generation(SettingsView settings) {
    return AbilityGeneration.valueOf(settings.get("abilityScoreGenerationMethod", String.class));
  }

  private static String name(String text) {
    if (text == null) throw invalid();
    String stripped = text.strip();
    if (stripped.isEmpty()
        || stripped.codePointCount(0, stripped.length()) > 80
        || stripped
            .codePoints()
            .anyMatch(
                code ->
                    Character.isISOControl(code) || Character.getType(code) == Character.FORMAT))
      throw invalid();
    return stripped;
  }

  private static CampaignException invalid() {
    return new CampaignException(Code.VALIDATION_FAILED);
  }

  private record Access(Header character, boolean dm) {}

  private record Target(UUID id, String classKey) {}

  private record LevelAction(UUID id, Advancement input) {}

  private record EditAction(UUID id, Edit input) {}

  public enum Step {
    SPECIES,
    CLASS,
    ABILITIES,
    BACKGROUND,
    EQUIPMENT,
    REVIEW
  }

  public record Creation(
      String speciesKey,
      String classKey,
      String fightingStyleKey,
      AbilityGeneration abilityMethod,
      List<Integer> abilityScores,
      String backgroundKey,
      List<String> skillChoices,
      List<String> skillReplacements,
      List<String> languageChoices,
      List<String> equipmentChoices,
      String name,
      List<String> expertiseChoices) {}

  public record Wizard(
      List<Choice> species,
      List<Choice> classes,
      List<Choice> backgrounds,
      List<Choice> styles,
      List<String> skills,
      List<EquipmentGroup> equipment,
      boolean gold,
      AbilityGeneration method,
      List<Integer> rolled,
      boolean dm,
      List<String> replacements,
      boolean rogueExpertise) {}

  public record Advancement(
      String classKey,
      String subclassKey,
      String hitPointMethod,
      Map<Ability, Integer> improvements,
      String featKey,
      boolean acknowledgeLoss,
      String fightingStyleKey,
      List<String> expertiseChoices,
      List<String> bonusSkills) {}

  public record LevelPlan(
      String classKey,
      int level,
      boolean granted,
      boolean subclassRequired,
      boolean improvementRequired,
      List<Choice> subclasses,
      List<Choice> features,
      List<String> hpMethods,
      List<Choice> feats,
      Map<Ability, AbilityScores.ImprovementPreview> losses,
      List<Choice> styles,
      boolean expertiseRequired,
      List<String> trainedSkills,
      List<String> newSkills) {}

  public record Edit(
      String section,
      String name,
      Integer currentHp,
      Integer temporaryHp,
      Map<UUID, String> equipment,
      List<Integer> abilityScores,
      String conditionKey,
      boolean addCondition,
      String resourceKey,
      Integer used) {}
}
