package com.github.sbadolankaur.rpg.engine.monster;

import java.util.List;

/**
 * A challenge rating with its proficiency bonus and experience points (RS-MON-005, RS-MON-044). The
 * tables of Ruleset 12.3 are the only place these values are written.
 */
public final class ChallengeRating implements Comparable<ChallengeRating> {
  private static final List<String> NAMES =
      List.of(
          "0", "1/8", "1/4", "1/2", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
          "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27",
          "28", "29", "30");

  /** The XP of each rating, in the order of NAMES. A rating of 0 is worth 0 here (see xp). */
  private static final int[] XP = {
    0, 25, 50, 100, 200, 450, 700, 1100, 1800, 2300, 2900, 3900, 5000, 5900, 7200, 8400, 10000,
    11500, 13000, 15000, 18000, 20000, 22000, 25000, 33000, 41000, 50000, 62000, 75000, 90000,
    105000, 120000, 135000, 155000
  };

  private final int index;

  private ChallengeRating(int index) {
    this.index = index;
  }

  /** Parses a rating such as "0", "1/8" or "17". */
  public static ChallengeRating of(String text) {
    int index = NAMES.indexOf(text);
    if (index < 0) throw new IllegalArgumentException("Not a challenge rating: " + text);
    return new ChallengeRating(index);
  }

  public String text() {
    return NAMES.get(index);
  }

  /** The proficiency bonus of this rating. Fractions and 0 count as 0 to 4, so they give +2. */
  public int proficiencyBonus() {
    int whole = index < 4 ? 0 : index - 3;
    if (whole <= 4) return 2;
    if (whole <= 8) return 3;
    if (whole <= 12) return 4;
    if (whole <= 16) return 5;
    if (whole <= 20) return 6;
    if (whole <= 24) return 7;
    if (whole <= 28) return 8;
    return 9;
  }

  /** XP for this rating. A rating of 0 is worth 10 XP if the creature can deal damage, else 0. */
  public int xp(boolean canDealDamage) {
    if (index == 0) return canDealDamage ? 10 : 0;
    return XP[index];
  }

  @Override
  public int compareTo(ChallengeRating other) {
    return Integer.compare(index, other.index);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ChallengeRating rating && rating.index == index;
  }

  @Override
  public int hashCode() {
    return index;
  }

  @Override
  public String toString() {
    return "CR " + text();
  }
}
