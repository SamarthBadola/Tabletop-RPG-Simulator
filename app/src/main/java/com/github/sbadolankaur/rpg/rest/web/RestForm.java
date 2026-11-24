package com.github.sbadolankaur.rpg.rest.web;

import static com.github.sbadolankaur.rpg.rest.application.RestInput.invalid;

import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Kind;
import com.github.sbadolankaur.rpg.rest.application.RestInput;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.util.MultiValueMap;

final class RestForm {
  private static final Pattern CHOICE =
      Pattern.compile("([A-Za-z]+)\\[([^\\[\\]]+)\\](?:\\[([^\\[\\]]+)\\])?");
  private final MultiValueMap<String, String> fields;

  RestForm(MultiValueMap<String, String> fields) {
    this.fields = fields;
  }

  RestInput input() {
    var counts = new HashMap<UUID, Integer>();
    var targets = new HashMap<UUID, Integer>();
    var food = new HashMap<UUID, Boolean>();
    var dice = new HashMap<UUID, Map<Integer, Integer>>();
    var slots = new HashMap<UUID, Map<Integer, Integer>>();
    for (String name : fields.keySet()) {
      var match = CHOICE.matcher(name);
      if (!match.matches()) {
        String field = name.split("\\[", 2)[0];
        if (List.of("spendCount", "spendUntil", "ateAndDrank", "recoverHitDice", "arcaneRecovery")
            .contains(field)) throw invalid(field, "INCORRECT");
        continue;
      }
      String field = match.group(1);
      if (!List.of("spendCount", "spendUntil", "ateAndDrank", "recoverHitDice", "arcaneRecovery")
          .contains(field)) continue;
      String value = single(name, field);
      if (value == null || value.isBlank()) continue;
      UUID id = parse(field, match.group(2), UUID::fromString);
      boolean nested = field.equals("recoverHitDice") || field.equals("arcaneRecovery");
      if (nested != (match.group(3) != null)) throw invalid(field, "INCORRECT");
      switch (field) {
        case "spendCount" -> put(counts, id, parse(field, value, Integer::valueOf), field);
        case "spendUntil" -> put(targets, id, parse(field, value, Integer::valueOf), field);
        case "ateAndDrank" -> put(food, id, yesNo(field, value), field);
        case "recoverHitDice" -> nested(dice, id, match.group(3), value, field);
        case "arcaneRecovery" -> nested(slots, id, match.group(3), value, field);
        default -> throw new IllegalStateException("Unknown rest choice");
      }
    }
    String kind = single("kind", "kind");
    String duration = single("durationMinutes", "durationMinutes");
    if (kind == null) throw invalid("kind", "REQUIRED");
    if (duration == null) throw invalid("durationMinutes", "REQUIRED");
    return new RestInput(
        kind(kind),
        parse("durationMinutes", duration, Integer::valueOf),
        ids("participantIds"),
        counts,
        targets,
        flag("interrupted"),
        food,
        dice,
        slots,
        ids("songOfRestPerformerIds"),
        ids("songOfRestExcludedIds"));
  }

  static Kind kind(String value) {
    return parse("kind", value, raw -> Kind.valueOf(raw.toUpperCase(Locale.ROOT)));
  }

  private List<UUID> ids(String field) {
    return fields.getOrDefault(field, List.of()).stream()
        .map(raw -> parse(field, raw, UUID::fromString))
        .toList();
  }

  private static void nested(
      Map<UUID, Map<Integer, Integer>> output, UUID id, String key, String value, String field) {
    put(
        output.computeIfAbsent(id, ignored -> new HashMap<>()),
        parse(field, key, Integer::valueOf),
        parse(field, value, Integer::valueOf),
        field);
  }

  private static <K, V> void put(Map<K, V> map, K key, V value, String field) {
    if (map.putIfAbsent(key, value) != null) throw invalid(field, "INCORRECT");
  }

  private static boolean yesNo(String field, String value) {
    return switch (value) {
      case "yes" -> true;
      case "no" -> false;
      default -> throw invalid(field, "UNKNOWN_VALUE");
    };
  }

  boolean flag(String field) {
    String value = single(field, field);
    if (value == null) return false;
    return switch (value) {
      case "true", "on", "1" -> true;
      case "false", "off", "0" -> false;
      default -> throw invalid(field, "UNKNOWN_VALUE");
    };
  }

  String single(String key, String field) {
    var values = fields.getOrDefault(key, List.of());
    if (values.size() > 1) throw invalid(field, "INCORRECT");
    return values.isEmpty() ? null : values.getFirst();
  }

  static <T> T parse(String field, String value, Function<String, T> parser) {
    if (value == null) throw invalid(field, "REQUIRED");
    try {
      return parser.apply(value);
    } catch (IllegalArgumentException error) {
      throw invalid(field, "UNKNOWN_VALUE");
    }
  }
}
