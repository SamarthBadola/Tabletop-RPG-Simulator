package com.github.sbadolankaur.rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PackInputTest {
  @Test
  void canonicalJsonSortsFieldsAndNormalizesTextFiles() {
    var tree = PackJson.parse("{\"z\":-0,\"a\":\"a\\n\\u000f\",\"nested\":{\"b\":2,\"a\":1}}");
    assertThat(PackJson.canonical(tree))
        .isEqualTo("{\"a\":\"a\\n\\u000f\",\"nested\":{\"a\":1,\"b\":2},\"z\":0}");
    assertThat(PackJson.normalizeText("One \r\nTwo\t\r\n\r\n")).isEqualTo("One\nTwo");
    assertThat(PackJson.hash("abc"))
        .isEqualTo("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void doesNotAcceptDuplicateFieldsFractionsOrLossyIntegers() {
    for (String json :
        new String[] {
          "{\"a\":1,\"a\":2}",
          "{\"a\":1.0}",
          "{\"a\":9007199254740992}",
          "{\"a\":\"\\uD800\"}",
          "{} {}"
        }) {
      assertThatThrownBy(() -> PackJson.parse(json)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void reportsAllParseErrorsInDeterministicFileOrder() {
    var pack =
        Map.of(
            "b.json",
            new byte[] {(byte) 0xc3},
            "a.json",
            new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
    assertThatThrownBy(() -> PackInput.parse(pack))
        .isInstanceOfSatisfying(
            ContentInvalid.class,
            invalid ->
                assertThat(invalid.findings())
                    .extracting(ContentFinding::file)
                    .containsExactly("a.json", "b.json"));
  }
}
