package com.github.sbadolankaur.rpg.content;

import java.util.List;

public final class ContentInvalid extends IllegalArgumentException {
  private final List<ContentFinding> findings;

  ContentInvalid(List<ContentFinding> findings) {
    super("CONTENT_INVALID: " + findings.size() + " finding(s)");
    this.findings = List.copyOf(findings);
  }

  public List<ContentFinding> findings() {
    return findings;
  }

  static void rejectIfPresent(List<ContentFinding> findings) {
    if (!findings.isEmpty()) {
      throw new ContentInvalid(findings);
    }
  }
}
