package com.github.sbadolankaur.rpg.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

record PackLayout(JsonNode manifest, String notice, List<Draft> definitions) {
  static final String MANIFEST = "manifest.json";
  static final Pattern LOCAL_KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");
  private static final Set<String> META =
      Set.of("pack", "packVersion", "ruleset", "license", "attribution", "hash");

  static PackLayout check(PackInput input, PackSchemas schemas, boolean seeded) {
    var findings = new ArrayList<ContentFinding>();
    JsonNode manifest = input.json().get(MANIFEST);
    if (manifest == null) {
      findings.add(new ContentFinding(MANIFEST, "", "CS-PKG-009", "One manifest is required"));
    } else {
      schemas.checkManifest(manifest, findings);
    }
    var json = new TreeMap<>(input.json());
    json.remove(MANIFEST);
    if (json.isEmpty()) {
      findings.add(
          new ContentFinding(MANIFEST, "", "CS-PKG-011", "At least one definition is required"));
    }
    String id = manifest == null ? "" : manifest.path("id").asString("");
    String notice = input.text().getOrDefault("NOTICE", "");
    if (manifest != null && manifest.isObject()) {
      checkManifest(manifest, seeded, notice, findings);
    }
    var definitions = new ArrayList<Draft>();
    for (var entry : json.entrySet()) {
      String file = entry.getKey();
      String[] path = file.split("/", -1);
      ContentKind kind = path.length == 2 ? ContentKind.fromFolder(path[0]) : null;
      if (kind == null || !validPath(file)) {
        findings.add(
            new ContentFinding(
                file, "", "CS-PKG-011", "Expected a definition in a known kind folder"));
        continue;
      }
      if (!(entry.getValue() instanceof ObjectNode body)) {
        findings.add(new ContentFinding(file, "", "CS-PKG-011", "A definition must be one object"));
        continue;
      }
      String local = path[1].substring(0, path[1].length() - 5);
      String key = body.path("key").asString("");
      if (!LOCAL_KEY.matcher(local).matches()
          || local.length() > 64
          || !key.equals(id + ":" + local)) {
        findings.add(
            new ContentFinding(
                file,
                "/key",
                "CS-PKG-010",
                "The key must match the manifest namespace and file name"));
      }
      if (!body.path("kind").asString("").equals(kind.id())) {
        findings.add(
            new ContentFinding(file, "/kind", "CS-ENV-001", "The kind must match its folder"));
      }
      body.propertyNames().stream()
          .filter(META::contains)
          .sorted()
          .forEach(
              name ->
                  findings.add(
                      new ContentFinding(
                          file, "/" + name, "CS-STO-001", "The loader supplies this field")));
      definitions.add(new Draft(file, kind, key, body));
    }

    var referencedText = new HashSet<String>();
    for (Draft draft : definitions) {
      String expected = "text/" + draft.file().substring(0, draft.file().length() - 5) + ".md";
      walk(
          draft.body(),
          "",
          entry -> {
            if (entry.node().isObject() && entry.node().has("file")) {
              String path = entry.node().path("file").asString("");
              if (!path.equals(expected) || !input.text().containsKey(path)) {
                findings.add(
                    new ContentFinding(
                        draft.file(),
                        entry.pointer(),
                        "CS-PKG-014",
                        "A text reference must point to this definition's text file"));
              } else {
                referencedText.add(path);
              }
            }
          });
    }
    input
        .text()
        .keySet()
        .forEach(
            file -> {
              if (!file.equals("NOTICE") && (!validPath(file) || !referencedText.contains(file))) {
                findings.add(
                    new ContentFinding(
                        file,
                        "",
                        file.startsWith("text/") ? "CS-PKG-014" : "CS-PKG-011",
                        "Unexpected or unreferenced file"));
              }
            });
    ContentInvalid.rejectIfPresent(findings);
    return new PackLayout(manifest, PackJson.normalizeText(notice), List.copyOf(definitions));
  }

  private static void checkManifest(
      JsonNode manifest, boolean seeded, String notice, List<ContentFinding> findings) {
    String id = manifest.path("id").asString("");
    if (id.startsWith("srd-") && !seeded) {
      findings.add(
          new ContentFinding(
              MANIFEST, "/id", "CS-PKG-009", "Only bundled packs can use the srd- prefix"));
    }
    if (manifest.path("schema").isIntegralNumber() && manifest.path("schema").longValue() != 1) {
      findings.add(
          new ContentFinding(
              MANIFEST,
              "/schema",
              "CS-PKG-018",
              "Unsupported schema version. The engine reads version 1"));
    }
    JsonNode vocabulary = manifest.path("vocabulary");
    if (vocabulary.isIntegralNumber() && vocabulary.longValue() > ContentVocabulary.VERSION) {
      findings.add(
          new ContentFinding(
              MANIFEST,
              "/vocabulary",
              "CS-PKG-018",
              "The pack needs vocabulary "
                  + vocabulary.longValue()
                  + "; the engine has "
                  + ContentVocabulary.VERSION));
    }
    if (manifest.path("license").path("attributionRequired").asBoolean(false)
        && (manifest.path("attribution").asString("").isBlank() || notice.isBlank())) {
      findings.add(
          new ContentFinding(
              MANIFEST,
              "/attribution",
              "CS-PKG-017",
              "Attribution and a non-empty NOTICE are required"));
    }
    var seen = new HashSet<String>();
    JsonNode dependencies = manifest.path("dependencies");
    if (dependencies.isArray()) {
      for (int i = 0; i < dependencies.size(); i++) {
        String key = dependencies.get(i).path("id").asString("");
        if (key.equals(id) || !seen.add(key)) {
          findings.add(
              new ContentFinding(
                  MANIFEST,
                  "/dependencies/" + i + "/id",
                  "CS-OVR-001",
                  "A dependency cannot be self or repeated"));
        }
      }
    }
  }

  private static boolean validPath(String file) {
    return PATH.matcher(file).matches()
        && !List.of(file.split("/")).contains("..")
        && !List.of(file.split("/")).contains(".");
  }

  static void walk(JsonNode node, String pointer, Consumer<LocatedNode> consumer) {
    consumer.accept(new LocatedNode(pointer, node));
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        walk(
            entry.getValue(),
            pointer + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"),
            consumer);
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) walk(node.get(i), pointer + "/" + i, consumer);
    }
  }

  static JsonNode inlineText(JsonNode node, Map<String, String> text) {
    return inlineText(node, text, "");
  }

  private static JsonNode inlineText(JsonNode node, Map<String, String> text, String pointer) {
    if (node instanceof ObjectNode object) {
      if (object.size() == 1 && object.path("file").isString()) {
        return StringNode.valueOf(PackJson.normalizeText(text.get(object.path("file").asString())));
      }
      var mapped = PackJson.MAPPER.createObjectNode();
      for (var property : object.properties()) {
        mapped.set(
            property.getKey(),
            inlineText(property.getValue(), text, pointer + "/" + property.getKey()));
      }
      return mapped;
    }
    if (node.isArray()) {
      var mapped = PackJson.MAPPER.createArrayNode();
      for (int i = 0; i < node.size(); i++) {
        mapped.add(inlineText(node.get(i), text, pointer + "/" + i));
      }
      return mapped;
    }
    return node.isString() && PackValidation.inlineProse(pointer)
        ? StringNode.valueOf(PackJson.normalizeText(node.asString()))
        : node;
  }

  record Draft(String file, ContentKind kind, String key, ObjectNode body) {}

  record LocatedNode(String pointer, JsonNode node) {}
}
