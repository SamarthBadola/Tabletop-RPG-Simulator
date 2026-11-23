package com.github.sbadolankaur.rpg.content;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

record PackInput(Map<String, JsonNode> json, Map<String, String> text) {
  static Map<String, byte[]> readFolder(Path folder) throws IOException {
    if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
      throw new ContentInvalid(
          List.of(new ContentFinding("", "", "CS-PKG-001", "Expected a pack folder")));
    }
    var files = new TreeMap<String, byte[]>();
    var findings = new ArrayList<ContentFinding>();
    long bytes = 0;
    int count = 0;
    try (var paths = Files.walk(folder)) {
      var items = paths.iterator();
      while (items.hasNext()) {
        Path path = items.next();
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
        String name =
            folder.relativize(path).toString().replace(folder.getFileSystem().getSeparator(), "/");
        if (ignored(name)) continue;
        if (++count > PackLimits.PACK_FILES) {
          findings.add(limit("", "pack file count"));
          break;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
          findings.add(
              new ContentFinding(name, "", "CS-PKG-001", "Only regular files can be loaded"));
          continue;
        }
        int max = fileLimit(name);
        if (Files.size(path) > max) {
          findings.add(limit(name, "file bytes"));
          continue;
        }
        try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
          byte[] data = stream.readNBytes(max + 1);
          if (data.length > max) {
            findings.add(limit(name, "file bytes"));
            continue;
          }
          bytes += data.length;
          if (bytes > PackLimits.PACK_BYTES) {
            findings.add(limit("", "total pack bytes"));
            break;
          }
          files.put(name, data);
        }
      }
    }
    ContentInvalid.rejectIfPresent(findings);
    return files;
  }

  static PackInput parse(Map<String, byte[]> files) {
    var findings = new ArrayList<ContentFinding>();
    var sorted = new TreeMap<>(files);
    long total = 0;
    int count = 0;
    for (var entry : sorted.entrySet()) {
      if (ignored(entry.getKey())) continue;
      ++count;
      total += entry.getValue().length;
      if (entry.getKey().length() > PackLimits.PATH_LENGTH)
        findings.add(limit(entry.getKey().substring(0, 80), "path length"));
      if (entry.getValue().length > fileLimit(entry.getKey()))
        findings.add(limit(entry.getKey(), "file bytes"));
    }
    if (count > PackLimits.PACK_FILES) findings.add(limit("", "pack file count"));
    if (total > PackLimits.PACK_BYTES) findings.add(limit("", "total pack bytes"));
    ContentInvalid.rejectIfPresent(findings);
    var json = new TreeMap<String, JsonNode>();
    var text = new TreeMap<String, String>();
    for (var entry : sorted.entrySet()) {
      String file = entry.getKey();
      if (ignored(file)) continue;
      try {
        String value =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(entry.getValue()))
                .toString();
        if (value.startsWith("\ufeff")) {
          findings.add(
              new ContentFinding(file, "", "CS-PKG-013", "UTF-8 byte order marks are not allowed"));
        } else if (file.endsWith(".json")) {
          json.put(file, PackJson.parse(value));
        } else {
          text.put(file, value);
        }
      } catch (java.nio.charset.CharacterCodingException invalid) {
        findings.add(new ContentFinding(file, "", "CS-PKG-013", "Invalid UTF-8"));
      } catch (JacksonException | IllegalArgumentException invalid) {
        findings.add(
            new ContentFinding(
                file,
                "",
                "CS-KEY-007",
                "Expected strict JSON with no duplicate fields, bounded depth and interoperable integers"));
      }
    }
    ContentInvalid.rejectIfPresent(findings);
    return new PackInput(Collections.unmodifiableMap(json), Collections.unmodifiableMap(text));
  }

  static int fileLimit(String name) {
    if (name.equals("manifest.json")) return PackLimits.MANIFEST_BYTES;
    return name.endsWith(".json") ? PackLimits.DEFINITION_BYTES : PackLimits.TEXT_BYTES;
  }

  private static ContentFinding limit(String file, String limit) {
    return new ContentFinding(file, "", "CS-SEC-001", "Exceeded the limit for " + limit);
  }

  private static boolean ignored(String name) {
    return name.startsWith("__MACOSX/")
        || name.equals(".DS_Store")
        || name.endsWith("/.DS_Store")
        || name.equals("Thumbs.db")
        || name.endsWith("/Thumbs.db");
  }
}
