package com.github.sbadolankaur.rpg.campaign.persistence;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsStore {
  private final JdbcClient jdbc;

  public SettingsStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Decision> decisions(UUID campaign) {
    Map<String, Decision> values = new LinkedHashMap<>();
    jdbc.sql(
            """
        SELECT s.key, s.value::text, u.username, s.decided_at, s.source FROM campaign_setting s
        JOIN app_user u ON u.id = s.decided_by WHERE s.campaign_id = ?
        """)
        .param(campaign)
        .query(
            (r, i) ->
                new Decision(
                    r.getString(1),
                    r.getString(2),
                    r.getString(3),
                    r.getObject(4, OffsetDateTime.class),
                    r.getString(5)))
        .list()
        .forEach(row -> values.put(row.key(), row));
    return values;
  }

  public void save(
      UUID campaign,
      String key,
      String value,
      String source,
      UUID actor,
      OffsetDateTime now,
      long gameSeconds) {
    jdbc.sql(
            """
        INSERT INTO campaign_setting (campaign_id, key, value, source, decided_by, decided_at, decided_game_time)
        VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
        ON CONFLICT (campaign_id, key) DO UPDATE SET value = EXCLUDED.value, source = EXCLUDED.source,
          decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at,
          decided_game_time = EXCLUDED.decided_game_time
        """)
        .params(campaign, key, value, source, actor, now, gameSeconds)
        .update();
  }

  public void reset(UUID campaign, String key) {
    jdbc.sql("DELETE FROM campaign_setting WHERE campaign_id = ? AND key = ?")
        .params(campaign, key)
        .update();
  }

  public Setup setup(UUID campaign) {
    return jdbc.sql("SELECT setup_step, settings_mode FROM campaign WHERE id = ?")
        .param(campaign)
        .query((r, i) -> new Setup(r.getInt(1), r.getString(2)))
        .single();
  }

  public void mode(UUID campaign, String mode) {
    jdbc.sql("UPDATE campaign SET settings_mode = ? WHERE id = ?").params(mode, campaign).update();
  }

  public void setupStep(UUID campaign, int step) {
    jdbc.sql("UPDATE campaign SET setup_step = ? WHERE id = ?").params(step, campaign).update();
  }

  public record Setup(int step, String mode) {}

  public record Decision(
      String key, String valueJson, String decidedBy, OffsetDateTime decidedAt, String source) {}
}
