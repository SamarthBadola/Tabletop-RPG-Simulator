package com.github.sbadolankaur.rpg.engine.rest;

import com.github.sbadolankaur.rpg.engine.combat.BardicInspiration;
import com.github.sbadolankaur.rpg.engine.combat.ClockExpiry;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.Exhaustion;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.dice.RollResult;
import com.github.sbadolankaur.rpg.engine.kernel.DurationKind;
import com.github.sbadolankaur.rpg.engine.kernel.GameClock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Resolves campaign-time rest rules without persistence or a wall clock. */
public final class RestResolver {
  private RestResolver() {}

  public static Preview preview(
      long start, Request request, List<Participant> party, Settings rules) {
    long end = clock(start, request.durationMinutes(), rules).end();
    Kind benefit = benefit(request, rules);
    var byId = new HashMap<UUID, Participant>();
    party.forEach(state -> byId.put(state.id(), state));
    if (request.plans().isEmpty()
        || request.plans().size() > 30
        || request.plans().stream().map(Plan::id).distinct().count() != request.plans().size()) {
      throw new Rejected(Problem.INVALID_CHOICES, List.of());
    }
    var blocked = new ArrayList<UUID>();
    for (var plan : request.plans()) {
      var state = byId.get(plan.id());
      if (state == null) throw new Rejected(Problem.NOT_FOUND, List.of(plan.id()));
      if (state.health().state() == State.DEAD || state.health().state() == State.DYING)
        blocked.add(plan.id());
    }
    if (!blocked.isEmpty()) throw new Rejected(Problem.REST_NOT_ALLOWED, blocked);

    validateSongs(request, byId, start);
    var results = new ArrayList<PreviewRow>();
    for (var plan : request.plans()) {
      var state = byId.get(plan.id());
      int available = availableHitDice(state);
      if ((plan.spendCount() != null && plan.spendCount() < 0)
          || (plan.spendUntil() != null && plan.spendUntil() <= 0)
          || (plan.spendCount() != null && plan.spendUntil() != null))
        throw new Rejected(Problem.INVALID_CHOICES, List.of(state.id()));
      if (plan.spendCount() != null && plan.spendCount() > available)
        throw new Rejected(Problem.HIT_DICE_UNAVAILABLE, List.of(state.id()));

      boolean eligible = benefit != Kind.LONG || longRestEligible(state, end, rules);
      if (plan.recoverHitDice() != null) {
        if (request.kind() != Kind.LONG) throw invalid("recoverHitDice", "INCORRECT");
        validateRecovery(state, plan.recoverHitDice());
      }
      if (!plan.arcaneRecovery().isEmpty()) {
        if (request.kind() != Kind.SHORT) throw invalid("arcaneRecovery", "INCORRECT");
        validateArcaneRecovery(state, plan.arcaneRecovery());
      }
      var spent = new TreeMap<>(state.spentHitDice());
      spent.values().removeIf(count -> count == 0);
      var expended = new TreeMap<>(state.usedSlots());
      expended.entrySet().removeIf(slot -> slot.getKey() > 5 || slot.getValue() == 0);
      results.add(
          new PreviewRow(
              state.id(),
              available,
              benefit == Kind.SHORT ? available : 0,
              benefit == Kind.LONG && eligible ? recoveryLimit(state) : 0,
              spent,
              benefit == Kind.SHORT && canRecoverSlots(state) ? (state.wizardLevel() + 1) / 2 : 0,
              expended,
              benefit == Kind.SHORT ? bestSong(request, byId, state, start) : 0,
              eligible ? List.of() : List.of("long_rest_no_benefit")));
    }
    return new Preview(end, benefit, request.interrupted(), results);
  }

