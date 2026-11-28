package com.github.sbadolankaur.rpg.encounter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Entry;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Tactics;
import com.github.sbadolankaur.rpg.encounter.application.EncounterState.Vitals;
import com.github.sbadolankaur.rpg.engine.combat.CombatHealth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** The Regeneration marks of a combatant (RS-MON-015) survive a save and a reload. */
@Testcontainers
class EncounterStoreIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.16"));

  @Test
  void regenerationMarksSurviveASaveAndAReload() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var dataSource =
        new SingleConnectionDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true)) {
      var jdbc = JdbcClient.create(dataSource);
      // A temporary copy without foreign keys shadows the real table on this one connection,
      // so a combatant needs no campaign or encounter rows around it.
      jdbc.sql("CREATE TEMP TABLE combatant (LIKE public.combatant INCLUDING ALL)").update();
      var store = new EncounterStore(jdbc, JsonMapper.builder().build());

      UUID encounter = UUID.randomUUID();
      UUID troll = UUID.randomUUID();
      UUID goblin = UUID.randomUUID();
      var tactics = Tactics.initial(1, false, List.of());
      var vitals = new Vitals(CombatHealth.of(0, 84, 0), null, Set.of());

      store.add(encounter, new Entry(troll, null, "srd-5.1:troll", 1, 12, null, tactics, vitals));
      store.add(
          encounter,
          new Entry(goblin, null, "srd-5.1:goblin", 2, 14, null, tactics, vitals)
              .marks(true, false));

      var saved = byId(store.entries(encounter));
      assertThat(saved.get(troll).dyingByTrait()).isFalse();
      assertThat(saved.get(troll).regenSuppressed()).isFalse();
      assertThat(saved.get(goblin).dyingByTrait()).isTrue();
      assertThat(saved.get(goblin).regenSuppressed()).isFalse();

      store.update(saved.get(troll).marks(true, true));
      store.update(saved.get(goblin).marks(false, true));

      var updated = byId(store.entries(encounter));
      assertThat(updated.get(troll).dyingByTrait()).isTrue();
      assertThat(updated.get(troll).regenSuppressed()).isTrue();
      assertThat(updated.get(goblin).dyingByTrait()).isFalse();
      assertThat(updated.get(goblin).regenSuppressed()).isTrue();
      // Changing another field keeps the marks.
      store.update(updated.get(troll).initiative(5, null));
      var again = byId(store.entries(encounter)).get(troll);
      assertThat(again.initiative()).isEqualTo(5);
      assertThat(again.dyingByTrait()).isTrue();
      assertThat(again.regenSuppressed()).isTrue();
    }
  }

  private static Map<UUID, Entry> byId(List<Entry> entries) {
    var map = new HashMap<UUID, Entry>();
    entries.forEach(entry -> map.put(entry.id(), entry));
    return map;
  }
}
