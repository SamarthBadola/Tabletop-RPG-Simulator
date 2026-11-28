package com.github.sbadolankaur.rpg.encounter.application;

import static com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.invalid;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.combat.Exhaustion;
import com.github.sbadolankaur.rpg.engine.combat.TurnBudget;
import com.github.sbadolankaur.rpg.engine.dice.CriticalDiceMode;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AdvantageSources;
import com.github.sbadolankaur.rpg.engine.kernel.CheckRules;
import com.github.sbadolankaur.rpg.engine.kernel.D20Test;
import com.github.sbadolankaur.rpg.engine.kernel.ProficiencyBonus;
import com.github.sbadolankaur.rpg.engine.monster.HookedDamage;
import com.github.sbadolankaur.rpg.engine.monster.Regeneration;
import com.github.sbadolankaur.rpg.engine.monster.ZeroHitPointHook;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Offer;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
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
        context -> resolveAttack(context, request));
  }

  static void resolveAttack(Context context, Attack request) {
    var attacker = context.canAct(request.actor());
    var weapon =
        attacker.attacks().stream()
            .filter(option -> option.key().equals(request.key()))
            .findFirst()
            .orElseThrow(EncounterCommands::invalid);
    resolveAttack(context, request, attacker, weapon, false);
  }

  private static void resolveAttack(
      Context context,
      Attack request,
      Creature attacker,
      CombatRoster.AttackOption weapon,
      boolean opportunity) {
    var target = target(context, request.target());
    context.geometry.validateEnemyNearTarget(
        request.enemyNearTarget(), attacker.features().contains(RogueFeatures.SNEAK_ATTACK));
    if (request.actor().equals(request.target())) throw new CampaignException(Code.INVALID_TARGET);
    if (request.distance() < 0 || request.distance() > weapon.longRange())
      throw new CampaignException(Code.TARGET_OUT_OF_RANGE);
    if (request.cover() == Cover.TOTAL) throw new CampaignException(Code.TOTAL_COVER);
    if (request.knockOut() && weapon.ranged()) throw invalid();
    if (sourced(attacker, "srd-5.1:charmed", target.entry().id()))
      throw new CampaignException(Code.INVALID_TARGET);
    if (!Encounters.ties(context.entries.values()).isEmpty()) throw invalid();
    if (!opportunity) {
      spend(
          context,
          budget ->
              weapon.offHand()
                  ? budget.offHand()
                  : budget.attack(attacker.attacksPerAction(), weapon.light()));
    }

    boolean seeTarget =
        CombatConditions.canSee(
            attacker.conditions(),
            target.conditions(),
            target.entry().tactics().hiddenFrom(request.actor()) || request.targetObscured());
    boolean seenByTarget =
        CombatConditions.canSee(
            target.conditions(),
            attacker.conditions(),
            attacker.entry().tactics().hiddenFrom(request.target()) || request.attackerObscured());
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
            target.entry().tactics().dodging() && target.speed() > 0 && !target.incapacitated(),
            helper != null,
            request.fearSourceVisible() && attacker.conditions().contains("srd-5.1:frightened"),
            request.mode());
    var opponentSources =
        CombatEffects.rollSources(
            context, target, "attack", "against_holder", request.distance(), seenByTarget);
    var ownSources =
        CombatEffects.rollSources(
            context, attacker, "attack", "holder", request.distance(), seeTarget);
    var advantages = new ArrayList<>(sources.advantages());
    advantages.addAll(opponentSources.advantages());
    advantages.addAll(ownSources.advantages());
    var penalties = new ArrayList<>(sources.disadvantages());
    penalties.addAll(opponentSources.disadvantages());
    penalties.addAll(ownSources.disadvantages());
    if (weapon.ranged() && request.distance() > weapon.normalRange()) penalties.add("Long range");
    if (Exhaustion.disadvantagesAttacksAndSaves(attacker.vitals().exhaustion()))
      penalties.add("Exhaustion");
    var preflight = new ArrayList<String>();
    if (hasDamageDice(weapon)) preflight.add("criticalDiceMode");
    if (attacker.vitals().inspiration() != null
        || !CuttingWords.offers(context, request.actor(), Step.DEFENDING_RESULT).isEmpty())
      preflight.add("rollDecisionInfo");
    var slots = DivineSmite.available(context, attacker);
    if (!slots.isEmpty() || request.smiteSlotLevel() != null) preflight.add(DivineSmite.SETTING);
    if (target.vitals().health().current() == 0 && target.vitals().health().temporary() > 0)
      preflight.add("temporaryHpAbsorbsDeathSaveFailure");
    if (MonsterHooks.defender(context, target).undeadFortitude() && !request.knockOut())
      preflight.add("autoOutcomeOnNat20Checks");
    context.preflight(preflight);
    D20Test.NaturalMode hookNaturalMode =
        preflight.contains("autoOutcomeOnNat20Checks")
            ? D20Test.NaturalMode.valueOf(context.setting("autoOutcomeOnNat20Checks", String.class))
            : null;
    String smiteTiming = DivineSmite.validate(context, weapon, request.smiteSlotLevel(), slots);
    String criticalMode = context.setting("criticalDiceMode", String.class);
    boolean absorb =
        target.vitals().health().current() == 0
            && target.vitals().health().temporary() > 0
            && context.setting("temporaryHpAbsorbsDeathSaveFailure", Boolean.class);
    protection(context, request, target, penalties);
    RollMode mode = new AdvantageSources(advantages, penalties).mode();
    boolean sacred =
        !"unarmed".equals(weapon.key())
            && !weapon.offHand()
            && OathFeatures.sacredActive(context, attacker);
    boolean magicalWeapon = weapon.magical() || sacred;
    var attack =
        CombatRolls.attack(
            weapon.bonus() + (sacred ? Math.max(1, attacker.abilities().get(Ability.CHA)) : 0),
            target.armor(),
            request.cover(),
            weapon.criticalOn(),
            CombatConditions.automaticCritical(target.conditions(), request.distance()),
            mode,
            context.random());
    context.dice("Attack", request.actor(), attack.roll());
    if (attacker.vitals().inspiration() != null) {
      boolean criticalOnHit =
          CombatConditions.automaticCritical(target.conditions(), request.distance());
      var inspired =
          CombatFeatures.inspireRoll(
              context,
              attacker,
              attack.roll(),
              Point.AFTER_ATTACK_ROLL,
              !request.targetAbsent() && attack.hit());
      attack =
          CombatRolls.attackOutcome(inspired, attack.target(), weapon.criticalOn(), criticalOnHit);
    }
    int armor = attack.target();
    boolean criticalOnHit =
        CombatConditions.automaticCritical(target.conditions(), request.distance());
    var reducedAttack =
        CuttingWords.lowerRoll(
            context,
            request.actor(),
            Point.AFTER_ATTACK_ROLL,
            attack.roll(),
            roll ->
                !request.targetAbsent()
                    && CombatRolls.attackOutcome(roll, armor, weapon.criticalOn(), criticalOnHit)
                        .hit());
    attack = CombatRolls.attackOutcome(reducedAttack, armor, weapon.criticalOn(), criticalOnHit);
    if (request.targetAbsent())
      attack = new CombatRolls.Attack(attack.roll(), attack.target(), false, false);
    context.result("attack", attack);
    var currentAttacker = context.creature(attacker.entry().id());
    if (currentAttacker.entry().tactics().hidden()
        || currentAttacker.entry().tactics().stealth() != null) {
      Tactics flags = currentAttacker.entry().tactics();
      context.update(
          currentAttacker
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
    boolean hit = attack.hit();
    context.result("actorId", request.actor());
    context.result("action", "attack");
    context.result("outcome", hit ? (attack.critical() ? "critical-hit" : "hit") : "miss");
    context.result("hit", hit);
    if (hit) {
      var damage = new LinkedHashMap<String, Integer>();
      var smite =
          DivineSmite.onConfirmedHit(
              context,
              currentAttacker,
              target,
              weapon,
              request.smiteSlotLevel(),
              smiteTiming,
              slots);
      var sneak =
          RogueFeatures.sneakAttack(
              context, currentAttacker, weapon, mode, request.enemyNearTarget());
      boolean halve = RogueFeatures.uncannyDodge(context, target, seenByTarget);
      var defender = context.creature(request.target());
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
      if (smite != null) {
        var roll =
            CombatRolls.damage(
                smite, attack.critical(), CriticalDiceMode.valueOf(criticalMode), context.random());
        context.dice("Divine Smite", request.actor(), roll);
        damage.merge("radiant", roll.total(), Math::addExact);
      }
      if (sneak != null) {
        var roll =
            CombatRolls.damage(
                sneak, attack.critical(), CriticalDiceMode.valueOf(criticalMode), context.random());
        context.dice("Sneak Attack", request.actor(), roll);
        damage.merge(weapon.damage().getFirst().type(), roll.total(), Math::addExact);
      }
      CuttingWords.lowerDamage(context, request.actor(), damage);
      var applied = new LinkedHashMap<String, Integer>();
      int total = 0;
      for (var value : damage.entrySet()) {
        String type = value.getKey();
        boolean magical = magicalWeapon || smite != null && "radiant".equals(type);
        boolean resistant =
            defense(target, "resistance", type, magical)
                || target.conditions().contains("srd-5.1:petrified");
        int amount =
            CombatHealth.damageAmount(
                value.getValue(),
                0,
                halve,
                resistant,
                defense(target, "vulnerability", type, magical),
                defense(target, "immunity", type, magical));
        applied.put(type, amount);
        total = Math.addExact(total, amount);
      }
      var plain =
          target
              .vitals()
              .health()
              .damage(total, attack.critical(), target.deathMode(), request.knockOut(), absorb);
      var hook =
          HookedDamage.resolve(
              target.vitals().health(),
              plain,
              target.deathMode(),
              applied,
              attack.critical(),
              request.knockOut(),
              MonsterHooks.defender(context, target),
              new Regeneration.State(
                  target.entry().dyingByTrait(), target.entry().regenSuppressed()),
              (ability, dc) -> hookSave(context, defender, ability, dc, hookNaturalMode));
      var after = hook.damage();
      context.health(defender, after.health());
      MonsterHooks.writeMarks(context, request.target(), hook);
      if (hook.result() != ZeroHitPointHook.Result.NONE)
        context.result("zeroHitPointHook", hook.result().name());
      if (total > 0 && defender.conditions().contains("srd-5.1:turned"))
        CombatEffects.remove(context, context.creature(request.target()), "srd-5.1:turned", null);
      context.dm(
          total > 0 ? "DamageTaken" : "DamageBlocked",
          Map.of(
              "targetId",
              request.target(),
              "magical",
              magicalWeapon,
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
            "HitPointsLost", Map.of("targetId", request.target(), "amount", after.hitPointsLost()));
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
            hit && attack.critical()));
  }

  public Reply featureUse(
      UUID campaign, UUID id, UUID actor, Envelope envelope, CombatFeatures.Use form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "feature-uses",
        form,
        true,
        context -> CombatFeatures.use(context, form));
  }

  public Reply manualMove(UUID campaign, UUID id, UUID actor, Envelope envelope, ManualMove move) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "manual-moves",
        move,
        true,
        context -> resolveManualMove(context, move));
  }

  static void resolveManualMove(Context context, ManualMove move) {
    context.geometry.requireManualMovement();
    resolveMove(context, move);
  }

  // Step 14 supplies reach exits from the map through this same movement resolver.
  static void resolveMove(Context context, ManualMove move) {
    var mover = context.acting(move.actorId());
    if (mover.incapacitated() || mover.entry().tactics().surprised())
      throw new CampaignException(Code.NO_ACTIONS_LEFT);
    if (!Encounters.ties(context.entries.values()).isEmpty()) throw invalid();
    for (UUID id : move.leavesReachOf()) {
      if (!context.entries.containsKey(id) || id.equals(move.actorId())) throw invalid();
    }
    int cost =
        TurnBudget.movementCost(
            move.distanceFeet(), false, mover.conditions().contains(CombatConditions.PRONE), false);
    if (cost > context.table.budget().movementRemaining(mover.speed()))
      throw new CampaignException(Code.NO_ACTIONS_LEFT);

    boolean disengaged = context.table.budget().disengaged();
    for (UUID id : move.leavesReachOf()) {
      var reactor = context.creature(id);
      String reason =
          disengaged ? "The mover used Disengage." : opportunityRestriction(reactor, mover);
      if (reason == null && opportunityWeapons(reactor, mover).isEmpty())
        reason = "No eligible melee attack is available.";
      if (reason != null)
        context.dm(
            "OpportunityAttackSkipped",
            Map.of("reactorId", id, "reactor", reactor.name(), "reason", reason));
    }

    if (!disengaged && !move.leavesReachOf().isEmpty()) {
      var settings = new ArrayList<String>();
      if (move.leavesReachOf().stream()
          .map(context::creature)
          .flatMap(creature -> opportunityWeapons(creature, mover).stream())
          .anyMatch(CombatActions::hasDamageDice)) settings.add("criticalDiceMode");
      if (move.leavesReachOf().stream()
          .map(context::creature)
          .anyMatch(
              reactor ->
                  reactor.vitals().inspiration() != null
                      && !opportunityWeapons(reactor, mover).isEmpty()))
        settings.add("rollDecisionInfo");
      if (move.leavesReachOf().stream()
          .map(context::creature)
          .anyMatch(
              reactor ->
                  !opportunityWeapons(reactor, mover).isEmpty()
                      && !DivineSmite.available(context, reactor).isEmpty()))
        settings.add(DivineSmite.SETTING);
      context.preflight(settings);
      context
          .reactions()
          .window(
              Point.ON_MOVE_STEP,
              () -> opportunityOffers(context, move, cost),
              null,
              (decider, key) -> opportunityAttack(context, move.actorId(), decider, key));
    }

    var after = context.creature(move.actorId());
    boolean applied = mayFinishMove(context, after, cost);
    if (applied) spend(context, budget -> budget.move(cost, after.speed()));
    var movement =
        Map.of(
            "actorId",
            move.actorId(),
            "distanceFeet",
            applied ? move.distanceFeet() : 0,
            "movementSpent",
            applied ? cost : 0);
    context.dm(applied ? "MovementResolved" : "MovementInterrupted", movement);
    context.result("actorId", move.actorId());
    context.result("action", "manual-move");
    context.result("outcome", applied ? "moved" : "interrupted");
    context.result("movement", movement);
  }

  private static boolean mayFinishMove(Context context, Creature mover, int cost) {
    return mover.vitals().health().current() > 0
        && !mover.incapacitated()
        && cost <= context.table.budget().movementRemaining(mover.speed());
  }

  private static List<Offer> opportunityOffers(Context context, ManualMove move, int cost) {
    var mover = context.creature(move.actorId());
    if (!mayFinishMove(context, mover, cost)) return List.of();
    return move.leavesReachOf().stream()
        .map(context::creature)
        .flatMap(
            reactor ->
                opportunityWeapons(reactor, mover).stream()
                    .map(
                        weapon ->
                            new Offer(
                                Step.DEFENDING_RESULT,
                                reactor.entry().id(),
                                reactor.entry().initiative(),
                                0,
                                new Option(
                                    "opportunity:" + weapon.key(),
                                    "Opportunity attack: " + weapon.label(),
                                    true,
                                    null,
                                    0))))
        .toList();
  }

  private static String opportunityRestriction(Creature reactor, Creature mover) {
    if (!Encounters.present(reactor.entry())) return "The creature is not in the encounter.";
    if (!reactor.entry().tactics().reactionAvailable()) return "Reaction already used this round.";
    if (!canReact(reactor)) return "The creature cannot react.";
    if (sourced(reactor, "srd-5.1:charmed", mover.entry().id()))
      return "The creature cannot attack the mover while charmed by it.";
    if (!CombatConditions.canSee(
        reactor.conditions(),
        mover.conditions(),
        mover.entry().tactics().hiddenFrom(reactor.entry().id())))
      return "The creature cannot see the mover.";
    return null;
  }

  private static List<CombatRoster.AttackOption> opportunityWeapons(
      Creature reactor, Creature mover) {
    if (opportunityRestriction(reactor, mover) != null) return List.of();
    var weapons =
        reactor.attacks().stream()
            .filter(weapon -> !weapon.ranged() && !weapon.offHand())
            .filter(weapon -> reactor.entry().character() != null || !weapon.magical())
            .toList();
    if (reactor.entry().character() == null || !weapons.isEmpty()) return weapons;
    int strength = reactor.abilities().get(Ability.STR);
    int level = reactor.classLevels().values().stream().mapToInt(Integer::intValue).sum();
    int damage = Math.max(0, 1 + strength);
    var unarmed =
        new CombatRoster.AttackOption(
            "unarmed",
            "Unarmed strike",
            strength + ProficiencyBonus.forLevel(level),
            20,
            false,
            5,
            5,
            false,
            false,
            false,
            List.of(new CombatRoster.DamageDice(Integer.toString(damage), "bludgeoning")));
    return List.of(unarmed);
  }

  private static void opportunityAttack(
      Context context, UUID mover, UUID reactorId, String option) {
    var reactor = context.creature(reactorId);
    var weapon =
        opportunityWeapons(reactor, context.creature(mover)).stream()
            .filter(attack -> option.equals("opportunity:" + attack.key()))
            .findFirst()
            .orElseThrow();
    context.update(reactor.entry().tactics(reactor.entry().tactics().spendReaction()));
    context.dm("ReactionUsed", Map.of("deciderId", reactorId, "optionKey", option));
    var attack =
        new Attack(
            reactorId,
            mover,
            weapon.key(),
            weapon.longRange(),
            Cover.NONE,
            RollMode.NORMAL,
            false,
            false,
            false,
            false,
            false,
            false,
            List.of(),
            null,
            null);
    context
        .reactions()
        .nested(
            "opportunity-attack",
            () -> {
              resolveAttack(context, attack, context.creature(reactorId), weapon, true);
              return null;
            });
  }

  private static boolean hasDamageDice(CombatRoster.AttackOption weapon) {
    return weapon.damage().stream().anyMatch(dice -> dice.notation().contains("d"));
  }

  private static void protection(
      Context context, Attack request, Creature target, List<String> disadvantages) {
    var candidates = request.protectingAllyIds();
    if (candidates.isEmpty() || context.reactions() == null || request.targetAbsent()) return;
    for (UUID id : candidates) {
      if (!context.entries.containsKey(id)
          || id.equals(request.actor())
          || id.equals(request.target())) throw invalid();
    }
    context
        .reactions()
        .window(
            Point.BEFORE_ATTACK_ROLL,
            () ->
                candidates.stream()
                    .map(context::creature)
                    .filter(creature -> Encounters.present(creature.entry()))
                    .filter(CombatActions::canReact)
                    .filter(
                        creature ->
                            creature.shield() && creature.features().contains("srd-5.1:protection"))
                    .filter(
                        creature ->
                            CombatConditions.canSee(
                                creature.conditions(),
                                target.conditions(),
                                target.entry().tactics().hiddenFrom(creature.entry().id())))
                    .map(
                        creature ->
                            new Offer(
                                Step.DEFENDING_RESULT,
                                creature.entry().id(),
                                creature.entry().initiative(),
                                0,
                                new Option("protection", "Protection", true, null, 0)))
                    .toList(),
            null,
            (decider, key) -> {
              var defender = context.creature(decider);
              context.update(defender.entry().tactics(defender.entry().tactics().spendReaction()));
              disadvantages.add("Protection");
              context.dm("ReactionUsed", Map.of("deciderId", decider, "optionKey", key));
            });
  }

  static boolean canReact(Creature creature) {
    var tactics = creature.entry().tactics();
    return CombatConditions.canReact(
        creature.conditions(), tactics.reactionAvailable(), tactics.surprised());
  }

  public Reply hide(UUID campaign, UUID id, UUID actor, Envelope envelope, Hide form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "hides",
        form,
        true,
        context -> {
          if (context.table.phase() == EncounterState.Phase.ACTIVE) {
            var creature = context.canAct(form.actor());
            spendRogueAction(context, creature, form.bonusAction(), "srd-5.1:cunning-action");
          } else if (context.table.phase() != EncounterState.Phase.SETUP
              || Boolean.TRUE.equals(form.bonusAction())) throw invalid();
          CombatStealth.hide(
              context,
              target(context, form.actor()),
              form.obscuredFrom(),
              form.opponents().isEmpty() ? form.obscuredFrom() : form.opponents());
        });
  }

  public Reply stealthTies(UUID campaign, UUID id, UUID actor, Envelope envelope, StealthTie form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "stealth-ties",
        form,
        true,
        context -> {
          if (context.table.phase() != EncounterState.Phase.SETUP
              && context.table.phase() != EncounterState.Phase.ACTIVE) throw invalid();
          CombatStealth.resolveTies(context, target(context, form.actor()), form.noticed());
        });
  }

  private static void spendRogueAction(
      Context context, Creature creature, Boolean bonus, String feature) {
    if (Boolean.TRUE.equals(bonus)) {
      if (!creature.features().contains(feature)) throw invalid();
      spend(context, TurnBudget::bonusAction);
    } else spend(context, TurnBudget::action);
  }

  private static Creature spendAfterActor(Context context, UUID actor) {
    var creature = context.canAct(actor);
    spend(context, TurnBudget::action);
    return creature;
  }

  public record Hide(
      UUID actor,
      List<UUID> obscuredFrom,
      List<UUID> opponents,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean bonusAction) {
    public Hide {
      obscuredFrom = List.copyOf(obscuredFrom);
      opponents = List.copyOf(opponents);
    }
  }

  public record StealthTie(UUID actor, List<UUID> noticed) {
    public StealthTie {
      noticed = List.copyOf(noticed);
    }
  }

  public Reply simple(
      UUID campaign,
      UUID id,
      UUID actor,
      Envelope envelope,
      UUID creature,
      Simple action,
      boolean bonus) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        action.name(),
        bonus ? Map.of("actor", creature, "bonusAction", true) : creature,
        true,
        context -> {
          var acting = context.canAct(creature, action == Simple.DASH || action == Simple.DODGE);
          if (bonus
              && ((action != Simple.DASH && action != Simple.DISENGAGE)
                  || !acting.features().contains("srd-5.1:cunning-action"))) throw invalid();
          var flags = acting.entry().tactics();
          switch (action) {
            case DASH -> spend(context, budget -> budget.dash(bonus));
            case DISENGAGE -> spend(context, budget -> budget.disengage(bonus));
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
          if (form.actor().equals(form.recipient()) || form.recipient().equals(form.target()))
            throw invalid();
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
          var acting = context.canAct(form.actor());
          if (form.note() == null || form.note().length() > 200) throw invalid();
          if (Boolean.TRUE.equals(form.bonusAction()))
            spendRogueAction(context, acting, true, "srd-5.1:fast-hands");
          else spend(context, budget -> budget.interact(form.usesAction()));
          context.dm("ObjectUsed", Map.of("combatantId", form.actor(), "note", form.note()));
        });
  }

  public Reply releaseGrapple(UUID campaign, UUID id, UUID actor, Envelope envelope, Help form) {
    return commands.run(
        campaign,
        id,
        actor,
        envelope,
        "grapple-releases",
        form,
        true,
        context -> {
          var holder = target(context, form.actor());
          var grabbed = target(context, form.recipient());
          if (!sourced(grabbed, CombatConditions.GRAPPLED, holder.entry().id())) throw invalid();
          CombatEffects.remove(context, grabbed, CombatConditions.GRAPPLED, holder.entry().id());
          context.dm(
              "GrappleReleased", Map.of("actorId", form.actor(), "targetId", form.recipient()));
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
          var first = context.canAct(form.actor(), form.kind() == ContestKind.ESCAPE);
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
                    checkSources(context, first, null).mode(),
                    context.random());
            String defense = form.acrobatics() ? "acrobatics" : "athletics";
            Ability ability = form.acrobatics() ? Ability.DEX : Ability.STR;
            var opposed =
                CombatRolls.d20(
                    defense,
                    second.skills().getOrDefault(defense, second.abilities().get(ability)),
                    checkSources(context, second, null).mode(),
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
              case ESCAPE ->
                  CombatEffects.remove(
                      context, first, CombatConditions.GRAPPLED, second.entry().id());
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
        context -> resolveCheck(context, form));
  }

  static List<Map<String, Object>> resolveCheck(Context context, Check form) {
    context.acting(form.actor());
    if (form.ability() == null
        || form.combatants().isEmpty()
        || form.combatants().size() > 30
        || new HashSet<>(form.combatants()).size() != form.combatants().size()
        || (form.dc() == null && form.save())
        || (form.dc() != null && (form.dc() < 1 || form.dc() > 40))) throw invalid();
    if (!form.combatants().containsAll(form.cover().keySet())
        || !form.cover().isEmpty() && (!form.save() || form.ability() != Ability.DEX))
      throw invalid();
    if (form.cover().containsValue(Cover.TOTAL)) throw new CampaignException(Code.TOTAL_COVER);
    if (Boolean.TRUE.equals(form.bonusAction())) {
      if (form.save()
          || !"sleight_of_hand".equals(form.skill())
          || !form.combatants().equals(List.of(form.actor()))) throw invalid();
      spendRogueAction(context, context.canAct(form.actor()), true, "srd-5.1:fast-hands");
    }
    if (form.searchTarget() != null) {
      if (form.save()
          || form.ability() != Ability.WIS
          || !"perception".equals(form.skill())
          || !form.combatants().equals(List.of(form.actor()))
          || form.dc() != null) throw invalid();
      var subject = target(context, form.searchTarget());
      if (!subject.entry().tactics().hiddenFrom(form.actor())
          || subject.entry().tactics().stealth() == null) throw invalid();
      spendAfterActor(context, form.actor());
    }
    var rolling =
        form.combatants().stream()
            .map(id -> target(context, id))
            .filter(subject -> !forcedFailure(form, subject))
            .toList();
    var decisions = new ArrayList<String>();
    if (form.dc() != null && !rolling.isEmpty()) decisions.add("autoOutcomeOnNat20Checks");
    if (rolling.stream()
        .anyMatch(
            subject ->
                subject.vitals().inspiration() != null
                    || !form.save()
                        && !CuttingWords.offers(
                                context, subject.entry().id(), Step.DEFENDING_RESULT)
                            .isEmpty())) decisions.add("rollDecisionInfo");
    context.preflight(decisions);
    var naturalMode =
        decisions.contains("autoOutcomeOnNat20Checks")
            ? D20Test.NaturalMode.valueOf(context.setting("autoOutcomeOnNat20Checks", String.class))
            : null;
    var kind = form.save() ? D20Test.Kind.SAVING_THROW : D20Test.Kind.ABILITY_CHECK;
    var outcome = new ArrayList<Map<String, Object>>();
    for (UUID subject : form.combatants()) {
      var creature = target(context, subject);
      boolean forced = forcedFailure(form, creature);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("combatantId", subject);
      result.put("automaticFailure", forced);
      if (!forced) {
        int bonus =
            form.save()
                ? creature.saves().get(form.ability())
                : form.skill() == null
                    ? creature.abilityCheck(form.ability())
                    : creature
                        .skills()
                        .getOrDefault(form.skill(), creature.abilities().get(form.ability()));
        var sources =
            form.save()
                ? saveSources(context, creature, form.ability())
                : assistedCheck(context, creature, form.skill(), form.fearSourceVisible());
        Cover cover = form.cover().getOrDefault(subject, Cover.NONE);
        var roll = CombatRolls.coveredSave(bonus, cover, sources.mode(), context.random());
        context.dice(form.save() ? "Saving throw" : "Ability check", subject, roll);
        Boolean success =
            form.dc() == null
                ? null
                : D20Test.resolve(roll, form.dc(), kind, naturalMode).success();
        roll =
            CombatFeatures.inspireRoll(
                context,
                creature,
                roll,
                form.save() ? Point.AFTER_SAVE_ROLL : Point.AFTER_CHECK_ROLL,
                success);
        if (!form.save()) {
          roll =
              CuttingWords.lowerRoll(
                  context,
                  subject,
                  Point.AFTER_CHECK_ROLL,
                  roll,
                  candidate ->
                      form.dc() == null
                          ? null
                          : D20Test.resolve(candidate, form.dc(), kind, naturalMode).success());
        }
        if (form.searchTarget() != null)
          result.put(
              "detected",
              CombatStealth.search(
                  context,
                  context.creature(form.searchTarget()),
                  creature,
                  roll.total(),
                  form.noticedOnTie()));
        result.put("roll", roll);
        if (form.dc() != null)
          result.put("success", D20Test.resolve(roll, form.dc(), kind, naturalMode).success());
      } else result.put("success", false);
      outcome.add(result);
    }
    context.result("outcomes", outcome);
    context.dm("TestResolved", Map.of("outcomes", outcome));
    return List.copyOf(outcome);
  }

  /** What gives a creature advantage or disadvantage on a saving throw of one ability. */
  private static AdvantageSources saveSources(Context context, Creature creature, Ability ability) {
    boolean dodge =
        ability == Ability.DEX
            && creature.entry().tactics().dodging()
            && creature.speed() > 0
            && !creature.incapacitated();
    boolean restraint =
        ability == Ability.DEX && creature.conditions().contains("srd-5.1:restrained");
    var authored = CombatEffects.rollSources(context, creature, "save", "holder", null, null);
    var saveAdvantages = new ArrayList<>(authored.advantages());
    var savePenalties = new ArrayList<>(authored.disadvantages());
    if (dodge) saveAdvantages.add("Dodge");
    if (restraint) savePenalties.add("Restrained");
    if (Exhaustion.disadvantagesAttacksAndSaves(creature.vitals().exhaustion()))
      savePenalties.add("Exhaustion");
    return new AdvantageSources(saveAdvantages, savePenalties);
  }

  private static boolean forcedFailure(Check form, Creature creature) {
    return form.save()
        ? CombatConditions.forcedSaveFailure(creature.conditions(), form.ability())
        : CombatConditions.forcedCheckFailure(
            creature.conditions(), form.requiresSight(), form.requiresHearing());
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
                  checkSources(context, helper, "medicine").mode(),
                  context.random());
          context.dice("Stabilize", form.actor(), roll);
          boolean success = roll.total() >= 10;
          if (success) context.health(target, target.vitals().health().stabilize());
          context.dm("FirstAidResolved", Map.of("targetId", form.recipient(), "success", success));
          context.result("success", success);
        });
  }

  static void addCondition(Context context, Creature creature, String key, UUID source) {
    CombatEffects.add(context, creature, new ConditionInstance(key, source));
  }

  static void removeCondition(Context context, Creature creature, String key) {
    CombatEffects.remove(context, creature, key, null);
  }

  static boolean sourced(Creature creature, String condition, UUID source) {
    return creature.entry().tactics().conditions().contains(new Condition(condition, source))
        || creature.vitals().effects().stream()
            .anyMatch(effect -> effect.sameSource(new ConditionInstance(condition, source)));
  }

  static Creature target(Context context, UUID target) {
    var creature = context.creature(target);
    if (!Encounters.present(creature.entry()) || creature.vitals().health().state() == State.DEAD)
      throw new CampaignException(Code.INVALID_TARGET);
    return creature;
  }

  private static AdvantageSources assistedCheck(
      Context context, Creature creature, String skill, boolean fearSourceVisible) {
    var sources = checkSources(context, creature, skill);
    var bonuses = new ArrayList<>(sources.advantages());
    var penalties = new ArrayList<>(sources.disadvantages());
    if (fearSourceVisible && creature.conditions().contains("srd-5.1:frightened"))
      penalties.add("Frightened");
    context.entries.values().stream()
        .filter(Encounters::present)
        .filter(
            entry ->
                creature.entry().id().equals(entry.tactics().helping())
                    && entry.tactics().helpedTarget() == null)
        .findFirst()
        .ifPresent(
            helper -> {
              bonuses.add("Help");
              var flags = helper.tactics();
              context.update(
                  helper.tactics(
                      flags.flags(flags.dodging(), flags.hidden(), null, null, flags.readied())));
            });
    return new AdvantageSources(bonuses, penalties);
  }

  static AdvantageSources checkSources(Context context, Creature creature, String skill) {
    var authored = CombatEffects.rollSources(context, creature, "check", "holder", null, null);
    var penalties = new ArrayList<>(authored.disadvantages());
    if (creature.conditions().contains("srd-5.1:poisoned")) penalties.add("Poisoned");
    if (Exhaustion.disadvantagesChecks(creature.vitals().exhaustion())) penalties.add("Exhaustion");
    if ("stealth".equals(skill) && creature.noisyArmor()) penalties.add("Armor");
    return new AdvantageSources(authored.advantages(), penalties);
  }

  private static boolean defense(Creature target, String kind, String type, boolean magical) {
    return target.defenses().stream()
        .anyMatch(defense -> defense.kind().equals(kind) && defense.applies(type, magical));
  }

  /** The saving throw of a zero-hit-point trait. It is rolled like any other saving throw. */
  static boolean hookSave(
      Context context,
      Creature creature,
      Ability ability,
      int dc,
      D20Test.NaturalMode naturalMode) {
    if (CombatConditions.forcedSaveFailure(creature.conditions(), ability)) return false;
    var sources = saveSources(context, creature, ability);
    var roll =
        CombatRolls.coveredSave(
            creature.saves().get(ability), Cover.NONE, sources.mode(), context.random());
    context.dice("Saving throw", creature.entry().id(), roll);
    Boolean success = D20Test.resolve(roll, dc, D20Test.Kind.SAVING_THROW, naturalMode).success();
    roll = CombatFeatures.inspireRoll(context, creature, roll, Point.AFTER_SAVE_ROLL, success);
    return D20Test.resolve(roll, dc, D20Test.Kind.SAVING_THROW, naturalMode).success();
  }

  static void spend(Context context, UnaryOperator<TurnBudget> spend) {
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
      boolean targetAbsent,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<UUID> protectingAllyIds,
      @JsonInclude(JsonInclude.Include.NON_NULL) Integer smiteSlotLevel,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean enemyNearTarget) {
    public Attack {
      if (smiteSlotLevel != null && (smiteSlotLevel < 1 || smiteSlotLevel > 9)) throw invalid();
      protectingAllyIds =
          protectingAllyIds == null
              ? List.of()
              : protectingAllyIds.stream().distinct().sorted().toList();
    }
  }

  public record ManualMove(UUID actorId, int distanceFeet, List<UUID> leavesReachOf) {
    public ManualMove {
      if (actorId == null || distanceFeet < 1 || distanceFeet > 1000 || leavesReachOf.size() > 30)
        throw invalid();
      leavesReachOf = leavesReachOf.stream().distinct().sorted().toList();
    }
  }

  public record Contest(
      UUID actor, UUID target, int distance, ContestKind kind, boolean acrobatics) {}

  public record Check(
      UUID actor,
      List<UUID> combatants,
      Ability ability,
      String skill,
      Integer dc,
      boolean save,
      Map<UUID, Cover> cover,
      UUID searchTarget,
      Boolean noticedOnTie,
      boolean requiresSight,
      boolean requiresHearing,
      boolean fearSourceVisible,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean bonusAction) {
    public Check {
      combatants = List.copyOf(combatants);
      cover = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(cover));
    }
  }

  public record Help(UUID actor, UUID recipient, UUID target, int distance) {}

  public record Ready(UUID actor, String trigger, String action) {}

  public record ObjectUse(
      UUID actor,
      String note,
      boolean usesAction,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean bonusAction) {}
}
