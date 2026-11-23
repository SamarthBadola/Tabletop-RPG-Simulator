package com.github.sbadolankaur.rpg.engine.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The immutable definition index held by a campaign action. */
public record ContentSet(
    List<String> packHashes, Map<Key, Definition> index, List<String> warnings) {
  public ContentSet {
    packHashes = List.copyOf(packHashes);
    index = Map.copyOf(index);
    warnings = List.copyOf(warnings);
  }

  public Optional<Definition> find(String kind, String key) {
    return Optional.ofNullable(index.get(new Key(kind, key)));
  }

  public record Key(String kind, String key) {}
}
