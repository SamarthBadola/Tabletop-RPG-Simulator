package com.github.sbadolankaur.rpg.content;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class BundledPacksFixture {
  private BundledPacksFixture() {}

  public static ContentPack stepFive() throws IOException {
    return new PackLoader()
        .load(
            PackInput.readFolder(Path.of("src/test/resources/content-fixtures/srd-step5")),
            true,
            List.of());
  }
}
