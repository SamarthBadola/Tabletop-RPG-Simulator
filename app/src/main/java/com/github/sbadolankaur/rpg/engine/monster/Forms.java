package com.github.sbadolankaur.rpg.engine.monster;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shapechanger forms (RS-MON-024, RS-MON-025, E-124). The form is a field of the combatant and
 * gates traits and actions. Armor class and speed can differ by form. This is not Polymorph: hit
 * points and conditions never change with the form.
 */
public final class Forms {
  private Forms() {}

  /** A form: its armor class, speeds and the actions that belong only to it. */
  public record Form(String key, int armorClass, Map<String, Integer> speeds, Set<String> actions) {
    public Form {
      speeds = Map.copyOf(speeds);
      actions = Set.copyOf(actions);
    }
  }

  /** The forms of a creature, with its true form and the one it is in now. */
  public record State(List<Form> forms, String trueForm, String current) {
    public State {
      forms = List.copyOf(forms);
      if (forms.stream().noneMatch(form -> form.key().equals(trueForm))
          || forms.stream().noneMatch(form -> form.key().equals(current))) {
        throw new IllegalArgumentException("Unknown form");
      }
    }

    public Form currentForm() {
      return forms.stream().filter(form -> form.key().equals(current)).findFirst().orElseThrow();
    }

    /**
     * Changing form takes an action. Armor class, speed and actions follow at once (RS-MON-R21).
     */
    public State change(String formKey) {
      return new State(forms, trueForm, formKey);
    }

    /** The creature returns to its true form when it dies. */
    public State onDeath() {
      return change(trueForm);
    }

    /** An action that belongs to no form is always available. One that does needs that form. */
    public boolean actionAvailable(String action) {
      boolean gated = forms.stream().anyMatch(form -> form.actions().contains(action));
      return !gated || currentForm().actions().contains(action);
    }
  }

  /** Adhesive and Grappler (mimic, RS-MON-025). */
  public static final class Adhesive {
    private Adhesive() {}

    /** A creature of Huge size or smaller that touches the mimic in object form is grappled. */
    public static boolean grapples(boolean inObjectForm, int touchingSizeRank, int hugeRank) {
      return inObjectForm && touchingSizeRank <= hugeRank;
    }

    /** The escape is an ordinary check against the DC on the stat block, with disadvantage. */
    public static boolean escapeHasDisadvantage() {
      return true;
    }

    /** The mimic has advantage on attacks against a creature it has grappled. */
    public static boolean attackHasAdvantage(boolean targetGrappledByIt) {
      return targetGrappledByIt;
    }
  }
}
