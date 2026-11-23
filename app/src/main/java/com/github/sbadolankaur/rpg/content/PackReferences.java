package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.content.ContentSet.Key;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

final class PackReferences {
  private PackReferences() {}

  static List<ContentPack> dependencies(
      JsonNode manifest, List<ContentPack> available, List<ContentFinding> findings) {
    var selected = new ArrayList<ContentPack>();
    Map<String, ContentPack> byHash = new HashMap<>();
    available.forEach(pack -> byHash.put(pack.hash(), pack));
    JsonNode declared = manifest.path("dependencies");
    for (int i = 0; i < declared.size(); i++) {
      var dep = declared.get(i);
      String key = dep.path("id").asString();
      String minimum = dep.path("minVersion").asString();
      String pointer = "/dependencies/" + i;
      var match =
          available.stream()
              .filter(p -> p.key().equals(key))
              .filter(
                  p ->
                      compareVersion(p.version(), minimum, 1) == 0
                          && compareVersion(p.version(), minimum, 3) >= 0)
              .max(
                  Comparator.comparing(
                      ContentPack::version, (left, right) -> compareVersion(left, right, 3)));
      if (match.isEmpty()) {
        findings.add(
            new ContentFinding(
                "manifest.json",
                pointer,
                "CS-OVR-003",
                "No compatible loaded dependency for " + key));
      } else if (invalidChain(match.get(), byHash, Set.of(manifest.path("id").asString()), 1)) {
        findings.add(
            new ContentFinding(
                "manifest.json",
                pointer,
                "CS-OVR-001",
                "Dependency cycle, broken chain or depth limit exceeded"));
      } else {
        selected.add(match.get());
      }
    }
    return List.copyOf(selected);
  }

  private static boolean invalidChain(
      ContentPack node, Map<String, ContentPack> byHash, Set<String> ancestors, int depth) {
    if (node == null || depth > PackLimits.DEPENDENCY_DEPTH || ancestors.contains(node.key()))
      return true;
    var chain = new HashSet<>(ancestors);
    chain.add(node.key());
    return node.dependencyHashes().stream()
        .anyMatch(hash -> invalidChain(byHash.get(hash), byHash, chain, depth + 1));
  }

  private static int compareVersion(String left, String right, int components) {
    String[] a = left.split("\\.");
    String[] b = right.split("\\.");
    for (int i = 0; i < components; i++) {
      int compare = new BigInteger(a[i]).compareTo(new BigInteger(b[i]));
      if (compare != 0) return compare;
    }
    return 0;
  }

  static void check(
      PackLayout layout,
      List<ContentPack> dependencies,
      PackSchemas schemas,
      List<ContentFinding> findings,
      List<ContentFinding> warnings) {
    var own = new HashMap<Key, PackLayout.Draft>();
    Set<Key> external = new HashSet<>();
    Map<Key, List<Key>> grants = new HashMap<>();
    Set<Key> granted = new HashSet<>();
    layout.definitions().forEach(draft -> own.put(new Key(draft.kind().id(), draft.key()), draft));
    dependencies.forEach(
        pack ->
            pack.definitions()
                .forEach(definition -> external.add(new Key(definition.kind(), definition.key()))));
    for (var draft : layout.definitions()) {
      var original = new Key(draft.kind().id(), draft.key());
      var references = new ArrayList<>(schemas.annotated(draft, "x-reference"));
      PackLayout.walk(
          draft.body(),
          "",
          entry -> {
            JsonNode node = entry.node();
            if (node.isObject()
                && node.path("kind").asString("").equals("feature")
                && node.path("from").isArray()) {
              for (int i = 0; i < node.path("from").size(); i++) {
                references.add(
                    new PackSchemas.Annotation(
                        entry.pointer() + "/from/" + i, "feature", node.path("from").get(i)));
              }
            }
          });
      for (var field : references) {
        String kind = field.value();
        var target = new Key(kind, field.node().asString(""));
        if (!own.containsKey(target) && !external.contains(target)) {
          findings.add(
              new ContentFinding(
                  draft.file(),
                  field.pointer(),
                  "CS-KEY-004",
                  "Expected an existing " + kind + " key from this pack or a direct dependency"));
        }
        if (kind.equals("feature")
            && (field.pointer().contains("/features/") || field.pointer().contains("/from/"))) {
          granted.add(target);
          if (own.containsKey(target))
            grants.computeIfAbsent(original, ignored -> new ArrayList<>()).add(target);
        }
      }
      var override = draft.body().path("overrides");
      if (override.isString()
          && !external.contains(new Key(draft.kind().id(), override.asString()))) {
        findings.add(
            new ContentFinding(
                draft.file(),
                "/overrides",
                "CS-OVR-006",
                "Override must target the same kind in a direct dependency"));
      }
    }
    var completed = new HashSet<Key>();
    for (var draft : layout.definitions()) {
      var key = new Key(draft.kind().id(), draft.key());
      if (cyclic(key, grants, new HashSet<>(), completed)) {
        findings.add(new ContentFinding(draft.file(), "", "CS-VAL-005", "Grants form a cycle"));
        break;
      }
    }
    for (var draft : layout.definitions()) {
      if (draft.kind() == ContentKind.FEATURE
          && !granted.contains(new Key("feature", draft.key()))) {
        warnings.add(
            new ContentFinding(
                draft.file(), "", "CS-VAL-002", "No definition grants this feature"));
      }
    }
  }

  private static boolean cyclic(
      Key key, Map<Key, List<Key>> grants, Set<Key> visiting, Set<Key> complete) {
    if (complete.contains(key)) return false;
    if (!visiting.add(key)) return true;
    for (Key next : grants.getOrDefault(key, List.of())) {
      if (cyclic(next, grants, visiting, complete)) return true;
    }
    visiting.remove(key);
    complete.add(key);
    return false;
  }
}
