package com.github.sbadolankaur.rpg.architecture.fixture.features.player.web;

import com.github.sbadolankaur.rpg.architecture.fixture.features.dm.persistence.DmRepository;

/** Deliberately violates the feature web-to-persistence boundary for an ArchUnit test. */
public final class PlayerPage {
  private final DmRepository repository = null;
}
