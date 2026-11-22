package com.github.sbadolankaur.rpg.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Canonical JSON for the integer-only RFC 8785 subset used by content packs. */
final class PackJson {
  static final JsonMapper MAPPER =
      JsonMapper.builder(
              JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(PackLimits.JSON_DEPTH)
                          .build())
                  .build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();
  private static final long MAX_INTEGER = (1L << 53) - 1;

  private PackJson() {}

  static JsonNode parse(String data) {
    JsonNode tree;
    try {
      tree = MAPPER.readTree(data);
    } catch (tools.jackson.core.JacksonException invalid) {
      throw new IllegalArgumentException("Invalid JSON", invalid);
    }
    strictlyCheck(tree);
    return tree;
  }

  private static void strictlyCheck(JsonNode node) {
    if (node.isMissingNode()
        || (node.isNumber()
            && (!node.isIntegralNumber()
                || !node.canConvertToLong()
                || node.longValue() < -MAX_INTEGER
                || node.longValue() > MAX_INTEGER))) {
      throw new IllegalArgumentException("Expected JSON with integers in the interoperable range");
    }
    if (node.isArray() && node.size() > PackLimits.ARRAY_ENTRIES) {
      throw new IllegalArgumentException(
          "JSON array exceeds " + PackLimits.ARRAY_ENTRIES + " entries");
    }
    if (node.isString()) {
      requireUnicode(node.asString());
    }
    if (node.isObject()) {
      node.propertyNames().forEach(PackJson::requireUnicode);
    }
    node.forEach(PackJson::strictlyCheck);
  }

  private static void requireUnicode(String value) {
    if (value.codePoints().anyMatch(cp -> cp >= 0xd800 && cp <= 0xdfff)) {
      throw new IllegalArgumentException("Unpaired Unicode surrogate");
    }
  }

  static String canonical(JsonNode value) {
    var result = new StringBuilder();
    append(value, result);
    return result.toString();
  }

  private static void append(JsonNode node, StringBuilder out) {
    if (node.isObject()) {
      out.append('{');
      boolean first = true;
      var fields =
          node.properties().stream()
              .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
              .toList();
      for (var field : fields) {
        if (!first) out.append(',');
        first = false;
        string(field.getKey(), out);
        out.append(':');
        append(field.getValue(), out);
      }
      out.append('}');
    } else if (node.isArray()) {
      out.append('[');
      for (int i = 0; i < node.size(); i++) {
        if (i > 0) out.append(',');
        append(node.get(i), out);
      }
      out.append(']');
    } else if (node.isString()) {
      string(node.asString(), out);
    } else if (node.isIntegralNumber()) {
      out.append(node.longValue());
    } else if (node.isNull() || node.isBoolean()) {
      out.append(node);
    } else {
      throw new IllegalArgumentException("Unsupported canonical JSON value");
    }
  }

  private static void string(String value, StringBuilder out) {
    requireUnicode(value);
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '"' -> out.append("\\\"");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\r' -> out.append("\\r");
        case '\n' -> out.append("\\n");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 32) {
            out.append("\\u00").append(HexFormat.of().toHexDigits((byte) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  static String normalizeText(String input) {
    return input
        .lines()
        .map(String::stripTrailing)
        .collect(java.util.stream.Collectors.joining("\n"))
        .stripTrailing();
  }

  static String hash(String canonical) {
    try {
      return "sha256:"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
