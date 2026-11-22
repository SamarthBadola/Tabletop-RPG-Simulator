package com.github.sbadolankaur.rpg.engine.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbilityScoresTest {
  @Test
  void abilityEnumContainsExactlyTheSixRulesetAbilities() {
    assertThat(Ability.values())
        .containsExactly(
            Ability.STR, Ability.DEX, Ability.CON, Ability.INT, Ability.WIS, Ability.CHA);
  }

  @Test
  void modifierUsesFloorDivisionForScoresBelowTen() {
    AbilityScores scores = scores(Map.of(Ability.STR, 9));

    assertThat(scores.modifier(Ability.STR)).isEqualTo(-1);
  }

  @Test
  void modifierMatchesTheRulesetRangeAtScoresOneAndThirty() {
    AbilityScores scores = scores(Map.of(Ability.STR, 1, Ability.DEX, 30));

    assertThat(scores.modifier(Ability.STR)).isEqualTo(-5);
    assertThat(scores.modifier(Ability.DEX)).isEqualTo(10);
  }

  @Test
  void appliesImprovementsAndAbilityStatModifiersThenClampsToZeroAndCeiling() {
    AbilityScores scores =
        new AbilityScores(
            completeScores(Map.of(Ability.STR, 20, Ability.DEX, 5)),
            Map.of(Ability.STR, 2),
            Map.of(
                Ability.STR,
                List.of(modifier(Ability.STR, 12, "strength-bonus")),
                Ability.DEX,
                List.of(modifier(Ability.DEX, -10, "dexterity-drain"))),
            Map.of(Ability.STR, 22),
            30);

    assertThat(scores.score(Ability.STR)).isEqualTo(30);
    assertThat(scores.score(Ability.DEX)).isZero();
  }

  @Test
  void defaultMaximumIsTwentyAndCanBeRaisedPerAbility() {
    AbilityScores scores =
        new AbilityScores(
            completeScores(Map.of(Ability.STR, 18)),
            Map.of(),
            Map.of(),
            Map.of(Ability.STR, 22),
            30);

    assertThat(scores.maxScore(Ability.STR)).isEqualTo(22);
    assertThat(scores.maxScore(Ability.DEX)).isEqualTo(20);
  }

  @Test
  void defensiveCopiesKeepTheAbilitySnapshotImmutable() {
    EnumMap<Ability, Integer> bases = new EnumMap<>(Ability.class);
    bases.put(Ability.STR, 10);
    AbilityScores scores =
        new AbilityScores(completeScores(bases), Map.of(), Map.of(), Map.of(), 30);
    bases.put(Ability.STR, 20);

    assertThat(scores.score(Ability.STR)).isEqualTo(10);
  }

  @Test
  void RS_ABL_005_improvementPreviewReportsExcessBeforeApplication() {
    AbilityScores scores =
        new AbilityScores(
            completeScores(Map.of(Ability.STR, 19)),
            Map.of(),
            Map.of(),
            Map.of(Ability.STR, 20),
            30);

    AbilityScores.ImprovementPreview preview = scores.previewImprovement(Ability.STR, 2);
    AbilityScores improved = scores.withImprovement(Ability.STR, preview.applied());

    assertThat(preview.requested()).isEqualTo(2);
    assertThat(preview.applied()).isEqualTo(1);
    assertThat(preview.lost()).isEqualTo(1);
    assertThat(preview.resultingScore()).isEqualTo(20);
    assertThat(improved.score(Ability.STR)).isEqualTo(20);
    assertThat(scores.score(Ability.STR)).isEqualTo(19);
  }

  private static AbilityScores scores(Map<Ability, Integer> baseScores) {
    return new AbilityScores(completeScores(baseScores), Map.of(), Map.of(), Map.of(), 30);
  }

  private static Map<Ability, Integer> completeScores(Map<Ability, Integer> overrides) {
    EnumMap<Ability, Integer> scores = new EnumMap<>(Ability.class);
    Arrays.stream(Ability.values()).forEach(ability -> scores.put(ability, 10));
    scores.putAll(overrides);
    return scores;
  }

  private static Modifier modifier(Ability ability, int value, String source) {
    return new Modifier(
        ability.statKey(),
        ModOp.ADD,
        Ratio.of(value),
        null,
        DurationSpec.permanent(),
        source,
        source,
        1,
        1);
  }
}
