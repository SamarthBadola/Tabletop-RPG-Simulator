package com.github.sbadolankaur.rpg.engine.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Two hands, each holding at most one item (RS-EQP-011). A two-handed item fills both. The state is
 * immutable: each change returns the new state, or throws {@link HandsException} and changes
 * nothing.
 */
public final class HandState {
  public static final HandState EMPTY = new HandState(null, null);

  private final HeldItem main;
  private final HeldItem off;

  private HandState(HeldItem main, HeldItem off) {
    this.main = main;
    this.off = off;
  }

  public HeldItem in(Hand hand) {
    return hand == Hand.MAIN ? main : off;
  }

  public boolean isFree(Hand hand) {
    return in(hand) == null;
  }

  public boolean hasFreeHand() {
    return main == null || off == null;
  }

  /** Puts the item in the hand, or in both hands for a two-handed item (RS-EQP-005, RS-EQP-026). */
  public HandState equip(HeldItem item, Hand hand) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(hand, "hand");
    if (item.twoHanded()) {
      var blocking = occupants(main, off);
      if (!blocking.isEmpty()) {
        throw new HandsException("A two-handed item needs both hands free", blocking);
      }
      return new HandState(item, item);
    }
    if (item.kind() == HeldItem.Kind.SHIELD) {
      var other = in(hand.other());
      if (other != null && other.kind() == HeldItem.Kind.SHIELD) {
        throw new HandsException("Only one shield counts", List.of(other.id()));
      }
    }
    var current = in(hand);
    if (current != null) {
      throw new HandsException("That hand is already holding something", List.of(current.id()));
    }
    return hand == Hand.MAIN ? new HandState(item, off) : new HandState(main, item);
  }

  /** Empties the hand. A two-handed item leaves both hands. */
  public HandState unequip(Hand hand) {
    var held = in(hand);
    if (held == null) return this;
    if (held.twoHanded()) return EMPTY;
    return hand == Hand.MAIN ? new HandState(null, off) : new HandState(main, null);
  }

  /** The grips that the item in this hand can use for an attack now (RS-EQP-012, C-F5). */
  public Set<Grip> grips(Hand hand) {
    var held = in(hand);
    if (held == null || held.kind() != HeldItem.Kind.WEAPON) return Set.of();
    if (held.twoHanded()) return Set.of(Grip.TWO_HANDED);
    if (held.versatile() && isFree(hand.other())) return Set.of(Grip.ONE_HANDED, Grip.TWO_HANDED);
    return Set.of(Grip.ONE_HANDED);
  }

  /**
   * Whether a somatic component can be made: a hand is free, or a hand holds an item that supplies
   * the focus or components (RS-EQP-016).
   */
  public boolean canMakeSomaticGesture() {
    return hasFreeHand()
        || (main != null && main.suppliesFocus())
        || (off != null && off.suppliesFocus());
  }

  /** A light weapon in each hand allows the off-hand attack (RS-EQP-020). */
  public boolean twoWeaponFighting() {
    return main != null
        && off != null
        && main != off
        && main.kind() == HeldItem.Kind.WEAPON
        && off.kind() == HeldItem.Kind.WEAPON
        && main.light()
        && off.light();
  }

  private static List<String> occupants(HeldItem a, HeldItem b) {
    var ids = new ArrayList<String>();
    if (a != null) ids.add(a.id());
    if (b != null && !ids.contains(b.id())) ids.add(b.id());
    return ids;
  }
}