  public static Resolution resolve(
      long start, Request request, List<Participant> party, Settings rules, RandomSource random) {
    var preview = preview(start, request, party, rules);
    var events = new ArrayList<Event>();
    events.add(
        new Event(
            "RestStarted",
            start,
            null,
            Map.of("kind", request.kind().name(), "durationMinutes", request.durationMinutes())));
    var advance = advance(start, request.durationMinutes() * 60L, party, rules, random);
    events.addAll(advance.events());

    var states = new LinkedHashMap<UUID, Working>();
    advance.participants().forEach(state -> states.put(state.id(), new Working(state)));
    for (int i = 0; i < request.plans().size(); i++) {
      var plan = request.plans().get(i);
      var state = states.get(plan.id());
      if (preview.benefit() == Kind.SHORT) {
        shortRest(
            state,
            plan,
            preview.participants().get(i).songOfRestDie(),
            rules,
            random,
            preview.end(),
            events);
      } else if (preview.benefit() == Kind.LONG) {
        if (preview.participants().get(i).warnings().isEmpty())
          longRest(state, plan, preview.end(), events, random);
        else
          events.add(
              new Event("long_rest_no_benefit", preview.end(), state.initial.id(), Map.of()));
      }
    }

    events.add(
        new Event(
            request.interrupted() ? "RestInterrupted" : "RestCompleted",
            preview.end(),
            null,
            Map.of("kind", request.kind().name())));
    return new Resolution(
        preview.end(), states.values().stream().map(Working::snapshot).toList(), events);
  }

  public static Resolution advance(
      long start, long seconds, List<Participant> party, Settings rules, RandomSource random) {
    var clock = GameClock.advance(start, seconds, rules.startTimeOfDay(), rules.dawn());
    var states = party.stream().map(Working::new).toList();
    var events = new ArrayList<Event>();
    if (seconds > 0) {
      events.add(
          new Event(
              "ClockAdvanced",
              clock.end(),
              null,
              Map.of(
                  "previousGameSeconds",
                  start,
                  "gameTimeSeconds",
                  clock.end(),
                  "timeOfDaySeconds",
                  clock.timeOfDaySeconds())));
    }

    var expirations = new ArrayList<Timer>();
    for (var state : states) {
      var recoveryAt = state.health.state() == State.STABLE ? state.recoveryAt : null;
      for (var expiry :
          ClockExpiry.due(
              clock.end(), state.effects, state.inspiration, recoveryAt, state.recoveryOrder)) {
        expirations.add(new Timer(expiry, state));
      }
    }
    expirations.sort(Comparator.comparing(Timer::expiry, ClockExpiry.ORDER));
    for (var timer : expirations) {
      events.add(timer.state().expire(timer.expiry()));
    }
    for (long dawn : clock.dawns()) {
      events.add(new Event("DawnReached", dawn, null, Map.of()));
      for (var state : states) recharge(state, Trigger.DAWN, dawn, events, random);
    }
    return new Resolution(clock.end(), states.stream().map(Working::snapshot).toList(), events);
  }

  private static GameClock.Advance clock(long start, int minutes, Settings rules) {
    if (minutes < 1 || minutes > GameClock.MAX_ADVANCE / 60)
      throw new Rejected(Problem.INVALID_CHOICES, List.of());
    return GameClock.advance(start, minutes * 60L, rules.startTimeOfDay(), rules.dawn());
  }

  private static Kind benefit(Request request, Settings rules) {
    if (request.interrupted())
      return request.kind() == Kind.LONG
              && request.durationMinutes() >= 60
              && rules.interruptedLongRest()
          ? Kind.SHORT
          : null;
    int minimum = request.kind() == Kind.LONG ? rules.longMinutes() : rules.shortMinutes();
    if (request.durationMinutes() < minimum) throw new Rejected(Problem.REST_TOO_SHORT, List.of());
    return request.kind();
  }

  private static int availableHitDice(Participant state) {
    return state.hitDice().entrySet().stream()
        .mapToInt(die -> die.getValue() - state.spentHitDice().getOrDefault(die.getKey(), 0))
        .sum();
  }

  private static int recoveryLimit(Participant state) {
    return Math.max(1, state.hitDice().values().stream().mapToInt(Integer::intValue).sum() / 2);
  }

  private static boolean longRestEligible(Participant state, long end, Settings rules) {
    return state.health().current() > 0
        && (state.lastLongRest() == null
            || end - state.lastLongRest() >= rules.longIntervalHours() * 3600L);
  }

  private static void validateRecovery(Participant state, Map<Integer, Integer> chosen) {
    long count = 0;
    for (var entry : chosen.entrySet()) {
      if (!state.hitDice().containsKey(entry.getKey()))
        throw invalid("recoverHitDice", "UNKNOWN_VALUE");
      if (entry.getValue() < 0
          || entry.getValue() > state.spentHitDice().getOrDefault(entry.getKey(), 0))
        throw invalid("recoverHitDice", "OUT_OF_RANGE");
      count += entry.getValue();
    }
    if (count > recoveryLimit(state)) throw invalid("recoverHitDice", "OUT_OF_RANGE");
  }

