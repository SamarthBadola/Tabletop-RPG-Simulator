package com.github.sbadolankaur.rpg.campaign.persistence;

import com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CommandStore {
  private final JdbcClient jdbc;

  public CommandStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Clock clock(UUID campaign, boolean lock) {
    String query = "SELECT game_seconds, version FROM game_clock WHERE campaign_id = ?";
    return jdbc.sql(lock ? query + " FOR UPDATE" : query)
        .param(campaign)
        .query((r, i) -> new Clock(r.getLong(1), r.getLong(2)))
        .single();
  }

  public void updateClock(UUID campaign, long seconds, long version) {
    jdbc.sql("UPDATE game_clock SET game_seconds = ?, version = ? WHERE campaign_id = ?")
        .params(seconds, version, campaign)
        .update();
  }

  public Optional<SavedAction> action(UUID campaign, UUID id) {
    return jdbc.sql(
            """
        SELECT actor_user_id, request_hash, result::text FROM campaign_action
        WHERE campaign_id = ? AND action_id = ?
        """)
        .params(campaign, id)
        .query((r, i) -> new SavedAction(r.getObject(1, UUID.class), r.getBytes(2), r.getString(3)))
        .optional();
  }

  public void saveAction(
      UUID campaign,
      UUID id,
      UUID actor,
      byte[] hash,
      String kind,
      String result,
      String settingsRead,
      OffsetDateTime now) {
    jdbc.sql(
            """
        INSERT INTO campaign_action (campaign_id, action_id, actor_user_id, request_hash, kind,
                                     result, settings_read, created_at)
        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
        """)
        .params(campaign, id, actor, hash, kind, result, settingsRead, now)
        .update();
  }

  public long nextEventSequence(UUID campaign) {
    return jdbc.sql("SELECT COALESCE(MAX(seq), 0) + 1 FROM campaign_event WHERE campaign_id = ?")
        .param(campaign)
        .query(Long.class)
        .single();
  }

  public void addEvent(
      UUID campaign,
      long sequence,
      UUID id,
      String type,
      long seconds,
      String audience,
      String payload,
      OffsetDateTime now) {
    jdbc.sql(
            """
        INSERT INTO campaign_event (campaign_id, seq, action_id, type, game_time_seconds, audience, payload, created_at)
        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
        """)
        .params(campaign, sequence, id, type, seconds, audience, payload, now)
        .update();
  }

  public List<EventSummary> recentEvents(UUID campaign, UUID actor, boolean dm) {
    return jdbc.sql(
            """
        SELECT type, game_time_seconds FROM campaign_event
        WHERE campaign_id = ? AND (? OR audience = 'ALL' OR (audience = 'PLAYERS' AND ? = ANY(player_ids)))
        ORDER BY seq DESC LIMIT 10
        """)
        .params(campaign, dm, actor)
        .query((r, i) -> new EventSummary(r.getString(1), r.getLong(2)))
        .list();
  }

  public record SavedAction(UUID actor, byte[] hash, String result) {}

  public record EventSummary(String type, long gameSeconds) {}
}
