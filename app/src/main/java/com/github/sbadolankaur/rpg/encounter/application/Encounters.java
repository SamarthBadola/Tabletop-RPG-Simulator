package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.campaign.application.Campaigns;
import com.github.sbadolankaur.rpg.character.application.CharacterCatalog.Choice;
import com.github.sbadolankaur.rpg.character.application.Characters;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Condition;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Phase;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Table;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Tactics;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.encounter.persistence.AfflictionStore;
import com.github.sbadolankaur.rpg.encounter.persistence.AfflictionStore.CureChoice;
import com.github.sbadolankaur.rpg.encounter.persistence.EncounterStore;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.DeathMode;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance.Boundary;
import com.github.sbadolankaur.rpg.engine.combat.Exhaustion;
import com.github.sbadolankaur.rpg.engine.combat.TurnBudget;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Encounters {
  static final Comparator<Entry> ORDER =
      Comparator.<Entry, Integer>comparing(
              Entry::initiative, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Entry::tie, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparingInt(Entry::ordinal);
  private final Campaigns campaigns;
  private final Characters characters;
  private final EncounterCommands commands;
  private final EncounterStore store;
  private final CombatRoster roster;
  private final AfflictionStore afflictions;

  Encounters(
      Campaigns campaigns,
      Characters characters,
      EncounterCommands commands,
      EncounterStore store,
      CombatRoster roster,
      AfflictionStore afflictions) {
    this.campaigns = campaigns;
    this.characters = characters;
    this.commands = commands;
    this.store = store;
    this.roster = roster;
    this.afflictions = afflictions;
  }

  public List<Table> list(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    return store.list(campaign);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public View view(UUID campaign, UUID id, UUID actor, long before, int limit) {
    campaigns.requireDm(campaign, actor);
    Table table = commands.require(campaign, id, false);
    var content = roster.content(campaign, actor);
    var entries = store.entries(id);
    var rows =
        entries.stream()
            .filter(Encounters::present)
            .sorted(ORDER)
            .map(
                entry -> {
                  var creature = roster.load(campaign, actor, entry, content);
                  return new Combatant(
                      entry.id(),
                      creature.name(),
                      entry.initiative(),
                      creature.vitals().health(),
                      creature.speed(),
                      creature.vitals().exhaustion(),
                      entry.tactics().stealth() == null
                          ? Set.of()
                          : entry.tactics().stealth().ties(),
                      entry.tactics().surprised(),
                      entry.tactics().deathSaveDue(),
                      creature.conditions().stream().sorted().toList(),
                      creature.attacks().stream()
                          .map(option -> new Choice(option.key(), option.label()))
                          .toList(),
                      creature.resources(),
                      creature.features());
                })
            .toList();
    var players =
        table.phase() == Phase.FINISHED
            ? List.<Choice>of()
            : characters.list(campaign, actor).stream()
                .map(view -> new Choice(view.id().toString(), view.name()))
                .toList();
    var monsters =
        content.index().values().stream()
            .filter(definition -> definition.kind().equals("monster"))
            .map(definition -> new Choice(definition.key(), definition.name()))
            .distinct()
            .sorted(Comparator.comparing(Choice::label))
            .toList();
    if (before < 1 || limit < 1 || limit > 200) throw EncounterCommands.invalid();
    var events = store.log(id, actor, true, before, limit);
    return new View(
        table,
        rows,
        players,
        monsters,
        events,
        ties(entries).stream().map(Entry::id).toList(),
        store.latestReversible(id).map(EncounterStore.Event::id).orElse(null),
        CombatStealth.suggestions(entries),
        conditionNames(content),
        table.phase() == Phase.FINISHED ? store.latestRecap(id).orElse(null) : null,
        rows.stream()
                .filter(row -> row.id().equals(table.acting()))
                .anyMatch(row -> row.features().contains(CombatFeatures.LAY_ON_HANDS))
            ? afflictions.activeCures(id).stream()
                .collect(Collectors.groupingBy(CureChoice::target))
            : Map.of());
  }

  @Transactional(readOnly = true)
  public String currentTurn(UUID campaign, UUID actor) {
    campaigns.requireDm(campaign, actor);
    return store.list(campaign).stream()
        .filter(table -> table.phase() == Phase.ACTIVE && table.acting() != null)
        .findFirst()
        .map(
            table ->
                store.entries(table.id()).stream()
                    .filter(entry -> entry.id().equals(table.acting()) && present(entry))
                    .findFirst()
                    .map(
                        entry ->
                            roster
                                .load(campaign, actor, entry, roster.content(campaign, actor))
                                .name())
                    .orElse(null))
        .orElse(null);
  }

  public List<PlayerEvent> visibleEvents(UUID campaign, UUID id, UUID reader) {
    campaigns.requireMember(campaign, reader);
    commands.require(campaign, id, false);
    return store.log(id, reader, false, Long.MAX_VALUE, 50).stream()
        .map(
            event ->
                new PlayerEvent(
                    event.type(),
                    event.payload().path("text").asString(),
                    event.gameSeconds(),
                    event.round()))
        .toList();
  }

  public Reply add(UUID campaign, UUID id, UUID actor, Envelope envelope, Join form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "add-combatants",
        form,
        false,
        context -> {
          if ((form.character() == null) == (form.monster() == null)
              || form.count() < 1
              || form.count() > 20
              || form.character() != null && (form.count() != 1 || form.deathMode() != null))
            throw EncounterCommands.invalid();
          if (context.table.phase() == Phase.FINISHED || context.table.phase() == Phase.INITIATIVE)
            throw new CampaignException(Code.ENCOUNTER_STARTED);
          boolean active = context.table.phase() == Phase.ACTIVE;
          if (!form.caughtOffGuard().isEmpty()) {
            if (!active
                || new java.util.HashSet<>(form.caughtOffGuard()).size()
                    != form.caughtOffGuard().size()) throw EncounterCommands.invalid();
            context.preflight(List.of("midCombatSurprise"));
            if (!context.setting("midCombatSurprise", Boolean.class))
              throw EncounterCommands.invalid();
            for (UUID surprised : form.caughtOffGuard()) {
              var target = CombatActions.target(context, surprised).entry();
              context.update(target.tactics(target.tactics().surprise(true)));
            }
          }
          if (active
              && campaigns.requireDm(campaign, actor).sessionState()
                  != com.github.sbadolankaur.rpg.campaign.application.CampaignView.SessionState
                      .ACTIVE) throw new CampaignException(Code.SESSION_NOT_ACTIVE);
          if (!active
              && (form.fixed() != null || form.nextRound() || form.mode() != RollMode.NORMAL))
            throw EncounterCommands.invalid();
          if (context.entries.values().stream().filter(Encounters::present).count() + form.count()
              > 100) throw EncounterCommands.invalid();

          var conditions =
              form.condition() == null
                  ? List.<Condition>of()
                  : List.of(new Condition(conditionKey(context, form.condition()), null));
          int nextRound = active ? context.table.round() : 1;
          if (active && form.nextRound()) nextRound++;
          if (form.character() != null) characters.sheet(campaign, actor, form.character());
          var existing =
              form.character() == null
                  ? null
                  : context.entries.values().stream()
                      .filter(entry -> form.character().equals(entry.character()))
                      .findFirst()
                      .orElse(null);
          if (existing != null && present(existing)) throw EncounterCommands.invalid();

          Integer shared = null;
          if (active && form.monster() != null && form.count() > 1)
            context.preflight(List.of("groupInitiative"));
          boolean group =
              active
                  && form.monster() != null
                  && form.count() > 1
                  && context.setting("groupInitiative", Boolean.class);
          var added = new ArrayList<UUID>();
          for (int i = 0; i < form.count(); i++) {
            var tactics =
                Tactics.initial(nextRound, form.surprised(), conditions)
                    .deathMode(form.deathMode());
            if (existing != null) {
              context.update(
                  existing.tactics(tactics.retainLastTurn(existing.tactics().lastTurn())));
              if (active && existing.initiative() != null)
                scheduleArrival(context, existing.id(), form.nextRound());
              if (!form.hiddenFrom().isEmpty())
                CombatStealth.hide(
                    context, context.creature(existing.id()), form.hiddenFrom(), form.hiddenFrom());
              added.add(existing.id());
              continue;
            }
            UUID row = ServerIds.at(Instant.now());
            Vitals npc = null;
            if (form.monster() != null) {
              int maximum =
                  roster
                      .monster(context.content, form.monster())
                      .path("hp")
                      .path("average")
                      .asInt();
              npc = new Vitals(CombatHealth.of(maximum, maximum, 0), null, Set.of());
            }
            int ordinal =
                context.entries.values().stream().mapToInt(Entry::ordinal).max().orElse(0) + 1;
            var entry =
                new Entry(row, form.character(), form.monster(), ordinal, null, null, tactics, npc);
            context.add(entry);
            var creature = context.creature(row);
            if (active) {
              int initiative =
                  group && shared != null
                      ? shared
                      : form.fixed() == null
                          ? initiative(context, row, creature.initiative(), form.mode())
                          : form.fixed();
              if (group) shared = initiative;
              assignInitiative(context, entry, initiative);
              scheduleArrival(context, row, form.nextRound());
            }
            if (!form.hiddenFrom().isEmpty())
              CombatStealth.hide(
                  context, context.creature(row), form.hiddenFrom(), form.hiddenFrom());
            added.add(row);
          }
          context.dm("CombatantsAdded", Map.of("ids", added));
          context.result("combatantIds", added);
        });
  }

  private static void scheduleArrival(Context context, UUID row, boolean nextRound) {
    var entry = context.entries.get(row);
    var current = context.entries.get(context.table.acting());
    boolean passed = current != null && ORDER.compare(entry, current) <= 0;
    int firstRound = context.table.round() + (nextRound || passed ? 1 : 0);
    context.update(entry.tactics(entry.tactics().assigned(firstRound)));
  }

  public Reply remove(UUID cp, UUID id, UUID actor, Envelope envelope, UUID combatant) {
    return commands.run(
        cp,
        id,
        actor,
        envelope,
        "remove-combatant",
        combatant,
        false,
        context -> {
          var entry = context.creature(combatant).entry();
          if (context.table.phase() == Phase.FINISHED || !present(entry))
            throw EncounterCommands.invalid();
          if (context.table.phase() == Phase.ACTIVE
              && campaigns.requireDm(cp, actor).sessionState()
                  != com.github.sbadolankaur.rpg.campaign.application.CampaignView.SessionState
                      .ACTIVE) throw new CampaignException(Code.SESSION_NOT_ACTIVE);
          if (context.table.phase() == Phase.SETUP) context.remove(combatant);
          else {
            CombatEffects.leaveEncounter(context, context.creature(combatant));
            var leaving = context.creature(combatant).entry();
            context.update(leaving.removed());
          }
          context.dm("CombatantRemoved", Map.of("combatantId", combatant));
          if (combatant.equals(context.table.acting())) nextTurn(context);
        });
  }

  public Reply start(UUID cp, UUID id, UUID actor, Envelope envelope, Set<UUID> surprised) {
    var chosen = surprised.stream().sorted().toList();
    return commands.run(
        cp,
        id,
        actor,
        envelope,
        "start",
        chosen,
        true,
        context -> {
          if (context.table.phase() != Phase.SETUP)
            throw new CampaignException(Code.ENCOUNTER_STARTED);
          if (store.hasRunning(cp, id)) throw new CampaignException(Code.ENCOUNTER_ACTIVE);
          if (context.entries.size() < 2 || !context.entries.keySet().containsAll(chosen))
            throw EncounterCommands.invalid();
          if (context.entries.values().stream()
              .filter(row -> row.monster() != null)
              .collect(Collectors.groupingBy(Entry::monster, Collectors.counting()))
              .values()
              .stream()
              .anyMatch(count -> count > 1)) context.preflight(List.of("groupInitiative"));
          boolean grouped =
              context.entries.values().stream()
                      .filter(e -> e.monster() != null)
                      .collect(Collectors.groupingBy(Entry::monster, Collectors.counting()))
                      .values()
                      .stream()
                      .anyMatch(count -> count > 1)
                  && context.setting("groupInitiative", Boolean.class);
          Map<String, Integer> groupRolls = new HashMap<>();
          for (var entry : List.copyOf(context.entries.values())) {
            var creature = context.creature(entry.id());
            int rolled =
                grouped && entry.monster() != null
                    ? groupRolls.computeIfAbsent(
                        entry.monster(),
                        key ->
                            initiative(context, entry.id(), creature.initiative(), RollMode.NORMAL))
                    : initiative(context, entry.id(), creature.initiative(), RollMode.NORMAL);
            var tactics =
                entry
                    .tactics()
                    .surprise(entry.tactics().surprised() || surprised.contains(entry.id()));
            context.update(entry.initiative(rolled, null).tactics(tactics));
          }
          context.dm("InitiativeRolled", Map.of("combatants", context.entries.size()));
          context.table = context.table.position(Phase.INITIATIVE, 1, null, TurnBudget.start());
          if (ties(context.entries.values()).isEmpty()) nextTurn(context);
        });
  }

  private static int initiative(Context context, UUID id, int modifier, RollMode mode) {
    var roll = CombatRolls.d20("Initiative modifier", modifier, mode, context.random());
    context.dice("Initiative", id, roll);
    context.recharge(
        context.creature(id),
        com.github.sbadolankaur.rpg.engine.rest.RestResolver.Trigger.ROLL_INITIATIVE);
    return roll.total();
  }

  private static void assignInitiative(Context context, Entry entry, int initiative) {
    for (var other : List.copyOf(context.entries.values())) {
      if (other.id().equals(entry.id()) || !present(other)) continue;
      if (Integer.valueOf(initiative).equals(other.initiative()))
        context.update(other.initiative(initiative, null));
    }
    context.update(entry.initiative(initiative, null));
  }

  public Reply ties(UUID cp, UUID id, UUID actor, Envelope envelope, Tie form) {
    return commands.run(
        cp,
        id,
        actor,
        envelope,
        "initiative-ties",
        form,
        true,
        context -> {
          var tied = ties(context.entries.values());
          if (tied.isEmpty() || form.rollOff() == !form.order().isEmpty())
            throw EncounterCommands.invalid();
          var expected = tied.stream().map(Entry::id).toList();
          List<UUID> order;
          if (form.rollOff()) {
            var rolled = new HashMap<UUID, Integer>();
            for (UUID combatant : expected) {
              var roll = CombatRolls.d20("Roll-off", 0, RollMode.NORMAL, context.random());
              context.dice("Initiative tie-break", combatant, roll);
              rolled.put(combatant, roll.total());
            }
            order =
                expected.stream()
                    .sorted(
                        Comparator.<UUID>comparingInt(rolled::get)
                            .reversed()
                            .thenComparing(UUID::compareTo))
                    .toList();
          } else {
            if (form.order().size() != expected.size()
                || !new HashSet<>(form.order()).containsAll(expected))
              throw EncounterCommands.invalid();
            order = form.order();
          }
          for (int i = 0; i < order.size(); i++) {
            var entry = context.entries.get(order.get(i));
            context.update(entry.initiative(entry.initiative(), i));
          }
          context.dm("InitiativeTiesResolved", Map.of("order", order));
          if (context.table.phase() == Phase.INITIATIVE && ties(context.entries.values()).isEmpty())
            nextTurn(context);
        });
  }

  public Reply endTurn(UUID cp, UUID id, UUID actor, Envelope envelope, UUID combatant) {
    return commands.run(
        cp,
        id,
        actor,
        envelope,
        "end-turn",
        combatant,
        true,
        context -> resolveEndTurn(context, combatant));
  }

  static void resolveEndTurn(Context context, UUID combatant) {
    var entry = context.acting(combatant).entry();
    if (!ties(context.entries.values()).isEmpty()) throw EncounterCommands.invalid();
    var current = context.creature(combatant);
    if (entry.tactics().deathSaveDue() && current.vitals().health().state() == State.DYING) {
      resolveDeathSave(context, current);
    }
    var updated = context.entries.get(combatant);
    context.update(updated.tactics(updated.tactics().end().deathSave(false)));
    CombatEffects.expire(context, combatant, Boundary.END);
    context.dm("TurnEnded", Map.of("combatantId", combatant));
    nextTurn(context);
  }

  public Reply deathSave(UUID cp, UUID id, UUID actor, Envelope envelope, UUID combatant) {
    return commands.run(
        cp,
        id,
        actor,
        envelope,
        "death-saves",
        combatant,
        true,
        context -> resolveExplicitDeathSave(context, combatant));
  }

  static void resolveExplicitDeathSave(Context context, UUID combatant) {
    var current = context.acting(combatant);
    if (!current.entry().tactics().deathSaveDue()
        || current.vitals().health().state() != State.DYING) throw EncounterCommands.invalid();
    resolveDeathSave(context, current);
  }

  public Reply finish(UUID cp, UUID id, UUID actor, Envelope envelope) {
    return commands.run(
        cp,
        id,
        actor,
        envelope,
        "end",
        Map.of(),
        false,
        context -> {
          if (context.table.phase() != Phase.ACTIVE) throw EncounterCommands.invalid();
          finish(context);
        });
  }

  static void finish(Context context) {
    for (var entry : List.copyOf(context.entries.values())) {
      CombatEffects.leaveEncounter(context, context.creature(entry.id()));
    }
    context.table =
        context.table.position(Phase.FINISHED, context.table.round(), null, TurnBudget.start());
    var recap = context.recap();
    context.dm(
        "EncounterEnded",
        Map.of(
            "rounds",
            recap.rounds(),
            "fallen",
            recap.fallen(),
            "resourcesSpent",
            recap.resourcesSpent()));
    context.result("recap", recap);
  }

  static boolean present(Entry entry) {
    return entry.present();
  }

  static List<Entry> ties(Iterable<Entry> roster) {
    Map<Integer, List<Entry>> groups = new java.util.TreeMap<>(Comparator.reverseOrder());
    for (var entry : roster) {
      if (present(entry) && entry.initiative() != null)
        groups.computeIfAbsent(entry.initiative(), number -> new ArrayList<>()).add(entry);
    }
    return groups.values().stream()
        .filter(group -> group.size() > 1 && group.stream().anyMatch(entry -> entry.tie() == null))
        .findFirst()
        .map(List::copyOf)
        .orElse(List.of());
  }

  private static void nextTurn(Context context) {
    var remaining =
        context.entries.values().stream()
            .filter(Encounters::present)
            .filter(entry -> context.creature(entry.id()).vitals().health().state() != State.DEAD)
            .sorted(ORDER)
            .toList();
    if (remaining.isEmpty()) {
      finish(context);
      return;
    }
    int round = context.table.round();
    long previous = context.seconds;
    var possible = eligible(remaining, round);
    if (possible.isEmpty()) {
      round++;
      long seconds = context.setting("roundAdvanceSeconds", Long.class);
      context.seconds = Math.addExact(context.seconds, seconds);
      context.dm("RoundEnded", Map.of("advanceSeconds", seconds));
      possible = eligible(remaining, round);
    }
    Entry next = possible.getFirst();
    context.update(next.tactics(next.tactics().start(round)));
    context.table = context.table.position(Phase.ACTIVE, round, next.id(), TurnBudget.start());
    context.sweepRoundClock(previous);
    CombatEffects.expire(context, next.id(), Boundary.START);
    context.dm("TurnStarted", Map.of("combatantId", next.id()));
    var creature = context.creature(next.id());
    if (creature.vitals().health().state() == State.DYING) {
      context.update(creature.entry().tactics(creature.entry().tactics().deathSave(true)));
    }
  }

  private static List<Entry> eligible(List<Entry> roster, int round) {
    return roster.stream()
        .filter(
            entry -> entry.tactics().firstRound() <= round && entry.tactics().lastTurn() < round)
        .toList();
  }

  static void resolveDeathSave(Context context, CombatRoster.Creature creature) {
    RollMode mode =
        Exhaustion.disadvantagesAttacksAndSaves(creature.vitals().exhaustion())
            ? RollMode.DISADVANTAGE
            : RollMode.NORMAL;
    if (creature.vitals().inspiration() != null) context.preflight(List.of("rollDecisionInfo"));
    var roll = CombatRolls.d20("Death save", 0, mode, context.random());
    UUID id = creature.entry().id();
    context.dice("DeathSave", id, roll);
    roll =
        CombatFeatures.inspireRoll(
            context,
            creature,
            roll,
            Point.AFTER_SAVE_ROLL,
            roll.naturalD20() != 1 && (roll.naturalD20() == 20 || roll.total() >= 10));
    creature = context.creature(id);
    context.update(creature.entry().tactics(creature.entry().tactics().deathSave(false)));
    var after =
        context.health(
            creature, creature.vitals().health().deathSave(roll.naturalD20(), roll.total()));
    context.dm(
        "DeathSaveResolved",
        Map.of("combatantId", creature.entry().id(), "health", after.health()));
  }

  static String conditionKey(Context context, String key) {
    if (key == null
        || !CombatConditions.names().containsKey(key)
            && context.content.find("condition", key).isEmpty()) throw EncounterCommands.invalid();
    return key;
  }

  private static Map<String, String> conditionNames(
      com.github.sbadolankaur.rpg.engine.content.ContentSet content) {
    var names = new java.util.TreeMap<>(CombatConditions.names());
    content.index().values().stream()
        .filter(definition -> "condition".equals(definition.kind()))
        .forEach(definition -> names.put(definition.key(), definition.name()));
    return names;
  }

  public record Join(
      UUID character,
      String monster,
      int count,
      RollMode mode,
      Integer fixed,
      boolean nextRound,
      String condition,
      boolean surprised,
      DeathMode deathMode,
      List<UUID> hiddenFrom,
      List<UUID> caughtOffGuard) {
    public Join {
      hiddenFrom = List.copyOf(hiddenFrom);
      caughtOffGuard = List.copyOf(caughtOffGuard);
    }
  }

  public record Tie(List<UUID> order, boolean rollOff) {
    public Tie {
      order = List.copyOf(order);
    }
  }

  public record View(
      Table table,
      List<Combatant> combatants,
      List<Choice> characters,
      List<Choice> monsters,
      List<EncounterStore.Event> events,
      List<UUID> tieIds,
      UUID reversible,
      Set<UUID> surpriseSuggestions,
      Map<String, String> conditionNames,
      Recap recap,
      Map<UUID, List<CureChoice>> cureChoices) {}

  public record Recap(int rounds, List<String> fallen, Map<String, Integer> resourcesSpent) {}

  public record Combatant(
      UUID id,
      String name,
      Integer initiative,
      CombatHealth health,
      int speed,
      int exhaustion,
      Set<UUID> stealthTies,
      boolean surprised,
      boolean deathSaveDue,
      List<String> conditions,
      List<Choice> attacks,
      Map<String, Integer> resources,
      Set<String> features) {}

  public record PlayerEvent(String type, String text, long gameSeconds, int round) {}
}
