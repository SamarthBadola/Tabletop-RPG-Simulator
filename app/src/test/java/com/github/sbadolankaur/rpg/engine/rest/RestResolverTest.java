package com.github.sbadolankaur.rpg.engine.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import com.github.sbadolankaur.rpg.engine.combat.ConditionInstance;
import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.kernel.DurationKind;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Kind;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Participant;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Plan;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Request;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Resource;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Settings;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Trigger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestResolverTest {
  private static final UUID FIGHTER = new UUID(0, 1);
  private static final UUID WIZARD = new UUID(0, 2);
  private static final Settings STANDARD = Settings.standard();

  @Test
  void rsRst002FiveLevelFighterSpendsTwoHitDiceAndHealsThirteen() {
    var dice = new ForcedDice(6, 3);
    var result =
        RestResolver.resolve(
            0,
            new Request(
                Kind.SHORT,
                60,
                false,
                List.of(new Plan(FIGHTER, 2, null, true, null, Map.of())),
                List.of()),
            List.of(fighter(12, 0, 0, null, Map.of(10, 5), Map.of())),
            STANDARD,
            dice);
    assertThat(result.end()).isEqualTo(3600);
    assertThat(result.participants().getFirst().health().current()).isEqualTo(25);
    assertThat(result.participants().getFirst().spentHitDice()).containsEntry(10, 2);
    assertThat(result.events()).filteredOn(event -> event.type().equals("HitDieSpent")).hasSize(2);
    dice.assertExhausted();
  }

  @Test
  void levelEightLongRestRecoversFourSpentDiceAndOneExhaustion() {
    var result =
        RestResolver.resolve(
            0,
            new Request(Kind.LONG, 480, false, List.of(Plan.noChoices(FIGHTER)), List.of()),
            List.of(fighter(7, 5, 2, null, Map.of(10, 8), Map.of(10, 6))),
            STANDARD,
            new ForcedDice());
    var fighter = result.participants().getFirst();
    assertThat(fighter.health()).isEqualTo(CombatHealth.of(32, 32, 0));
    assertThat(fighter.exhaustion()).isOne();
    assertThat(fighter.spentHitDice()).containsEntry(10, 2);
    assertThat(fighter.lastLongRest()).isEqualTo(28_800L);
  }

  @Test
  void repeatedLongRestUsesTheIntervalEndToEndAndAdvancesTimeWithoutHealing() {
    var request = new Request(Kind.LONG, 480, false, List.of(Plan.noChoices(FIGHTER)), List.of());
    var initial = List.of(fighter(7, 5, 2, 0L, Map.of(10, 8), Map.of(10, 6)));
    var tooSoon = RestResolver.resolve(8 * 3600, request, initial, STANDARD, new ForcedDice());
    var nextDay = RestResolver.resolve(16 * 3600, request, initial, STANDARD, new ForcedDice());
    assertThat(tooSoon.end()).isEqualTo(16 * 3600);
    assertThat(tooSoon.participants()).isEqualTo(initial);
    assertThat(tooSoon.events()).anyMatch(e -> e.type().equals("long_rest_no_benefit"));
    assertThat(nextDay.participants().getFirst().health().current()).isEqualTo(32);
  }

  @Test
  void interruptedRestAdvancesOnlyElapsedTimeAndGivesNoBenefitsByDefault() {
    var request = new Request(Kind.LONG, 300, true, List.of(Plan.noChoices(FIGHTER)), List.of());
    var initial = List.of(fighter(7, 5, 2, null, Map.of(10, 8), Map.of(10, 6)));
    var result = RestResolver.resolve(0, request, initial, STANDARD, new ForcedDice());
    assertThat(result.end()).isEqualTo(5 * 3600);
    assertThat(result.participants()).isEqualTo(initial);
    assertThat(result.events().getLast().type()).isEqualTo("RestInterrupted");
  }

  @Test
  void epicHeroismReturnsShortRestResourcesWhileLeavingLongRestOnlyResourcesSpent() {
    var result =
        RestResolver.resolve(
            0,
            new Request(Kind.SHORT, 5, false, List.of(Plan.noChoices(FIGHTER)), List.of()),
            List.of(fighter(7, 0, 0, null, Map.of(10, 2), Map.of())),
            Settings.epicHeroism(),
            new ForcedDice());
    assertThat(result.end()).isEqualTo(300);
    assertThat(result.participants().getFirst().resources())
        .extracting(Resource::used)
        .containsExactly(0, 1);
  }

  @Test
  void arcaneRecoveryReturnsUpToHalfTheWizardLevelRoundedUp() {
    var wizard =
        new Participant(
            WIZARD,
            CombatHealth.of(14, 20, 0),
            20,
            2,
            0,
            null,
            null,
            Map.of(6, 5),
            Map.of(),
            Map.of(1, 2, 2, 1, 6, 1),
            List.of(
                new Resource(
                    "arcane-recovery", 0, Map.of(Trigger.LONG_REST, random -> Integer.MAX_VALUE))),
            List.of(),
            null,
            5,
            0,
            "arcane-recovery",
            Set.of());
    var valid = new Plan(WIZARD, null, null, true, null, Map.of(2, 1, 1, 1));
    var invalid = new Plan(WIZARD, null, null, true, null, Map.of(6, 1));
    var resolved =
        RestResolver.resolve(
            0,
            new Request(Kind.SHORT, 60, false, List.of(valid), List.of()),
            List.of(wizard),
            STANDARD,
            new ForcedDice());
    var after = resolved.participants().getFirst();
    assertThat(after.usedSlots()).containsEntry(1, 1).containsEntry(2, 0).containsEntry(6, 1);
    assertThat(after.resources().getFirst().used()).isOne();
    assertChoiceError(Kind.SHORT, valid, List.of(after), "arcaneRecovery", "INCORRECT");
    assertChoiceError(Kind.LONG, valid, List.of(wizard), "arcaneRecovery", "INCORRECT");
    assertChoiceError(Kind.SHORT, invalid, List.of(wizard), "arcaneRecovery", "OUT_OF_RANGE");
    assertChoiceError(
        Kind.SHORT,
        new Plan(WIZARD, null, null, true, null, Map.of(1, 2, 2, 1)),
        List.of(wizard),
        "arcaneRecovery",
        "OUT_OF_RANGE");
    assertChoiceError(
        Kind.SHORT,
        new Plan(WIZARD, null, null, true, null, Map.of(2, 2)),
        List.of(wizard),
        "arcaneRecovery",
        "OUT_OF_RANGE");
    assertThatThrownBy(
            () ->
                RestResolver.resolve(
                    0,
                    new Request(Kind.SHORT, 60, false, List.of(invalid), List.of()),
                    List.of(wizard),
                    STANDARD,
                    new ForcedDice()))
        .isInstanceOf(RestResolver.Rejected.class);
  }

  @Test
  void recoveryAllowanceDoesNotDependOnHitDiceMethod() {
    var initial = List.of(fighter(7, 0, 1, null, Map.of(10, 3, 8, 2), Map.of(10, 2, 8, 2)));
    var request =
        new Request(
            Kind.LONG,
            480,
            false,
            List.of(new Plan(FIGHTER, null, null, false, null, Map.of())),
            List.of());
    for (var rules : List.of(STANDARD, fixedDice())) {
      var preview = RestResolver.preview(0, request, initial, rules);
      assertThat(preview.participants().getFirst().recoveryAllowance()).isEqualTo(2);
      var result = RestResolver.resolve(0, request, initial, rules, new ForcedDice());
      assertThat(result.participants().getFirst().spentHitDice())
          .containsEntry(10, 0)
          .containsEntry(8, 2);
      assertThat(result.participants().getFirst().exhaustion()).isOne();
    }
  }

  @Test
  void songOfRestUsesHighestDieOnlyOnceAndSharesTheHitDiceMethod() {
    UUID first = new UUID(0, 3);
    UUID second = new UUID(0, 4);
    var party =
        List.of(
            fighter(10, 0, 0, null, Map.of(10, 2), Map.of()),
            bard(first, 2, Set.of()),
            bard(second, 9, Set.of("srd-5.1:deafened")));
    var request =
        new Request(
            Kind.SHORT,
            60,
            false,
            List.of(
                new Plan(FIGHTER, 2, null, true, null, Map.of()),
                Plan.noChoices(first),
                Plan.noChoices(second)),
            List.of(
                new RestResolver.Song(first, Set.of(FIGHTER, first, second)),
                new RestResolver.Song(second, Set.of(FIGHTER, first, second))));
    var preview = RestResolver.preview(0, request, party, STANDARD);
    assertThat(preview.participants())
        .extracting(RestResolver.PreviewRow::songOfRestDie)
        .containsExactly(8, 8, 0);
    var rolledDice = new ForcedDice(1, 8, 2);
    var rolled = RestResolver.resolve(0, request, party, STANDARD, rolledDice);
    rolledDice.assertExhausted();
    assertThat(rolled.participants().getFirst().health().current()).isEqualTo(25);
    var fixed = RestResolver.resolve(0, request, party, fixedDice(), new ForcedDice());
    assertThat(fixed.participants().getFirst().health().current()).isEqualTo(31);
    assertThat(fixed.events())
        .filteredOn(event -> event.type().equals("SongOfRestRolled"))
        .hasSize(1);
  }

  @Test
  void interruptedLongRestCanGrantOnlyShortRestBenefits() {
    var rules = new Settings(60, 480, 24, true, false, 28800, 21600);
    var request =
        new Request(
            Kind.LONG,
            60,
            true,
            List.of(new Plan(FIGHTER, 1, null, true, null, Map.of())),
            List.of());
    var party = List.of(fighter(7, 5, 2, null, Map.of(10, 2), Map.of()));
    assertThat(RestResolver.preview(0, request, party, STANDARD).requiresConfirmation()).isTrue();
    assertThat(RestResolver.preview(0, request, party, rules).interruptionOutcome())
        .contains("grants short rest benefits");
    var result = RestResolver.resolve(0, request, party, rules, new ForcedDice(3));
    var fighter = result.participants().getFirst();
    assertThat(fighter.health()).isEqualTo(CombatHealth.of(12, 32, 5));
    assertThat(fighter.exhaustion()).isEqualTo(2);
    assertThat(fighter.lastLongRest()).isNull();
    assertThat(fighter.resources()).extracting(Resource::used).containsExactly(0, 1);
  }

  @Test
  void concentrationEndsOnlyOnABenefitingLongRest() {
    var effect =
        new ConditionInstance(
            "concentrating",
            null,
            new ConditionInstance.End(DurationKind.CONCENTRATION, null, 100_000L, null));
    var state = withEffects(fighter(7, 0, 0, 0L, Map.of(10, 1), Map.of()), List.of(effect));
    var normal = new Request(Kind.LONG, 480, false, List.of(Plan.noChoices(FIGHTER)), List.of());
    var interrupted = new Request(Kind.LONG, 480, true, normal.plans(), List.of());
    assertThat(
            RestResolver.resolve(0, normal, List.of(state), STANDARD, new ForcedDice())
                .participants()
                .getFirst()
                .effects())
        .containsExactly(effect);
    assertThat(
            RestResolver.resolve(57600, interrupted, List.of(state), STANDARD, new ForcedDice())
                .participants()
                .getFirst()
                .effects())
        .containsExactly(effect);
    assertThat(
            RestResolver.resolve(57600, normal, List.of(state), STANDARD, new ForcedDice())
                .participants()
                .getFirst()
                .effects())
        .isEmpty();
  }

  @Test
  void clockExpiresEffectsByDeadlineAndThenFiresEveryCrossedDawn() {
    var late =
        new ConditionInstance(
            "late", null, new ConditionInstance.End(DurationKind.CLOCK, null, 120L, null));
    var first =
        new ConditionInstance(
            "first", null, new ConditionInstance.End(DurationKind.CLOCK, null, 60L, null));
    var tied =
        new ConditionInstance(
            "tied", null, new ConditionInstance.End(DurationKind.CLOCK, null, 60L, null));
    var party =
        List.of(
            withEffects(
                fighter(12, 0, 0, null, Map.of(10, 1), Map.of()), List.of(late, first, tied)));
    var advanced = RestResolver.advance(0, 3 * 86400L, party, STANDARD, new ForcedDice());
    assertThat(advanced.events().stream().filter(event -> !event.type().equals("ClockAdvanced")))
        .extracting(RestResolver.Event::seconds)
        .containsExactly(60L, 60L, 120L, 79200L, 165600L, 252000L);
    assertThat(advanced.events().subList(1, 4))
        .extracting(event -> event.data().get("effect"))
        .containsExactly("first", "tied", "late");
    assertThat(
            RestResolver.advance(
                    advanced.end(), 0, advanced.participants(), STANDARD, new ForcedDice())
                .events())
        .isEmpty();
  }

  @Test
  void hitDiceRecoveryRefusesUnknownSizesExcessCountsAndShortRestChoices() {
    var party = List.of(fighter(10, 0, 0, null, Map.of(10, 3, 8, 2), Map.of(10, 2, 8, 1)));
    assertChoiceError(
        Kind.LONG,
        new Plan(FIGHTER, null, null, true, Map.of(6, 1), Map.of()),
        party,
        "recoverHitDice",
        "UNKNOWN_VALUE");
    for (var choice : List.of(Map.of(10, 3), Map.of(10, 2, 8, 1), Map.of(10, -1))) {
      assertChoiceError(
          Kind.LONG,
          new Plan(FIGHTER, null, null, true, choice, Map.of()),
          party,
          "recoverHitDice",
          "OUT_OF_RANGE");
    }
    assertChoiceError(
        Kind.SHORT,
        new Plan(FIGHTER, null, null, true, Map.of(10, 1), Map.of()),
        party,
        "recoverHitDice",
        "INCORRECT");
  }

  private static void assertChoiceError(
      Kind kind, Plan plan, List<Participant> party, String field, String code) {
    assertThatThrownBy(
            () ->
                RestResolver.preview(
                    0,
                    new Request(
                        kind, kind == Kind.LONG ? 480 : 60, false, List.of(plan), List.of()),
                    party,
                    STANDARD))
        .isInstanceOfSatisfying(
            RestResolver.Rejected.class,
            failure -> {
              assertThat(failure.field()).isEqualTo(field);
              assertThat(failure.fieldCode()).isEqualTo(code);
            });
  }

  private static Participant withEffects(Participant state, List<ConditionInstance> effects) {
    return new Participant(
        state.id(),
        state.health(),
        state.baseMaximumHp(),
        state.constitutionModifier(),
        state.exhaustion(),
        state.lastLongRest(),
        state.recoveryAt(),
        state.hitDice(),
        state.spentHitDice(),
        state.usedSlots(),
        state.resources(),
        effects,
        state.inspiration(),
        state.wizardLevel(),
        state.bardLevel(),
        state.arcaneRecoveryKey(),
        state.conditions());
  }

  @Test
  void songOfRestRefusesDuplicateOutsideAndIncapacitatedPerformers() {
    UUID bard = new UUID(0, 3);
    UUID outside = new UUID(0, 4);
    var song = new RestResolver.Song(bard, Set.of(FIGHTER, bard));
    var plans = List.of(Plan.noChoices(FIGHTER), Plan.noChoices(bard));
    var fighter = fighter(7, 0, 0, null, Map.of(10, 1), Map.of());
    var eligible = List.of(fighter, bard(bard, 2, Set.of()));
    assertSongError(Kind.SHORT, plans, List.of(song, song), eligible);
    assertSongError(
        Kind.SHORT, plans, List.of(new RestResolver.Song(outside, Set.of(FIGHTER))), eligible);
    assertSongError(Kind.LONG, plans, List.of(song), eligible);
    assertSongError(Kind.SHORT, plans, List.of(song), List.of(fighter, bard(bard, 1, Set.of())));
    assertSongError(
        Kind.SHORT,
        plans,
        List.of(song),
        List.of(fighter, bard(bard, 2, Set.of("srd-5.1:stunned"))));
  }

  @Test
  void formulaRechargeCapsTheAmountAndKeepsTheDiceInTheAudit() {
    var random = new ForcedDice(4);
    var recharge = RestResolver.evaluateRecharge(2, source -> source.nextInt(6) + 1, random);
    assertThat(recharge.amount()).isEqualTo(2);
    assertThat(recharge.dice()).containsExactly(new RestResolver.RechargeDie(6, 4));
    random.assertExhausted();
  }

  private static void assertSongError(
      Kind kind, List<Plan> plans, List<RestResolver.Song> songs, List<Participant> party) {
    assertThatThrownBy(
            () ->
                RestResolver.preview(
                    0,
                    new Request(kind, kind == Kind.LONG ? 480 : 60, false, plans, songs),
                    party,
                    STANDARD))
        .isInstanceOfSatisfying(
            RestResolver.Rejected.class,
            failure -> {
              assertThat(failure.field()).isEqualTo("songOfRestPerformerIds");
              assertThat(failure.fieldCode()).isEqualTo("INCORRECT");
            });
  }

  private static Settings fixedDice() {
    return new Settings(60, 480, 24, false, true, 28800, 21600);
  }

  private static Participant bard(UUID id, int level, Set<String> conditions) {
    return new Participant(
        id,
        CombatHealth.of(10, 32, 0),
        32,
        0,
        0,
        null,
        null,
        Map.of(8, level),
        Map.of(),
        Map.of(),
        List.of(),
        List.of(),
        null,
        0,
        level,
        null,
        conditions);
  }

  private static Participant fighter(
      int hp,
      int temporary,
      int exhaustion,
      Long last,
      Map<Integer, Integer> dice,
      Map<Integer, Integer> spent) {
    return new Participant(
        FIGHTER,
        CombatHealth.of(hp, 32, temporary),
        32,
        2,
        exhaustion,
        last,
        null,
        dice,
        spent,
        Map.of(),
        List.of(
            new Resource(
                "action-surge",
                1,
                Map.of(
                    Trigger.SHORT_REST,
                    random -> Integer.MAX_VALUE,
                    Trigger.LONG_REST,
                    random -> Integer.MAX_VALUE)),
            new Resource(
                "long-rest-only", 1, Map.of(Trigger.LONG_REST, random -> Integer.MAX_VALUE))),
        List.of(),
        null,
        0,
        0,
        null,
        Set.of());
  }
}
