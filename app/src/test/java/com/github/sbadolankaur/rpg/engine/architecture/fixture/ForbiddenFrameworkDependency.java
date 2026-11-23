package com.github.sbadolankaur.rpg.engine.architecture.fixture;

import org.springframework.context.ApplicationContext;

/** Test-only counterexample used to prove the pure-engine ArchUnit rule detects a violation. */
public final class ForbiddenFrameworkDependency {
  private final ApplicationContext applicationContext = null;
}
