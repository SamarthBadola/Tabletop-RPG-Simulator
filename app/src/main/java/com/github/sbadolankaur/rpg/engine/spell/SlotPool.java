package com.github.sbadolankaur.rpg.engine.spell;

import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** Slot limits come from class progression, while a character stores only the slots spent. */
public record SlotPool(Map<Integer, Integer> limits, Map<Integer, Integer> used) {
  private static final int HIGHEST_SLOT = 9;

  public SlotPool {
    limits = checkedCounts(limits);
    used = checkedCounts(used);
  }

  public static Optional<SlotPool> forClass(
      ContentSet content, String classKey, int classLevel, Map<Integer, Integer> spent) {
    var spellcasting =
        content
            .find("class", classKey)
            .map(definition -> definition.fields().get("spellcasting"))
            .orElse(null);
    if (!(spellcasting instanceof Map<?, ?> profile)
        || !(profile.get("slots") instanceof String tableKey)) return Optional.empty();
    var rows =
        content.find("ruleTable", tableKey).map(table -> table.fields().get("rows")).orElse(null);
    if (!(rows instanceof List<?> table)) return Optional.empty();
    for (var entry : table) {
      if (!(entry instanceof Map<?, ?> row) || !Long.valueOf(classLevel).equals(row.get("level")))
        continue;
      var limits = new HashMap<Integer, Integer>();
      for (int level = 1; level <= HIGHEST_SLOT; level++) {
        if (row.get("slot" + level) instanceof Long count && count > 0)
          limits.put(level, Math.toIntExact(count));
      }
      return Optional.of(new SlotPool(limits, spent));
    }
    return Optional.empty();
  }

  private static Map<Integer, Integer> checkedCounts(Map<Integer, Integer> counts) {
    var result = Map.copyOf(counts);
    for (var entry : result.entrySet()) {
      if (entry.getKey() < 1 || entry.getKey() > HIGHEST_SLOT || entry.getValue() < 0)
        throw new IllegalArgumentException("Invalid spell slot count");
    }
    return result;
  }

  public int remaining(int slotLevel) {
    return Math.max(0, limits.getOrDefault(slotLevel, 0) - used.getOrDefault(slotLevel, 0));
  }

  public List<Integer> availableFrom(int spellLevel) {
    if (spellLevel < 1 || spellLevel > HIGHEST_SLOT)
      throw new IllegalArgumentException("Expected a leveled spell");
    return IntStream.rangeClosed(spellLevel, HIGHEST_SLOT)
        .filter(level -> remaining(level) > 0)
        .boxed()
        .toList();
  }

  public boolean canSpend(int spellLevel, int slotLevel) {
    if (spellLevel == 0) return slotLevel == 0;
    return spellLevel > 0 && slotLevel >= spellLevel && remaining(slotLevel) > 0;
  }

  public SlotPool spend(int spellLevel, int slotLevel) {
    if (!canSpend(spellLevel, slotLevel))
      throw new IllegalArgumentException("Spell slot unavailable");
    if (slotLevel == 0) return this;
    var next = new HashMap<>(used);
    next.merge(slotLevel, 1, Integer::sum);
    return new SlotPool(limits, next);
  }
}
