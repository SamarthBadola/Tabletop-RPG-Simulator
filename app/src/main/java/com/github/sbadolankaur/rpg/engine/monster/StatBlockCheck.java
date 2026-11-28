package com.github.sbadolankaur.rpg.engine.monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks a stat block against its challenge rating (RS-MON-004, RS-MON-005, E-116). A pack monster
 * with any finding fails the pack build, and a homebrew monster gets the findings as warnings.
 */
public final class StatBlockCheck {
  private StatBlockCheck() {}

  /** How a bonus is built from the ability modifier. */
  public enum Proficiency {
    NONE,
    PROFICIENT,
    EXPERTISE
  }

  /** A saving throw, skill or attack bonus as printed, with the ability score behind it. */
  public record Bonus(String label, int abilityScore, Proficiency proficiency, int printed) {}

  /** The numbers of a stat block that the check reads. */
  public record Definition(
      String name,
      ChallengeRating challengeRating,
      MonsterSize size,
      int hitDiceCount,
      int constitutionScore,
      int printedAverageHp,
      int printedXp,
      int printedProficiencyBonus,
      boolean canDealDamage,
      List<Bonus> bonuses) {
    public Definition {
      bonuses = List.copyOf(bonuses);
    }
  }

  public enum Kind {
    HIT_POINT_AVERAGE,
    PROFICIENCY_BONUS,
    XP,
    BONUS
  }

  /** One difference between what is printed and what the rules give. */
  public record Finding(Kind kind, String label, int expected, int printed) {}

  public static int modifier(int abilityScore) {
    return Math.floorDiv(abilityScore - 10, 2);
  }

  public static List<Finding> check(Definition monster) {
    var findings = new ArrayList<Finding>();
    int proficiency = monster.challengeRating().proficiencyBonus();
    int constitutionModifier = modifier(monster.constitutionScore());
    int average =
        HitDice.withConstitutionModifier(
                monster.size(), monster.hitDiceCount(), constitutionModifier)
            .average();
    if (average != monster.printedAverageHp()) {
      findings.add(
          new Finding(Kind.HIT_POINT_AVERAGE, "Hit points", average, monster.printedAverageHp()));
    }
    if (proficiency != monster.printedProficiencyBonus()) {
      findings.add(
          new Finding(
              Kind.PROFICIENCY_BONUS,
              "Proficiency bonus",
              proficiency,
              monster.printedProficiencyBonus()));
    }
    int xp = monster.challengeRating().xp(monster.canDealDamage());
    if (xp != monster.printedXp()) {
      findings.add(new Finding(Kind.XP, "Experience points", xp, monster.printedXp()));
    }
    for (var bonus : monster.bonuses()) {
      int expected = modifier(bonus.abilityScore());
      switch (bonus.proficiency()) {
        case PROFICIENT -> expected += proficiency;
        case EXPERTISE -> expected += 2 * proficiency;
        case NONE -> {}
      }
      if (expected != bonus.printed()) {
        findings.add(new Finding(Kind.BONUS, bonus.label(), expected, bonus.printed()));
      }
    }
    return List.copyOf(findings);
  }
}
