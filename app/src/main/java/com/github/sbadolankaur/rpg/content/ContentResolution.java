package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.content.ContentSet.Key;
import com.github.sbadolankaur.rpg.engine.content.Definition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class ContentResolution {
  private ContentResolution() {}

  static ContentSet resolve(List<ContentPack> ordered) {
    var definitions = new LinkedHashMap<Key, Definition>();
    var overriding = new LinkedHashMap<Key, Definition>();
    var names = new LinkedHashMap<String, Definition>();
    var seenPacks = new HashSet<String>();
    var hashes = new HashSet<String>();
    var warnings = new ArrayList<String>();

    for (var pack : ordered) {
      if (!seenPacks.add(pack.key()) || !hashes.containsAll(pack.dependencyHashes())) {
        throw new IllegalArgumentException(
            "Pack versions must be unique and follow their dependencies");
      }
      hashes.add(pack.hash());
      for (Definition definition : pack.definitions()) {
        var key = new Key(definition.kind(), definition.key());
        definitions.put(key, definition);
        var first =
            names.putIfAbsent(
                definition.kind() + ":" + definition.name().toLowerCase(Locale.ROOT), definition);
        if (first != null && !first.packKey().equals(definition.packKey())) {
          warnings.add("Same name: " + first.key() + ", " + definition.key());
        }
        if (definition.overrides() != null) {
          var target = new Key(definition.kind(), definition.overrides());
          var shadowed = overriding.put(target, definition);
          if (shadowed != null)
            warnings.add("Shadowed: " + shadowed.key() + " by " + definition.key());
        }
      }
    }

    var resolved = new LinkedHashMap<Key, Definition>();
    definitions.forEach(
        (key, original) -> {
          Definition current = original;
          for (Definition following = overriding.get(key);
              following != null;
              following = overriding.get(new Key(following.kind(), following.key()))) {
            current = following;
          }
          resolved.put(key, current);
        });
    return new ContentSet(ordered.stream().map(ContentPack::hash).toList(), resolved, warnings);
  }
}
