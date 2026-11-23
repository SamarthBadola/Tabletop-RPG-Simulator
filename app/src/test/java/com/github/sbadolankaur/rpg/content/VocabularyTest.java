package com.github.sbadolankaur.rpg.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VocabularyTest {
  @Test
  void checkedInVocabularyMatchesTheJavaRegistry() throws Exception {
    var path = Path.of("src/main/resources/content-schema/vocabulary.json");
    var generated = ContentVocabulary.export();
    if (Boolean.getBoolean("content.writeVocabulary")) Files.writeString(path, generated);
    assertThat(Files.readString(path).replace("\r\n", "\n")).isEqualTo(generated);
  }
}
