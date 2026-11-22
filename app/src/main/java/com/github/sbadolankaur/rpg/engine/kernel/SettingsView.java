package com.github.sbadolankaur.rpg.engine.kernel;

import java.util.Map;

/** Typed snapshot of the effective campaign settings selected for an action. */
public record SettingsView(Map<String, Object> values) {
  public SettingsView {
    values = Map.copyOf(values);
    if (values.values().stream()
        .anyMatch(
            value ->
                !(value instanceof String || value instanceof Long || value instanceof Boolean))) {
      throw new IllegalArgumentException("Settings must be text, integers or booleans");
    }
  }

  public Object value(String key) {
    Object value = values.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Unknown setting: " + key);
    }
    return value;
  }

  public <T> T get(String key, Class<T> type) {
    return type.cast(value(key));
  }
}
