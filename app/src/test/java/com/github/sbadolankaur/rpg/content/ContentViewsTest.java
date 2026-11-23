package com.github.sbadolankaur.rpg.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.content.Definition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class ContentViewsTest {
  private final ContentViews views = new ContentViews();

  @Test
  void visiblePoisonShowsOnlyItsNameAndAnyAuthoredText() throws Exception {
    var files =
        new HashMap<>(
            PackInput.readFolder(Path.of("src/test/resources/content-fixtures/sample-v1")));
    String path = "poisons/poison.json";
    byte[] original = files.get(path);
    Definition noText = poison(files);
    assertThat(views.forPlayer(noText, false)).isEmpty();
    assertThat(views.forPlayer(noText, true))
        .containsExactlyEntriesOf(Map.of("name", "Fixture poison"));

    var withText = (ObjectNode) PackJson.parse(new String(original, StandardCharsets.UTF_8));
    withText.put("text", "You feel unwell.");
    files.put(path, PackJson.canonical(withText).getBytes(StandardCharsets.UTF_8));
    Definition authoredText = poison(files);
    assertThat(views.forPlayer(authoredText, false)).isEmpty();
    assertThat(views.forPlayer(authoredText, true))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("name", "Fixture poison", "text", "You feel unwell."));
  }

  @Test
  void hidesMonsterIdentityUntilRevealedAndNeverProjectsStatBlockNumbers() throws Exception {
    var pack =
        new PackLoader()
            .loadFolder(Path.of("src/test/resources/content-fixtures/sample-v1"), List.of());
    var monster =
        pack.definitions().stream()
            .filter(definition -> definition.kind().equals("monster"))
            .findFirst()
            .orElseThrow();
    assertThat(views.forPlayer(monster, false)).isEmpty();
    assertThat(views.forPlayer(monster, true)).containsOnlyKeys("name", "size", "type");
  }

  @Test
  void authoredSchemaPropertiesHaveExplicitVisibility() throws Exception {
    try (var files = Files.list(Path.of("src/main/resources/content-schema"))) {
      for (var file : files.filter(path -> path.toString().endsWith(".schema.json")).toList()) {
        var schema = PackJson.MAPPER.readTree(file.toFile());
        PackLayout.walk(
            schema,
            "",
            node -> {
              if (!node.node().path("type").asString("").equals("object")) return;
              node.node()
                  .path("properties")
                  .properties()
                  .forEach(
                      field ->
                          assertThat(field.getValue().path("x-visibility").asString(""))
                              .as(file + node.pointer() + "/" + field.getKey())
                              .isIn("public", "dm", "revealed"));
            });
      }
    }
  }

  @Test
  void rendersOnlyTheTextSubsetAndEscapesHtmlImagesAndLinks() {
    String html =
        ContentText.html(
            """
        A **bold** and *plain* paragraph.

        - <script>bad</script>
        - ![Moon](script.svg)
        - [Link](external)

        Name | Size
        --- | ---
        Oak | Large
        """);
    assertThat(html)
        .contains(
            "<strong>bold</strong>",
            "<em>plain</em>",
            "<ul>",
            "&lt;script&gt;bad&lt;/script&gt;",
            "![Moon](script.svg)",
            "[Link](external)",
            "<table>")
        .doesNotContain("<script>", "<a ", "<img ");
  }

  private static Definition poison(Map<String, byte[]> files) {
    return new PackLoader()
        .load(files, false, List.of()).definitions().stream()
            .filter(definition -> definition.kind().equals("poison"))
            .findFirst()
            .orElseThrow();
  }
}
