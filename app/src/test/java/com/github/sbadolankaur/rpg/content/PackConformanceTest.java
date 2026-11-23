package com.github.sbadolankaur.rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class PackConformanceTest {
  private final PackLoader loader = new PackLoader();

  @Test
  void allKindFixturesLoadAndEveryKindRejectsAnUnknownField() throws Exception {
    var files = PackInput.readFolder(Path.of("src/test/resources/content-fixtures/sample-v1"));
    var pack = loader.load(files, false, List.of());
    assertThat(pack.definitions()).hasSize(ContentKind.values().length);
    assertThat(pack.warnings()).isEmpty();
    for (var file : files.entrySet()) {
      if (file.getKey().equals("manifest.json")) continue;
      var changed = new HashMap<>(files);
      var tree = (ObjectNode) PackJson.parse(new String(file.getValue(), StandardCharsets.UTF_8));
      tree.put("unrecognizedField", true);
      changed.put(file.getKey(), PackJson.canonical(tree).getBytes(StandardCharsets.UTF_8));
      assertThatThrownBy(() -> loader.load(changed, false, List.of()))
          .isInstanceOfSatisfying(
              ContentInvalid.class,
              invalid ->
                  assertThat(invalid.findings())
                      .anySatisfy(
                          finding -> {
                            assertThat(finding.file()).isEqualTo(file.getKey());
                            assertThat(finding.rule()).isEqualTo("CS-ENV-001");
                          }));
    }
  }

  @Test
  void poisonAfflictionsRequireASaveAndLeavePoisonDefinitionsIntact() throws Exception {
    var files = PackInput.readFolder(Path.of("src/test/resources/content-fixtures/sample-v1"));
    String path = "afflictions/affliction.json";
    var changed = new HashMap<>(files);
    var affliction = (ObjectNode) PackJson.MAPPER.readTree(files.get(path));
    affliction.put("afflictionKind", "POISON");
    changed.put(path, PackJson.MAPPER.writeValueAsBytes(affliction));
    var pack = loader.load(changed, false, List.of());
    assertThat(
            pack.definitions().stream()
                .filter(definition -> definition.kind().equals("affliction"))
                .findFirst()
                .orElseThrow()
                .fields())
        .containsEntry("afflictionKind", "POISON");
    assertThat(
            pack.definitions().stream()
                .filter(definition -> definition.kind().equals("poison"))
                .findFirst()
                .orElseThrow()
                .fields())
        .containsEntry("delivery", "contact");
    affliction.remove("save");
    changed.put(path, PackJson.MAPPER.writeValueAsBytes(affliction));
    expectRule(changed, "CS-ENV-001");
  }

  @Test
  void rejectsBadReferencesAndFormulasBeforeHashing() throws Exception {
    var files =
        new HashMap<>(
            PackInput.readFolder(Path.of("src/test/resources/content-fixtures/sample-v1")));
    String file = "resources/resource.json";
    byte[] original = files.get(file);
    var invalid = (ObjectNode) PackJson.parse(new String(original, StandardCharsets.UTF_8));
    invalid.put("max", "10 / (level - 1)");
    files.put(file, PackJson.canonical(invalid).getBytes(StandardCharsets.UTF_8));
    expectRule(files, "CS-EXP-010");
    files.put(file, original);
    file = "subclasses/subclass.json";
    invalid = (ObjectNode) PackJson.parse(new String(files.get(file), StandardCharsets.UTF_8));
    invalid.put("class", "sample-pack:feature");
    files.put(file, PackJson.canonical(invalid).getBytes(StandardCharsets.UTF_8));
    expectRule(files, "CS-KEY-004");
  }

  @Test
  void aLaterOverrideWinsAndTheOriginalRemainsInIndependentContentSets() throws Exception {
    var files = PackInput.readFolder(Path.of("src/test/resources/content-fixtures/sample-v1"));
    ContentPack original = loader.load(files, false, List.of());
    ContentPack first = override("early-pack", original);
    ContentPack second = override("later-pack", original);
    ContentSet resolved = ContentResolution.resolve(List.of(original, first, second));
    assertThat(resolved.find("condition", "sample-pack:condition").orElseThrow().key())
        .isEqualTo("later-pack:other");
    assertThat(resolved.warnings()).anyMatch(warning -> warning.startsWith("Shadowed:"));
    assertThat(
            ContentResolution.resolve(List.of(original, first))
                .find("condition", "sample-pack:condition")
                .orElseThrow()
                .key())
        .isEqualTo("early-pack:other");
    assertThat(
            ContentResolution.resolve(List.of(original))
                .find("condition", "sample-pack:condition")
                .orElseThrow()
                .key())
        .isEqualTo("sample-pack:condition");
    assertThatThrownBy(() -> ContentResolution.resolve(List.of(second, original)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsRetiredTriggersAndFeatureGrantCycles() throws Exception {
    var files =
        new HashMap<>(
            PackInput.readFolder(Path.of("src/test/resources/content-fixtures/sample-v1")));
    String path = "features/feature.json";
    var tree = (ObjectNode) PackJson.parse(new String(files.get(path), StandardCharsets.UTF_8));
    tree.putObject("trigger").put("point", "ON_ATTACK_ROLL");
    files.put(path, PackJson.canonical(tree).getBytes(StandardCharsets.UTF_8));
    expectRule(files, "CS-VOC-004");
    tree.remove("trigger");
    var choice = tree.putArray("choices").addObject();
    choice
        .put("kind", "feature")
        .put("id", "loop")
        .put("count", 1)
        .putArray("from")
        .add("sample-pack:feature");
    files.put(path, PackJson.canonical(tree).getBytes(StandardCharsets.UTF_8));
    expectRule(files, "CS-VAL-005");
  }

  private ContentPack override(String key, ContentPack original) {
    var manifest =
        """
      {"schema":1,"id":"%s","name":"Override","version":"1.0.0","ruleset":"srd-5.1","vocabulary":1,
       "license":{"id":"custom","url":"https://example.invalid/license","attributionRequired":false},
       "dependencies":[{"id":"sample-pack","minVersion":"1.0.0"}]}
      """
            .formatted(key);
    var body =
        """
      {"kind":"condition","key":"%s:other","name":"Alternate","overrides":"sample-pack:condition",
       "text":"Fixture replacement.","parts":[{"type":"restriction","movement":"crawl_or_stand"}]}
      """
            .formatted(key);
    return loader.load(
        Map.of(
            "manifest.json",
            manifest.getBytes(StandardCharsets.UTF_8),
            "conditions/other.json",
            body.getBytes(StandardCharsets.UTF_8)),
        false,
        List.of(original));
  }

  private void expectRule(Map<String, byte[]> files, String rule) {
    assertThatThrownBy(() -> loader.load(files, false, List.of()))
        .isInstanceOfSatisfying(
            ContentInvalid.class,
            invalid ->
                assertThat(invalid.findings()).extracting(ContentFinding::rule).contains(rule));
  }
}
