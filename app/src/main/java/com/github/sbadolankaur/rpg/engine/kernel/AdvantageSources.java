package com.github.sbadolankaur.rpg.engine.kernel;

import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The applicable advantage and disadvantage sources for one d20 test. */
public record AdvantageSources(List<String> advantages, List<String> disadvantages) {
  public AdvantageSources {
    advantages = List.copyOf(advantages);
    disadvantages = List.copyOf(disadvantages);
  }

  /** Collects the named sources that match this roll and its conditions. */
  public static AdvantageSources resolve(
      List<Source> sources, RollKind kind, Ability ability, Set<String> conditions) {
    List<String> advantages = new ArrayList<>();
    List<String> disadvantages = new ArrayList<>();
    for (Source source : sources) {
      if (source.kind() == kind
          && (source.ability() == null || source.ability() == ability)
          && conditions.containsAll(source.requiredConditions())) {
        (source.mode() == RollMode.ADVANTAGE ? advantages : disadvantages).add(source.label());
      }
    }
    return new AdvantageSources(advantages, disadvantages);
  }

  /** Multiple sources of one kind do not stack; at least one of each kind cancels. */
  public RollMode mode() {
    if (!advantages.isEmpty() && !disadvantages.isEmpty()) return RollMode.NORMAL;
    if (!advantages.isEmpty()) return RollMode.ADVANTAGE;
    if (!disadvantages.isEmpty()) return RollMode.DISADVANTAGE;
    return RollMode.NORMAL;
  }

  public enum RollKind {
    ATTACK,
    ABILITY_CHECK,
    SAVING_THROW
  }

  public record Source(
      String label, RollMode mode, RollKind kind, Ability ability, Set<String> requiredConditions) {
    public Source {
      if (label == null || label.isBlank()) {
        throw new IllegalArgumentException("An advantage source needs a label");
      }
      if (Objects.requireNonNull(mode, "mode") == RollMode.NORMAL) {
        throw new IllegalArgumentException("A source must give advantage or disadvantage");
      }
      Objects.requireNonNull(kind, "kind");
      requiredConditions = Set.copyOf(requiredConditions);
    }
  }
}
