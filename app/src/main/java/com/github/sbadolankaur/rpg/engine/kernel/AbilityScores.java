package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable ability-score inputs and their derived score, maximum, and modifier. */
public final class AbilityScores {
  private static final int DEFAULT_MAXIMUM = 20;

  private final Map<Ability, Integer> baseScores;
  private final Map<Ability, Integer> improvements;
  private final Map<Ability, List<Modifier>> modifiers;
  private final Map<Ability, Integer> maximumScores;
  private final int ceiling;

  /**
   * Creates a score snapshot. The three modifier maps may omit abilities, which then use zero
   * improvements, no stat modifiers, or the default maximum of 20 respectively.
   */
  public AbilityScores(
      Map<Ability, Integer> baseScores,
      Map<Ability, Integer> improvements,
      Map<Ability, List<Modifier>> modifiers,
      Map<Ability, Integer> maximumScores,
      int ceiling) {
    this.baseScores = Map.copyOf(Objects.requireNonNull(baseScores, "baseScores"));
    this.improvements = Map.copyOf(Objects.requireNonNull(improvements, "improvements"));
    Objects.requireNonNull(modifiers, "modifiers");
    this.modifiers =
        modifiers.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    this.maximumScores = Map.copyOf(Objects.requireNonNull(maximumScores, "maximumScores"));
    if (!this.baseScores.keySet().equals(java.util.EnumSet.allOf(Ability.class))) {
      throw new IllegalArgumentException("A score is required for every ability");
    }
    if (ceiling < 0 || ceiling > 30) {
      throw new IllegalArgumentException("Ability ceiling must be between 0 and 30");
    }
    this.ceiling = ceiling;
    validateRange(this.baseScores, 1, 30, "Base score");
    validateRange(this.improvements, 0, 30, "Improvement");
    validateRange(this.maximumScores, 1, 30, "Maximum score");
    for (Ability ability : Ability.values()) {
      int improvedScore =
          Math.addExact(this.baseScores.get(ability), this.improvements.getOrDefault(ability, 0));
      if (this.improvements.getOrDefault(ability, 0) > 0
          && improvedScore > this.maximumScores.getOrDefault(ability, DEFAULT_MAXIMUM)) {
        throw new IllegalArgumentException(
            "Ability improvements cannot exceed the ability maximum");
      }
    }
  }

  /** Returns base score plus improvements and stat modifiers, clamped to 0..ceiling. */
  public int score(Ability ability) {
    Objects.requireNonNull(ability, "ability");
    int base = Math.addExact(baseScores.get(ability), improvements.getOrDefault(ability, 0));
    int raw = new Stat(ability.statKey(), base, modifiers.getOrDefault(ability, List.of())).value();
    return Math.clamp(raw, 0, ceiling);
  }

  /** Returns the maximum used to cap a level-up or feat improvement, defaulting to 20. */
  public int maxScore(Ability ability) {
    Objects.requireNonNull(ability, "ability");
    return maximumScores.getOrDefault(ability, DEFAULT_MAXIMUM);
  }

  /** Returns the ability modifier using floor division, including for scores below 10. */
  public int modifier(Ability ability) {
    return Math.floorDiv(score(ability) - 10, 2);
  }

  /** Previews the approved +1 or +2 improvement and reports any points that would be lost. */
  public ImprovementPreview previewImprovement(Ability ability, int requested) {
    Objects.requireNonNull(ability, "ability");
    if (requested != 1 && requested != 2) {
      throw new IllegalArgumentException("An ability improvement must be +1 or +2");
    }
    int chosenScore = Math.addExact(baseScores.get(ability), improvements.getOrDefault(ability, 0));
    int room = Math.max(0, maxScore(ability) - chosenScore);
    int applied = Math.min(requested, room);
    return new ImprovementPreview(requested, applied, requested - applied, chosenScore + applied);
  }

  /** Returns a new snapshot after applying a previously confirmed +1 or +2 improvement. */
  public AbilityScores withImprovement(Ability ability, int amount) {
    Objects.requireNonNull(ability, "ability");
    if (amount == 0) return this;
    ImprovementPreview preview = previewImprovement(ability, amount);
    if (preview.applied() == 0) return this;
    Map<Ability, Integer> updated = new java.util.EnumMap<>(Ability.class);
    updated.putAll(improvements);
    updated.merge(ability, preview.applied(), Math::addExact);
    return new AbilityScores(baseScores, updated, modifiers, maximumScores, ceiling);
  }

  public record ImprovementPreview(int requested, int applied, int lost, int resultingScore) {}

  private static void validateRange(
      Map<Ability, Integer> values, int minimum, int maximum, String description) {
    for (Map.Entry<Ability, Integer> entry : values.entrySet()) {
      Objects.requireNonNull(entry.getKey(), description + " ability");
      int value = Objects.requireNonNull(entry.getValue(), description + " value");
      if (value < minimum || value > maximum) {
        throw new IllegalArgumentException(
            description + " must be between " + minimum + " and " + maximum);
      }
    }
  }
}