  private static boolean canRecoverSlots(Participant state) {
    return state.wizardLevel() > 0
        && state.arcaneRecoveryKey() != null
        && state.resources().stream()
            .anyMatch(r -> r.key().equals(state.arcaneRecoveryKey()) && r.used() == 0);
  }

  private static void validateArcaneRecovery(Participant state, Map<Integer, Integer> slots) {
    if (!canRecoverSlots(state)) throw invalid("arcaneRecovery", "INCORRECT");
    long total = 0;
    for (var slot : slots.entrySet()) {
      int level = slot.getKey();
      int count = slot.getValue();
      if (level < 1 || level > 5 || count < 0 || count > state.usedSlots().getOrDefault(level, 0))
        throw invalid("arcaneRecovery", "OUT_OF_RANGE");
      total += (long) level * count;
    }
    if (total > (state.wizardLevel() + 1) / 2) throw invalid("arcaneRecovery", "OUT_OF_RANGE");
  }

  private static void validateSongs(Request request, Map<UUID, Participant> party, long now) {
    var participants =
        request.plans().stream().map(Plan::id).collect(java.util.stream.Collectors.toSet());
    var performers = new java.util.HashSet<UUID>();
    for (var song : request.songs()) {
      var performer = party.get(song.performer());
      if (request.kind() != Kind.SHORT
          || !participants.contains(song.performer())
          || !performers.add(song.performer())
          || performer.bardLevel() < 2
          || performer.health().state() != State.ALIVE
          || CombatConditions.incapacitated(activeConditions(performer, now)))
        throw invalid("songOfRestPerformerIds", "INCORRECT");
      if (!participants.containsAll(song.listeners()))
        throw invalid("songOfRestExcludedIds", "INCORRECT");
    }
  }

  private static int bestSong(
      Request request, Map<UUID, Participant> states, Participant listener, long now) {
    if (activeConditions(listener, now).contains("srd-5.1:deafened")) return 0;
    return request.songs().stream()
        .filter(song -> song.listeners().contains(listener.id()))
        .mapToInt(song -> songDie(states.get(song.performer()).bardLevel()))
        .max()
        .orElse(0);
  }

  private static Set<String> activeConditions(Participant state, long now) {
    var conditions = new java.util.HashSet<>(state.conditions());
    state.effects().stream()
        .filter(effect -> effect.end().seconds() == null || effect.end().seconds() > now)
        .map(ConditionInstance::key)
        .forEach(conditions::add);
    return conditions;
  }

  private static int songDie(int level) {
    return level >= 17 ? 12 : level >= 13 ? 10 : level >= 9 ? 8 : 6;
  }

  private static void shortRest(
      Working state,
      Plan plan,
      int song,
      Settings rules,
      RandomSource random,
      long now,
      List<Event> events) {
    int dice = plan.spendCount() == null ? Integer.MAX_VALUE : plan.spendCount();
    int target = plan.spendUntil() == null ? Integer.MAX_VALUE : plan.spendUntil();
    int spent = 0;
    if (plan.spendCount() != null || plan.spendUntil() != null) {
      for (var die : new TreeMap<>(state.initial.hitDice()).descendingMap().entrySet()) {
        int size = die.getKey();
        while (state.spent.getOrDefault(size, 0) < die.getValue()
            && spent < dice
            && state.health.current() < target) {
          var roll =
              hitDie(size, state.initial.constitutionModifier(), rules.fixedHitDice(), random);
          state.spent.merge(size, 1, Integer::sum);
          spent++;
          events.add(
              new Event(
                  "HitDieSpent", now, state.initial.id(), Map.of("size", size, "roll", roll)));
          heal(state, Math.max(0, roll.total()), now, events);
          if (spent == 1 && song > 0) {
            var songRoll =
                healingDie("Song of Rest", "song-of-rest", song, rules.fixedHitDice())
                    .build()
                    .roll(random);
            events.add(
                new Event("SongOfRestRolled", now, state.initial.id(), Map.of("roll", songRoll)));
            heal(state, songRoll.total(), now, events);
          }
        }
      }
    }
    for (var recovery : new TreeMap<>(plan.arcaneRecovery()).entrySet()) {
      int level = recovery.getKey();
      int count = recovery.getValue();
      if (count == 0) continue;
      state.slots.computeIfPresent(level, (key, used) -> used - count);
      state.used.put(state.initial.arcaneRecoveryKey(), 1);
      events.add(
          new Event(
              "SpellSlotsRecovered",
              now,
              state.initial.id(),
              Map.of("level", level, "amount", count)));
    }
    recharge(state, Trigger.SHORT_REST, now, events, random);
    endRestEffects(state, Kind.SHORT, now, events);
  }

