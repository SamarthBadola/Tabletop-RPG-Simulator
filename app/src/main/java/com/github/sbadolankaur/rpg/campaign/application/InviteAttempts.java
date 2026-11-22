package com.github.sbadolankaur.rpg.campaign.application;

import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class InviteAttempts {
  private static final long WINDOW = TimeUnit.MINUTES.toNanos(15);
  private static final int MAX_ENTRIES = 4096;
  private final LinkedHashMap<String, Window> addresses = new LinkedHashMap<>(256, .75f, true);

  public <T> T check(String address, Supplier<T> operation) {
    Window window = reserve(address);
    try {
      return operation.get();
    } catch (CampaignException failure) {
      if (failure.code() == CampaignException.Code.INVITE_INVALID) {
        synchronized (this) {
          window.failures++;
        }
      }
      throw failure;
    } finally {
      synchronized (this) {
        window.inFlight--;
      }
    }
  }

  private synchronized Window reserve(String address) {
    long now = System.nanoTime();
    Window window = addresses.get(address);
    if (window == null || now - window.start >= WINDOW) {
      if (window == null && addresses.size() == MAX_ENTRIES) {
        addresses.pollFirstEntry();
      }
      window = new Window(now);
      addresses.put(address, window);
    }
    if (window.failures + window.inFlight >= 10) {
      long remaining = Math.max(1, WINDOW - (now - window.start));
      throw new CampaignException(
          CampaignException.Code.RATE_LIMITED,
          Math.ceilDiv(remaining, TimeUnit.SECONDS.toNanos(1)));
    }
    window.inFlight++;
    return window;
  }

  private static final class Window {
    private final long start;
    private int failures;
    private int inFlight;

    private Window(long start) {
      this.start = start;
    }
  }
}
