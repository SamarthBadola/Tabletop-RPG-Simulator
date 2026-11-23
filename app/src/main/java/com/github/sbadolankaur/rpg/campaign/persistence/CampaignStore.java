package com.github.sbadolankaur.rpg.campaign.persistence;

import com.github.sbadolankaur.rpg.campaign.application.CampaignView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CampaignStore {
  private static final String MEMBER_VIEW =
      """
      SELECT c.id, c.name, m.role, COALESCE((
          SELECT s.status FROM game_session s
          WHERE s.campaign_id = c.id ORDER BY s.number DESC LIMIT 1
      ), 'NONE') AS session_state
      FROM campaign_member m JOIN campaign c ON c.id = m.campaign_id
      WHERE m.user_id = :user
      """;

  private final JdbcClient jdbc;

  public CampaignStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void create(UUID id, String name, UUID creator, OffsetDateTime createdAt) {
    jdbc.sql("INSERT INTO campaign (id, name, created_at) VALUES (?, ?, ?)")
        .params(id, name, createdAt)
        .update();
    jdbc.sql(
            "INSERT INTO campaign_member (campaign_id, user_id, role, joined_at) VALUES (?, ?, 'DM', ?)")
        .params(id, creator, createdAt)
        .update();
    jdbc.sql("INSERT INTO game_clock (campaign_id) VALUES (?)").param(id).update();
  }

  public List<CampaignView> listFor(UUID user) {
    return jdbc.sql(MEMBER_VIEW + " ORDER BY c.name, c.created_at, c.id")
        .param("user", user)
        .query(CampaignStore::view)
        .list();
  }

  public Optional<CampaignView> findFor(UUID id, UUID user) {
    return jdbc.sql(MEMBER_VIEW + " AND c.id = :campaign")
        .param("user", user)
        .param("campaign", id)
        .query(CampaignStore::view)
        .optional();
  }

  private static CampaignView view(ResultSet row, int index) throws SQLException {
    return new CampaignView(
        row.getObject("id", UUID.class),
        row.getString("name"),
        CampaignView.Role.valueOf(row.getString("role")),
        CampaignView.SessionState.valueOf(row.getString("session_state")));
  }
}
