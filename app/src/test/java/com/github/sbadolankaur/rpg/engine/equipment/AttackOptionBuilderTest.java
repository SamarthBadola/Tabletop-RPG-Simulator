package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.AttackOption.Kind;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttackOptionBuilderTest {
  private static WeaponSpec melee(
      String id,
      WeaponCategory category,
      String dice,
      String type,
      String versatile,
      WeaponProperty... properties) {
    return new WeaponSpec(
        id,
        category,
        false,
        dice,
        type,
        versatile,
        Set.of(properties),
        0,
        0,
        thrownNormal(properties),
        thrownLong(properties),
        null,
        SpecialRule.NONE,
        0,
        false);
  }

  private static int thrownNormal(WeaponProperty... properties) {
    return Set.of(properties).contains(WeaponProperty.THROWN) ? 20 : 0;
  }

  private static int thrownLong(WeaponProperty... properties) {
    return Set.of(properties).contains(WeaponProperty.THROWN) ? 60 : 0;
  }

  private static final WeaponSpec DAGGER =
      melee(
          "dagger",
          WeaponCategory.SIMPLE,
          "1d4",
          "piercing",
          null,
          WeaponProperty.FINESSE,
          WeaponProperty.LIGHT,
          WeaponProperty.THROWN);
  private static final WeaponSpec SHORTSWORD =
      melee(
          "shortsword",
          WeaponCategory.MARTIAL,
          "1d6",
          "piercing",
          null,
          WeaponProperty.FINESSE,
          WeaponProperty.LIGHT);
  private static final WeaponSpec LONGSWORD =
      melee(
          "longsword", WeaponCategory.MARTIAL, "1d8", "slashing", "1d10", WeaponProperty.VERSATILE);
  private static final WeaponSpec GREATSWORD =
      melee(
          "greatsword",
          WeaponCategory.MARTIAL,
          "2d6",
          "slashing",
          null,
          WeaponProperty.HEAVY,
          WeaponProperty.TWO_HANDED);
  private static final WeaponSpec SPEAR =
      melee(
          "spear",
          WeaponCategory.SIMPLE,
          "1d6",
          "piercing",
          "1d8",
          WeaponProperty.THROWN,
          WeaponProperty.VERSATILE);
  private static final WeaponSpec GLAIVE =
      melee(
          "glaive",
          WeaponCategory.MARTIAL,
          "1d10",
          "slashing",
          null,
          WeaponProperty.HEAVY,
          WeaponProperty.REACH,
          WeaponProperty.TWO_HANDED);
  private static final WeaponSpec LANCE =
      new WeaponSpec(
          "lance",
          WeaponCategory.MARTIAL,
          false,
          "1d12",
          "piercing",
          null,
          Set.of(WeaponProperty.REACH),
          0,
          0,
          0,
          0,
          null,
          SpecialRule.LANCE,
          0,
          false);
  private static final WeaponSpec SHORTBOW =
      new WeaponSpec(
          "shortbow",
          WeaponCategory.SIMPLE,
          true,
          "1d6",
          "piercing",
          null,
          Set.of(WeaponProperty.AMMUNITION, WeaponProperty.TWO_HANDED),
          80,
          320,
          0,
          0,
          "arrow",
          SpecialRule.NONE,
          0,
          false);
  private static final WeaponSpec LIGHT_CROSSBOW =
      new WeaponSpec(
          "light-crossbow",
          WeaponCategory.SIMPLE,
          true,
          "1d8",
          "piercing",
          null,
          Set.of(WeaponProperty.AMMUNITION, WeaponProperty.LOADING, WeaponProperty.TWO_HANDED),
          80,
          320,
          0,
          0,
          "bolt",
          SpecialRule.NONE,
          0,
          false);
  private static final WeaponSpec DART =
      new WeaponSpec(
          "dart",
          WeaponCategory.SIMPLE,
          true,
          "1d4",
          "piercing",
          null,
          Set.of(WeaponProperty.FINESSE, WeaponProperty.THROWN),
          0,
          0,
          20,
          60,
          null,
          SpecialRule.NONE,
          0,
          false);
  private static final WeaponSpec NET =
      new WeaponSpec(
          "net",
          WeaponCategory.MARTIAL,
          true,
          null,
          null,
          null,
          Set.of(WeaponProperty.THROWN),
          0,
          0,
          5,
          15,
          null,
          SpecialRule.NET,
          0,
          false);

  private static final Map<String, WeaponSpec> CATALOG =
      Map.ofEntries(
          Map.entry("dagger", DAGGER),
          Map.entry("shortsword", SHORTSWORD),
          Map.entry("longsword", LONGSWORD),
          Map.entry("greatsword", GREATSWORD),
          Map.entry("spear", SPEAR),
          Map.entry("glaive", GLAIVE),
          Map.entry("lance", LANCE),
          Map.entry("shortbow", SHORTBOW),
          Map.entry("light-crossbow", LIGHT_CROSSBOW),
          Map.entry("dart", DART),
          Map.entry("net", NET));

  private static HeldItem held(WeaponSpec w) {
    return HeldItem.weapon(
        w.id(),
        w.has(WeaponProperty.TWO_HANDED),
        w.versatileDice() != null,
        w.has(WeaponProperty.LIGHT));
  }

  private static AttackerState attacker(Map<Ability, Integer> scores, HandState hands) {
    return state(scores, hands, CreatureSize.MEDIUM, false, Map.of("arrow", 20, "bolt", 20));
  }

  private static AttackerState state(
      Map<Ability, Integer> scores,
      HandState hands,
      CreatureSize size,
      boolean mounted,
      Map<String, Integer> ammunition) {
    return new AttackerState(
        ArmorClassCalculatorTest.scores(scores),
        2,
        size,
        Set.of(WeaponCategory.SIMPLE, WeaponCategory.MARTIAL),
        Set.of(),
        hands,
        mounted,
        ammunition);
  }

  private static List<AttackOption> of(List<AttackOption> all, Kind kind, String source) {
    return all.stream().filter(o -> o.kind() == kind && o.source().equals(source)).toList();
  }

  @Test
  void T_54_finesseUsesTheBetterModifierAndSaysWhichOne() {
    var hands = HandState.EMPTY.equip(held(SHORTSWORD), Hand.MAIN);
    var dexterous =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(Ability.DEX, 16, Ability.STR, 10), hands), CATALOG, List.of()),
                Kind.MELEE,
                "shortsword")
            .get(0);
    assertThat(dexterous.ability()).isEqualTo(Ability.DEX);
    assertThat(dexterous.attackBonus()).isEqualTo(3 + 2);
    assertThat(dexterous.damageBonus()).isEqualTo(3);
    var strong =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(Ability.DEX, 12, Ability.STR, 16), hands), CATALOG, List.of()),
                Kind.MELEE,
                "shortsword")
            .get(0);
    assertThat(strong.ability()).isEqualTo(Ability.STR);
    var tie =
        of(
                AttackOptionBuilder.build(attacker(Map.of(), hands), CATALOG, List.of()),
                Kind.MELEE,
                "shortsword")
            .get(0);
    assertThat(tie.ability()).isEqualTo(Ability.STR);
  }

  @Test
  void T_54_aNonFinesseMeleeWeaponUsesStrengthAndARangedOneUsesDexterity() {
    var sword = HandState.EMPTY.equip(held(LONGSWORD), Hand.MAIN);
    var melee =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(Ability.DEX, 18, Ability.STR, 12), sword), CATALOG, List.of()),
                Kind.MELEE,
                "longsword")
            .get(0);
    assertThat(melee.ability()).isEqualTo(Ability.STR);
    var bow = HandState.EMPTY.equip(held(SHORTBOW), Hand.MAIN);
    var ranged =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(Ability.DEX, 18, Ability.STR, 12), bow), CATALOG, List.of()),
                Kind.RANGED,
                "shortbow")
            .get(0);
    assertThat(ranged.ability()).isEqualTo(Ability.DEX);
    assertThat(ranged.normalRange()).isEqualTo(80);
    assertThat(ranged.longRange()).isEqualTo(320);
    assertThat(ranged.ammunitionType()).isEqualTo("arrow");
  }

  @Test
  void T_54_versatileOffersBothGripsOnlyWhenTheOtherHandIsFree() {
    var alone = HandState.EMPTY.equip(held(LONGSWORD), Hand.MAIN);
    var options =
        of(
            AttackOptionBuilder.build(attacker(Map.of(), alone), CATALOG, List.of()),
            Kind.MELEE,
            "longsword");
    assertThat(options.stream().map(AttackOption::damageDice).toList())
        .containsExactly("1d8", "1d10");
    var withShield = alone.equip(HeldItem.shield("shield"), Hand.OFF);
    var one =
        of(
            AttackOptionBuilder.build(attacker(Map.of(), withShield), CATALOG, List.of()),
            Kind.MELEE,
            "longsword");
    assertThat(one.stream().map(AttackOption::damageDice).toList()).containsExactly("1d8");
  }

  @Test
  void T_54_aThrowableMeleeWeaponGivesAMeleeAndAThrownOptionWithBothRanges() {
    var hands = HandState.EMPTY.equip(held(DAGGER), Hand.MAIN);
    var all = AttackOptionBuilder.build(attacker(Map.of(), hands), CATALOG, List.of());
    assertThat(of(all, Kind.MELEE, "dagger")).hasSize(1);
    var thrown = of(all, Kind.THROWN, "dagger").get(0);
    assertThat(thrown.normalRange()).isEqualTo(20);
    assertThat(thrown.longRange()).isEqualTo(60);
  }

  @Test
  void RS_EQP_023_aCarriedThrowableAppearsEvenThoughItIsNotHeld() {
    var all =
        AttackOptionBuilder.build(
            attacker(Map.of(), HandState.EMPTY), CATALOG, List.of("spear", "longsword"));
    assertThat(of(all, Kind.THROWN, "spear")).hasSize(1);
    assertThat(of(all, Kind.THROWN, "longsword")).isEmpty();
  }

  @Test
  void T_54_aRangedWeaponInMeleeAndAMeleeWeaponThatIsThrownAreImprovised() {
    var bow = HandState.EMPTY.equip(held(SHORTBOW), Hand.MAIN);
    var inMelee =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(Ability.STR, 14), bow), CATALOG, List.of()),
                Kind.IMPROVISED_MELEE,
                "shortbow")
            .get(0);
    assertThat(inMelee.damageDice()).isEqualTo("1d4");
    assertThat(inMelee.proficient()).isFalse();
    assertThat(inMelee.attackBonus()).isEqualTo(2);
    var sword = HandState.EMPTY.equip(held(LONGSWORD), Hand.MAIN);
    var thrownSword =
        of(
                AttackOptionBuilder.build(attacker(Map.of(), sword), CATALOG, List.of()),
                Kind.IMPROVISED_THROWN,
                "longsword")
            .get(0);
    assertThat(thrownSword.normalRange()).isEqualTo(20);
    assertThat(thrownSword.longRange()).isEqualTo(60);
  }

  @Test
  void RS_EQP_029_anyOtherHeldObjectCanBeUsedAsAnImprovisedWeapon() {
    var hands = HandState.EMPTY.equip(HeldItem.object("chair"), Hand.MAIN);
    var all = AttackOptionBuilder.build(attacker(Map.of(), hands), CATALOG, List.of());
    assertThat(of(all, Kind.IMPROVISED_MELEE, "chair")).hasSize(1);
    assertThat(of(all, Kind.IMPROVISED_THROWN, "chair")).hasSize(1);
  }

  @Test
  void T_54_aLoadingWeaponAllowsOneShotPerAction() {
    var hands = HandState.EMPTY.equip(held(LIGHT_CROSSBOW), Hand.MAIN);
    var crossbow =
        of(
                AttackOptionBuilder.build(attacker(Map.of(), hands), CATALOG, List.of()),
                Kind.RANGED,
                "light-crossbow")
            .get(0);
    assertThat(crossbow.shotsPerAction()).isEqualTo(1);
    var bow =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(), HandState.EMPTY.equip(held(SHORTBOW), Hand.MAIN)),
                    CATALOG,
                    List.of()),
                Kind.RANGED,
                "shortbow")
            .get(0);
    assertThat(bow.shotsPerAction()).isNull();
  }

  @Test
  void RS_EQP_024_aRangedOptionIsBlockedWhenThereIsNoAmmunition() {
    var hands = HandState.EMPTY.equip(held(SHORTBOW), Hand.MAIN);
    var out =
        AttackOptionBuilder.build(
            state(Map.of(), hands, CreatureSize.MEDIUM, false, Map.of("arrow", 0)),
            CATALOG,
            List.of());
    assertThat(of(out, Kind.RANGED, "shortbow").get(0).blockedReason()).isEqualTo("No ammunition");
  }

  @Test
  void RS_EQP_021_aHeavyWeaponGivesSmallAndTinyAttackersDisadvantage() {
    var hands = HandState.EMPTY.equip(held(GREATSWORD), Hand.MAIN);
    var small =
        of(
                AttackOptionBuilder.build(
                    state(Map.of(), hands, CreatureSize.SMALL, false, Map.of()),
                    CATALOG,
                    List.of()),
                Kind.MELEE,
                "greatsword")
            .get(0);
    assertThat(small.alwaysDisadvantage()).hasSize(1);
    var medium =
        of(
                AttackOptionBuilder.build(
                    state(Map.of(), hands, CreatureSize.MEDIUM, false, Map.of()),
                    CATALOG,
                    List.of()),
                Kind.MELEE,
                "greatsword")
            .get(0);
    assertThat(medium.alwaysDisadvantage()).isEmpty();
  }

  @Test
  void RS_EQP_022_aReachWeaponAddsFiveFeetToTheReach() {
    var glaive =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(), HandState.EMPTY.equip(held(GLAIVE), Hand.MAIN)),
                    CATALOG,
                    List.of()),
                Kind.MELEE,
                "glaive")
            .get(0);
    assertThat(glaive.reachFeet()).isEqualTo(10);
    var sword =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(), HandState.EMPTY.equip(held(LONGSWORD), Hand.MAIN)),
                    CATALOG,
                    List.of()),
                Kind.MELEE,
                "longsword")
            .get(0);
    assertThat(sword.reachFeet()).isEqualTo(5);
  }

  @Test
  void RS_EQP_027_aLanceHasDisadvantageWithinFiveFeetAndNeedsTwoHandsUnlessMounted() {
    var withShield =
        HandState.EMPTY.equip(held(LANCE), Hand.MAIN).equip(HeldItem.shield("shield"), Hand.OFF);
    var unmounted =
        of(
                AttackOptionBuilder.build(
                    state(Map.of(), withShield, CreatureSize.MEDIUM, false, Map.of()),
                    CATALOG,
                    List.of()),
                Kind.MELEE,
                "lance")
            .get(0);
    assertThat(unmounted.disadvantageWithinFiveFeet()).isTrue();
    assertThat(unmounted.blockedReason()).isNotNull();
    var mounted =
        of(
                AttackOptionBuilder.build(
                    state(Map.of(), withShield, CreatureSize.MEDIUM, true, Map.of()),
                    CATALOG,
                    List.of()),
                Kind.MELEE,
                "lance")
            .get(0);
    assertThat(mounted.blockedReason()).isNull();
    var twoHands =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(), HandState.EMPTY.equip(held(LANCE), Hand.MAIN)),
                    CATALOG,
                    List.of()),
                Kind.MELEE,
                "lance")
            .get(0);
    assertThat(twoHands.blockedReason()).isNull();
  }

  @Test
  void RS_EQP_028_aNetRestrainsOnAHitAndAllowsOneAttackPerAction() {
    var hands =
        HandState.EMPTY.equip(
            held(
                new WeaponSpec(
                    "net",
                    WeaponCategory.MARTIAL,
                    true,
                    null,
                    null,
                    null,
                    Set.of(WeaponProperty.THROWN),
                    0,
                    0,
                    5,
                    15,
                    null,
                    SpecialRule.NET,
                    0,
                    false)),
            Hand.MAIN);
    var net =
        of(
                AttackOptionBuilder.build(attacker(Map.of(), hands), CATALOG, List.of()),
                Kind.THROWN,
                "net")
            .get(0);
    assertThat(net.appliesCondition()).isEqualTo("restrained");
    assertThat(net.shotsPerAction()).isEqualTo(1);
    assertThat(net.damageDice()).isNull();
    assertThat(net.normalRange()).isEqualTo(5);
  }

  @Test
  void RS_EQP_020_theOffHandAttackAddsTheModifierToDamageOnlyWhenItIsNegative() {
    var hands = HandState.EMPTY.equip(held(DAGGER), Hand.MAIN).equip(held(SHORTSWORD), Hand.OFF);
    var good =
        AttackOptionBuilder.build(
            attacker(Map.of(Ability.STR, 16, Ability.DEX, 10), hands), CATALOG, List.of());
    var offHand = of(good, Kind.OFF_HAND, "shortsword").get(0);
    assertThat(offHand.damageBonus()).isZero();
    assertThat(of(good, Kind.MELEE, "dagger").get(0).damageBonus()).isEqualTo(3);
    var bad =
        AttackOptionBuilder.build(
            attacker(Map.of(Ability.STR, 8, Ability.DEX, 8), hands), CATALOG, List.of());
    assertThat(of(bad, Kind.OFF_HAND, "shortsword").get(0).damageBonus()).isEqualTo(-1);
  }

  @Test
  void RS_EQP_018_proficiencyComesFromTheCategoryOrTheWeaponName() {
    var hands = HandState.EMPTY.equip(held(LONGSWORD), Hand.MAIN);
    var none =
        new AttackerState(
            ArmorClassCalculatorTest.scores(Map.of()),
            2,
            CreatureSize.MEDIUM,
            Set.of(WeaponCategory.SIMPLE),
            Set.of(),
            hands,
            false,
            Map.of());
    var unskilled =
        of(AttackOptionBuilder.build(none, CATALOG, List.of()), Kind.MELEE, "longsword").get(0);
    assertThat(unskilled.proficient()).isFalse();
    assertThat(unskilled.attackBonus()).isZero();
    var named =
        new AttackerState(
            ArmorClassCalculatorTest.scores(Map.of()),
            2,
            CreatureSize.MEDIUM,
            Set.of(),
            Set.of("longsword"),
            hands,
            false,
            Map.of());
    var skilled =
        of(AttackOptionBuilder.build(named, CATALOG, List.of()), Kind.MELEE, "longsword").get(0);
    assertThat(skilled.proficient()).isTrue();
    assertThat(skilled.attackBonus()).isEqualTo(2);
  }

  @Test
  void RS_EQP_030_aMagicBonusAddsToAttackAndDamageAndMarksTheOptionMagical() {
    var plusTwo =
        new WeaponSpec(
            "longsword",
            WeaponCategory.MARTIAL,
            false,
            "1d8",
            "slashing",
            "1d10",
            Set.of(WeaponProperty.VERSATILE),
            0,
            0,
            0,
            0,
            null,
            SpecialRule.NONE,
            2,
            true);
    var hands = HandState.EMPTY.equip(held(plusTwo), Hand.MAIN);
    var option =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(Ability.STR, 14), hands),
                    Map.of("longsword", plusTwo),
                    List.of()),
                Kind.MELEE,
                "longsword")
            .get(0);
    assertThat(option.attackBonus()).isEqualTo(2 + 2 + 2);
    assertThat(option.damageBonus()).isEqualTo(2 + 2);
    assertThat(option.magical()).isTrue();
    assertThat(option.silvered()).isTrue();
  }

  @Test
  void RS_ATK_007_anUnarmedStrikeIsAlwaysListedAndIsProficient() {
    var all =
        AttackOptionBuilder.build(
            attacker(Map.of(Ability.STR, 16), HandState.EMPTY), CATALOG, List.of());
    var strike = of(all, Kind.UNARMED, "unarmed").get(0);
    assertThat(strike.damageBonus()).isEqualTo(1 + 3);
    assertThat(strike.attackBonus()).isEqualTo(3 + 2);
    assertThat(strike.damageDice()).isNull();
    assertThat(all).hasSize(1);
  }

  @Test
  void aTwoHandedWeaponIsListedOnceNotOncePerHand() {
    var hands = HandState.EMPTY.equip(held(GREATSWORD), Hand.MAIN);
    var all = AttackOptionBuilder.build(attacker(Map.of(), hands), CATALOG, List.of());
    assertThat(of(all, Kind.MELEE, "greatsword")).hasSize(1);
  }

  @Test
  void aDartIsAThrownRangedWeaponUsingTheBetterModifier() {
    var hands = HandState.EMPTY.equip(held(DART), Hand.MAIN);
    var dart =
        of(
                AttackOptionBuilder.build(
                    attacker(Map.of(Ability.DEX, 16), hands), CATALOG, List.of()),
                Kind.THROWN,
                "dart")
            .get(0);
    assertThat(dart.ability()).isEqualTo(Ability.DEX);
    assertThat(dart.normalRange()).isEqualTo(20);
  }
}
