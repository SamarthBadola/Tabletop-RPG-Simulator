package com.github.sbadolankaur.rpg.campaign.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InviteStore {
  private final JdbcClient jdbc;

  public InviteStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public boolean insert(
      UUID id,
      UUID campaign,
      byte[] hash,
      UUID actor,
      OffsetDateTime now,
      OffsetDateTime expires,
      int uses) {
    return jdbc.sql(
                """
        INSERT INTO invite (id, campaign_id, code_hash, created_by, created_at, expires_at, max_uses, uses_left)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (code_hash) DO NOTHING
        """)
            .params(id, campaign, hash, actor, now, expires, uses, uses)
            .update()
        == 1;
  }

  public Optional<LockedInvite> lock(byte[] hash) {
    return jdbc.sql(
            """
        SELECT id, campaign_id, code_hash, expires_at, uses_left, revoked_at
        FROM invite WHERE code_hash = ? FOR UPDATE
        """)
        .param(hash)
        .query(
            (row, index) ->
                new LockedInvite(
                    row.getObject("id", UUID.class), row.getObject("campaign_id", UUID.class),
                    row.getBytes("code_hash"), row.getObject("expires_at", OffsetDateTime.class),
                    row.getInt("uses_left"), row.getObject("revoked_at", OffsetDateTime.class)))
        .optional();
  }

  public boolean addPlayer(UUID campaign, UUID user, OffsetDateTime now) {
    return jdbc.sql(
                """
        INSERT INTO campaign_member (campaign_id, user_id, role, joined_at)
        VALUES (?, ?, 'PLAYER', ?) ON CONFLICT (campaign_id, user_id) DO NOTHING
        """)
            .params(campaign, user, now)
            .update()
        == 1;
  }

  public void consume(UUID id) {
    jdbc.sql("UPDATE invite SET uses_left = uses_left - 1 WHERE id = ?").param(id).update();
  }

  public int revoke(UUID campaign, UUID id, OffsetDateTime now) {
    return jdbc.sql(
            """
        UPDATE invite SET revoked_at = ? WHERE campaign_id = ? AND id = ? AND revoked_at IS NULL
        """)
        .params(now, campaign, id)
        .update();
  }

  public List<InviteRow> list(UUID campaign) {
    return jdbc.sql(
            """
        SELECT id, created_at, expires_at, uses_left, revoked_at FROM invite
        WHERE campaign_id = ? ORDER BY created_at DESC, id
        """)
        .param(campaign)
        .query(
            (r, i) ->
                new InviteRow(
                    r.getObject("id", UUID.class),
                    r.getObject("created_at", OffsetDateTime.class),
                    r.getObject("expires_at", OffsetDateTime.class),
                    r.getInt("uses_left"),
                    r.getObject("revoked_at", OffsetDateTime.class)))
        .list();
  }

  public List<MemberRow> members(UUID campaign) {
    return jdbc.sql(
            """
        SELECT u.username, m.role, m.joined_at FROM campaign_member m
        JOIN app_user u ON u.id = m.user_id WHERE m.campaign_id = ? ORDER BY u.username
        """)
        .param(campaign)
        .query(
            (r, i) ->
                new MemberRow(r.getString(1), r.getString(2), r.getObject(3, OffsetDateTime.class)))
        .list();
  }

  public record LockedInvite(
      UUID id,
      UUID campaign,
      byte[] hash,
      OffsetDateTime expires,
      int uses,
      OffsetDateTime revokedAt) {}

  public record InviteRow(
      UUID id,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt,
      int usesLeft,
      OffsetDateTime revokedAt) {}

  public record MemberRow(String username, String role, OffsetDateTime joinedAt) {}
}
