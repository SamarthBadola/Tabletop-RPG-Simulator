package com.github.sbadolankaur.rpg.campaign.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FormTokenStore {
  private final JdbcClient jdbc;

  public FormTokenStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void deleteExpired(OffsetDateTime cutoff) {
    jdbc.sql(
            """
            DELETE FROM form_token
            WHERE (consumed_at IS NULL AND issued_at <= ?) OR consumed_at <= ?
            """)
        .params(cutoff, cutoff)
        .update();
  }

  public void insert(UUID token, byte[] session, String purpose, OffsetDateTime issuedAt) {
    jdbc.sql("INSERT INTO form_token (token, session_hash, purpose, issued_at) VALUES (?, ?, ?, ?)")
        .params(token, session, purpose, issuedAt)
        .update();
  }

  public Optional<Row> lock(UUID token, byte[] session, String purpose) {
    return jdbc.sql(
            """
        SELECT issued_at, consumed_at, request_hash, result_location
        FROM form_token WHERE token = ? AND session_hash = ? AND purpose = ?
        FOR UPDATE
        """)
        .params(token, session, purpose)
        .query(
            (row, index) ->
                new Row(
                    row.getObject("issued_at", OffsetDateTime.class),
                    row.getObject("consumed_at", OffsetDateTime.class),
                    row.getBytes("request_hash"),
                    row.getString("result_location")))
        .optional();
  }

  public void consume(UUID token, OffsetDateTime now, byte[] requestHash, String location) {
    jdbc.sql(
            """
        UPDATE form_token SET consumed_at = ?, request_hash = ?, result_location = ? WHERE token = ?
        """)
        .params(now, requestHash, location, token)
        .update();
  }

  public record Row(
      OffsetDateTime issuedAt, OffsetDateTime consumedAt, byte[] requestHash, String location) {}
}
