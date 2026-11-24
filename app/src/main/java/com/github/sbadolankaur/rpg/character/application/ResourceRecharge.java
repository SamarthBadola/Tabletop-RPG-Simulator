package com.github.sbadolankaur.rpg.character.application;

import com.github.sbadolankaur.rpg.character.application.CharacterSheets.View;
import com.github.sbadolankaur.rpg.engine.content.ContentExpression;
import com.github.sbadolankaur.rpg.engine.content.ContentExpression.Type;
import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import com.github.sbadolankaur.rpg.engine.rest.RestResolver.Trigger;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Interprets a character resource definition for the game's recharge events. */
public final class ResourceRecharge {
  private static final String SRD = "srd-5.1:";

  private ResourceRecharge() {}

  public static Map<Trigger, ToIntFunction<RandomSource>> rules(
      ContentSet content, View sheet, String key) {
    var rules = new EnumMap<Trigger, ToIntFunction<RandomSource>>(Trigger.class);
    content
        .find("resource", key)
        .ifPresent(
            definition -> {
              if (definition.fields().get("recharge") instanceof List<?> entries) {
                for (Object entry : entries) {
                  if (entry instanceof Map<?, ?> rule) {
                    Trigger trigger = Trigger.valueOf((String) rule.get("on"));
                    rules.put(trigger, restore(sheet, rule.get("restore")));
                  }
                }
              }
            });
    if (key.equals(SRD + "bardic-inspiration-uses")
        && sheet.features().stream()
            .anyMatch(feature -> feature.key().equals(SRD + "font-of-inspiration")))
      rules.put(Trigger.SHORT_REST, random -> Integer.MAX_VALUE);
    return rules;
  }

  private static ToIntFunction<RandomSource> restore(View sheet, Object input) {
    if ("all".equals(input)) return random -> Integer.MAX_VALUE;
    var variables = new HashMap<String, Object>();
    variables.put("level", (long) sheet.level());
    variables.put("prof", (long) sheet.proficiency());
    variables.put("hp", (long) sheet.currentHp());
    variables.put("hp_max", (long) sheet.maximumHp());
    sheet
        .abilities()
        .forEach(
            (ability, score) -> {
              String key = ability.name().toLowerCase(java.util.Locale.ROOT);
              variables.put("mod:" + key, (long) score.modifier());
              variables.put("score:" + key, (long) score.value());
            });
    var types = new HashMap<String, Type>();
    variables.keySet().forEach(variable -> types.put(variable, Type.INT));
    var formula = ContentExpression.parse(input.toString(), types);
    return random ->
        (int) Math.clamp((Long) formula.evaluate(variables, random), 0, Integer.MAX_VALUE);
  }
}