  private static RollResult hitDie(int size, int constitution, boolean fixed, RandomSource random) {
    return healingDie("Hit Die", "hit-die", size, fixed)
        .fixed("Constitution", "constitution", constitution)
        .build()
        .roll(random);
  }

  private static RollPlan.Builder healingDie(String label, String source, int size, boolean fixed) {
    var plan = RollPlan.builder();
    return fixed
        ? plan.fixed(label, source, size / 2 + 1)
        : plan.dice(label, source, DiceExpression.parse("1d" + size), false);
  }

  private static void longRest(
      Working state, Plan plan, long now, List<Event> events, RandomSource random) {
    if (plan.ateAndDrank() && state.exhaustion > 0) {
      state.exhaustion--;
      int max = Exhaustion.maximumHp(state.initial.baseMaximumHp(), state.exhaustion);
      state.health =
          CombatHealth.of(Math.min(state.health.current(), max), max, state.health.temporary());
      events.add(
          new Event(
              "ExhaustionReduced", now, state.initial.id(), Map.of("remaining", state.exhaustion)));
    }
    heal(state, state.health.maximum() - state.health.current(), now, events);
    int limit = recoveryLimit(state.initial);
    for (var die : state.spent.descendingMap().entrySet()) {
      int restore =
          plan.recoverHitDice() == null
              ? Math.min(limit, die.getValue())
              : plan.recoverHitDice().getOrDefault(die.getKey(), 0);
      if (restore > 0) {
        die.setValue(die.getValue() - restore);
        limit -= restore;
        events.add(
            new Event(
                "HitDiceRecovered",
                now,
                state.initial.id(),
                Map.of("size", die.getKey(), "amount", restore)));
      }
    }
    for (var slot : state.slots.entrySet()) {
      if (slot.getValue() > 0) {
        events.add(
            new Event(
                "SpellSlotsRecovered",
                now,
                state.initial.id(),
                Map.of("level", slot.getKey(), "amount", slot.getValue())));
        slot.setValue(0);
      }
    }
    recharge(state, Trigger.LONG_REST, now, events, random);
    if (state.health.temporary() > 0) {
      state.health = CombatHealth.of(state.health.current(), state.health.maximum(), 0);
      events.add(new Event("TemporaryHitPointsEnded", now, state.initial.id(), Map.of()));
    }
    endRestEffects(state, Kind.LONG, now, events);
    state.lastLongRest = now;
  }

  private static void heal(Working state, int amount, long now, List<Event> events) {
    var next = state.health.heal(amount);
    int change = next.current() - state.health.current();
    if (change > 0) {
      state.health = next;
      state.recoveryAt = null;
      state.recoveryOrder = 0;
      events.add(new Event("HpRegained", now, state.initial.id(), Map.of("amount", change)));
    }
  }

  private static void recharge(
      Working state, Trigger trigger, long now, List<Event> events, RandomSource random) {
    for (var resource : state.initial.resources()) {
      int used = state.used.get(resource.key());
      var rule = resource.recharge().get(trigger);
      if (used == 0 || rule == null) continue;
      var recharge = evaluateRecharge(used, rule, random);
      if (recharge.amount() > 0) state.used.put(resource.key(), used - recharge.amount());
      if (recharge.recordable()) {
        events.add(
            new Event(
                "ResourceRecharged",
                now,
                state.initial.id(),
                Map.of(
                    "resource",
                    resource.key(),
                    "amount",
                    recharge.amount(),
                    "dice",
                    recharge.dice())));
      }
    }
  }

  public static Recharge evaluateRecharge(
      int used, ToIntFunction<RandomSource> rule, RandomSource random) {
    var draws = new ArrayList<RechargeDie>();
    int amount =
        rule.applyAsInt(
            bound -> {
              int zeroBased = random.nextInt(bound);
              draws.add(new RechargeDie(bound, zeroBased + 1));
              return zeroBased;
            });
    return new Recharge(Math.clamp(amount, 0, used), List.copyOf(draws));
  }

  public record RechargeDie(int size, int face) {}

