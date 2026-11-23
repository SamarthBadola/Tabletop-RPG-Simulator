package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.dice.DiceExpression;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression.DiceGroup;
import com.github.sbadolankaur.rpg.engine.dice.DiceExpression.FixedValue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

final class PackValidation {
  private static final long[] XP = {
    0, 200, 450, 700, 1100, 1800, 2300, 2900, 3900, 5000, 5900, 7200, 8400, 10000, 11500, 13000,
    15000, 18000, 20000, 22000, 25000, 33000, 41000, 50000, 62000, 75000, 90000, 105000, 120000,
    135000, 155000
  };
  private static final Map<String, Integer> HIT_DICE =
      Map.of("tiny", 4, "small", 6, "medium", 8, "large", 10, "huge", 12, "gargantuan", 20);

  private PackValidation() {}

  static void text(
      PackLayout.Draft draft, Map<String, String> files, List<ContentFinding> findings) {
    PackLayout.walk(
        draft.body(),
        "",
        entry -> {
          JsonNode field = entry.node();
          if (field.isString() && forbidden(field.asString())) {
            add(
                findings,
                draft,
                entry.pointer(),
                "CS-SEC-006",
                "A string contains control or formatting characters");
          }
          if (field.isString() && inlineProse(entry.pointer())) {
            if (field.asString().getBytes(StandardCharsets.UTF_8).length
                > PackLimits.INLINE_TEXT_BYTES) {
              add(findings, draft, entry.pointer(), "CS-ENV-007", "Inline text exceeds 16 KiB");
            }
          }
          if (field.isObject()
              && field.has("file")
              && forbidden(files.getOrDefault(field.path("file").asString(""), ""))) {
            add(
                findings,
                draft,
                entry.pointer(),
                "CS-SEC-006",
                "Referenced prose contains control or formatting characters");
          }
        });
  }

  static boolean inlineProse(String pointer) {
    return pointer.matches(
            ".*/(text|source|higherLevel|equipment|languages|lairActions|regionalEffects|reactionTrigger)$")
        || pointer.matches(".*/notes/[0-9]+$");
  }

  private static boolean forbidden(String text) {
    return text.codePoints()
        .anyMatch(
            cp ->
                cp != '\n'
                    && cp != '\t'
                    && cp != '\r'
                    && (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT));
  }

  static void consistency(PackLayout.Draft draft, List<ContentFinding> findings) {
    JsonNode body = draft.body();
    switch (draft.kind()) {
      case SPELL, ITEM -> {
        int tier = body.path("tier").intValue();
        if ((body.path("effects").size() > 0) != (tier == 2)) {
          add(findings, draft, "/effects", "CS-VAL-006", "Exactly tier 2 has effects");
        }
        boolean hasRolls = body.path("rolls").size() > 0;
        if (draft.kind() == ContentKind.SPELL
            && ((tier == 1 && !hasRolls) || (tier == 0 && hasRolls))) {
          add(
              findings,
              draft,
              "/rolls",
              "CS-VAL-006",
              "Tier 1 needs rolls and tier 0 cannot have them");
        }
      }
      case CLASS -> {
        int at = 1;
        for (JsonNode level : body.path("levels")) {
          if (level.path("level").intValue() != at) {
            add(
                findings,
                draft,
                "/levels/" + (at - 1),
                "CS-ENV-002",
                "Class levels must start at 1 and have no gaps");
          }
          ++at;
        }
      }
      case MONSTER -> monster(draft, findings);
      default -> {}
    }
  }

  private static void monster(PackLayout.Draft draft, List<ContentFinding> findings) {
    JsonNode body = draft.body();
    JsonNode hp = body.path("hp");
    int expectedDie = HIT_DICE.get(body.path("size").asString());
    int con = Math.floorDiv(body.path("abilities").path("con").intValue() - 10, 2);
    try {
      long twiceMean = 0;
      long fixed = 0;
      long count = 0;
      boolean ordinary = true;
      for (var term : DiceExpression.parse(hp.path("dice").asString()).terms()) {
        switch (term) {
          case DiceGroup die -> {
            ordinary &=
                die.sign() == 1
                    && die.size() == expectedDie
                    && die.selection() == DiceExpression.Selection.NONE
                    && die.rerollAtOrBelow() == null
                    && die.floorAtLeast() == null;
            twiceMean += (long) die.sign() * die.count() * (die.size() + 1);
            count += die.count();
          }
          case FixedValue value -> fixed += (long) value.sign() * value.value();
        }
      }
      if (!ordinary || count < 1 || fixed != count * con) {
        add(
            findings,
            draft,
            "/hp/dice",
            "CS-VAL-004",
            "Hit dice must match size and the Constitution modifier");
      }
      if (Math.floorDiv(twiceMean + 2 * fixed, 2) != hp.path("average").longValue()) {
        add(
            findings,
            draft,
            "/hp/average",
            "CS-VAL-004",
            "Printed hit points do not match the rounded-down dice average");
      }
    } catch (IllegalArgumentException invalid) {
      add(
          findings,
          draft,
          "/hp/dice",
          "CS-VAL-004",
          "Expected fixed hit dice and a Constitution modifier");
    }
    JsonNode challenge = body.path("challenge");
    JsonNode rating = challenge.path("cr");
    long xp = challenge.path("xp").longValue();
    long expectedXp =
        rating.isIntegralNumber()
            ? XP[rating.intValue()]
            : switch (rating.asString()) {
              case "1/8" -> 25;
              case "1/4" -> 50;
              case "1/2" -> 100;
              default -> throw new IllegalStateException("Unvalidated challenge rating");
            };
    if (xp != expectedXp && !(rating.isIntegralNumber() && rating.intValue() == 0 && xp == 10)) {
      add(findings, draft, "/challenge/xp", "CS-VAL-004", "XP does not match the challenge rating");
    }
    int proficiency = rating.isIntegralNumber() ? Math.max(2, 1 + (rating.intValue() + 3) / 4) : 2;
    if (challenge.path("proficiencyBonus").longValue() != proficiency) {
      add(
          findings,
          draft,
          "/challenge/proficiencyBonus",
          "CS-VAL-004",
          "Proficiency does not match the challenge rating");
    }
    body.path("saves")
        .properties()
        .forEach(
            save -> {
              int modifier =
                  Math.floorDiv(body.path("abilities").path(save.getKey()).intValue() - 10, 2);
              long bonus = save.getValue().longValue();
              if (bonus != modifier
                  && bonus != modifier + proficiency
                  && bonus != modifier + 2 * proficiency) {
                add(
                    findings,
                    draft,
                    "/saves/" + save.getKey(),
                    "CS-VAL-004",
                    "The save bonus does not match the ability and proficiency");
              }
            });
  }

  private static void add(
      List<ContentFinding> list,
      PackLayout.Draft draft,
      String pointer,
      String rule,
      String message) {
    list.add(new ContentFinding(draft.file(), pointer, rule, message));
  }
}
