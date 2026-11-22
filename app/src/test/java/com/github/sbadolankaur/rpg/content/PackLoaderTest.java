package com.github.sbadolankaur.rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PackLoaderTest {
  private final PackLoader loader = new PackLoader();

  @Test
  void ordinaryLoaderRejectsAnEmptyPackAndLoadsExactlyOneSourceVerifiedDefinition()
      throws Exception {
    var files = PackInput.readFolder(Path.of("src/main/resources/content/srd-5.1"));
    assertThatThrownBy(
            () ->
                loader.load(
                    Map.of(
                        "manifest.json", files.get("manifest.json"), "NOTICE", files.get("NOTICE")),
                    true,
                    List.of()))
        .isInstanceOfSatisfying(
            ContentInvalid.class,
            invalid ->
                assertThat(invalid.findings())
                    .extracting(ContentFinding::rule)
                    .contains("CS-PKG-011"));

    var seed = loader.load(files, true, List.of());
    assertThat(seed.version()).isEqualTo("0.1.0");
    assertThat(seed.definitions())
        .singleElement()
        .satisfies(
            def -> {
              assertThat(def.key()).isEqualTo("srd-5.1:prone");
              String text = (String) def.fields().get("text");
              var source =
                  PackJson.MAPPER.readTree(
                      Path.of("src/main/resources/content/srd-5.1-source-index.json").toFile());
              String line =
                  text.replaceAll("(?m)^- ", "")
                      .replace('\u2019', '\'')
                      .replaceAll("\\s+", " ")
                      .strip();
              assertThat(PackJson.hash(line))
                  .isEqualTo("sha256:" + source.path("spans").get(0).path("sha256").asString());
            });
    assertThatThrownBy(() -> loader.load(files, false, List.of()))
        .isInstanceOfSatisfying(
            ContentInvalid.class,
            invalid ->
                assertThat(invalid.findings())
                    .extracting(ContentFinding::rule)
                    .contains("CS-PKG-009"));
  }

  @Test
  void reportsEveryUnknownConditionPartAndRejectsAuthorSuppliedPackMetadata() throws Exception {
    var files = new HashMap<>(PackInput.readFolder(Path.of("src/main/resources/content/srd-5.1")));
    String path = "conditions/prone.json";
    var original = new String(files.get(path), StandardCharsets.UTF_8);
    files.put(
        path, original.replace("advantage_source", "unknown").getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> loader.load(files, true, List.of()))
        .isInstanceOfSatisfying(
            ContentInvalid.class,
            invalid ->
                assertThat(invalid.findings())
                    .hasSize(3)
                    .allSatisfy(finding -> assertThat(finding.rule()).isEqualTo("CS-VOC-004")));
    files.put(
        path,
        original
            .replace("\"kind\"", "\"license\":\"custom\",\"kind\"")
            .getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> loader.load(files, true, List.of()))
        .isInstanceOfSatisfying(
            ContentInvalid.class,
            invalid ->
                assertThat(invalid.findings())
                    .extracting(ContentFinding::rule)
                    .contains("CS-STO-001"));
  }

  @Test
  void normalizesPackFormattingButRejectsChangedContentUnderTheSameVersion() throws Exception {
    var files = new HashMap<>(PackInput.readFolder(Path.of("src/main/resources/content/srd-5.1")));
    var pack = loader.load(files, true, List.of());
    String path = "text/conditions/prone.md";
    String original = new String(files.get(path), StandardCharsets.UTF_8);
    files.put(path, original.replace("\n", "  \r\n").getBytes(StandardCharsets.UTF_8));
    assertThat(loader.load(files, true, List.of(pack)).hash()).isEqualTo(pack.hash());
    files.put(path, (original + "\nChanged content.\n").getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> loader.load(files, true, List.of(pack)))
        .isInstanceOfSatisfying(
            ContentInvalid.class,
            invalid ->
                assertThat(invalid.findings())
                    .extracting(ContentFinding::rule)
                    .contains("CS-PKG-019"));
  }
}
