package com.github.sbadolankaur.rpg.encounter.application;

import static com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.invalid;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope;
import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Reply;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.Code;
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Condition;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Tactics;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth.State;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls.Cover;
import com.github.sbadolankaur.rpg.engine.combat.TurnBudget;
import com.github.sbadolankaur.rpg.engine.dice.CriticalDiceMode;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AdvantageSources;
import com.github.sbadolankaur.rpg.engine.kernel.CheckRules;
import com.github.sbadolankaur.rpg.engine.kernel.D20Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;

@Service
public class CombatActions {
  private static final List<String> SIZES =
      List.of("tiny", "small", "medium", "large", "huge", "gargantuan");
  private final EncounterCommands commands;

  CombatActions(EncounterCommands commands) {
    this.commands = commands;
  }

  public Reply attack(UUID campaign, UUID id, UUID actor, Envelope envelope, Attack request) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "attacks",
        request,
        true,
        context -> {
          var attacker = context.canAct(request.actor());
          var target = target(context, request.target());
          if (request.actor().equals(request.target()))
            throw new CampaignException(Code.INVALID_TARGET);
          var budgetBefore = context.table.budget();
          var weapon =
              attacker.attacks().stream()
                  .filter(option -> option.key().equals(request.key()))
                  .findFirst()
                  .orElseThrow(EncounterCommands::invalid);
          if (request.distance() < 0 || request.distance() > weapon.longRange())
            throw new CampaignException(Code.TARGET_OUT_OF_RANGE);
          if (request.cover() == Cover.TOTAL) throw new CampaignException(Code.TOTAL_COVER);
          if (request.knockOut() && weapon.ranged()) throw invalid();
          if (sourced(attacker, "srd-5.1:charmed", target.entry().id()))
            throw new CampaignException(Code.INVALID_TARGET);
          if (!Encounters.ties(context.entries.values()).isEmpty()) throw invalid();
          spend(
              context,
              budget ->
                  weapon.offHand()
                      ? budget.offHand()
                      : budget.attack(attacker.attacksPerAction(), weapon.light()));

          boolean seeTarget =
              CombatConditions.canSee(
                  attacker.conditions(),
                  target.conditions(),
                  target.entry().tactics().hidden() || request.targetObscured());
          boolean seenByTarget =
              CombatConditions.canSee(
                  target.conditions(),
                  attacker.conditions(),
                  attacker.entry().tactics().hidden() || request.attackerObscured());
          var helper =
              context.entries.values().stream()
                  .filter(Encounters::present)
                  .filter(
                      entry ->
                          attacker.entry().id().equals(entry.tactics().helping())
                              && target.entry().id().equals(entry.tactics().helpedTarget()))
                  .findFirst()
                  .orElse(null);
          var sources =
              CombatConditions.attack(
                  attacker.conditions(),
                  target.conditions(),
                  request.distance(),
                  seeTarget,
                  seenByTarget,
                  weapon.ranged()
                      && (request.nearbyHostile()
                          || request.distance() <= 5 && seenByTarget && !target.incapacitated()),
                  target.entry().tactics().dodging()
                      && target.speed() > 0
                      && !target.incapacitated(),
                  helper != null,
                  request.fearSourceVisible()
                      && attacker.conditions().contains("srd-5.1:frightened"),
                  request.mode());
          RollMode mode = sources.mode();
          if (weapon.ranged() && request.distance() > weapon.normalRange()) {
            var penalties = new ArrayList<>(sources.disadvantages());
            penalties.add("Long range");
            mode = new AdvantageSources(sources.advantages(), penalties).mode();
          }

          var preflight = new ArrayList<String>();
          if (weapon.damage().stream().anyMatch(dice -> dice.notation().contains("d")))
            preflight.add("criticalDiceMode");
          if (target.vitals().health().current() == 0 && target.vitals().health().temporary() > 0)
            preflight.add("temporaryHpAbsorbsDeathSaveFailure");
          context.preflight(preflight);
          String criticalMode = context.setting("criticalDiceMode", String.class);
          boolean absorb =
              target.vitals().health().current() == 0
                  && target.vitals().health().temporary() > 0
                  && context.setting("temporaryHpAbsorbsDeathSaveFailure", Boolean.class);
          var attack =
              CombatRolls.attack(
                  weapon.bonus(),
                  target.armor(),
                  request.cover(),
                  weapon.criticalOn(),
                  CombatConditions.automaticCritical(target.conditions(), request.distance()),
                  mode,
                  context.random());
          context.dice("Attack", request.actor(), attack.roll());
          context.result("attack", attack);
          if (attacker.entry().tactics().hidden()) {
            Tactics flags = attacker.entry().tactics();
            context.update(
                attacker
                    .entry()
                    .tactics(
                        flags.flags(
                            flags.dodging(),
                            false,
                            flags.helping(),
                            flags.helpedTarget(),
                            flags.readied())));
          }
          if (helper != null) {
            var flags = helper.tactics();
            context.update(
                helper.tactics(
                    flags.flags(flags.dodging(), flags.hidden(), null, null, flags.readied())));
          }
          boolean hit = attack.hit() && !request.targetAbsent();
          var undo =
              new AttackUndo(
                  budgetBefore,
                  attacker.entry(),
                  target.entry(),
                  helper,
                  target.vitals(),
                  context.equipment(target),
                  hit);
          context.result("actorId", request.actor());
          context.result("action", "attack");
          context.result("outcome", hit ? (attack.critical() ? "critical-hit" : "hit") : "miss");
          context.result("hit", hit);
          if (hit) {
            var damage = new LinkedHashMap<String, Integer>();
            for (var part : weapon.damage()) {
              var roll =
                  CombatRolls.damage(
                      CombatRolls.weaponDamage(weapon.label(), weapon.key(), part.notation()),
                      attack.critical(),
                      CriticalDiceMode.valueOf(criticalMode),
                      context.random());
              context.dice("Weapon damage", request.actor(), roll);
              damage.merge(part.type(), roll.total(), Math::addExact);
            }
            var applied = new LinkedHashMap<String, Integer>();
            int total = 0;
            for (var value : damage.entrySet()) {
              String type = value.getKey();
              boolean resistant =
                  defense(target, "resistance", type, weapon.magical())
                      || target.conditions().contains("srd-5.1:petrified");
              int amount =
                  CombatHealth.damageAmount(
                      value.getValue(),
                      0,
                      false,
                      resistant,
                      defense(target, "vulnerability", type, weapon.magical()),
                      defense(target, "immunity", type, weapon.magical()));
              applied.put(type, amount);
              total = Math.addExact(total, amount);
            }
            var after =
                target
                    .vitals()
                    .health()
                    .damage(
                        total, attack.critical(), target.deathMode(), request.knockOut(), absorb);
            context.health(target, after.health());
            context.dm(
                total > 0 ? "DamageTaken" : "DamageBlocked",
                Map.of(
                    "targetId",
                    request.target(),
                    "magical",
                    weapon.magical(),
                    "damage",
                    applied,
                    "absorbed",
                    after.absorbed(),
                    "hitPointsLost",
                    after.hitPointsLost(),
                    "state",
                    after.health().state()));
            if (after.hitPointsLost() > 0)
              context.dm(
                  "HitPointsLost",
                  Map.of("targetId", request.target(), "amount", after.hitPointsLost()));
            context.result("hitPointsAfter", after.health());
            context.result("damage", applied);
            if (target.owner() != null)
              context.tell(
                  "OwnHitPointsChanged",
                  target.owner(),
                  target.name() + " now has " + after.health().current() + " hit points.");
          }
          context.dm(
              "AttackResolved",
              Map.of(
                  "actorId",
                  request.actor(),
                  "targetId",
                  request.target(),
                  "cover",
                  request.cover(),
                  "weapon",
                  weapon.key(),
                  "hit",
                  hit,
                  "critical",
                  hit && attack.critical(),
                  "undo",
                  undo));
        });
  }

  public Reply simple(
      UUID campaign, UUID id, UUID actor, Envelope envelope, UUID creature, Simple action) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        action.name(),
        creature,
        true,
        context -> {
          var acting = context.canAct(creature);
          var flags = acting.entry().tactics();
          switch (action) {
            case DASH -> spend(context, TurnBudget::dash);
            case DISENGAGE -> spend(context, TurnBudget::disengage);
            case DODGE -> {
              spend(context, TurnBudget::action);
              context.update(
                  acting
                      .entry()
                      .tactics(
                          flags.flags(
                              true,
                              flags.hidden(),
                              flags.helping(),
                              flags.helpedTarget(),
                              flags.readied())));
            }
            case HIDE -> {
              spend(context, TurnBudget::action);
              var roll =
                  CombatRolls.d20(
                      "Stealth",
                      acting.skills().getOrDefault("stealth", acting.abilities().get(Ability.DEX)),
                      checkMode(acting),
                      context.random());
              context.dice("Hide", creature, roll);
              context.update(
                  acting
                      .entry()
                      .tactics(
                          flags.flags(
                              flags.dodging(),
                              true,
                              flags.helping(),
                              flags.helpedTarget(),
                              flags.readied())));
              context.dm("HideResolved", Map.of("combatantId", creature, "stealth", roll.total()));
            }
            case STAND -> {
              if (!acting.conditions().contains(CombatConditions.PRONE) || acting.speed() == 0)
                throw invalid();
              spend(context, budget -> budget.move(acting.speed() / 2, acting.speed()));
              removeCondition(context, acting, CombatConditions.PRONE);
            }
            case DROP_PRONE -> addCondition(context, acting, CombatConditions.PRONE, null);
          }
          context.dm(
              "TurnActionResolved", Map.of("combatantId", creature, "action", action.name()));
        });
  }

  public Reply help(UUID campaign, UUID id, UUID actor, Envelope envelope, Help form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "helps",
        form,
        true,
        context -> {
          var helping = context.canAct(form.actor());
          target(context, form.recipient());
          if (form.target() != null) {
            target(context, form.target());
            if (form.distance() < 0 || form.distance() > 5)
              throw new CampaignException(Code.TARGET_OUT_OF_RANGE);
          }
          spend(context, TurnBudget::action);
          Tactics flags = helping.entry().tactics();
          context.update(
              helping
                  .entry()
                  .tactics(
                      flags.flags(
                          flags.dodging(),
                          flags.hidden(),
                          form.recipient(),
                          form.target(),
                          flags.readied())));
          context.dm(
              "HelpApplied", Map.of("combatantId", form.actor(), "recipient", form.recipient()));
        });
  }

  public Reply ready(UUID campaign, UUID id, UUID actor, Envelope envelope, Ready form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "readies",
        form,
        true,
        context -> {
          var acting = context.canAct(form.actor());
          if (form.trigger() == null
              || form.trigger().isBlank()
              || form.trigger().length() > 200
              || !List.of("attack", "move", "other").contains(form.action())) throw invalid();
          spend(context, TurnBudget::action);
          Tactics flags = acting.entry().tactics();
          context.update(
              acting
                  .entry()
                  .tactics(
                      flags.flags(
                          flags.dodging(),
                          flags.hidden(),
                          flags.helping(),
                          flags.helpedTarget(),
                          form.action() + ": " + form.trigger())));
          context.dm(
              "ActionReadied",
              Map.of(
                  "combatantId", form.actor(), "trigger", form.trigger(), "action", form.action()));
        });
  }

  public Reply objectUse(UUID campaign, UUID id, UUID actor, Envelope envelope, ObjectUse form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "object-uses",
        form,
        true,
        context -> {
          context.canAct(form.actor());
          if (form.note() == null || form.note().length() > 200) throw invalid();
          spend(context, budget -> budget.interact(form.usesAction()));
          context.dm("ObjectUsed", Map.of("combatantId", form.actor(), "note", form.note()));
        });
  }

  public Reply contest(UUID campaign, UUID id, UUID actor, Envelope envelope, Contest form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        form.kind().name(),
        form,
        true,
        context -> {
          var first = context.canAct(form.actor());
          var second = target(context, form.target());
          if (first.entry().id().equals(second.entry().id())) throw invalid();
          if (form.distance() < 0 || form.distance() > 5)
            throw new CampaignException(Code.TARGET_OUT_OF_RANGE);
          if (form.kind() != ContestKind.ESCAPE
              && SIZES.indexOf(second.size()) > SIZES.indexOf(first.size()) + 1)
            throw new CampaignException(Code.INVALID_TARGET);
          if (form.kind() == ContestKind.ESCAPE
              && !sourced(first, CombatConditions.GRAPPLED, second.entry().id()))
            throw new CampaignException(Code.INVALID_TARGET);
          spend(
              context,
              budget ->
                  form.kind() == ContestKind.ESCAPE
                      ? budget.action()
                      : budget.attack(first.attacksPerAction(), false));
          boolean won = second.incapacitated();
          if (!won) {
            var check =
                CombatRolls.d20(
                    "Athletics",
                    first.skills().getOrDefault("athletics", first.abilities().get(Ability.STR)),
                    checkMode(first),
                    context.random());
            String defense = form.acrobatics() ? "acrobatics" : "athletics";
            Ability ability = form.acrobatics() ? Ability.DEX : Ability.STR;
            var opposed =
                CombatRolls.d20(
                    defense,
                    second.skills().getOrDefault(defense, second.abilities().get(ability)),
                    checkMode(second),
                    context.random());
            context.dice("Contest", form.actor(), check);
            context.dice("Contest defense", form.target(), opposed);
            won =
                CheckRules.contest(check.total(), opposed.total())
                    == CheckRules.ContestResult.WIN_A;
          }
          if (won) {
            switch (form.kind()) {
              case GRAPPLE ->
                  addCondition(context, second, CombatConditions.GRAPPLED, first.entry().id());
              case SHOVE_PRONE -> addCondition(context, second, CombatConditions.PRONE, null);
              case SHOVE_PUSH -> context.result("pushedFeet", 5);
              case ESCAPE -> removeCondition(context, first, CombatConditions.GRAPPLED);
            }
          }
          context.dm(
              "ContestResolved",
              Map.of(
                  "type",
                  form.kind().name(),
                  "actorId",
                  form.actor(),
                  "targetId",
                  form.target(),
                  "success",
                  won));
          context.result("success", won);
        });
  }

  public Reply check(UUID campaign, UUID id, UUID actor, Envelope envelope, Check form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        form.save() ? "saving-throws" : "ability-checks",
        form,
        true,
        context -> {
          context.acting(form.actor());
          if (form.ability() == null
              || form.combatants().isEmpty()
              || form.combatants().size() > 30
              || new HashSet<>(form.combatants()).size() != form.combatants().size()
              || (form.dc() == null && form.save())
              || (form.dc() != null && (form.dc() < 1 || form.dc() > 40))) throw invalid();
          if (form.dc() != null
              && form.combatants().stream()
                  .map(context::creature)
                  .anyMatch(
                      subject ->
                          !form.save()
                              || !CombatConditions.forcedSaveFailure(
                                  subject.conditions(), form.ability())))
            context.preflight(List.of("autoOutcomeOnNat20Checks"));
          var outcome = new ArrayList<Map<String, Object>>();
          for (UUID subject : form.combatants()) {
            var creature = target(context, subject);
            boolean forced =
                form.save()
                    && CombatConditions.forcedSaveFailure(creature.conditions(), form.ability());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("combatantId", subject);
            result.put("automaticFailure", forced);
            if (!forced) {
              int bonus =
                  form.save()
                      ? creature.saves().get(form.ability())
                      : form.skill() == null
                          ? creature.abilities().get(form.ability())
                          : creature
                              .skills()
                              .getOrDefault(form.skill(), creature.abilities().get(form.ability()));
              boolean dodge =
                  form.save()
                      && form.ability() == Ability.DEX
                      && creature.entry().tactics().dodging()
                      && creature.speed() > 0
                      && !creature.incapacitated();
              boolean restraint =
                  form.save()
                      && form.ability() == Ability.DEX
                      && creature.conditions().contains("srd-5.1:restrained");
              var sources =
                  form.save()
                      ? new AdvantageSources(
                          dodge ? List.of("Dodge") : List.of(),
                          restraint ? List.of("Restrained") : List.of())
                      : new AdvantageSources(
                          List.of(),
                          checkMode(creature) == RollMode.DISADVANTAGE
                              ? List.of("Poisoned")
                              : List.of());
              var roll =
                  CombatRolls.d20("Ability modifier", bonus, sources.mode(), context.random());
              context.dice(form.save() ? "Saving throw" : "Ability check", subject, roll);
              result.put("roll", roll);
              if (form.dc() != null)
                result.put(
                    "success",
                    D20Test.resolve(
                            roll,
                            form.dc(),
                            form.save() ? D20Test.Kind.SAVING_THROW : D20Test.Kind.ABILITY_CHECK,
                            D20Test.NaturalMode.valueOf(
                                context.setting("autoOutcomeOnNat20Checks", String.class)))
                        .success());
            } else result.put("success", false);
            outcome.add(result);
          }
          context.result("outcomes", outcome);
          context.dm("TestResolved", Map.of("outcomes", outcome));
        });
  }

  public Reply firstAid(UUID campaign, UUID id, UUID actor, Envelope envelope, Help form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "stabilizations",
        form,
        true,
        context -> {
          var helper = context.canAct(form.actor());
          var target = target(context, form.recipient());
          if (target.vitals().health().state() != State.DYING
              || form.distance() > 5
              || form.distance() < 0) throw new CampaignException(Code.INVALID_TARGET);
          spend(context, TurnBudget::action);
          var roll =
              CombatRolls.d20(
                  "Medicine",
                  helper.skills().getOrDefault("medicine", helper.abilities().get(Ability.WIS)),
                  checkMode(helper),
                  context.random());
          context.dice("Stabilize", form.actor(), roll);
          boolean success = roll.total() >= 10;
          if (success) context.health(target, target.vitals().health().stabilize());
          context.dm("FirstAidResolved", Map.of("targetId", form.recipient(), "success", success));
          context.result("success", success);
        });
  }

  static void addCondition(Context context, Creature creature, String key, UUID source) {
    if (creature.immunities().contains(key)) return;
    if (source == null) {
      var state = creature.vitals();
      var conditions = new HashSet<>(state.conditions());
      if (conditions.add(key)) {
        context.vitals(creature, new Vitals(state.health(), state.recoveryAt(), conditions));
      }
      return;
    }
    var entries = new ArrayList<>(creature.entry().tactics().conditions());
    var condition = new Condition(key, source);
    if (!entries.contains(condition)) {
      entries.add(condition);
      context.update(creature.entry().tactics(creature.entry().tactics().conditions(entries)));
    }
  }

  static void removeCondition(Context context, Creature creature, String key) {
    var entry = creature.entry();
    context.update(
        entry.tactics(
            entry
                .tactics()
                .conditions(
                    entry.tactics().conditions().stream()
                        .filter(condition -> !condition.key().equals(key))
                        .toList())));
    var state = creature.vitals();
    if (state.conditions().contains(key)) {
      var conditions = new HashSet<>(state.conditions());
      conditions.remove(key);
      context.vitals(
          context.creature(entry.id()), new Vitals(state.health(), state.recoveryAt(), conditions));
    }
  }

  static boolean sourced(Creature creature, String condition, UUID source) {
    return creature.entry().tactics().conditions().contains(new Condition(condition, source));
  }

  static Creature target(Context context, UUID target) {
    var creature = context.creature(target);
    if (!Encounters.present(creature.entry()) || creature.vitals().health().state() == State.DEAD)
      throw new CampaignException(Code.INVALID_TARGET);
    return creature;
  }

  private static RollMode checkMode(Creature creature) {
    return creature.conditions().contains("srd-5.1:poisoned")
        ? RollMode.DISADVANTAGE
        : RollMode.NORMAL;
  }

  private static boolean defense(Creature target, String kind, String type, boolean magical) {
    return target.defenses().stream()
        .anyMatch(defense -> defense.kind().equals(kind) && defense.applies(type, magical));
  }

  private static void spend(Context context, UnaryOperator<TurnBudget> spend) {
    var table = context.table;
    try {
      context.table =
          table.position(table.phase(), table.round(), table.acting(), spend.apply(table.budget()));
    } catch (IllegalStateException spent) {
      throw new CampaignException(Code.NO_ACTIONS_LEFT);
    }
  }

  record AttackUndo(
      TurnBudget budget,
      EncounterState.Entry attacker,
      EncounterState.Entry target,
      EncounterState.Entry helper,
      Vitals targetVitals,
      Map<UUID, String> equipment,
      boolean hit) {}

  public enum Simple {
    DASH,
    DODGE,
    DISENGAGE,
    HIDE,
    STAND,
    DROP_PRONE
  }

  public enum ContestKind {
    GRAPPLE,
    ESCAPE,
    SHOVE_PRONE,
    SHOVE_PUSH
  }

  public record Attack(
      UUID actor,
      UUID target,
      String key,
      int distance,
      Cover cover,
      RollMode mode,
      boolean knockOut,
      boolean attackerObscured,
      boolean targetObscured,
      boolean nearbyHostile,
      boolean fearSourceVisible,
      boolean targetAbsent) {}

  public record Contest(
      UUID actor, UUID target, int distance, ContestKind kind, boolean acrobatics) {}

  public record Check(
      UUID actor, List<UUID> combatants, Ability ability, String skill, Integer dc, boolean save) {
    public Check {
      combatants = List.copyOf(combatants);
    }
  }

  public record Help(UUID actor, UUID recipient, UUID target, int distance) {}

  public record Ready(UUID actor, String trigger, String action) {}

  public record ObjectUse(UUID actor, String note, boolean usesAction) {}
}
