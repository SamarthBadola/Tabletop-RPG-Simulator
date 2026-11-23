package com.github.sbadolankaur.rpg.content;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

public final class BundledPacksFixture {
  private BundledPacksFixture() {}

  /** The bundled class definitions with a single test-only undead opponent. */
  public static ContentPack stepEight() throws IOException {
    var files = BundledContent.resources();
    var manifest = (ObjectNode) PackJson.MAPPER.readTree(files.get("manifest.json"));
    manifest.put("version", "99.8.0");
    files.put("manifest.json", PackJson.MAPPER.writeValueAsBytes(manifest));
    var monsters =
        PackInput.readFolder(Path.of("src/test/resources/content-fixtures/step7-combat"));
    var undead =
        (ObjectNode) PackJson.MAPPER.readTree(monsters.get("monsters/training-creature.json"));
    undead.put("key", "srd-5.1:undead-target").put("name", "Undead fixture").put("type", "undead");
    undead.putArray("conditionImmunities").add("srd-5.1:charmed");
    // Only referenced by an immune creature. The fixture never applies the condition.
    files.put(
        "conditions/charmed.json",
        """
        {"kind":"condition","key":"srd-5.1:charmed","name":"Charm reference",
         "text":"A condition reference for the immunity test.",
         "parts":[{"type":"advantage_source","roll":"attack","mode":"disadvantage","direction":"holder"}]}
        """
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    files.put("monsters/undead-target.json", PackJson.MAPPER.writeValueAsBytes(undead));
    return new PackLoader().load(files, true, List.of());
  }

  public static ContentPack piercingLongsword() throws IOException {
    var files = BundledContent.resources();
    var manifest = (ObjectNode) PackJson.MAPPER.readTree(files.get("manifest.json"));
    manifest.put("version", "99.8.1");
    files.put("manifest.json", PackJson.MAPPER.writeValueAsBytes(manifest));
    String path = "items/longsword.json";
    var sword = (ObjectNode) PackJson.MAPPER.readTree(files.get(path));
    ((ObjectNode) sword.path("weaponData")).put("damageType", "piercing");
    files.put(path, PackJson.MAPPER.writeValueAsBytes(sword));
    return new PackLoader().load(files, true, List.of());
  }

  public static ContentPack stepFive() throws IOException {
    return new PackLoader()
        .load(
            PackInput.readFolder(Path.of("src/test/resources/content-fixtures/srd-step5")),
            true,
            List.of());
  }
}
