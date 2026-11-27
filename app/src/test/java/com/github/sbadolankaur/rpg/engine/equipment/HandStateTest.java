package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class HandStateTest {
  private static final HeldItem GREATSWORD = HeldItem.weapon("greatsword", true, false, false);
  private static final HeldItem LONGSWORD = HeldItem.weapon("longsword", false, true, false);
  private static final HeldItem DAGGER = HeldItem.weapon("dagger", false, false, true);
  private static final HeldItem SHORTSWORD = HeldItem.weapon("shortsword", false, false, true);
  private static final HeldItem SHIELD = HeldItem.shield("shield");
  private static final HeldItem FOCUS = HeldItem.focus("arcane-focus");
  private static final HeldItem TORCH = HeldItem.object("torch");

  private static HandsException refused(Runnable action) {
    try {
      action.run();
    } catch (HandsException e) {
      return e;
    }
    throw new AssertionError("The equip was not refused");
  }

  @Test
  void T_52_aTwoHandedWeaponAndAShieldAreRefusedInEitherOrderAndTheBlockerIsNamed() {
    var withShield = HandState.EMPTY.equip(SHIELD, Hand.OFF);
    assertThat(refused(() -> withShield.equip(GREATSWORD, Hand.MAIN)).blockingItemIds())
        .containsExactly("shield");
    var withSword = HandState.EMPTY.equip(GREATSWORD, Hand.MAIN);
    assertThat(refused(() -> withSword.equip(SHIELD, Hand.OFF)).blockingItemIds())
        .containsExactly("greatsword");
  }

  @Test
  void RS_EQP_026_aTwoHandedWeaponFillsBothHandsAndLeavesThemTogether() {
    var state = HandState.EMPTY.equip(GREATSWORD, Hand.MAIN);
    assertThat(state.hasFreeHand()).isFalse();
    assertThat(state.in(Hand.OFF)).isEqualTo(GREATSWORD);
    assertThat(state.unequip(Hand.OFF).hasFreeHand()).isTrue();
    assertThat(state.unequip(Hand.MAIN).isFree(Hand.OFF)).isTrue();
  }

  @Test
  void RS_EQP_011_aHandHoldsOneItemAndTheOccupantIsNamed() {
    var state = HandState.EMPTY.equip(LONGSWORD, Hand.MAIN);
    assertThat(refused(() -> state.equip(TORCH, Hand.MAIN)).blockingItemIds())
        .containsExactly("longsword");
    assertThat(state.equip(TORCH, Hand.OFF).in(Hand.OFF)).isEqualTo(TORCH);
  }

  @Test
  void RS_EQP_005_onlyOneShieldCounts() {
    var state = HandState.EMPTY.equip(SHIELD, Hand.MAIN);
    var second = HeldItem.shield("second-shield");
    assertThat(refused(() -> state.equip(second, Hand.OFF)).blockingItemIds())
        .containsExactly("shield");
  }

  @Test
  void T_52_versatileGripsFollowTheOtherHand() {
    var alone = HandState.EMPTY.equip(LONGSWORD, Hand.MAIN);
    assertThat(alone.grips(Hand.MAIN)).isEqualTo(Set.of(Grip.ONE_HANDED, Grip.TWO_HANDED));
    var withShield = alone.equip(SHIELD, Hand.OFF);
    assertThat(withShield.grips(Hand.MAIN)).isEqualTo(Set.of(Grip.ONE_HANDED)); // C-F5
    var withTorch = alone.equip(TORCH, Hand.OFF);
    assertThat(withTorch.grips(Hand.MAIN)).isEqualTo(Set.of(Grip.ONE_HANDED));
    var great = HandState.EMPTY.equip(GREATSWORD, Hand.MAIN);
    assertThat(great.grips(Hand.MAIN)).isEqualTo(Set.of(Grip.TWO_HANDED));
    assertThat(HandState.EMPTY.grips(Hand.MAIN)).isEmpty();
    assertThat(HandState.EMPTY.equip(SHIELD, Hand.MAIN).grips(Hand.MAIN)).isEmpty();
  }

  @Test
  void T_52_twoLightWeaponsGiveAnOffHandOption() {
    var both = HandState.EMPTY.equip(DAGGER, Hand.MAIN).equip(SHORTSWORD, Hand.OFF);
    assertThat(both.twoWeaponFighting()).isTrue();
    var heavier = HandState.EMPTY.equip(DAGGER, Hand.MAIN).equip(LONGSWORD, Hand.OFF);
    assertThat(heavier.twoWeaponFighting()).isFalse();
    assertThat(HandState.EMPTY.equip(DAGGER, Hand.MAIN).twoWeaponFighting()).isFalse();
  }

  @Test
  void T_52_aSomaticComponentNeedsAFreeHandOrAHandHoldingTheFocus() {
    var weaponAndShield = HandState.EMPTY.equip(LONGSWORD, Hand.MAIN).equip(SHIELD, Hand.OFF);
    assertThat(weaponAndShield.canMakeSomaticGesture()).isFalse();
    assertThat(HandState.EMPTY.equip(LONGSWORD, Hand.MAIN).canMakeSomaticGesture()).isTrue();
    var weaponAndFocus = HandState.EMPTY.equip(LONGSWORD, Hand.MAIN).equip(FOCUS, Hand.OFF);
    assertThat(weaponAndFocus.canMakeSomaticGesture()).isTrue();
    assertThat(HandState.EMPTY.equip(GREATSWORD, Hand.MAIN).canMakeSomaticGesture()).isFalse();
    var staff = new HeldItem("quarterstaff-focus", HeldItem.Kind.WEAPON, true, false, false, true);
    assertThat(HandState.EMPTY.equip(staff, Hand.MAIN).canMakeSomaticGesture()).isTrue();
    var holySymbolShield =
        new HeldItem("emblazoned-shield", HeldItem.Kind.SHIELD, false, false, false, true);
    assertThat(
            HandState.EMPTY
                .equip(LONGSWORD, Hand.MAIN)
                .equip(holySymbolShield, Hand.OFF)
                .canMakeSomaticGesture())
        .isTrue();
  }

  @Test
  void aRefusedEquipChangesNothing() {
    var state = HandState.EMPTY.equip(SHIELD, Hand.OFF);
    refused(() -> state.equip(GREATSWORD, Hand.MAIN));
    assertThat(state.in(Hand.OFF)).isEqualTo(SHIELD);
    assertThat(state.isFree(Hand.MAIN)).isTrue();
  }
}
