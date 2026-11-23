package com.github.sbadolankaur.rpg.campaign.persistence;

import com.github.sbadolankaur.rpg.engine.reaction.ReactionReplay.Prompt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ReactionStore {
  private static final String COLUMNS =
      "id, campaign_id, encounter_id, opened_by_action_id, offers, continuation, version, created_at";
  private final JdbcClient jdbc;
  private final JsonMapper json;

  public ReactionStore(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public Optional<Window> forEncounter(UUID campaign, UUID encounter) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM pending_reaction WHERE campaign_id = ? AND encounter_id = ?")
        .params(campaign, encounter)
        .query(this::window)
        .optional();
  }

  public Optional<Window> findEncounterWindow(UUID campaign, UUID id) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM pending_reaction WHERE campaign_id = ? AND id = ? AND encounter_id IS NOT NULL")
        .params(campaign, id)
        .query(this::window)
        .optional();
  }

  public boolean campaignHasWindow(UUID campaign) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM pending_reaction WHERE campaign_id = ?)")
        .param(campaign)
        .query(Boolean.class)
        .single();
  }

  public boolean protectsCharacter(UUID campaign, UUID character) {
    return jdbc.sql(
            """
        SELECT EXISTS (
          SELECT 1 FROM pending_reaction r JOIN combatant c ON c.encounter_id = r.encounter_id
          WHERE r.campaign_id = ? AND c.character_id = ?
        )
        """)
        .params(campaign, character)
        .query(Boolean.class)
        .single();
  }

  public void open(Window window) {
    jdbc.sql(
            """
        INSERT INTO pending_reaction
          (id, campaign_id, encounter_id, opened_by_action_id, point, depth, offers,
           continuation, version, created_at)
        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
        """)
        .params(
            window.id(),
            window.campaign(),
            window.encounter(),
            window.openedBy(),
            window.prompt().point().name(),
            window.prompt().frames().size(),
            json.writeValueAsString(window.prompt()),
            window.continuation().toString(),
            window.version(),
            window.createdAt())
        .update();
  }

  public void advance(Window previous, Window next) {
    requireUpdated(
        jdbc.sql(
                """
        UPDATE pending_reaction
        SET point = ?, depth = ?, offers = CAST(? AS jsonb), continuation = CAST(? AS jsonb), version = ?
        WHERE id = ? AND version = ?
        """)
            .params(
                next.prompt().point().name(),
                next.prompt().frames().size(),
                json.writeValueAsString(next.prompt()),
                next.continuation().toString(),
                next.version(),
                previous.id(),
                previous.version())
            .update());
  }

  public void close(Window window) {
    requireUpdated(
        jdbc.sql("DELETE FROM pending_reaction WHERE id = ? AND version = ?")
            .params(window.id(), window.version())
            .update());
  }

  private static void requireUpdated(int rows) {
    if (rows != 1)
      throw new IllegalStateException("Reaction window changed while its owner was locked");
  }

  private Window window(ResultSet row, int index) throws SQLException {
    return new Window(
        row.getObject(1, UUID.class),
        row.getObject(2, UUID.class),
        row.getObject(3, UUID.class),
        row.getObject(4, UUID.class),
        json.readValue(row.getString(5), Prompt.class),
        json.readTree(row.getString(6)),
        row.getLong(7),
        row.getObject(8, OffsetDateTime.class));
  }

  public record Window(
      UUID id,
      UUID campaign,
      UUID encounter,
      UUID openedBy,
      Prompt prompt,
      JsonNode continuation,
      long version,
      OffsetDateTime createdAt) {}
}
