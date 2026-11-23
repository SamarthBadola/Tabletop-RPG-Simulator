package com.github.sbadolankaur.rpg.engine.combat;

/** Remaining turn resources. Movement is tracked as spent so modifiers can change the allowance. */
public record TurnBudget(
    int actions,
    int attacksLeft,
    boolean bonusAvailable,
    boolean interactionAvailable,
    int movementSpent,
    int dashes,
    boolean disengaged,
    boolean mainHandLightAttack,
    boolean actionSurged,
    java.util.Set<java.util.UUID> sneakAttackUsed) {
  public TurnBudget {
    sneakAttackUsed =
        sneakAttackUsed == null
            ? java.util.Set.of()
            : java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(sneakAttackUsed));
  }

  public static TurnBudget start() {
    return new TurnBudget(1, 0, true, true, 0, 0, false, false, false, java.util.Set.of());
  }

  public TurnBudget attack(int attacksPerAction, boolean lightMainHand) {
    boolean beginsAction = attacksLeft == 0;
    if (beginsAction && actions == 0) throw new IllegalStateException("No actions left");
    int available = beginsAction ? attacksPerAction : attacksLeft;
    return new TurnBudget(
        actions - (beginsAction ? 1 : 0),
        available - 1,
        bonusAvailable,
        interactionAvailable,
        movementSpent,
        dashes,
        disengaged,
        mainHandLightAttack || lightMainHand,
        actionSurged,
        sneakAttackUsed);
  }

  public TurnBudget offHand() {
    if (!bonusAvailable || !mainHandLightAttack)
      throw new IllegalStateException("No off-hand attack available");
    return new TurnBudget(
        actions,
        attacksLeft,
        false,
        interactionAvailable,
        movementSpent,
        dashes,
        disengaged,
        mainHandLightAttack,
        actionSurged,
        sneakAttackUsed);
  }

  public TurnBudget action() {
    if (actions == 0) throw new IllegalStateException("No actions left");
    return new TurnBudget(
        actions - 1,
        attacksLeft,
        bonusAvailable,
        interactionAvailable,
        movementSpent,
        dashes,
        disengaged,
        mainHandLightAttack,
        actionSurged,
        sneakAttackUsed);
  }

  public TurnBudget sneakAttack(java.util.UUID attacker) {
    if (sneakAttackUsed.contains(attacker))
      throw new IllegalStateException("Sneak Attack already used");
    var used = new java.util.TreeSet<>(sneakAttackUsed);
    used.add(attacker);
    return new TurnBudget(
        actions,
        attacksLeft,
        bonusAvailable,
        interactionAvailable,
        movementSpent,
        dashes,
        disengaged,
        mainHandLightAttack,
        actionSurged,
        used);
  }

  public TurnBudget surge() {
    if (actionSurged) throw new IllegalStateException("Action Surge already used on this turn");
    return new TurnBudget(
        Math.incrementExact(actions),
        attacksLeft,
        bonusAvailable,
        interactionAvailable,
        movementSpent,
        dashes,
        disengaged,
        mainHandLightAttack,
        true,
        sneakAttackUsed);
  }

  public TurnBudget bonusAction() {
    if (!bonusAvailable) throw new IllegalStateException("No bonus action left");
    return new TurnBudget(
        actions,
        attacksLeft,
        false,
        interactionAvailable,
        movementSpent,
        dashes,
        disengaged,
        mainHandLightAttack,
        actionSurged,
        sneakAttackUsed);
  }

  public TurnBudget dash() {
    return dash(false);
  }

  public TurnBudget dash(boolean bonus) {
    var spent = bonus ? bonusAction() : action();
    return new TurnBudget(
        spent.actions,
        attacksLeft,
        spent.bonusAvailable,
        interactionAvailable,
        movementSpent,
        dashes + 1,
        disengaged,
        mainHandLightAttack,
        actionSurged,
        sneakAttackUsed);
  }

  public TurnBudget disengage() {
    return disengage(false);
  }

  public TurnBudget disengage(boolean bonus) {
    var spent = bonus ? bonusAction() : action();
    return new TurnBudget(
        spent.actions,
        attacksLeft,
        spent.bonusAvailable,
        interactionAvailable,
        movementSpent,
        dashes,
        true,
        mainHandLightAttack,
        actionSurged,
        sneakAttackUsed);
  }

  public TurnBudget interact(boolean usesAction) {
    if (usesAction || !interactionAvailable) return action();
    return new TurnBudget(
        actions,
        attacksLeft,
        bonusAvailable,
        false,
        movementSpent,
        dashes,
        disengaged,
        mainHandLightAttack,
        actionSurged,
        sneakAttackUsed);
  }

  public int movementRemaining(int speed) {
    return Math.max(0, Math.multiplyExact(speed, 1 + dashes) - movementSpent);
  }

  public TurnBudget move(int cost, int speed) {
    if (cost < 0 || cost > movementRemaining(speed))
      throw new IllegalStateException("No movement left");
    return new TurnBudget(
        actions,
        attacksLeft,
        bonusAvailable,
        interactionAvailable,
        movementSpent + cost,
        dashes,
        disengaged,
        mainHandLightAttack,
        actionSurged,
        sneakAttackUsed);
  }

  public static int movementCost(int feet, boolean difficult, boolean crawling, boolean squeezing) {
    if (feet < 0) throw new IllegalArgumentException("Distance must be nonnegative");
    return Math.multiplyExact(
        feet, 1 + (difficult ? 1 : 0) + (crawling ? 1 : 0) + (squeezing ? 1 : 0));
  }
}
