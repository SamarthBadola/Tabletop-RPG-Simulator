package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.DeathMode;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.TurnBudget;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EncounterState {
  private EncounterState() {}

  public enum Phase {
    SETUP,
    INITIATIVE,
    ACTIVE,
    FINISHED
  }

  public record Table(
      UUID id,
      UUID campaign,
      String name,
      Phase phase,
      int round,
      UUID acting,
      TurnBudget budget,
      long version) {
    public Table position(Phase phase, int round, UUID acting, TurnBudget budget) {
      return new Table(id, campaign, name, phase, round, acting, budget, version);
    }
  }

  public record Entry(
      UUID id,
      UUID character,
      String monster,
      int ordinal,
      Integer initiative,
      Integer tie,
      Tactics tactics,
      Vitals npcVitals) {
    public Entry initiative(Integer next, Integer tie) {
      return new Entry(id, character, monster, ordinal, next, tie, tactics, npcVitals);
    }

    public Entry tactics(Tactics next) {
      return new Entry(id, character, monster, ordinal, initiative, tie, next, npcVitals);
    }

    public Entry vitals(Vitals next) {
      return new Entry(id, character, monster, ordinal, initiative, tie, tactics, next);
    }
  }

  public record Tactics(
      int firstRound,
      int lastTurn,
      boolean surprised,
      boolean dodging,
      boolean hidden,
      boolean reactionAvailable,
      boolean deathSaveDue,
      UUID helping,
      UUID helpedTarget,
      String readied,
      List<Condition> conditions,
      DeathMode deathMode,
      Stealth stealth) {
    public Tactics {
      conditions = List.copyOf(conditions);
    }

    public static Tactics initial(int firstRound, boolean surprised, List<Condition> conditions) {
      return new Tactics(
          firstRound,
          0,
          surprised,
          false,
          false,
          true,
          false,
          null,
          null,
          null,
          conditions,
          null,
          null);
    }

    public Tactics start(int round) {
      return new Tactics(
          firstRound,
          round,
          surprised,
          false,
          hidden,
          true,
          false,
          null,
          null,
          null,
          conditions,
          deathMode,
          stealth);
    }

    public Tactics end() {
      return new Tactics(
          firstRound,
          lastTurn,
          false,
          dodging,
          hidden,
          reactionAvailable,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          conditions,
          deathMode,
          stealth);
    }

    public Tactics flags(
        boolean dodging, boolean hidden, UUID helping, UUID target, String readied) {
      return new Tactics(
          firstRound,
          lastTurn,
          surprised,
          dodging,
          hidden,
          reactionAvailable,
          deathSaveDue,
          helping,
          target,
          readied,
          conditions,
          deathMode,
          hidden ? stealth : null);
    }

    public Tactics assigned(int round) {
      return new Tactics(
          round,
          lastTurn,
          surprised,
          dodging,
          hidden,
          reactionAvailable,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          conditions,
          deathMode,
          stealth);
    }

    public Tactics retainLastTurn(int previous) {
      return new Tactics(
          firstRound,
          previous,
          surprised,
          dodging,
          hidden,
          reactionAvailable,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          conditions,
          deathMode,
          stealth);
    }

    public Tactics spendReaction() {
      if (!reactionAvailable) throw new IllegalStateException("No reaction available");
      return new Tactics(
          firstRound,
          lastTurn,
          surprised,
          dodging,
          hidden,
          false,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          conditions,
          deathMode,
          stealth);
    }

    public Tactics deathSave(boolean due) {
      return new Tactics(
          firstRound,
          lastTurn,
          surprised,
          dodging,
          hidden,
          reactionAvailable,
          due,
          helping,
          helpedTarget,
          readied,
          conditions,
          deathMode,
          stealth);
    }

    public Tactics conditions(List<Condition> next) {
      return new Tactics(
          firstRound,
          lastTurn,
          surprised,
          dodging,
          hidden,
          reactionAvailable,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          next,
          deathMode,
          stealth);
    }

    Tactics deathMode(DeathMode mode) {
      return new Tactics(
          firstRound,
          lastTurn,
          surprised,
          dodging,
          hidden,
          reactionAvailable,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          conditions,
          mode,
          stealth);
    }

    Tactics surprise(boolean value) {
      return new Tactics(
          firstRound,
          lastTurn,
          value,
          dodging,
          hidden,
          reactionAvailable,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          conditions,
          deathMode,
          stealth);
    }

    Tactics stealth(Stealth next) {
      return new Tactics(
          firstRound,
          lastTurn,
          surprised,
          dodging,
          next != null && !next.hiddenFrom().isEmpty(),
          reactionAvailable,
          deathSaveDue,
          helping,
          helpedTarget,
          readied,
          conditions,
          deathMode,
          next);
    }

    boolean hiddenFrom(UUID observer) {
      return stealth == null ? hidden : stealth.hiddenFrom().contains(observer);
    }
  }

  public record Stealth(
      int total, Set<UUID> hiddenFrom, Set<UUID> noticedBy, Set<UUID> ties, Set<UUID> opponents) {
    public Stealth {
      hiddenFrom = Set.copyOf(hiddenFrom);
      noticedBy = Set.copyOf(noticedBy);
      ties = Set.copyOf(ties);
      opponents = Set.copyOf(opponents);
    }

    Stealth detectedBy(UUID observer) {
      var remaining = new HashSet<>(hiddenFrom);
      remaining.remove(observer);
      var noticed = new HashSet<>(noticedBy);
      noticed.add(observer);
      return new Stealth(total, remaining, noticed, ties, opponents);
    }
  }

  public record Condition(String key, UUID source) {}

  public record Vitals(
      CombatHealth health,
      Long recoveryAt,
      Set<String> conditions,
      List<ConditionInstance> effects,
      int exhaustion) {
    public Vitals {
      conditions = Set.copyOf(conditions);
      effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public Vitals(CombatHealth health, Long recoveryAt, Set<String> conditions) {
      this(health, recoveryAt, conditions, List.of(), 0);
    }

    Vitals effects(List<ConditionInstance> next) {
      return new Vitals(health, recoveryAt, conditions, next, exhaustion);
    }

    Vitals conditions(Set<String> next) {
      return new Vitals(health, recoveryAt, next, effects, exhaustion);
    }
  }
}
