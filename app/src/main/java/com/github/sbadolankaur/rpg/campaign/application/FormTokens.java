package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.persistence.FormTokenStore;
import com.github.sbadolankaur.rpg.platform.InstanceMac;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormTokens {
  private final FormTokenStore store;
  private final InstanceMac mac;

  public FormTokens(FormTokenStore store, InstanceMac mac) {
    this.store = store;
    this.mac = mac;
  }

  @Transactional
  public UUID issue(String sessionId, String purpose) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID token = ServerIds.at(now.toInstant());
    store.deleteExpired(now.minusHours(24));
    store.insert(token, digest(sessionId), purpose, now);
    return token;
  }

  @Transactional
  public String createOnce(
      String sessionId,
      String purpose,
      UUID token,
      String canonicalInput,
      Supplier<String> create) {
    return execute(
            sessionId, purpose, token, canonicalInput, () -> new Created<>(create.get(), null))
        .location();
  }

  @Transactional
  public <T> Submission<T> execute(
      String sessionId,
      String purpose,
      UUID token,
      String canonicalInput,
      Supplier<Created<T>> create) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    var row =
        store
            .lock(token, digest(sessionId), purpose)
            .orElseThrow(() -> new CampaignException(CampaignException.Code.VALIDATION_FAILED));
    OffsetDateTime reference = row.consumedAt() == null ? row.issuedAt() : row.consumedAt();
    if (!now.isBefore(reference.plusHours(24))) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }

    byte[] inputHash = mac.sign("form:" + token, canonicalInput);
    if (row.consumedAt() != null) {
      if (!MessageDigest.isEqual(row.requestHash(), inputHash)) {
        throw new CampaignException(CampaignException.Code.IDEMPOTENCY_KEY_CONFLICT);
      }
      return new Submission<>(row.location(), null, true);
    }

    Created<T> created = create.get();
    store.consume(token, now, inputHash, created.location());
    return new Submission<>(created.location(), created.firstResponseValue(), false);
  }

  public record Created<T>(String location, T firstResponseValue) {}

  public record Submission<T>(String location, T firstResponseValue, boolean replay) {}

  private static byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Missing SHA-256 implementation", exception);
    }
  }
}
