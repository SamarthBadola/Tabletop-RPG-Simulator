package com.github.sbadolankaur.rpg.architecture.fixture.ability;

/** Deliberately bypasses AbilityScores to prove that the ArchUnit rule catches direct storage. */
public final class DirectAbilityStorage {
  public int strength;
}
