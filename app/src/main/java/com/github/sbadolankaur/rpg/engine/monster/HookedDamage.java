package com.github.sbadolankaur.rpg.engine.monster;

import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import java.util.Map;

/**
 * One damage instance against a creature with a zero-hit-point trait (RS-MON-013 to RS-MON-015). It
 * runs the hook and keeps the marks of Regeneration up to date. A creature without such a trait is
 * not handled here: the caller keeps its ordinary damage result.
 */
public final class HookedDamage {
  private HookedDamage() {}

  /**
   * The damage as it ends, the Regeneration marks to store ({@code null} when the creature has no
   * Regeneration), what the hook did, and the save DC (0 when no save was made).
   */
  public record Resolution(
      CombatHealth.Damage damage,
      Regeneration.State marks,
      ZeroHitPointHook.Result result,
      int saveDc) {}

  /**
   * @param before the hit points before the damage
   * @param plain the ordinary result of the same damage, used for the amounts taken and absorbed
   * @param applied the damage by type, after resistance and vulnerability
   * @param marks the Regeneration marks before the damage
   * @param knockOut a knock-out blow leaves the creature stable at 0, so the hook does not run
   */
  public static Resolution resolve(
      CombatHealth before,
      CombatHealth.Damage plain,
      CombatHealth.DeathMode mode,
      Map<String, Integer> applied,
      boolean critical,
      boolean knockOut,
      ZeroHitPointHook.Defender defender,
      Regeneration.State marks,
      ZeroHitPointHook.SaveOutcome saves) {
    var state = defender.regeneration() ? marks : null;
    if (!defender.undeadFortitude() && !defender.regeneration()) {
      return new Resolution(plain, null, ZeroHitPointHook.Result.NONE, 0);
    }
    boolean acidOrFire =
        Regeneration.suppresses(applied.getOrDefault("acid", 0) + applied.getOrDefault("fire", 0));
    if (state != null) state = Regeneration.afterDamage(state, acidOrFire);
    boolean onlyDeathSaves = before.current() == 0 && mode == CombatHealth.DeathMode.PLAYER;
    if (plain.damageTaken() == 0 || knockOut || onlyDeathSaves) {
      return new Resolution(plain, state, ZeroHitPointHook.Result.NONE, 0);
    }
    var instance =
        new ZeroHitPointHook.DamageInstance(
            plain.damageTaken(),
            critical,
            applied.getOrDefault("radiant", 0) > 0,
            applied.getOrDefault("poison", 0) > 0,
            acidOrFire);
    var outcome = ZeroHitPointHook.apply(before, instance, defender, false, mode, saves);
    if (outcome.dyingByTrait() && state != null) {
      state = new Regeneration.State(true, state.suppressed());
    }
    int lost = Math.max(0, before.current() - outcome.health().current());
    var damage =
        new CombatHealth.Damage(outcome.health(), plain.damageTaken(), plain.absorbed(), lost);
    return new Resolution(damage, state, outcome.result(), outcome.saveDc());
  }
}
