package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.TurnBudget;
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
      List<Condition> conditions) {
    public Tactics {
      conditions = List.copyOf(conditions);
    }

    public static Tactics initial(int firstRound, boolean surprised, List<Condition> conditions) {
      return new Tactics(
          firstRound, 0, surprised, false, false, true, false, null, null, null, conditions);
    }

    public Tactics start(int round) {
      return new Tactics(
          firstRound, round, surprised, false, hidden, true, false, null, null, null, conditions);
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
          conditions);
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
          conditions);
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
          conditions);
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
          conditions);
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
          next);
    }
  }

  public record Condition(String key, UUID source) {}

  public record Vitals(CombatHealth health, Long recoveryAt, Set<String> conditions) {
    public Vitals {
      conditions = Set.copyOf(conditions);
    }
  }
}
