package com.github.sbadolankaur.rpg.engine.dice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable composition of labeled terms evaluated in insertion order. */
public final class RollPlan {
  static final String D20_TEST_LABEL = "d20";

  private record Entry(
      String label,
      String source,
      int sign,
      DiceExpression.DiceGroup group,
      Integer fixed,
      boolean doublesOnCrit,
      boolean d20Test,
      int criticalFrom) {}

  private final List<Entry> entries;
  private final RollMode mode;

  private RollPlan(List<Entry> entries, RollMode mode) {
    this.entries = List.copyOf(entries);
    this.mode = mode;
  }

  public static Builder builder() {
    return new Builder();
  }

  public RollPlan doubleDiceForCritical() {
    return doubleDiceForCritical(CriticalDiceMode.ROLL_AGAIN_ADD);
  }

  public RollPlan doubleDiceForCritical(CriticalDiceMode selectedMode) {
    Objects.requireNonNull(selectedMode);
    List<Entry> expanded = new ArrayList<>();
    for (Entry entry : entries) {
      if (entry.group() == null || !entry.doublesOnCrit()) {
        expanded.add(entry);
        continue;
      }
      DiceExpression.DiceGroup group = entry.group();
      boolean selection = group.selection() != DiceExpression.Selection.NONE;
      if (selectedMode == CriticalDiceMode.REJECT && selection) {
        expanded.add(entry);
      } else if (selectedMode == CriticalDiceMode.EXTRA_DIE_ONLY) {
        expanded.add(withCount(entry, group.count() + 1, group.count()));
      } else if (selectedMode == CriticalDiceMode.ROLL_AGAIN_ADD && selection) {
        expanded.add(entry);
        expanded.add(
            new Entry(
                entry.label(),
                entry.source(),
                entry.sign(),
                group,
                null,
                entry.doublesOnCrit(),
                false,
                0));
      } else {
        expanded.add(withCount(entry, group.count() * 2, group.count()));
      }
    }
    validatePlan(expanded, true);
    return new RollPlan(expanded, mode);
  }

  public RollResult roll(RandomSource source) {
    Objects.requireNonNull(source, "source");
    List<RollTerm> results = new ArrayList<>(entries.size());
    int total = 0;
    Integer natural = null;
    for (Entry entry : entries) {
      RollTerm result;
      if (entry.group() == null) {
        int subtotal = Math.multiplyExact(entry.sign(), entry.fixed());
        result =
            new RollTerm(
                entry.label(),
                entry.source(),
                entry.sign(),
                "",
                List.of(),
                entry.fixed(),
                subtotal,
                false);
      } else {
        result = rollGroup(entry, source);
        if (entry.d20Test()) {
          natural =
              result.dice().stream()
                  .filter(DieRoll::kept)
                  .map(DieRoll::value)
                  .findFirst()
                  .orElse(null);
        }
      }
      results.add(result);
      total = Math.addExact(total, result.subtotal());
    }
    return new RollResult(results, total, natural, mode);
  }

  private static RollTerm rollGroup(Entry entry, RandomSource source) {
    DiceExpression.DiceGroup group = entry.group();
    int count = group.count();
    int[] rolled = new int[count];
    int[] replacement = new int[count];
    boolean[] rerolled = new boolean[count];
    boolean[] floored = new boolean[count];
    int[] values = new int[count];
    boolean[] critical = new boolean[count];
    for (int i = 0; i < count; i++) rolled[i] = RollResult.draw(source, group.size()) + 1;
    if (group.rerollAtOrBelow() != null) {
      for (int i = 0; i < count; i++) {
        if (rolled[i] <= group.rerollAtOrBelow()) {
          replacement[i] = RollResult.draw(source, group.size()) + 1;
          rerolled[i] = true;
        }
      }
    }
    for (int i = 0; i < count; i++) {
      int effective = rerolled[i] ? replacement[i] : rolled[i];
      if (group.floorAtLeast() != null && effective < group.floorAtLeast()) {
        effective = group.floorAtLeast();
        floored[i] = true;
      }
      values[i] = effective;
      critical[i] = i >= entry.criticalFrom();
    }
    boolean[] kept = select(group, values);
    List<DieRoll> dice = new ArrayList<>(count);
    int keptSum = 0;
    for (int i = 0; i < count; i++) {
      dice.add(
          new DieRoll(
              rolled[i],
              rerolled[i] ? replacement[i] : null,
              values[i],
              kept[i],
              rerolled[i],
              floored[i],
              critical[i]));
      if (kept[i]) keptSum = Math.addExact(keptSum, values[i]);
    }
    int subtotal = Math.multiplyExact(entry.sign(), keptSum);
    return new RollTerm(
        entry.label(),
        entry.source(),
        entry.sign(),
        group.canonical(),
        dice,
        0,
        subtotal,
        entry.doublesOnCrit());
  }

