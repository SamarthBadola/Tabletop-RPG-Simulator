package com.github.sbadolankaur.rpg.engine.monster;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.monster.StatBlockCheck.Bonus;
import com.github.sbadolankaur.rpg.engine.monster.StatBlockCheck.Kind;
import com.github.sbadolankaur.rpg.engine.monster.StatBlockCheck.Proficiency;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatBlockCheckTest {
  /** WE-G4: the mage, challenge rating 6, 9d8 with printed average 40, Intelligence 17. */
  private static StatBlockCheck.Definition mage(int averageHp, int saveBonus, int xp) {
    return new StatBlockCheck.Definition(
        "Mage",
        ChallengeRating.of("6"),
        MonsterSize.MEDIUM,
        9,
        11,
        averageHp,
        xp,
        3,
        true,
        List.of(new Bonus("Intelligence save", 17, Proficiency.PROFICIENT, saveBonus)));
  }

  @Test
  void T_62_theMageFromTheWorkedExamplePasses() {
    assertThat(StatBlockCheck.check(mage(40, 6, 2300))).isEmpty();
  }

  @Test
  void T_62_aWrongHitPointAverageIsFlagged() {
    var findings = StatBlockCheck.check(mage(45, 6, 2300));
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).kind()).isEqualTo(Kind.HIT_POINT_AVERAGE);
    assertThat(findings.get(0).expected()).isEqualTo(40);
    assertThat(findings.get(0).printed()).isEqualTo(45);
  }

  @Test
  void T_62_aBonusThatDoesNotFollowTheProficiencyBonusIsFlagged() {
    var findings = StatBlockCheck.check(mage(40, 7, 2300));
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).kind()).isEqualTo(Kind.BONUS);
    assertThat(findings.get(0).expected()).isEqualTo(6);
  }

  @Test
  void T_62_aWrongXpOrProficiencyBonusIsFlagged() {
    assertThat(StatBlockCheck.check(mage(40, 6, 1800)).get(0).kind()).isEqualTo(Kind.XP);
    var wrong =
        new StatBlockCheck.Definition(
            "Mage",
            ChallengeRating.of("6"),
            MonsterSize.MEDIUM,
            9,
            11,
            40,
            2300,
            2,
            true,
            List.of());
    var findings = StatBlockCheck.check(wrong);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).kind()).isEqualTo(Kind.PROFICIENCY_BONUS);
    assertThat(findings.get(0).expected()).isEqualTo(3);
  }

  @Test
  void T_62_theOwlbearAndBugbearSpotChecksPass() {
    var owlbear =
        new StatBlockCheck.Definition(
            "Owlbear",
            ChallengeRating.of("3"),
            MonsterSize.LARGE,
            7,
            16,
            59,
            700,
            2,
            true,
            List.of(new Bonus("Beak", 20, Proficiency.PROFICIENT, 7)));
    assertThat(StatBlockCheck.check(owlbear)).isEmpty();
    var bugbear =
        new StatBlockCheck.Definition(
            "Bugbear",
            ChallengeRating.of("1"),
            MonsterSize.MEDIUM,
            5,
            12,
            27,
            200,
            2,
            true,
            List.of(new Bonus("Stealth", 14, Proficiency.EXPERTISE, 6)));
    assertThat(StatBlockCheck.check(bugbear)).isEmpty();
  }

  @Test
  void anUnproficientBonusIsTheModifierAlone() {
    var monster =
        new StatBlockCheck.Definition(
            "Test",
            ChallengeRating.of("1/4"),
            MonsterSize.SMALL,
            2,
            10,
            7,
            50,
            2,
            true,
            List.of(new Bonus("Strength check", 8, Proficiency.NONE, -1)));
    assertThat(StatBlockCheck.check(monster)).isEmpty();
  }

  @Test
  void modifiersRoundTowardsMinusInfinity() {
    assertThat(StatBlockCheck.modifier(10)).isZero();
    assertThat(StatBlockCheck.modifier(11)).isZero();
    assertThat(StatBlockCheck.modifier(9)).isEqualTo(-1);
    assertThat(StatBlockCheck.modifier(1)).isEqualTo(-5);
    assertThat(StatBlockCheck.modifier(20)).isEqualTo(5);
  }
}
