package com.github.sbadolankaur.rpg.engine.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.engine.combat.CombatConditions;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.CombatRolls;
import com.github.sbadolankaur.rpg.engine.dice.CriticalDiceMode;
import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Continuation;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Disclosure;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Offer;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReactionConformanceTest {
  private static final UUID ATTACKER = new UUID(0, 1);
  private static final UUID DEFENDER = new UUID(0, 2);
  private static final UUID SHIELD_ALLY = new UUID(0, 3);
  private static final Option SHIELD = new Option("shield", "Shield", true, "spell-slot", 1);
  private static final Option SMITE = new Option("smite", "Divine Smite", false, "spell-slot", 1);
  private static final Option DODGE = new Option("dodge", "Uncanny Dodge", true, null, 0);

  @Test
  void shieldPrecedesSmiteAndDodgeWhileAnAlreadySpentReactionPreventsDodge() {
    var mixed = resolveAttack(SHIELD_ALLY, 19, List.of("shield", "smite", "dodge"));
    assertThat(mixed.events()).containsExactly("shield", "smite", "dodge", "damage:10");
    var spent = resolveAttack(DEFENDER, 19, List.of("shield", "smite"));
    assertThat(spent.events()).containsExactly("shield", "smite", "damage:21");
    var becameMiss = resolveAttack(DEFENDER, 15, List.of("shield"));
    assertThat(becameMiss.events()).containsExactly("shield", "miss");
    var withheld = resolveAttack(SHIELD_ALLY, 19, List.of());
    assertThat(withheld.events()).containsExactly("damage:6");
  }

  private static AttackFixture resolveAttack(UUID shieldDecider, int natural, List<String> script) {
    var continuation = Continuation.empty();
    for (String option : script) {
      var attempt =
          ReactionReplay.run(
              "attack",
              continuation,
              new ForcedDice(natural),
              replay -> hit(replay, shieldDecider, true));
      assertThat(attempt.pending().options()).extracting(Option::key).contains(option);
      continuation = attempt.continuation().answer(attempt.pending(), option);
    }
    boolean smite = script.contains("smite");
    boolean miss = natural == 15;
    var dice =
        script.isEmpty()
            ? new ForcedDice(natural, 6)
            : miss ? new ForcedDice() : smite ? new ForcedDice(6, 8, 7) : new ForcedDice(6);
    var complete =
        ReactionReplay.run(
            "attack", continuation, dice, replay -> hit(replay, shieldDecider, !script.isEmpty()));
    assertThat(complete.pending()).isNull();
    dice.assertExhausted();
    return complete.completed();
  }

  private static AttackFixture hit(ReactionReplay replay, UUID shieldDecider, boolean options) {
    var attack =
        CombatRolls.attack(
            4, 17, CombatRolls.Cover.NONE, 20, false, RollMode.NORMAL, replay.random());
    var state = new AttackFixture();
    if (options) {
      replay.window(
          Point.AFTER_HIT,
          () -> {
            var eligible = new ArrayList<Offer>();
            if (!state.reactors.contains(shieldDecider))
              eligible.add(new Offer(Step.DEFENDING_RESULT, shieldDecider, 0, 0, SHIELD));
            if (state.armor <= attack.roll().total()) {
              eligible.add(new Offer(Step.ACTING_DAMAGE, ATTACKER, 0, 0, SMITE));
              if (!state.reactors.contains(DEFENDER))
                eligible.add(new Offer(Step.DEFENDING_DAMAGE, DEFENDER, 0, 0, DODGE));
            }
            return eligible;
          },
          null,
          (id, option) -> {
            state.events.add(option);
            switch (option) {
              case "shield" -> {
                state.armor += 5;
                state.reactors.add(id);
              }
              case "smite" -> state.smite = true;
              case "dodge" -> {
                state.dodge = true;
                state.reactors.add(id);
              }
              default -> throw new AssertionError(option);
            }
          });
    }
    if (!CombatRolls.attackOutcome(attack.roll(), state.armor, 20, false).hit()) {
      state.events.add("miss");
      return state;
    }
    var weapon = CombatRolls.weaponDamage("Weapon", "fixture", "1d8").roll(replay.random());
    int radiant =
        state.smite
            ? CombatRolls.damage(
                    CombatRolls.divineSmite(1, false),
                    false,
                    CriticalDiceMode.ROLL_AGAIN_ADD,
                    replay.random())
                .total()
            : 0;
    int damage =
        CombatHealth.damageAmount(weapon.total(), 0, state.dodge, false, false, false)
            + CombatHealth.damageAmount(radiant, 0, state.dodge, false, false, false);
    state.events.add("damage:" + damage);
    return state;
  }

  @Test
  void overtAndHiddenRollFieldsDoNotChangeShieldEligibility() {
    for (String mode : List.of("TOTAL_SHOWN", "NATURAL_DIE_ONLY", "OUTCOME_SHOWN")) {
      var first = disclosed(mode, 12, 16, 18);
      var second =
          switch (mode) {
            case "TOTAL_SHOWN" -> disclosed(mode, 20, 16, 25);
            case "NATURAL_DIE_ONLY" -> disclosed(mode, 12, 21, 18);
            default -> disclosed(mode, 12, 16, 22);
          };
      assertThat(second.disclosure()).isEqualTo(first.disclosure());
      assertThat(second.options()).containsExactlyElementsOf(first.options());
      assertThat(first.options()).containsExactly(SHIELD);
    }
    assertThatThrownBy(() -> disclosed("unknown", 12, 16, 18))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ReactionReplay.Prompt disclosed(String mode, int natural, int total, int armor) {
    return ReactionReplay.run(
            "attack",
            Continuation.empty(),
            new ForcedDice(natural),
            replay -> {
              var attack =
                  CombatRolls.attack(
                      total - natural,
                      armor,
                      CombatRolls.Cover.NONE,
                      20,
                      false,
                      RollMode.NORMAL,
                      replay.random());
              return replay.choose(
                  Point.AFTER_ATTACK_ROLL,
                  Step.DEFENDING_RESULT,
                  DEFENDER,
                  List.of(SHIELD),
                  Disclosure.forRoll(mode, attack.roll(), attack.hit()));
            })
        .pending();
  }

  @Test
  void counterspellChainUnwindsAndNeverOpensASeventhFrame() {
    var counter = new Option("counterspell", "Counterspell", true, "spell-slot", 1);
    Continuation continuation = Continuation.empty();
    for (int depth = 1; depth <= ReactionReplay.MAX_DEPTH; depth++) {
      var attempt =
          ReactionReplay.run(
              "spell",
              continuation,
              new ForcedDice(),
              replay -> cast(replay, 1, counter, new ArrayList<>(), true));
      assertThat(attempt.pending().frames()).hasSize(depth);
      assertThat(attempt.pending().point()).isEqualTo(Point.ON_CAST);
      continuation = attempt.continuation().answer(attempt.pending(), counter.key());
    }
    var complete =
        ReactionReplay.run(
            "spell",
            continuation,
            new ForcedDice(),
            replay -> cast(replay, 1, counter, new ArrayList<>(), true));
    assertThat(complete.depthLimits()).containsExactly(Point.ON_CAST);
    assertThat(complete.completed()).containsExactly(7, 6, 5, 4, 3, 2, 1);
    var without =
        ReactionReplay.run(
            "spell",
            Continuation.empty(),
            new ForcedDice(),
            replay -> cast(replay, 1, counter, new ArrayList<>(), false));
    assertThat(without.completed()).containsExactly(1);
  }

  private static List<Integer> cast(
      ReactionReplay replay, int depth, Option counter, List<Integer> events, boolean options) {
    String response =
        replay.choose(
            Point.ON_CAST,
            Step.DEFENDING_RESULT,
            new UUID(0, depth),
            options ? List.of(counter) : List.of(),
            null);
    if (response != null)
      replay.nested("counterspell", () -> cast(replay, depth + 1, counter, events, options));
    events.add(depth);
    return events;
  }

  @Test
  void promptAndContinuationRejectStaleChoicesAndInvalidCosts() {
    assertThatThrownBy(() -> new Option("test", "Test", true, "slot", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Option("test", "Test", true, null, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Option("test", "Test", true, " ", 1))
        .isInstanceOf(IllegalArgumentException.class);
    var prompt = disclosed("TOTAL_SHOWN", 12, 16, 18);
    assertThatThrownBy(() -> Continuation.empty().answer(prompt, "unoffered"))
        .isInstanceOf(IllegalArgumentException.class);
    var answered = Continuation.empty().answer(prompt, null);
    assertThatThrownBy(() -> answered.answer(prompt, "shield"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unavailableReactionStillAllowsAFreeOption() {
    for (String condition : List.of("incapacitated", "stunned", "unconscious", "turned")) {
      var conditions = Set.of("srd-5.1:" + condition);
      assertThat(CombatConditions.canReact(conditions, true, false)).isFalse();
      var open =
          ReactionReplay.run(
              "fixture",
              Continuation.empty(),
              new ForcedDice(),
              replay ->
                  replay.choose(
                      Point.AFTER_HIT,
                      Step.ACTING_DAMAGE,
                      DEFENDER,
                      CombatConditions.canReact(conditions, true, false)
                          ? List.of(SMITE, DODGE)
                          : List.of(SMITE),
                      null));
      assertThat(open.pending().options()).containsExactly(SMITE);
    }
    assertThat(CombatConditions.canReact(Set.of(), true, true)).isFalse();
    assertThat(CombatConditions.canReact(Set.of(), false, false)).isFalse();
    assertThat(CombatConditions.canReact(Set.of(), true, false)).isTrue();
  }

  @Test
  void nestedActionAfterADrawUsesTheDefaultAndDoesNotResolveTheSetting() {
    var dice = new ForcedDice(15);
    var outcome =
        ReactionReplay.run(
            "movement",
            Continuation.empty(),
            dice,
            replay -> {
              assertThat(replay.canAskSettings()).isTrue();
              replay.random().nextInt(20);
              return replay.nested(
                  "opportunity", () -> replay.canAskSettings() ? "ask" : "default");
            });
    dice.assertExhausted();
    assertThat(outcome.completed()).isEqualTo("default");
    var following =
        ReactionReplay.run(
            "attack",
            Continuation.empty(),
            new ForcedDice(),
            replay -> replay.canAskSettings() ? "ask" : "default");
    assertThat(following.completed()).isEqualTo("ask");
  }

  @Test
  void automaticFailureDoesNotOpenRerollAndEvasionUsesTheFinalResult() {
    var indomitable = new Option("indomitable", "Indomitable", false, "indomitable", 1);
    var initial =
        ReactionReplay.run(
            "save",
            Continuation.empty(),
            new ForcedDice(7),
            replay -> evasion(replay, indomitable, false));
    assertThat(initial.pending().point()).isEqualTo(Point.AFTER_SAVE_FAIL);
    var rerolled =
        ReactionReplay.run(
            "save",
            initial.continuation().answer(initial.pending(), "indomitable"),
            new ForcedDice(16),
            replay -> evasion(replay, indomitable, false));
    assertThat(rerolled.completed()).isZero();
    var passed =
        ReactionReplay.run(
            "save",
            initial.continuation().answer(initial.pending(), null),
            new ForcedDice(),
            replay -> evasion(replay, indomitable, false));
    assertThat(passed.completed()).isEqualTo(5);
    var automatic =
        ReactionReplay.run(
            "save",
            Continuation.empty(),
            new ForcedDice(),
            replay -> evasion(replay, indomitable, true));
    assertThat(automatic.pending()).isNull();
    assertThat(automatic.completed()).isEqualTo(5);
  }

  private static int evasion(ReactionReplay replay, Option reroll, boolean automatic) {
    if (automatic && CombatConditions.forcedSaveFailure(Set.of("srd-5.1:unconscious"), Ability.DEX))
      return 5;
    int roll = CombatRolls.d20("Dexterity", 1, RollMode.NORMAL, replay.random()).total();
    if (roll < 15
        && replay.choose(Point.AFTER_SAVE_FAIL, Step.ACTING_RESULT, DEFENDER, List.of(reroll), null)
            != null)
      roll = CombatRolls.d20("Dexterity", 1, RollMode.NORMAL, replay.random()).total();
    return roll >= 15 ? 0 : 5;
  }

  private static final class AttackFixture {
    private int armor = 17;
    private boolean smite;
    private boolean dodge;
    private final List<UUID> reactors = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    List<String> events() {
      return events;
    }
  }
}
