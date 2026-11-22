package com.github.sbadolankaur.rpg.campaign.persistence;

import com.github.sbadolankaur.rpg.campaign.application.CampaignView.SessionState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlayStore {
  private final JdbcClient jdbc;

  public PlayStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Session> latestSession(UUID campaign) {
    return jdbc.sql(
            """
        SELECT id, number, status, started_at FROM game_session
        WHERE campaign_id = ? ORDER BY number DESC LIMIT 1
        """)
        .param(campaign)
        .query(PlayStore::session)
        .optional();
  }

  public Optional<Session> session(UUID campaign, UUID id) {
    return jdbc.sql(
            """
        SELECT id, number, status, started_at FROM game_session WHERE campaign_id = ? AND id = ?
        """)
        .params(campaign, id)
        .query(PlayStore::session)
        .optional();
  }

  public void addSession(UUID campaign, UUID id, long number, OffsetDateTime time) {
    jdbc.sql(
            """
        INSERT INTO game_session (campaign_id, id, number, status, started_at) VALUES (?, ?, ?, 'ACTIVE', ?)
        """)
        .params(campaign, id, number, time)
        .update();
  }

  public void updateSession(UUID campaign, UUID id, SessionState state, OffsetDateTime ended) {
    jdbc.sql(
            """
        UPDATE game_session SET status = ?, ended_at = ? WHERE campaign_id = ? AND id = ?
        """)
        .params(state.name(), ended, campaign, id)
        .update();
  }

  public List<Scene> scenes(UUID campaign) {
    return jdbc.sql(
            """
        SELECT id, name, notes, ambient_light, active FROM scene
        WHERE campaign_id = ? ORDER BY name, created_at, id
        """)
        .param(campaign)
        .query(PlayStore::scene)
        .list();
  }

  public Optional<Scene> currentScene(UUID campaign, boolean dm) {
    return jdbc.sql(
            """
        SELECT id, name, CASE WHEN ? THEN notes ELSE NULL END AS notes, ambient_light, active
        FROM scene WHERE campaign_id = ? AND active
        """)
        .params(dm, campaign)
        .query(PlayStore::scene)
        .optional();
  }

  public void addScene(
      UUID campaign, UUID id, String name, String notes, Light light, OffsetDateTime now) {
    jdbc.sql(
            """
        INSERT INTO scene (id, campaign_id, name, notes, ambient_light, created_at) VALUES (?, ?, ?, ?, ?, ?)
        """)
        .params(id, campaign, name, notes, light.name(), now)
        .update();
  }

  public boolean editScene(UUID campaign, UUID id, String name, String notes, Light light) {
    return jdbc.sql(
                """
        UPDATE scene SET name = ?, notes = ?, ambient_light = ? WHERE campaign_id = ? AND id = ?
        """)
            .params(name, notes, light.name(), campaign, id)
            .update()
        == 1;
  }

  public void clearScene(UUID campaign) {
    jdbc.sql("UPDATE scene SET active = FALSE WHERE campaign_id = ? AND active")
        .param(campaign)
        .update();
  }

  public boolean activateScene(UUID campaign, UUID id, UUID session) {
    return jdbc.sql(
                """
        UPDATE scene SET active = TRUE, session_id = ? WHERE campaign_id = ? AND id = ?
        """)
            .params(session, campaign, id)
            .update()
        == 1;
  }

  private static Session session(ResultSet r, int i) throws SQLException {
    return new Session(
        r.getObject("id", UUID.class),
        r.getLong("number"),
        SessionState.valueOf(r.getString("status")),
        r.getObject("started_at", OffsetDateTime.class));
  }

  private static Scene scene(ResultSet r, int i) throws SQLException {
    return new Scene(
        r.getObject("id", UUID.class),
        r.getString("name"),
        r.getString("notes"),
        Light.valueOf(r.getString("ambient_light")),
        r.getBoolean("active"));
  }

  public enum Light {
    BRIGHT,
    DIM,
    DARK
  }

  public record Session(UUID id, long number, SessionState state, OffsetDateTime startedAt) {}

  public record Scene(UUID id, String name, String notes, Light light, boolean active) {}
}