  private static boolean[] select(DiceExpression.DiceGroup group, int[] values) {
    boolean[] kept = new boolean[values.length];
    java.util.Arrays.fill(kept, true);
    int selectedCount = group.selectionCount();
    if (group.selection() == DiceExpression.Selection.NONE) return kept;
    List<Integer> order = new ArrayList<>(values.length);
    for (int i = 0; i < values.length; i++) order.add(i);
    boolean highest =
        group.selection() == DiceExpression.Selection.KEEP_HIGH
            || group.selection() == DiceExpression.Selection.DROP_HIGH;
    order.sort(
        highest
            ? Comparator.<Integer>comparingInt(i -> values[i]).reversed().thenComparingInt(i -> i)
            : Comparator.<Integer>comparingInt(i -> values[i]).thenComparingInt(i -> i));
    if (group.selection() == DiceExpression.Selection.KEEP_HIGH
        || group.selection() == DiceExpression.Selection.KEEP_LOW) {
      java.util.Arrays.fill(kept, false);
      for (int i = 0; i < selectedCount; i++) kept[order.get(i)] = true;
    } else {
      for (int i = 0; i < selectedCount; i++) kept[order.get(i)] = false;
    }
    return kept;
  }

  private static Entry withCount(Entry entry, int newCount, int criticalFrom) {
    DiceExpression.DiceGroup g = entry.group();
    DiceExpression.DiceGroup expanded =
        new DiceExpression.DiceGroup(
            g.sign(),
            newCount,
            g.size(),
            g.selection(),
            g.selectionCount(),
            g.rerollAtOrBelow(),
            g.floorAtLeast());
    return new Entry(
        entry.label(),
        entry.source(),
        entry.sign(),
        expanded,
        null,
        entry.doublesOnCrit(),
        entry.d20Test(),
        criticalFrom);
  }

  private static void validatePlan(List<Entry> entries, boolean afterDoubling) {
    if (entries.isEmpty())
      throw new IllegalStateException("A roll plan must contain at least one term");
    DiceLimits limits = DiceLimits.DEFAULT;
    if (entries.size() > limits.maxTerms())
      throw new IllegalArgumentException("Roll plan exceeds the term limit");
    int dice = 0;
    for (Entry entry : entries)
      if (entry.group() != null) dice = Math.addExact(dice, entry.group().count());
    int max = afterDoubling ? limits.maxDiceAfterCriticalDoubling() : limits.maxTotalDice();
    if (dice > max)
      throw new IllegalArgumentException(
          "Roll plan exceeds the " + (afterDoubling ? "critical dice" : "total dice") + " limit");
  }

  private static void validateText(String value, boolean nullable, String name) {
    if (value == null) {
      if (nullable) return;
      throw new IllegalArgumentException(name + " must not be null");
    }
    if (value.isBlank() || value.length() > 64 || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(
          name + " must be non-blank, at most 64 characters, and contain no controls");
    }
  }

  public static final class Builder {
    private final List<Entry> entries = new ArrayList<>();
    private RollMode mode = RollMode.NORMAL;
    private boolean hasD20;

    public Builder d20(RollMode rollMode) {
      return d20(rollMode, null);
    }

