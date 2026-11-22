package com.github.sbadolankaur.rpg.campaign.application;

import com.github.sbadolankaur.rpg.campaign.persistence.FormTokenStore;
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

  public FormTokens(FormTokenStore store) {
    this.store = store;
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
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    var row =
        store
            .lock(token, digest(sessionId), purpose)
            .orElseThrow(() -> new CampaignException(CampaignException.Code.VALIDATION_FAILED));
    OffsetDateTime reference = row.consumedAt() == null ? row.issuedAt() : row.consumedAt();
    if (!now.isBefore(reference.plusHours(24))) {
      throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
    }

    byte[] inputHash = digest(canonicalInput);
    if (row.consumedAt() != null) {
      if (!MessageDigest.isEqual(row.requestHash(), inputHash)) {
        throw new CampaignException(CampaignException.Code.IDEMPOTENCY_KEY_CONFLICT);
      }
      return row.location();
    }

    String location = create.get();
    store.consume(token, now, inputHash, location);
    return location;
  }

  private static byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Missing SHA-256 implementation", exception);
    }
  }
}
