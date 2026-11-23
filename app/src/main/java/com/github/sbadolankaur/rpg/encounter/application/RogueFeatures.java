package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.campaign.application.CampaignException.FieldError;
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.AttackOption;
import com.github.sbadolankaur.rpg.encounter.application.CombatRoster.Creature;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.Context;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RollMode;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Option;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Point;
import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Step;
import java.util.List;
import java.util.Map;

final class RogueFeatures {
  static final String SNEAK_ATTACK = "srd-5.1:sneak-attack";
  static final String UNCANNY_DODGE = "srd-5.1:uncanny-dodge";

  private RogueFeatures() {}

  static void validate(Creature attacker, Boolean enemyNearTarget) {
    if (enemyNearTarget != null && !attacker.features().contains(SNEAK_ATTACK))
      throw new CampaignException(new FieldError("enemyNearTarget", "INCORRECT"));
  }

  static RollPlan sneakAttack(
      Context context,
      Creature attacker,
      AttackOption weapon,
      RollMode mode,
      Boolean enemyNearTarget) {
    int level = attacker.classLevel("srd-5.1:rogue");
    var id = attacker.entry().id();
    if (level < 1
        || !attacker.features().contains(SNEAK_ATTACK)
        || context.table.budget().sneakAttackUsed().contains(id)
        || mode == RollMode.DISADVANTAGE
        || mode != RollMode.ADVANTAGE && !Boolean.TRUE.equals(enemyNearTarget)) return null;
    String itemKey = weapon.key().replaceFirst("(-off-hand(-thrown)?|-thrown)$", "");
    var item = context.content.find("item", itemKey).orElse(null);
    if (item == null
        || !(item.fields().get("weaponData") instanceof Map<?, ?> data)
        || !("ranged".equals(data.get("kind"))
            || data.get("properties") instanceof List<?> properties
                && properties.contains("finesse"))) return null;
    CombatActions.spend(context, budget -> budget.sneakAttack(id));
    context.dm(
        "SneakAttackUsed",
        Map.of("combatantId", id, "turn", context.table.acting(), "round", context.table.round()));
    return RollPlan.builder()
        .dice("Sneak Attack", SNEAK_ATTACK, DiceExpression.parse((level + 1) / 2 + "d6"), true)
        .build();
  }

  static boolean uncannyDodge(Context context, Creature defender, boolean seesAttacker) {
    if (!seesAttacker
        || !defender.features().contains(UNCANNY_DODGE)
        || !CombatActions.canReact(defender)) return false;
    var id = defender.entry().id();
    var option = new Option("uncanny-dodge", "Uncanny Dodge", true, null, 0);
    String answer =
        context
            .reactions()
            .choose(Point.AFTER_HIT, Step.DEFENDING_DAMAGE, id, List.of(option), null);
    if (answer == null) return false;
    context.update(defender.entry().tactics(defender.entry().tactics().spendReaction()));
    context.dm("ReactionUsed", Map.of("deciderId", id, "optionKey", answer));
    return true;
  }
}
