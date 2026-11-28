package com.github.sbadolankaur.rpg.engine.monster;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.dice.RollPlan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Recharge X-Y" abilities (RS-MON-009). A recharge ability has one use. While it is spent, a d6 is
 * rolled at the start of each of the monster's turns and it recharges on a roll of X or higher. A
 * short or long rest recharges it too. Every spent ability rolls its own die (RS-MON-R15).
 */
public final class Recharge {
  private static final String SOURCE = "srd-5.1:monsters";

  private Recharge() {}

  /** An ability that recharges on a roll of {@code minimum} or higher (5 for Recharge 5-6). */
  public record Ability(String id, int minimum) {
    public Ability {
      if (minimum < 2 || minimum > 6) throw new IllegalArgumentException("Recharge is 2 to 6");
    }
  }

  /** One roll made at the start of a turn. */
  public record Roll(String id, int die, boolean recharged) {}

  /** The abilities still spent after the start of the turn, and the rolls that were made. */
  public record Turn(Set<String> spent, List<Roll> rolls) {
    public Turn {
      spent = Set.copyOf(spent);
      rolls = List.copyOf(rolls);
    }
  }

  /** Uses an ability. A spent ability cannot be used again until it recharges. */
  public static Set<String> spend(Set<String> spent, String id) {
    if (spent.contains(id)) throw new IllegalStateException("The ability is not recharged");
    var next = new LinkedHashSet<>(spent);
    next.add(id);
    return Set.copyOf(next);
  }

  /** The START_OF_OWN_TURN step: one d6 for each spent ability, in the order of the stat block. */
  public static Turn startOfTurn(List<Ability> abilities, Set<String> spent, RandomSource source) {
    var still = new LinkedHashSet<String>();
    var rolls = new ArrayList<Roll>();
    for (var ability : abilities) {
      if (!spent.contains(ability.id())) continue;
      int die =
          RollPlan.builder()
              .bonusDice("Recharge", SOURCE, DiceExpression.parse("1d6"))
              .build()
              .roll(source)
              .total();
      boolean recharged = die >= ability.minimum();
      rolls.add(new Roll(ability.id(), die, recharged));
      if (!recharged) still.add(ability.id());
    }
    return new Turn(still, rolls);
  }

  /** A short or a long rest recharges everything. */
  public static Set<String> onRest() {
    return Set.of();
  }
}