  public record Recharge(int amount, List<RechargeDie> dice) {
    public boolean recordable() {
      return amount > 0 || !dice.isEmpty();
    }
  }

  private static void endRestEffects(Working state, Kind kind, long now, List<Event> events) {
    var iter = state.effects.iterator();
    while (iter.hasNext()) {
      var effect = iter.next();
      var duration = effect.end().kind();
      if (duration == DurationKind.UNTIL_SHORT_OR_LONG_REST
          || (kind == Kind.LONG
              && (duration == DurationKind.UNTIL_LONG_REST
                  || duration == DurationKind.CONCENTRATION))) {
        iter.remove();
        events.add(
            new Event("EffectExpired", now, state.initial.id(), Map.of("effect", effect.key())));
      }
    }
  }

  public enum Kind {
    SHORT,
    LONG
  }

  public enum Trigger {
    SHORT_REST,
    LONG_REST,
    DAWN,
    ROLL_INITIATIVE,
    START_OF_OWN_TURN,
    MANUAL
  }

  public enum Problem {
    REST_NOT_ALLOWED,
    REST_TOO_SHORT,
    HIT_DICE_UNAVAILABLE,
    INVALID_CHOICES,
    NOT_FOUND
  }

  public static final class Rejected extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Problem problem;
    private final UUID[] participants;
    private final String field;
    private final String fieldCode;

    Rejected(Problem problem, List<UUID> participants) {
      this(problem, participants, null, null);
    }

    Rejected(Problem problem, List<UUID> participants, String field, String fieldCode) {
      super(problem.name());
      this.problem = problem;
      this.participants = participants.toArray(UUID[]::new);
      this.field = field;
      this.fieldCode = fieldCode;
    }

    public String field() {
      return field;
    }

    public String fieldCode() {
      return fieldCode;
    }

    public Problem problem() {
      return problem;
    }

