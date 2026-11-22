package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.content.Definition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Projects content to player-safe fields before it can reach a template. */
@Component
public final class ContentViews {
  private final PackSchemas schemas = new PackSchemas();

  public Map<String, Object> forPlayer(Definition definition, boolean revealed) {
    ContentKind kind = ContentKind.fromId(definition.kind());
    Map<String, Object> fields = definition.fields();
    if (kind == ContentKind.ITEM && !revealed && !fields.containsKey("unidentified")) {
      return Map.of("category", fields.get("category"));
    }
    boolean visible =
        revealed || (kind == ContentKind.HAZARD && "visible".equals(fields.get("visibility")));
    var byName = new LinkedHashMap<String, PackSchemas.Shape>();
    for (var shape : schemas.definitionShapes(kind)) {
      shape
          .node()
          .path("properties")
          .properties()
          .forEach(
              entry ->
                  byName.put(
                      entry.getKey(), new PackSchemas.Shape(shape.location(), entry.getValue())));
    }
    var result = new LinkedHashMap<String, Object>();
    byName.forEach(
        (name, shape) -> {
          if (!(kind == ContentKind.ITEM && visible && name.equals("unidentified"))) {
            projectField(name, shape, fields, visible, result);
          }
        });
    return Collections.unmodifiableMap(result);
  }

  private void projectField(
      String field,
      PackSchemas.Shape shape,
      Map<?, ?> record,
      boolean revealed,
      Map<String, Object> target) {
    String visibility = shape.node().path("x-visibility").asString("dm");
    if (!record.containsKey(field)
        || (!visibility.equals("public") && !(revealed && visibility.equals("revealed")))) {
      return;
    }
    target.put(field, projectValue(record.get(field), shape, revealed));
  }

  private Object projectValue(Object value, PackSchemas.Shape original, boolean revealed) {
    if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) return value;
    var shape = schemas.dereference(original);
    JsonNode rule = shape.node();
    if (value instanceof List<?> list) {
      return list.stream()
          .map(
              element ->
                  projectValue(
                      element,
                      new PackSchemas.Shape(shape.location(), rule.path("items")),
                      revealed))
          .toList();
    }
    var view = new LinkedHashMap<String, Object>();
    var source = (Map<?, ?>) value;
    rule.path("properties")
        .properties()
        .forEach(
            entry ->
                projectField(
                    entry.getKey(),
                    new PackSchemas.Shape(shape.location(), entry.getValue()),
                    source,
                    revealed,
                    view));
    return Collections.unmodifiableMap(view);
  }
}