    public Builder d20(RollMode rollMode, Integer floor) {
      if (hasD20) throw new IllegalStateException("A plan may contain at most one d20 test die");
      Objects.requireNonNull(rollMode);
      if (floor != null && (floor < 2 || floor > 20))
        throw new IllegalArgumentException("d20 floor must be 2..20");
      int count = rollMode == RollMode.NORMAL ? 1 : 2;
      DiceExpression.Selection selection =
          rollMode == RollMode.ADVANTAGE
              ? DiceExpression.Selection.KEEP_HIGH
              : rollMode == RollMode.DISADVANTAGE
                  ? DiceExpression.Selection.KEEP_LOW
                  : DiceExpression.Selection.NONE;
      DiceExpression.DiceGroup group =
          new DiceExpression.DiceGroup(
              1,
              count,
              20,
              selection,
              selection == DiceExpression.Selection.NONE ? 0 : 1,
              null,
              floor);
      entries.add(new Entry(D20_TEST_LABEL, null, 1, group, null, false, true, Integer.MAX_VALUE));
      mode = rollMode;
      hasD20 = true;
      return this;
    }

    public Builder fixed(String label, String source, int value) {
      validateText(label, false, "label");
      validateNotReserved(label);
      validateText(source, true, "source");
      if (value == Integer.MIN_VALUE)
        throw new IllegalArgumentException("Fixed value magnitude is outside the supported range");
      if (Math.abs(value) > DiceLimits.DEFAULT.maxIntegerLiteral())
        throw new IllegalArgumentException("Fixed value exceeds the literal limit");
      entries.add(
          new Entry(
              label,
              source,
              value < 0 ? -1 : 1,
              null,
              Math.abs(value),
              false,
              false,
              Integer.MAX_VALUE));
      return this;
    }

    public Builder bonusDice(String label, String source, DiceExpression expression) {
      return addDice(label, source, expression, 1, false);
    }

    public Builder penaltyDice(String label, String source, DiceExpression expression) {
      return addDice(label, source, expression, -1, false);
    }

    /** Adds each typed expression term separately, labeled with its canonical text. */
    public Builder fromExpression(DiceExpression expression) {
      Objects.requireNonNull(expression, "expression");
      for (DiceExpression.Term term : expression.terms()) {
        if (term instanceof DiceExpression.FixedValue fixed) {
          fixed(term.canonical(), null, fixed.sign() * fixed.value());
        } else {
          addDice(term.canonical(), null, new DiceExpression(List.of(term)), 0, false);
        }
      }
      return this;
    }

    public Builder dice(
        String label, String source, DiceExpression expression, boolean doublesOnCrit) {
      return addDice(label, source, expression, 0, doublesOnCrit);
    }

    private Builder addDice(
        String label,
        String source,
        DiceExpression expression,
        int forcedSign,
        boolean doublesOnCrit) {
      validateText(label, false, "label");
      validateNotReserved(label);
      validateText(source, true, "source");
      Objects.requireNonNull(expression, "expression");
      if (expression.terms().size() != 1
          || !(expression.terms().getFirst() instanceof DiceExpression.DiceGroup group)) {
        throw new IllegalArgumentException("dice() accepts exactly one dice group");
      }
      int sign = forcedSign == 0 ? group.sign() : forcedSign;
      DiceExpression.DiceGroup normalized =
          new DiceExpression.DiceGroup(
              1,
              group.count(),
              group.size(),
              group.selection(),
              group.selectionCount(),
              group.rerollAtOrBelow(),
              group.floorAtLeast());
      entries.add(
          new Entry(
              label, source, sign, normalized, null, doublesOnCrit, false, Integer.MAX_VALUE));
      return this;
    }

    private static void validateNotReserved(String label) {
      if (D20_TEST_LABEL.equals(label))
        throw new IllegalArgumentException("The label 'd20' is reserved for the d20 test term");
    }

    public RollPlan build() {
      validatePlan(entries, false);
      long d20s = entries.stream().filter(Entry::d20Test).count();
      if (d20s > 1)
        throw new IllegalArgumentException("A plan may contain at most one d20 test die");
      return new RollPlan(entries, mode);
    }
  }
}
