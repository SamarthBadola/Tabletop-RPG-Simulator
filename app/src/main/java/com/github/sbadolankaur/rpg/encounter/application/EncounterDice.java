package com.github.sbadolankaur.rpg.encounter.application;

import com.github.sbadolankaur.rpg.engine.dice.RandomSource;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class EncounterDice implements RandomSource {
  private final SecureRandom random = new SecureRandom();

  @Override
  public int nextInt(int bound) {
    return random.nextInt(bound);
  }
}
