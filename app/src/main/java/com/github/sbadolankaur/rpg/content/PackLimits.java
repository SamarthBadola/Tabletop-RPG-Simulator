package com.github.sbadolankaur.rpg.content;

final class PackLimits {
  static final int PACK_BYTES = 100 * 1024 * 1024;
  static final int PACK_FILES = 10_000;
  static final int DEFINITION_BYTES = 256 * 1024;
  static final int TEXT_BYTES = 64 * 1024;
  static final int INLINE_TEXT_BYTES = 16 * 1024;
  static final int MANIFEST_BYTES = 64 * 1024;
  static final int PATH_LENGTH = 200;
  static final int JSON_DEPTH = 32;
  static final int ARRAY_ENTRIES = 2_000;
  static final int DEPENDENCIES = 16;
  static final int DEPENDENCY_DEPTH = 8;

  private PackLimits() {}
}
