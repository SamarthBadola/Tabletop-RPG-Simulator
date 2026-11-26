package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.ArmorClassCalculator.Floor;
import com.github.sbadolankaur.rpg.engine.equipment.ArmorClassCalculator.Input;
import com.github.sbadolankaur.rpg.engine.equipment.ArmorClassCalculator.Modifier;
import com.github.sbadolankaur.rpg.engine.equipment.ArmorClassCalculator.When;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import com.github.sbadolankaur.rpg.engine.kernel.AbilityScores;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArmorClassCalculatorTest {
  static AbilityScores scores(Map<Ability, Integer> given) {
    var all = new EnumMap<Ability, Integer>(Ability.class);
    for (var ability : Ability.values()) all.put(ability, given.getOrDefault(ability, 10));
    return new AbilityScores(all, Map.of(), Map.of(), Map.of(), 30);
  }

  static AbilityScores withDex(int modifier) {
    return scores(Map.of(Ability.DEX, 10 + 2 * modifier));
  }

  private static ArmorSpec armor(
      String id,
      ArmorCategory category,
      int base,
      DexterityRule rule,
      int strength,
      boolean stealth) {
    return new ArmorSpec(id, category, base, rule, strength, stealth);
  }

  /** Every body armor of the SRD table in Ruleset 11.3. */
  static final List<ArmorSpec> TABLE =
      List.of(
          armor("padded", ArmorCategory.LIGHT, 11, DexterityRule.FULL, 0, true),
          armor("leather", ArmorCategory.LIGHT, 11, DexterityRule.FULL, 0, false),
          armor("studded-leather", ArmorCategory.LIGHT, 12, DexterityRule.FULL, 0, false),
          armor("hide", ArmorCategory.MEDIUM, 12, DexterityRule.CAPPED, 0, false),
          armor("chain-shirt", ArmorCategory.MEDIUM, 13, DexterityRule.CAPPED, 0, false),
          armor("scale-mail", ArmorCategory.MEDIUM, 14, DexterityRule.CAPPED, 0, true),
          armor("breastplate", ArmorCategory.MEDIUM, 14, DexterityRule.CAPPED, 0, false),
          armor("half-plate", ArmorCategory.MEDIUM, 15, DexterityRule.CAPPED, 0, true),
          armor("ring-mail", ArmorCategory.HEAVY, 14, DexterityRule.NONE, 0, true),
          armor("chain-mail", ArmorCategory.HEAVY, 16, DexterityRule.NONE, 13, true),
          armor("splint", ArmorCategory.HEAVY, 17, DexterityRule.NONE, 15, true),
          armor("plate", ArmorCategory.HEAVY, 18, DexterityRule.NONE, 15, true));

  @Test
  void T_48_everyArmorWithDexterityMinus1_0_2_and_5() {
    for (var armor : TABLE) {
      for (int dex : new int[] {-1, 0, 2, 5}) {
        int expected =
            switch (armor.category()) {
              case LIGHT -> armor.baseAc() + dex;
              case MEDIUM -> armor.baseAc() + Math.min(dex, 2);
              case HEAVY -> armor.baseAc();
            };
        var result = ArmorClassCalculator.compute(Input.of(withDex(dex), armor, false));
        assertThat(result.total()).isEqualTo(expected);
        assertThat(result.baseLabel()).isEqualTo(armor.id());
      }
    }
  }

  @Test
  void RS_EQP_001_unarmoredIsTenPlusDexterityAndAShieldAddsTwo() {
    assertThat(ArmorClassCalculator.compute(Input.of(withDex(3), null, false)).total())
        .isEqualTo(13);
    assertThat(ArmorClassCalculator.compute(Input.of(withDex(3), null, true)).total())
        .isEqualTo(15);
    assertThat(ArmorClassCalculator.compute(Input.of(withDex(-2), null, false)).total())
        .isEqualTo(8);
  }

  private static Input unarmored(
      AbilityScores scores, boolean shield, ArmorClassCalculator.BaseRule... rules) {
    return new Input(scores, null, 0, shield, 0, List.of(rules), List.of(), List.of());
  }

  @Test
  void T_49_theHighestEligibleBaseWinsAndTheBreakdownNamesIt() {
    // DEX +2, CON +3, WIS +4
    var scores = scores(Map.of(Ability.DEX, 14, Ability.CON, 16, Ability.WIS, 18));
    var all =
        new ArmorClassCalculator.BaseRule[] {
          ArmorClassCalculator.BARBARIAN,
          ArmorClassCalculator.MONK,
          ArmorClassCalculator.DRACONIC,
          ArmorClassCalculator.MAGE_ARMOR
        };
    var best = ArmorClassCalculator.compute(unarmored(scores, false, all));
    assertThat(best.total()).isEqualTo(16); // monk 10+2+4, barbarian 15, mage armor 15
    assertThat(best.baseLabel()).isEqualTo(ArmorClassCalculator.MONK.label());

    var withShield = ArmorClassCalculator.compute(unarmored(scores, true, all));
    // the monk loses the base with a shield; barbarian 15 beats mage armor 15 by order
    assertThat(withShield.baseLabel()).isEqualTo(ArmorClassCalculator.BARBARIAN.label());
    assertThat(withShield.total()).isEqualTo(17);

    var onlyMage =
        ArmorClassCalculator.compute(unarmored(withDex(2), false, ArmorClassCalculator.MAGE_ARMOR));
    assertThat(onlyMage.total()).isEqualTo(15);
    assertThat(onlyMage.baseLabel()).isEqualTo("Mage Armor");
  }

  @Test
  void T_49_wornArmorIsNeverCombinedWithAnAlternativeBase() {
    var scores = scores(Map.of(Ability.DEX, 14, Ability.CON, 20));
    var input =
        new Input(
            scores,
            TABLE.get(1),
            0,
            false,
            0,
            List.of(ArmorClassCalculator.BARBARIAN, ArmorClassCalculator.MAGE_ARMOR),
            List.of(),
            List.of());
    var result = ArmorClassCalculator.compute(input);
    assertThat(result.total()).isEqualTo(13); // leather 11 + 2
    assertThat(result.baseLabel()).isEqualTo("leather");
  }

  @Test
  void T_50_modifiersAddInOrderAndEachShowsItsSource() {
    var armor = TABLE.get(7); // half plate, 15 + 2
    var input =
        new Input(
            withDex(3),
            armor,
            2, // armor +2
            true,
            1, // shield +1
            List.of(),
            List.of(
                Modifier.always("Ring of Protection", 1),
                Modifier.always("Cloak of Protection", 1),
                new Modifier("Defense", 1, When.WHILE_ARMOR_WORN)),
            List.of());
    var result = ArmorClassCalculator.compute(input);
    assertThat(result.total()).isEqualTo(17 + 2 + 2 + 1 + 1 + 1 + 1);
    assertThat(result.lines().stream().map(ArmorClassCalculator.Line::label).toList())
        .containsExactly(
            "half-plate",
            "Armor +2",
            "Shield",
            "Shield +1",
            "Ring of Protection",
            "Cloak of Protection",
            "Defense");
  }

  @Test
  void T_50_bracersOfDefenseOnlyCountWithoutArmorAndWithoutAShield() {
    var bracers = new Modifier("Bracers of Defense", 2, When.WHILE_NO_ARMOR_AND_NO_SHIELD);
    AbilityScores scores = withDex(1);
    assertThat(compute(scores, null, false, bracers)).isEqualTo(13); // 10 + 1 + bracers 2
    assertThat(compute(scores, null, true, bracers)).isEqualTo(13); // 10 + 1 + shield 2, no bracers
    assertThat(compute(scores, TABLE.get(1), false, bracers)).isEqualTo(11 + 1);
  }

  private static int compute(AbilityScores scores, ArmorSpec armor, boolean shield, Modifier m) {
    return ArmorClassCalculator.compute(
            new Input(scores, armor, 0, shield, 0, List.of(), List.of(m), List.of()))
        .total();
  }

  @Test
  void T_50_aFloorAppliesLastAndOnlyRaises() {
    var barkskin = new Floor("Barkskin", 16);
    var low =
        ArmorClassCalculator.compute(
            new Input(withDex(0), null, 0, false, 0, List.of(), List.of(), List.of(barkskin)));
    assertThat(low.total()).isEqualTo(16);
    assertThat(low.floorsApplied()).containsExactly("Barkskin");
    var high =
        ArmorClassCalculator.compute(
            new Input(
                withDex(0), TABLE.get(11), 0, true, 0, List.of(), List.of(), List.of(barkskin)));
    assertThat(high.total()).isEqualTo(20);
    assertThat(high.floorsApplied()).isEmpty();
  }

  @Test
  void RS_EQP_001_aNegativeDexterityDoesNotReduceHeavyArmor() {
    assertThat(ArmorClassCalculator.compute(Input.of(withDex(-3), TABLE.get(11), false)).total())
        .isEqualTo(18);
  }

  @Test
  void RS_EQP_009_aFunctionOfTheStateSoTheSameInputGivesTheSameAnswer() {
    var input = Input.of(withDex(2), TABLE.get(5), true);
    assertThat(ArmorClassCalculator.compute(input)).isEqualTo(ArmorClassCalculator.compute(input));
  }
}
