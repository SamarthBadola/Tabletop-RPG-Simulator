package com.github.sbadolankaur.rpg.engine.monster;

import java.util.regex.Pattern;

/**
 * A monster's hit dice (RS-MON-003, RS-MON-004). They are written as N dice of the size's hit die
 * plus N times the Constitution modifier, which is the {@code bonus}.
 */
public record HitDice(MonsterSize size, int count, int bonus) {
  public HitDice {
    if (size == null) throw new IllegalArgumentException("A size is required");
    if (count < 1) throw new IllegalArgumentException("A monster has at least one hit die");
  }

  private static final Pattern WRITTEN = Pattern.compile("(\\d{1,3})d(\\d{1,2})([+-]\\d{1,4})?");

  /** Reads hit dice as a stat block writes them, such as "9d8+27". The die must fit the size. */
  public static HitDice parse(MonsterSize size, String written) {
    if (size == null || written == null)
      throw new IllegalArgumentException("Hit dice are required");
    var matcher = WRITTEN.matcher(written);
    if (!matcher.matches()) throw new IllegalArgumentException("Unreadable hit dice: " + written);
    if (Integer.parseInt(matcher.group(2)) != size.hitDie()) {
      throw new IllegalArgumentException("The hit die does not fit the size: " + written);
    }
    int bonus = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
    return new HitDice(size, Integer.parseInt(matcher.group(1)), bonus);
  }

  /** The dice with the bonus a Constitution modifier gives to each of them. */
  public static HitDice withConstitutionModifier(MonsterSize size, int count, int modifier) {
    return new HitDice(size, count, count * modifier);
  }

  /** The average of the dice plus the bonus, rounded down (RS-CNV-001). */
  public int average() {
    return Math.floorDiv(count * (size.hitDie() + 1) + 2 * bonus, 2);
  }

  /** The dice as text, such as "9d8+27" or "5d8". */
  public String expression() {
    String dice = count + "d" + size.hitDie();
    if (bonus == 0) return dice;
    return dice + (bonus > 0 ? "+" : "-") + Math.abs(bonus);
  }
}
