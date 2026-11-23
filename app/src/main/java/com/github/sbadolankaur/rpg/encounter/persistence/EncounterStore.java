package com.github.sbadolankaur.rpg.encounter.persistence;

import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Phase;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Table;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Tactics;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.encounter.application.Encounters.Recap;
import com.github.sbadolankaur.rpg.engine.combat.TurnBudget;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class EncounterStore {
  private static final String TABLE_COLUMNS =
      "id,campaign_id,name,state,round,acting_id,budget::text,version";
  private static final String EVENT_COLUMNS =
      """
      id,seq,game_time_seconds,round,type,audience,payload::text,
      (SELECT CASE WHEN undo.type IN ('CorrectionUndone','ActionUndone') THEN 'Undone' ELSE 'Corrected' END
       FROM combat_event undo WHERE undo.encounter_id = event.encounter_id AND undo.audience = 'DM'
       AND undo.type IN ('CorrectionApplied','CorrectionUndone','ActionUndone')
       AND undo.payload->>'entryId' = event.id::text ORDER BY undo.seq DESC LIMIT 1)
      """;
  private final JdbcClient jdbc;
  private final JsonMapper json;

  EncounterStore(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<Table> list(UUID campaign) {
    return jdbc.sql(
            "SELECT " + TABLE_COLUMNS + " FROM encounter WHERE campaign_id = ? ORDER BY id DESC")
        .param(campaign)
        .query(this::table)
        .list();
  }

  public Optional<Table> find(UUID campaign, UUID encounter, boolean lock) {
    return jdbc.sql(
            "SELECT "
                + TABLE_COLUMNS
                + " FROM encounter WHERE campaign_id = ? AND id = ?"
                + (lock ? " FOR UPDATE" : ""))
        .params(campaign, encounter)
        .query(this::table)
        .optional();
  }

  public boolean hasRunning(UUID campaign, UUID except) {
    return jdbc.sql(
            """
        SELECT EXISTS (SELECT 1 FROM encounter
          WHERE campaign_id = ? AND id <> ? AND state IN ('INITIATIVE', 'ACTIVE'))
        """)
        .params(campaign, except)
        .query(Boolean.class)
        .single();
  }

  public void create(UUID campaign, UUID id, String name) {
    jdbc.sql(
            """
        INSERT INTO encounter (id,campaign_id,name,state,round,budget,version)
        VALUES (?,?,?,'SETUP',0,CAST(? AS jsonb),0)
        """)
        .params(id, campaign, name, json.writeValueAsString(TurnBudget.start()))
        .update();
  }

  public void update(Table table, long version) {
    jdbc.sql(
            """
        UPDATE encounter SET state=?, round=?, acting_id=?, budget=CAST(? AS jsonb), version=?
        WHERE id=?
        """)
        .params(
            table.phase().name(),
            table.round(),
            table.acting(),
            json.writeValueAsString(table.budget()),
            version,
            table.id())
        .update();
  }

  public List<Entry> entries(UUID encounter) {
    return jdbc.sql(
            """
        SELECT id, character_id, monster_key, ordinal, initiative::integer, tie_order::integer, tactics::text, npc_vitals::text
        FROM combatant WHERE encounter_id = ? ORDER BY ordinal
        """)
        .param(encounter)
        .query(
            (row, index) -> {
              String vitals = row.getString(8);
              return new Entry(
                  row.getObject(1, UUID.class),
                  row.getObject(2, UUID.class),
                  row.getString(3),
                  row.getInt(4),
                  row.getObject(5, Integer.class),
                  row.getObject(6, Integer.class),
                  json.readValue(row.getString(7), Tactics.class),
                  vitals == null ? null : json.readValue(vitals, Vitals.class));
            })
        .list();
  }

  public void add(UUID encounter, Entry entry) {
    jdbc.sql(
            """
        INSERT INTO combatant (id,encounter_id,character_id,monster_key,ordinal,initiative,tie_order,tactics,npc_vitals)
        VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb))
        """)
        .params(
            entry.id(),
            encounter,
            entry.character(),
            entry.monster(),
            entry.ordinal(),
            entry.initiative(),
            entry.tie(),
            json.writeValueAsString(entry.tactics()),
            entry.npcVitals() == null ? null : json.writeValueAsString(entry.npcVitals()))
        .update();
  }

  public void update(Entry entry) {
    jdbc.sql(
            """
        UPDATE combatant SET initiative=?, tie_order=?, tactics=CAST(? AS jsonb), npc_vitals=CAST(? AS jsonb)
        WHERE id=?
        """)
        .params(
            entry.initiative(),
            entry.tie(),
            json.writeValueAsString(entry.tactics()),
            entry.npcVitals() == null ? null : json.writeValueAsString(entry.npcVitals()),
            entry.id())
        .update();
  }

  public void remove(UUID encounter, UUID id) {
    jdbc.sql("DELETE FROM combatant WHERE encounter_id = ? AND id = ?")
        .params(encounter, id)
        .update();
  }

  public Optional<Replay> action(UUID encounter, UUID action) {
    return jdbc.sql(
            """
        SELECT actor_id,request_hash,result::text,version FROM encounter_action
        WHERE encounter_id = ? AND action_id = ?
        """)
        .params(encounter, action)
        .query(
            (row, index) ->
                new Replay(
                    row.getObject(1, UUID.class),
                    row.getBytes(2),
                    json.readTree(row.getString(3)),
                    row.getLong(4)))
        .optional();
  }

  public Optional<Replay> reactionAction(UUID campaign, UUID window, UUID action) {
    return jdbc.sql(
            """
        SELECT a.actor_id, a.request_hash, a.result::text, a.version
        FROM encounter_action a JOIN encounter e ON a.encounter_id = e.id
        WHERE e.campaign_id = ? AND a.action_id = ?
          AND a.result->>'reactionWindowId' = ?
        """)
        .params(campaign, action, window.toString())
        .query(
            (row, index) ->
                new Replay(
                    row.getObject(1, UUID.class),
                    row.getBytes(2),
                    json.readTree(row.getString(3)),
                    row.getLong(4)))
        .optional();
  }

  public void action(
      UUID encounter,
      UUID action,
      UUID actor,
      byte[] hash,
      JsonNode result,
      JsonNode settings,
      long version) {
    jdbc.sql(
            """
        INSERT INTO encounter_action (encounter_id,action_id,actor_id,request_hash,result,version,settings_read)
        VALUES (?,?,?,?,CAST(? AS jsonb),?,CAST(? AS jsonb))
        """)
        .params(encounter, action, actor, hash, result.toString(), version, settings.toString())
        .update();
  }

  public UUID event(
      UUID encounter,
      UUID action,
      long seconds,
      int round,
      String type,
      String audience,
      UUID recipient,
      JsonNode payload) {
    UUID id = ServerIds.at(Instant.now());
    jdbc.sql(
            """
        INSERT INTO combat_event
            (id,encounter_id,action_id,game_time_seconds,round,type,audience,player_ids,payload)
        VALUES (?,?,?,?,?,?,?,CASE WHEN CAST(? AS uuid) IS NULL THEN '{}'::uuid[] ELSE ARRAY[CAST(? AS uuid)] END,
                CAST(? AS jsonb))
        """)
        .params(
            id,
            encounter,
            action,
            seconds,
            round,
            type,
            audience,
            recipient,
            recipient,
            payload.toString())
        .update();
    return id;
  }

  public List<Event> log(UUID encounter, UUID reader, boolean dm, long before, int limit) {
    return jdbc.sql(
            "SELECT "
                + EVENT_COLUMNS
                + " FROM combat_event AS event "
                + """
        WHERE encounter_id = ? AND seq < ?
        AND (? OR audience = 'ALL' OR (audience = 'PLAYERS' AND ? = ANY(player_ids)))
        ORDER BY seq DESC LIMIT ?
        """)
        .params(encounter, before, dm, reader, limit)
        .query(this::event)
        .list();
  }

  public List<JsonNode> committedPreimages(UUID encounter) {
    return jdbc.sql(
            """
        SELECT e.payload->'actionPreimage'
        FROM combat_event e
        WHERE e.encounter_id = ? AND e.audience = 'DM' AND jsonb_exists(e.payload, 'actionPreimage')
        AND NOT EXISTS (
          SELECT 1 FROM combat_event u WHERE u.encounter_id = e.encounter_id
          AND u.type IN ('ActionUndone', 'CorrectionUndone') AND u.payload->>'entryId' = e.id::text
        )
        ORDER BY e.seq
        """)
        .param(encounter)
        .query((row, index) -> json.readTree(row.getString(1)))
        .list();
  }

  public Optional<Recap> latestRecap(UUID encounter) {
    return jdbc.sql(
            """
        SELECT payload FROM combat_event WHERE encounter_id = ? AND type = 'EncounterEnded'
        ORDER BY seq DESC LIMIT 1
        """)
        .param(encounter)
        .query(
            (row, index) -> {
              var payload = json.readTree(row.getString(1));
              return new Recap(
                  payload.path("rounds").asInt(),
                  payload.path("fallen").valueStream().map(JsonNode::asString).toList(),
                  payload.path("resourcesSpent").properties().stream()
                      .collect(
                          java.util.stream.Collectors.toMap(
                              java.util.Map.Entry::getKey, item -> item.getValue().asInt())));
            })
        .optional();
  }

  public boolean eventExists(UUID encounter, UUID event) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM combat_event WHERE encounter_id = ? AND id = ?)")
        .params(encounter, event)
        .query(Boolean.class)
        .single();
  }

  public Optional<Event> latestReversible(UUID encounter) {
    return jdbc.sql(
            "SELECT "
                + EVENT_COLUMNS
                + " FROM combat_event AS event "
                + """
        WHERE encounter_id = ? AND audience = 'DM' ORDER BY seq DESC LIMIT 1
        """)
        .param(encounter)
        .query(this::event)
        .optional()
        .filter(
            row ->
                row.payload().has("actionPreimage")
                    || row.type().equals("CorrectionApplied")
                    || row.type().equals("AttackResolved"));
  }

  private Table table(ResultSet row, int index) throws SQLException {
    return new Table(
        row.getObject(1, UUID.class),
        row.getObject(2, UUID.class),
        row.getString(3),
        Phase.valueOf(row.getString(4)),
        row.getInt(5),
        row.getObject(6, UUID.class),
        json.readValue(row.getString(7), TurnBudget.class),
        row.getLong(8));
  }

  private Event event(ResultSet row, int index) throws SQLException {
    return new Event(
        row.getObject(1, UUID.class),
        row.getLong(2),
        row.getLong(3),
        row.getInt(4),
        row.getString(5),
        row.getString(6),
        json.readTree(row.getString(7)),
        row.getString(8));
  }

  public record Replay(UUID actor, byte[] hash, JsonNode result, long version) {}

  public record Event(
      UUID id,
      long sequence,
      long gameSeconds,
      int round,
      String type,
      String audience,
      JsonNode payload,
      String annotation) {
    public JsonNode displayPayload() {
      if (!payload.has("actionPreimage")) return payload;
      var display = (tools.jackson.databind.node.ObjectNode) payload.deepCopy();
      display.remove("actionPreimage");
      return display;
    }
  }
}
