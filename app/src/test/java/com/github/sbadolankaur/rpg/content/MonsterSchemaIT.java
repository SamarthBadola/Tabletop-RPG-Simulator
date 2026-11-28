package com.github.sbadolankaur.rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** The constraints that the monster migration adds (Build Brief, schema additions for monsters). */
@Testcontainers
class MonsterSchemaIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.16"));

  private static final String ID = "00000000-0000-0000-0000-000000000001";

  @Test
  void monsterColumnsKeepTheirDefaultsAndLimits() throws SQLException {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var sql = connection.createStatement()) {
      // Copies without foreign keys, so a row needs only its own columns.
      sql.execute("CREATE TEMP TABLE combatant_copy (LIKE combatant INCLUDING ALL)");
      sql.execute("CREATE TEMP TABLE item_copy (LIKE inventory_item INCLUDING ALL)");

      sql.execute(
          "INSERT INTO combatant_copy(id, encounter_id, monster_key, ordinal, tactics, npc_vitals)"
              + " VALUES ('"
              + ID
              + "', '"
              + ID
              + "', 'srd-5.1:goblin', 1, '{}', '{}')");
      var row =
          sql.executeQuery(
              "SELECT legendary_points, created, regen_suppressed, dying_by_trait, in_sunlight,"
                  + " form, controller_user_id FROM combatant_copy");
      assertThat(row.next()).isTrue();
      assertThat(row.getLong(1)).isEqualTo(0L);
      assertThat(row.getBoolean(2)).isFalse();
      assertThat(row.getBoolean(3)).isFalse();
      assertThat(row.getBoolean(4)).isFalse();
      assertThat(row.getBoolean(5)).isFalse();
      assertThat(row.getString(6)).isNull();
      assertThat(row.getObject(7)).isNull();
      assertThatThrownBy(() -> sql.execute("UPDATE combatant_copy SET legendary_points = -1"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> sql.execute("UPDATE combatant_copy SET form = ''"))
          .isInstanceOf(SQLException.class);
      sql.execute("UPDATE combatant_copy SET legendary_points = 3, form = 'object'");

      sql.execute(
          "INSERT INTO item_copy(id, player_character_id, item_key, quantity)"
              + " VALUES ('"
              + ID
              + "', '"
              + ID
              + "', 'srd-5.1:longsword', 1)");
      var corrosion = sql.executeQuery("SELECT corrosion FROM item_copy");
      assertThat(corrosion.next()).isTrue();
      assertThat(corrosion.getLong(1)).isEqualTo(0L);
      sql.execute("UPDATE item_copy SET corrosion = -4");
      assertThatThrownBy(() -> sql.execute("UPDATE item_copy SET corrosion = 1"))
          .isInstanceOf(SQLException.class);

      var lair =
          sql.executeQuery(
              "SELECT column_default FROM information_schema.columns"
                  + " WHERE table_name = 'encounter' AND column_name = 'in_lair'");
      assertThat(lair.next()).isTrue();
      assertThat(lair.getString(1)).isEqualTo("false");

      var point =
          sql.executeQuery(
              "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                  + " WHERE conname = 'pending_reaction_point'");
      assertThat(point.next()).isTrue();
      assertThat(point.getString(1)).contains("ON_TURN_END").contains("ON_READIED_TRIGGER");
    }
  }
}
