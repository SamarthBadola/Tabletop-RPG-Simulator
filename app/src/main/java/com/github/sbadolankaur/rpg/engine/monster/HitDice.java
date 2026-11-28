package com.github.sbadolankaur.rpg.engine.monster;

/**
 * A monster's hit dice (RS-MON-003, RS-MON-004). They are written as N dice of the size's hit die
 * plus N times the Constitution modifier, which is the {@code bonus}.
 */
public record HitDice(MonsterSize size, int count, int bonus) {
  public HitDice {
    if (size == null) throw new IllegalArgumentException("A size is required");
    if (count < 1) throw new IllegalArgumentException("A monster has at least one hit die");
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
