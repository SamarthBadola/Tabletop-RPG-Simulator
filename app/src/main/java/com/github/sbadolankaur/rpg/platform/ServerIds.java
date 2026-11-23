package com.github.sbadolankaur.rpg.platform;

import java.time.Instant;
import java.util.UUID;

/** Creates a UUID version 7 using the record's creation instant and secure random bits. */
public final class ServerIds {
  private ServerIds() {}

  public static UUID at(Instant instant) {
    UUID random = UUID.randomUUID();
    long time = instant.toEpochMilli() & 0xffff_ffff_ffffL;
    long versionAndRandom = 0x7000L | (random.getMostSignificantBits() & 0xfffL);
    return new UUID((time << 16) | versionAndRandom, random.getLeastSignificantBits());
  }
}
