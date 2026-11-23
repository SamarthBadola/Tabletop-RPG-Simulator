package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.content.Definition;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;

public record ContentPack(
    String key,
    String version,
    String hash,
    String ruleset,
    String license,
    String manifestJson,
    String notice,
    List<Definition> definitions,
    List<String> dependencyHashes,
    List<ContentFinding> warnings) {
  public ContentPack {
    definitions = List.copyOf(definitions);
    dependencyHashes = List.copyOf(dependencyHashes);
    warnings = List.copyOf(warnings);
  }

  static Definition definition(String packKey, String hash, JsonNode node) {
    return new Definition(
        packKey,
        node.path("kind").asString(),
        node.path("key").asString(),
        node.path("name").asString(),
        hash,
        node.path("overrides").asString(null),
        fields(node));
  }

  private static Map<String, Object> fields(JsonNode node) {
    var map = new TreeMap<String, Object>();
    node.properties().forEach(field -> map.put(field.getKey(), value(field.getValue())));
    return map;
  }

  private static Object value(JsonNode node) {
    if (node.isObject()) return fields(node);
    if (node.isArray())
      return java.util.stream.StreamSupport.stream(node.spliterator(), false)
          .map(ContentPack::value)
          .toList();
    if (node.isBoolean()) return node.asBoolean();
    if (node.isIntegralNumber()) return node.longValue();
    if (node.isNull()) return null;
    return node.asString();
  }
}
