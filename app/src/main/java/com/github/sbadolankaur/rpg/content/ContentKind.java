package com.github.sbadolankaur.rpg.content;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ContentKind {
  CLASS("class", "classes"),
  SUBCLASS("subclass", "subclasses"),
  SPECIES("species", "species"),
  SUBSPECIES("subspecies", "subspecies"),
  BACKGROUND("background", "backgrounds"),
  FEATURE("feature", "features"),
  FEAT("feat", "feats"),
  RESOURCE("resource", "resources"),
  RULE_TABLE("ruleTable", "tables"),
  CONDITION("condition", "conditions"),
  SPELL("spell", "spells"),
  ITEM("item", "items"),
  MONSTER("monster", "monsters"),
  HAZARD("hazard", "hazards"),
  POISON("poison", "poisons"),
  AFFLICTION("affliction", "afflictions"),
  AFFLICTION_TABLE("afflictionTable", "affliction-tables");

  private static final Map<String, ContentKind> BY_FOLDER =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(ContentKind::folder, Function.identity()));
  private static final Map<String, ContentKind> BY_NAME =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(ContentKind::id, Function.identity()));
  private final String id;
  private final String folder;

  ContentKind(String id, String folder) {
    this.id = id;
    this.folder = folder;
  }

  public String id() {
    return id;
  }

  public String folder() {
    return folder;
  }

  static ContentKind fromFolder(String folder) {
    return BY_FOLDER.get(folder);
  }

  static ContentKind fromId(String id) {
    return BY_NAME.get(id);
  }
}