    public List<UUID> participants() {
      return List.of(participants);
    }
  }

  private static Rejected invalid(String field, String code) {
    return new Rejected(Problem.INVALID_CHOICES, List.of(), field, code);
  }

  public record Settings(
      int shortMinutes,
      int longMinutes,
      int longIntervalHours,
      boolean interruptedLongRest,
      boolean fixedHitDice,
      int startTimeOfDay,
      int dawn) {
    public static Settings standard() {
      return new Settings(60, 480, 24, false, false, 28800, 21600);
    }

    public static Settings epicHeroism() {
      return new Settings(5, 60, 1, false, false, 28800, 21600);
    }
  }

  public record Resource(String key, int used, Map<Trigger, ToIntFunction<RandomSource>> recharge) {
    public Resource {
      recharge = Map.copyOf(recharge);
    }
  }

  public record Participant(
      UUID id,
      CombatHealth health,
      int baseMaximumHp,
      int constitutionModifier,
      int exhaustion,
      Long lastLongRest,
      Long recoveryAt,
      Map<Integer, Integer> hitDice,
      Map<Integer, Integer> spentHitDice,
      Map<Integer, Integer> usedSlots,
      List<Resource> resources,
      List<ConditionInstance> effects,
      BardicInspiration inspiration,
      int wizardLevel,
      int bardLevel,
      String arcaneRecoveryKey,
      Set<String> conditions,
      long recoveryOrder) {
    public Participant {
      hitDice = Map.copyOf(hitDice);
      spentHitDice = Map.copyOf(spentHitDice);
      usedSlots = Map.copyOf(usedSlots);
      resources = List.copyOf(resources);
      effects = List.copyOf(effects);
      conditions = Set.copyOf(conditions);
    }
  }

  public record Plan(
      UUID id,
      Integer spendCount,
      Integer spendUntil,
      boolean ateAndDrank,
      Map<Integer, Integer> recoverHitDice,
      Map<Integer, Integer> arcaneRecovery) {
    public Plan {
      if (recoverHitDice != null) recoverHitDice = Map.copyOf(recoverHitDice);
      arcaneRecovery = Map.copyOf(arcaneRecovery);
    }

    public static Plan noChoices(UUID id) {
      return new Plan(id, null, null, true, null, Map.of());
    }
  }

  public record Song(UUID performer, Set<UUID> listeners) {
    public Song {
      listeners = Set.copyOf(listeners);
    }
  }

  public record Request(
      Kind kind, int durationMinutes, boolean interrupted, List<Plan> plans, List<Song> songs) {
    public Request {
      plans = List.copyOf(plans);
      songs = List.copyOf(songs);
    }
  }

  public record Preview(
      long end, Kind benefit, boolean interrupted, List<PreviewRow> participants) {
    public Preview {
      participants = List.copyOf(participants);
    }

    public boolean requiresConfirmation() {
      return benefit == null || participants.stream().anyMatch(row -> !row.warnings().isEmpty());
    }

    public String interruptionOutcome() {
      if (!interrupted) return "";
      return benefit == Kind.SHORT
          ? "The interrupted long rest grants short rest benefits."
          : "The interrupted rest advances time without granting rest benefits.";
    }
  }

  public record PreviewRow(
      UUID id,
      int hitDiceAvailable,
      int spendable,
      int recoveryAllowance,
      Map<Integer, Integer> recoverableHitDice,
      int arcaneRecoveryBudget,
      Map<Integer, Integer> expendedSlots,
      int songOfRestDie,
      List<String> warnings) {
    public PreviewRow {
      recoverableHitDice = java.util.Collections.unmodifiableMap(new TreeMap<>(recoverableHitDice));
      expendedSlots = java.util.Collections.unmodifiableMap(new TreeMap<>(expendedSlots));
      warnings = List.copyOf(warnings);
    }

    public boolean canChooseHitDiceRecovery() {
      return recoveryAllowance > 0
          && recoverableHitDice.size() > 1
          && recoverableHitDice.values().stream().mapToInt(Integer::intValue).sum()
              > recoveryAllowance;
    }
  }

  public record Event(
      String type, long seconds, UUID character, Map<String, Object> data, long creationOrder) {
    public Event(String type, long seconds, UUID character, Map<String, Object> data) {
      this(type, seconds, character, data, 0);
    }

    public Event {
      data = Map.copyOf(data);
    }
  }

  public record Resolution(long end, List<Participant> participants, List<Event> events) {
    public Resolution {
      participants = List.copyOf(participants);
      events = List.copyOf(events);
    }
  }

  private record Timer(ClockExpiry expiry, Working state) {}

  private static final class Working {
    private final Participant initial;
    private final TreeMap<Integer, Integer> spent;
    private final TreeMap<Integer, Integer> slots;
    private final Map<String, Integer> used = new HashMap<>();
    private final List<ConditionInstance> effects;
    private CombatHealth health;
    private int exhaustion;
    private Long lastLongRest;
    private Long recoveryAt;
    private long recoveryOrder;
    private BardicInspiration inspiration;

    Working(Participant initial) {
      this.initial = initial;
      health = initial.health();
      exhaustion = initial.exhaustion();
      lastLongRest = initial.lastLongRest();
      recoveryAt = initial.recoveryAt();
      recoveryOrder = initial.recoveryOrder();
      inspiration = initial.inspiration();
      spent = new TreeMap<>(initial.spentHitDice());
      slots = new TreeMap<>(initial.usedSlots());
      effects = new ArrayList<>(initial.effects());
      initial.resources().forEach(r -> used.put(r.key(), r.used()));
    }

    Event expire(ClockExpiry expiry) {
      var type = "EffectExpired";
      Map<String, Object> data =
          switch (expiry.kind()) {
            case CONDITION -> {
              effects.remove(expiry.effect());
              yield Map.of("effect", expiry.effect().key());
            }
            case INSPIRATION -> {
              inspiration = null;
              yield Map.of("effect", "Bardic Inspiration");
            }
            case STABLE_RECOVERY -> {
              type = "StableRecovered";
              health = health.heal(1);
              recoveryAt = null;
              recoveryOrder = 0;
              yield Map.of("amount", 1);
            }
          };
      return new Event(type, expiry.deadline(), initial.id(), data, expiry.creationOrder());
    }

    Participant snapshot() {
      var resources =
          initial.resources().stream()
              .map(r -> new Resource(r.key(), used.get(r.key()), r.recharge()))
              .toList();
      return new Participant(
          initial.id(),
          health,
          initial.baseMaximumHp(),
          initial.constitutionModifier(),
          exhaustion,
          lastLongRest,
          recoveryAt,
          initial.hitDice(),
          spent,
          slots,
          resources,
          effects,
          inspiration,
          initial.wizardLevel(),
          initial.bardLevel(),
          initial.arcaneRecoveryKey(),
          initial.conditions(),
          recoveryOrder);
    }
  }
}
