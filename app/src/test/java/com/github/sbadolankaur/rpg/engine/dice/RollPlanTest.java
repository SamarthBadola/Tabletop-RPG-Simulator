package com.github.sbadolankaur.rpg.engine.dice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RollPlanTest {
  @Test
  void composesBlessAndBaneTermsInOrder() {
    ForcedDice blessDice = new ForcedDice(12, 3);
    RollResult bless =
        RollPlan.builder()
            .d20(RollMode.NORMAL)
            .fixed("Dexterity", "ability:DEX", 3)
            .fixed("Proficiency", "proficiency", 2)
            .bonusDice("Bless", "spell:bless", DiceExpression.parse("1d4"))
            .build()
            .roll(blessDice);
    blessDice.assertExhausted();
    assertThat(bless.total()).isEqualTo(20);
    assertThat(bless.naturalD20()).isEqualTo(12);
    assertThat(bless.terms())
        .extracting(RollTerm::label)
        .containsExactly("d20", "Dexterity", "Proficiency", "Bless");

    ForcedDice baneDice = new ForcedDice(12, 2);
    RollResult bane =
        RollPlan.builder()
            .d20(RollMode.NORMAL)
            .fixed("Bonus", null, 5)
            .penaltyDice("Bane", "spell:bane", DiceExpression.parse("1d4"))
            .build()
            .roll(baneDice);
    baneDice.assertExhausted();
    assertThat(bane.total()).isEqualTo(15);
  }

  @Test
  void typedExpressionExpandsIntoCanonicalLabeledTerms() {
    ForcedDice source = new ForcedDice(5, 2, 4, 6);
    RollResult result =
        RollPlan.builder().fromExpression(DiceExpression.parse("d8+3d6+3")).build().roll(source);
    source.assertExhausted();
    assertThat(result.terms()).extracting(RollTerm::label).containsExactly("1d8", "3d6", "3");
    assertThat(result.total()).isEqualTo(20);
  }

  @Test
  void tieSelectionIsStableForKeepAndDrop() {
    assertKept("2d20kh1", 10, 10, true, false);
    assertKept("2d20kl1", 10, 10, true, false);
    assertKept("2d20dh1", 10, 10, false, true);
    assertKept("2d20dl1", 10, 10, false, true);
  }

  private static void assertKept(
      String notation, int first, int second, boolean firstKept, boolean secondKept) {
    ForcedDice source = new ForcedDice(first, second);
    RollResult result = DiceExpression.parse(notation).roll(source);
    source.assertExhausted();
    assertThat(result.terms().getFirst().dice())
        .extracting(DieRoll::kept)
        .containsExactly(firstKept, secondKept);
  }

  @Test
  void doubleCriticalDamageWithoutDoublingTheModifierAndRecordsCriticalDice() {
    RollPlan plan =
        RollPlan.builder()
            .dice("sword", "weapon:longsword", DiceExpression.parse("1d8"), true)
            .fixed("Strength", "ability:STR", 3)
            .build()
            .doubleDiceForCritical();
    ForcedDice source = new ForcedDice(5, 2);
    RollResult result = plan.roll(source);
    source.assertExhausted();
    assertThat(result.total()).isEqualTo(10);
    assertThat(result.terms().getFirst().expression()).isEqualTo("2d8");
    assertThat(result.terms().getFirst().dice())
        .extracting(DieRoll::critical)
        .containsExactly(false, true);
  }

  @Test
  void criticalModesRespectKeepDropAndRejectedGroupRules() {
    DiceExpression keepOne = DiceExpression.parse("2d6kh1");
    RollPlan base = RollPlan.builder().dice("weapon", null, keepOne, true).build();
    RollPlan doubled = base.doubleDiceForCritical(CriticalDiceMode.ROLL_AGAIN_ADD);
    ForcedDice source = new ForcedDice(4, 2, 3, 6);
    RollResult result = doubled.roll(source);
    source.assertExhausted();
    assertThat(result.terms()).hasSize(2);
    assertThat(result.terms()).extracting(RollTerm::subtotal).containsExactly(4, 6);
    assertThat(result.terms().get(1).dice()).allMatch(DieRoll::critical);

    RollPlan extra = base.doubleDiceForCritical(CriticalDiceMode.EXTRA_DIE_ONLY);
    ForcedDice extraSource = new ForcedDice(4, 2, 6);
    RollResult extraResult = extra.roll(extraSource);
    extraSource.assertExhausted();
    assertThat(extraResult.terms().getFirst().expression()).isEqualTo("3d6kh1");
    assertThat(extraResult.terms().getFirst().dice())
        .extracting(DieRoll::critical)
        .containsExactly(false, false, true);

    RollPlan rejected = base.doubleDiceForCritical(CriticalDiceMode.REJECT);
    ForcedDice rejectedSource = new ForcedDice(4, 2);
    RollResult rejectedResult = rejected.roll(rejectedSource);
    rejectedSource.assertExhausted();
    assertThat(rejectedResult.total()).isEqualTo(4);
    assertThat(rejectedResult.terms()).hasSize(1);
    assertThat(rejectedResult.terms().getFirst().dice()).hasSize(2).noneMatch(DieRoll::critical);
  }

  @Test
  void replacesOneD20AndUpdatesTheNaturalResult() {
    ForcedDice initial = new ForcedDice(1, 14);
    RollResult first = RollPlan.builder().d20(RollMode.ADVANTAGE).build().roll(initial);
    initial.assertExhausted();
    ForcedDice replacement = new ForcedDice(9);
    RollResult updated = first.replaceD20(0, replacement);
    replacement.assertExhausted();
    assertThat(updated.naturalD20()).isEqualTo(14);
    assertThat(updated.terms().getFirst().dice().getFirst())
        .isEqualTo(new DieRoll(1, 9, 9, false, true, false, false));
    ForcedDice initialAgain = new ForcedDice(1, 14);
    RollResult beforeHighReplacement =
        RollPlan.builder().d20(RollMode.ADVANTAGE).build().roll(initialAgain);
    initialAgain.assertExhausted();
    ForcedDice highReplacement = new ForcedDice(20);
    RollResult afterHighReplacement = beforeHighReplacement.replaceD20(0, highReplacement);
    highReplacement.assertExhausted();
    assertThat(afterHighReplacement.naturalD20()).isEqualTo(20);
    assertThat(afterHighReplacement.terms().getFirst().dice())
        .extracting(DieRoll::kept)
        .containsExactly(true, false);
    assertThatThrownBy(
            () ->
                RollPlan.builder()
                    .fixed("x", null, 1)
                    .build()
                    .roll(new ForcedDice())
                    .replaceD20(0, new ForcedDice()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void differentialExhaustiveSmallGroupsMatchAReferenceEvaluator() {
    for (int count = 1; count <= 3; count++) {
      List<DiceExpression.Selection> selections =
          new ArrayList<>(
              List.of(
                  DiceExpression.Selection.NONE,
                  DiceExpression.Selection.KEEP_HIGH,
                  DiceExpression.Selection.KEEP_LOW));
      if (count > 1)
        selections.addAll(
            List.of(DiceExpression.Selection.DROP_HIGH, DiceExpression.Selection.DROP_LOW));
      for (DiceExpression.Selection selection : selections) {
        int firstN = selection == DiceExpression.Selection.NONE ? 0 : 1;
        int lastN =
            selection == DiceExpression.Selection.NONE
                ? 0
                : (selection == DiceExpression.Selection.KEEP_HIGH
                        || selection == DiceExpression.Selection.KEEP_LOW
                    ? count
                    : count - 1);
        for (int selectionCount = firstN; selectionCount <= lastN; selectionCount++) {
          for (Integer reroll : new Integer[] {null, 1, 3}) {
            for (Integer floor : new Integer[] {null, 2, 4}) {
              enumerate(count, selection, selectionCount, reroll, floor);
            }
          }
        }
      }
    }
  }

  private static void enumerate(
      int count,
      DiceExpression.Selection selection,
      int selectionCount,
      Integer reroll,
      Integer floor) {
    int[] initial = new int[count];
    enumerateInitial(0, initial, count, selection, selectionCount, reroll, floor);
  }

  private static void enumerateInitial(
      int index,
      int[] initial,
      int count,
      DiceExpression.Selection selection,
      int selectionCount,
      Integer reroll,
      Integer floor) {
    if (index < count) {
      for (int face = 1; face <= 4; face++) {
        initial[index] = face;
        enumerateInitial(index + 1, initial, count, selection, selectionCount, reroll, floor);
      }
      return;
    }
    int replacements = 0;
    if (reroll != null) for (int face : initial) if (face <= reroll) replacements++;
    int possibilities = (int) Math.pow(4, replacements);
    for (int variant = 0; variant < possibilities; variant++) {
      int[] forced = new int[count + replacements];
      System.arraycopy(initial, 0, forced, 0, count);
      int n = variant;
      for (int i = 0; i < replacements; i++) {
        forced[count + i] = n % 4 + 1;
        n /= 4;
      }
      String modifier =
          selection == DiceExpression.Selection.NONE
              ? ""
              : switch (selection) {
                case KEEP_HIGH -> "kh" + selectionCount;
                case KEEP_LOW -> "kl" + selectionCount;
                case DROP_HIGH -> "dh" + selectionCount;
                case DROP_LOW -> "dl" + selectionCount;
                case NONE -> "";
              };
      String notation =
          count
              + "d4"
              + modifier
              + (reroll == null ? "" : "r<=" + reroll)
              + (floor == null ? "" : "min" + floor);
      ForcedDice source = new ForcedDice(forced);
      RollResult actual = DiceExpression.parse(notation).roll(source);
      source.assertExhausted();
      assertThat(actual.total())
          .isEqualTo(
              referenceTotal(initial, forced, count, selection, selectionCount, reroll, floor));
    }
  }

  private static int referenceTotal(
      int[] initial,
      int[] forced,
      int count,
      DiceExpression.Selection selection,
      int selectionCount,
      Integer reroll,
      Integer floor) {
    int[] values = new int[count];
    int replacementIndex = count;
    for (int i = 0; i < count; i++) {
      int value = initial[i];
      if (reroll != null && value <= reroll) value = forced[replacementIndex++];
      if (floor != null && value < floor) value = floor;
      values[i] = value;
    }
    boolean[] kept = new boolean[count];
    java.util.Arrays.fill(kept, true);
    if (selection != DiceExpression.Selection.NONE) {
      List<Integer> sorted = new ArrayList<>();
      for (int i = 0; i < count; i++) sorted.add(i);
      boolean high =
          selection == DiceExpression.Selection.KEEP_HIGH
              || selection == DiceExpression.Selection.DROP_HIGH;
      sorted.sort(
          high
              ? java.util.Comparator.<Integer>comparingInt(i -> values[i])
                  .reversed()
                  .thenComparingInt(i -> i)
              : java.util.Comparator.<Integer>comparingInt(i -> values[i])
                  .thenComparingInt(i -> i));
      if (selection == DiceExpression.Selection.KEEP_HIGH
          || selection == DiceExpression.Selection.KEEP_LOW) {
        java.util.Arrays.fill(kept, false);
        for (int i = 0; i < selectionCount; i++) kept[sorted.get(i)] = true;
      } else for (int i = 0; i < selectionCount; i++) kept[sorted.get(i)] = false;
    }
    int result = 0;
    for (int i = 0; i < count; i++) if (kept[i]) result += values[i];
    return result;
  }
}
