package com.github.sbadolankaur.rpg.engine.spell;

/** Classifies one resolved damage instance against a creature with active concentration. */
public final class ConcentrationRules {
  private ConcentrationRules() {}

  public static DamageCheck afterDamage(int damageTaken, boolean incapacitated) {
    if (damageTaken < 0) throw new IllegalArgumentException("Damage must be nonnegative");
    return new DamageCheck(
        incapacitated, incapacitated || damageTaken == 0 ? 0 : Math.max(10, damageTaken / 2));
  }

  public record DamageCheck(boolean endsImmediately, int saveDc) {
    public boolean requiresSave() {
      return saveDc > 0;
    }
  }
}
