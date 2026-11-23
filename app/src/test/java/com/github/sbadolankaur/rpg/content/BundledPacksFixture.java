package com.github.sbadolankaur.rpg.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.node.ObjectNode;

public final class BundledPacksFixture {
  private BundledPacksFixture() {}

  /** The Step 8 feature handlers use these minimal class definitions in integration tests. */
  public static ContentPack stepEight() throws IOException {
    var files = BundledContent.resources();
    var manifest = (ObjectNode) PackJson.MAPPER.readTree(files.get("manifest.json"));
    manifest.put("version", "99.8.0");
    files.put("manifest.json", PackJson.MAPPER.writeValueAsBytes(manifest));
    put(
        files,
        "classes/paladin.json",
        """
        {"kind":"class","key":"srd-5.1:paladin","name":"Test Paladin","text":"Paladin fixture",
         "hitDie":10,"savingThrows":["wis","cha"],"proficiencies":{},
         "levels":[{"level":1,"features":["srd-5.1:lay-on-hands"]},
                   {"level":2,"features":["srd-5.1:divine-smite"]},{"level":3},{"level":4}]}
        """);
    put(
        files,
        "classes/bard.json",
        """
        {"kind":"class","key":"srd-5.1:bard","name":"Test Bard","text":"Bard fixture",
         "hitDie":8,"savingThrows":["dex","cha"],"proficiencies":{},
         "levels":[{"level":1,"features":["srd-5.1:bardic-inspiration"]}]}
        """);
    put(
        files,
        "features/divine-smite.json",
        """
        {"kind":"feature","key":"srd-5.1:divine-smite","name":"Divine Smite","text":"Smite fixture",
         "effects":[{"type":"reaction_option","point":"AFTER_HIT"}]}
        """);
    put(
        files,
        "features/lay-on-hands.json",
        """
        {"kind":"feature","key":"srd-5.1:lay-on-hands","name":"Lay on Hands","text":"Healing fixture",
         "cost":{"action":"action","resource":"srd-5.1:lay-on-hands-pool"},
         "effects":[{"type":"heal","amount":"1d4"}]}
        """);
    put(
        files,
        "features/bardic-inspiration.json",
        """
        {"kind":"feature","key":"srd-5.1:bardic-inspiration","name":"Bardic Inspiration","text":"Inspiration fixture",
         "cost":{"action":"bonus_action","resource":"srd-5.1:bardic-inspiration-uses"},
         "effects":[{"type":"reaction_option","point":"AFTER_ATTACK_ROLL"}]}
        """);
    put(files, "resources/lay-on-hands-pool.json", resource("lay-on-hands-pool", "20"));
    put(files, "resources/bardic-inspiration-uses.json", resource("bardic-inspiration-uses", "1"));
    var monsters =
        PackInput.readFolder(Path.of("src/test/resources/content-fixtures/step7-combat"));
    var undead =
        (ObjectNode) PackJson.MAPPER.readTree(monsters.get("monsters/training-creature.json"));
    undead.put("key", "srd-5.1:undead-target").put("name", "Undead fixture").put("type", "undead");
    files.put("monsters/undead-target.json", PackJson.MAPPER.writeValueAsBytes(undead));
    return new PackLoader().load(files, true, List.of());
  }

  private static String resource(String key, String max) {
    return """
        {"kind":"resource","key":"srd-5.1:%s","name":"Test pool","max":"%s",
         "recharge":[{"on":"LONG_REST","restore":"all"}]}
        """
        .formatted(key, max);
  }

  private static void put(Map<String, byte[]> files, String path, String content) {
    files.put(path, content.getBytes(StandardCharsets.UTF_8));
  }

  public static ContentPack stepFive() throws IOException {
    return new PackLoader()
        .load(
            PackInput.readFolder(Path.of("src/test/resources/content-fixtures/srd-step5")),
            true,
            List.of());
  }
}
