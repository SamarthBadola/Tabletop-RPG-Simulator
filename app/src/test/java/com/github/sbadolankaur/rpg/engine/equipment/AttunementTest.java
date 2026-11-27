package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.Attunement.Candidate;
import com.github.sbadolankaur.rpg.engine.equipment.Attunement.ItemCondition;
import com.github.sbadolankaur.rpg.engine.equipment.Attunement.Reason;
import com.github.sbadolankaur.rpg.engine.equipment.Attunement.Snapshot;
import com.github.sbadolankaur.rpg.engine.equipment.AttunementException.Code;
import com.github.sbadolankaur.rpg.engine.equipment.ItemEffects.ActiveItem;
import com.github.sbadolankaur.rpg.engine.equipment.ItemEffects.Bonus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttunementTest {
  private static Snapshot with(AttunedItem... items) {
    return new Snapshot("pc", true, false, List.of(items));
  }

  private static Code refused(Snapshot who, Candidate item) {
    try {
      Attunement.attune(who, item);
    } catch (AttunementException e) {
      return e.code();
    }
    throw new AssertionError("The attunement was not refused");
  }

  private static Candidate item(String instance, String definition) {
    return new Candidate(instance, definition, null, true);
  }

  @Test
  void T_56_attuningTakesAnHourAndAddsTheItem() {
    var result = Attunement.attune(with(), item("ring-1", "ring-of-protection"));
    assertThat(result.attuned()).hasSize(1);
    assertThat(result.clockAdvanceSeconds()).isEqualTo(3600);
  }

  @Test
  void T_56_theFourthItemIsRefused() {
    var three =
        with(new AttunedItem("a1", "a"), new AttunedItem("b1", "b"), new AttunedItem("c1", "c"));
    assertThat(refused(three, item("d1", "d"))).isEqualTo(Code.TOO_MANY_ITEMS);
    assertThat(
            Attunement.attune(
                    with(new AttunedItem("a1", "a"), new AttunedItem("b1", "b")), item("c1", "c"))
                .attuned())
        .hasSize(3);
  }

  @Test
  void RS_EQP_032_twoCopiesOfOneDefinitionAreRefused() {
    var who = with(new AttunedItem("ring-1", "ring-of-protection"));
    assertThat(refused(who, item("ring-2", "ring-of-protection")))
        .isEqualTo(Code.DUPLICATE_DEFINITION);
  }

  @Test
  void RS_EQP_032_anItemAttunedToAnotherCreatureIsRefused() {
    var other = new Candidate("ring-1", "ring-of-protection", "someone-else", true);
    assertThat(refused(with(), other)).isEqualTo(Code.ATTUNED_TO_ANOTHER);
    assertThat(
            refused(
                with(new AttunedItem("ring-1", "ring-of-protection")),
                item("ring-1", "ring-of-protection")))
        .isEqualTo(Code.ALREADY_ATTUNED);
  }

  @Test
  void RS_EQP_032_attuningNeedsNoEncounterTheLivingAndAMetPrerequisite() {
    assertThat(refused(new Snapshot("pc", true, true, List.of()), item("x", "x")))
        .isEqualTo(Code.ENCOUNTER_ACTIVE);
    assertThat(refused(new Snapshot("pc", false, false, List.of()), item("x", "x")))
        .isEqualTo(Code.CHARACTER_DEAD);
    assertThat(refused(with(), new Candidate("x", "x", null, false)))
        .isEqualTo(Code.PREREQUISITE_NOT_MET);
  }

  @Test
  void T_56_eachEndingConditionEndsTheAttunementByItself() {
    var ring = new AttunedItem("ring-1", "ring-of-protection");
    var who = with(ring);
    assertThat(Attunement.endings(who, List.of(new ItemCondition(ring, true, 0, "pc")))).isEmpty();
    assertThat(
            Attunement.endings(who, List.of(new ItemCondition(ring, false, 0, "pc")))
                .get(0)
                .reason())
        .isEqualTo(Reason.PREREQUISITE_LOST);
    assertThat(
            Attunement.endings(who, List.of(new ItemCondition(ring, true, 24 * 3600, "pc")))
                .get(0)
                .reason())
        .isEqualTo(Reason.TOO_FAR_FOR_24_HOURS);
    assertThat(Attunement.endings(who, List.of(new ItemCondition(ring, true, 24 * 3600 - 1, "pc"))))
        .isEmpty();
    assertThat(
            Attunement.endings(who, List.of(new ItemCondition(ring, true, 0, "other")))
                .get(0)
                .reason())
        .isEqualTo(Reason.ATTUNED_TO_ANOTHER);
    var dead = new Snapshot("pc", false, false, List.of(ring));
    assertThat(
            Attunement.endings(dead, List.of(new ItemCondition(ring, true, 0, "pc")))
                .get(0)
                .reason())
        .isEqualTo(Reason.CHARACTER_DIED);
  }

  @Test
  void RS_EQP_032_aShortRestEndsItExceptForACursedItem() {
    var who = with(new AttunedItem("ring-1", "r"), new AttunedItem("cloak-1", "c"));
    assertThat(Attunement.endByRest(who, "ring-1", false)).hasSize(1);
    try {
      Attunement.endByRest(who, "ring-1", true);
    } catch (AttunementException e) {
      assertThat(e.code()).isEqualTo(Code.CURSED);
      return;
    }
    throw new AssertionError("A cursed item was released");
  }

  private static ActiveItem ac(
      String definition,
      String instance,
      boolean worn,
      boolean needs,
      boolean attuned,
      int amount) {
    return new ActiveItem(
        definition, instance, worn, needs, attuned, List.of(new Bonus("ac", amount)));
  }

  @Test
  void T_56_aRingAndACloakOfProtectionAdd() {
    var total =
        ItemEffects.gather(
            List.of(
                ac("ring-of-protection", "r1", true, true, true, 1),
                ac("cloak-of-protection", "c1", true, true, true, 1)));
    assertThat(total.get("ac")).isEqualTo(2);
  }

  @Test
  void T_56_twoCopiesOfTheSameDefinitionCountOnce() {
    var total =
        ItemEffects.gather(
            List.of(
                ac("ring-of-protection", "r1", true, true, true, 1),
                ac("ring-of-protection", "r2", true, true, true, 1)));
    assertThat(total.get("ac")).isEqualTo(1);
  }

  @Test
  void RS_EQP_031_anItemCountsOnlyWhileWornAndAttunedWhenItNeedsAttunement() {
    assertThat(ItemEffects.gather(List.of(ac("ring", "r", false, true, true, 1))).isEmpty())
        .isTrue();
    assertThat(ItemEffects.gather(List.of(ac("ring", "r", true, true, false, 1))).isEmpty())
        .isTrue();
    assertThat(
            ItemEffects.gather(List.of(ac("shield-plus-1", "s", true, false, false, 1))).get("ac"))
        .isEqualTo(1);
  }
}
