package com.github.sbadolankaur.rpg.engine.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Orders due timers across creatures, including timers loaded from older saves. */
public record ClockExpiry(long deadline, long creationOrder, Kind kind, ConditionInstance effect) {
  public static final Comparator<ClockExpiry> ORDER =
      Comparator.comparingLong(ClockExpiry::deadline).thenComparingLong(ClockExpiry::creationOrder);

  public enum Kind {
    CONDITION,
    INSPIRATION,
    STABLE_RECOVERY
  }

  public static List<ClockExpiry> due(
      long now,
      List<ConditionInstance> effects,
      BardicInspiration inspiration,
      Long recoveryAt,
      long recoveryOrder) {
    var result = new ArrayList<ClockExpiry>();
    for (var effect : effects) {
      Long deadline = effect.end().seconds();
      if (deadline != null && deadline <= now)
        result.add(new ClockExpiry(deadline, effect.creationOrder(), Kind.CONDITION, effect));
    }
    if (inspiration != null && inspiration.endsAt() <= now)
      result.add(
          new ClockExpiry(
              inspiration.endsAt(), inspiration.creationOrder(), Kind.INSPIRATION, null));
    if (recoveryAt != null && recoveryAt <= now)
      result.add(new ClockExpiry(recoveryAt, recoveryOrder, Kind.STABLE_RECOVERY, null));
    return result;
  }
}
