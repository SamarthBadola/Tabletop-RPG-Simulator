package com.github.sbadolankaur.rpg.encounter.persistence;

import com.github.sbadolankaur.rpg.engine.affliction.Affliction;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AfflictionStore {
  private final JdbcClient jdbc;

  public AfflictionStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Map<UUID, Affliction> forCombatant(UUID combatant) {
    return jdbc
        .sql(
            """
        SELECT id, kind, state, ends_at_clock, ended_at_clock, end_reason
        FROM affliction WHERE combatant_id = ? ORDER BY id
        """)
        .param(combatant)
        .query(
            (row, index) ->
                new Affliction(
                    row.getObject(1, UUID.class), Affliction.Kind.valueOf(row.getString(2)),
                    Affliction.State.valueOf(row.getString(3)), row.getObject(4, Long.class),
                    row.getObject(5, Long.class), row.getString(6)))
        .list()
        .stream()
        .collect(Collectors.toUnmodifiableMap(Affliction::id, instance -> instance));
  }

  public List<CureChoice> activeCures(UUID encounter) {
    return jdbc.sql(
            """
        SELECT a.id, c.id, a.kind, a.definition_key
        FROM affliction a JOIN combatant c ON a.combatant_id = c.id
        JOIN encounter e ON c.encounter_id = e.id
        JOIN game_clock g ON e.campaign_id = g.campaign_id
        WHERE e.id = ? AND a.state = 'ACTIVE' AND a.kind IN ('DISEASE', 'POISON')
          AND (a.ends_at_clock IS NULL OR a.ends_at_clock > g.game_seconds)
        ORDER BY a.definition_key, a.id
        """)
        .param(encounter)
        .query(
            (row, index) ->
                new CureChoice(
                    row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                    Affliction.Kind.valueOf(row.getString(3)), row.getString(4)))
        .list();
  }

  public record CureChoice(UUID id, UUID target, Affliction.Kind kind, String definitionKey) {}

  public void update(UUID combatant, Affliction affliction) {
    int changed =
        jdbc.sql(
                """
        UPDATE affliction SET state = ?, ended_at_clock = ?, end_reason = ?
        WHERE combatant_id = ? AND id = ?
        """)
            .params(
                affliction.state().name(),
                affliction.endedAt(),
                affliction.endReason(),
                combatant,
                affliction.id())
            .update();
    if (changed != 1)
      throw new IllegalStateException("Affliction changed while the encounter was locked");
  }
}
