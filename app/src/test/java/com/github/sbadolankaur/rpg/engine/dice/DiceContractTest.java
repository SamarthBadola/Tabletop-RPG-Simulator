package com.github.sbadolankaur.rpg.engine.dice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DiceContractTest {
  @Test
  void limitAndErrorNamesMatchTheApprovedContract() {
    assertThat(DiceLimits.DEFAULT)
        .isEqualTo(new DiceLimits(2, 1000, 100, 200, 400, 20, 200, 1_000_000));
    assertThat(DiceErrorCode.values())
        .containsExactly(
            DiceErrorCode.DICE_EMPTY,
            DiceErrorCode.DICE_SYNTAX,
            DiceErrorCode.DICE_VALUE_OUT_OF_RANGE,
            DiceErrorCode.DICE_MODIFIER_CONFLICT,
            DiceErrorCode.DICE_LIMIT_EXCEEDED);
  }

  @Test
  void diceLimitsAcceptTheirInclusiveLowerAndEqualityBoundaries() {
    assertThat(new DiceLimits(1, 1, 1, 1, 1, 1, 1, 0))
        .isEqualTo(new DiceLimits(1, 1, 1, 1, 1, 1, 1, 0));
  }

  @Test
  void engineSourcesStayInsideThePureJdkBoundary() throws IOException {
    Path engine = Path.of("src/main/java/com/github/sbadolankaur/rpg/engine");
    assertThat(Files.isDirectory(engine)).isTrue();
    Set<String> forbidden =
        Set.of(
            "org.springframework.",
            "jakarta.persistence.",
            "java.net.http.",
            "java.io.File",
            "java.nio.file.",
            "Math.random(",
            "java.util.Random",
            "ThreadLocalRandom",
            "UUID.randomUUID(",
            "System.currentTimeMillis(",
            "Instant.now(",
            "LocalDateTime.now(",
            "LocalDate.now(",
            "new Date(",
            ".toLowerCase()",
            ".toUpperCase()");
    try (Stream<Path> paths = Files.walk(engine)) {
      List<Path> sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
      assertThat(sources).isNotEmpty();
      for (Path source : sources) {
        String text = Files.readString(source, StandardCharsets.UTF_8);
        for (String banned : forbidden)
          assertThat(text).as(source + " must not reference " + banned).doesNotContain(banned);
      }
    }
  }
}
