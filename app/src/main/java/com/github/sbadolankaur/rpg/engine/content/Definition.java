package com.github.sbadolankaur.rpg.engine.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An authored definition without storage or JSON-library dependencies. */
public record Definition(
    String packKey,
    String kind,
    String key,
    String name,
    String hash,
    String overrides,
    Map<String, Object> fields) {
  public Definition {
    fields = immutableMap(fields);
  }

  private static Map<String, Object> immutableMap(Map<?, ?> source) {
    var result = new LinkedHashMap<String, Object>();
    source.forEach((name, value) -> result.put((String) name, immutableValue(value)));
    return Collections.unmodifiableMap(result);
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> nested) return immutableMap(nested);
    if (value instanceof List<?> elements)
      return elements.stream().map(Definition::immutableValue).toList();
    if (value == null
        || value instanceof String
        || value instanceof Long
        || value instanceof Boolean) return value;
    throw new IllegalArgumentException("Unsupported definition value");
  }
}
