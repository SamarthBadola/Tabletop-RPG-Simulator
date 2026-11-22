package com.github.sbadolankaur.rpg.content;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
class BundledContent implements ApplicationRunner {
  static final String PACK_KEY = "srd-5.1";
  static final String VERSION = "0.1.0";
  private static final String FOLDER = "content/" + PACK_KEY + "/";
  private final PackLoader loader;
  private final ContentPacks packs;

  BundledContent(PackLoader loader, ContentPacks packs) {
    this.loader = loader;
    this.packs = packs;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    ContentPack pack = loader.load(resources(), true, List.of());
    if (!pack.key().equals(PACK_KEY)
        || !pack.version().equals(VERSION)
        || pack.definitions().size() != 1) {
      throw new IllegalStateException(
          "The Step 5 bundle must contain SRD 5.1 version 0.1.0 and one definition");
    }
    packs.seed(pack);
  }

  static Map<String, byte[]> resources() throws IOException {
    var files = new TreeMap<String, byte[]>();
    var resolver = new PathMatchingResourcePatternResolver(BundledContent.class.getClassLoader());
    for (var resource : resolver.getResources("classpath*:" + FOLDER + "**/*")) {
      String uri = resource.getURL().toExternalForm();
      int start = uri.lastIndexOf(FOLDER);
      if (start < 0 || uri.endsWith("/")) continue;
      String path = uri.substring(start + FOLDER.length());
      try (var stream = resource.getInputStream()) {
        if (files.putIfAbsent(path, stream.readNBytes(PackInput.fileLimit(path) + 1)) != null) {
          throw new IllegalStateException("A bundled content path is present more than once");
        }
      }
    }
    return files;
  }
}
