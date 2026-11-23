package com.github.sbadolankaur.rpg.campaign;

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

@Testcontainers
class CampaignSchemaIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.15"));

  @Test
  void upgradesAnInitializedInstallationWithoutReplacingItsAccounts() throws SQLException {
    var v1 = flyway("upgrade").target("1").load();
    v1.migrate();
    long originalFileNode = accountTableFileNode();
    try (var db = connect("upgrade");
        var sql = db.createStatement()) {
      sql.execute(
          """
          INSERT INTO app_user VALUES (
            '00000000-0000-0000-0000-000000000001', 'owner', 'existing-hash',
            'Owner', TRUE, CURRENT_TIMESTAMP
          )
          """);
      sql.execute("UPDATE instance_state SET initialized = TRUE");
    }

    flyway("upgrade").load().migrate();
    assertThat(accountTableFileNode()).isEqualTo(originalFileNode);

    try (var db = connect("upgrade");
        var sql = db.createStatement()) {
      try (var users =
          sql.executeQuery("SELECT username, instance_admin, password_hash FROM app_user")) {
        assertThat(users.next()).isTrue();
        assertThat(users.getString(1)).isEqualTo("owner");
        assertThat(users.getBoolean(2)).isTrue();
        assertThat("existing-hash".equals(users.getString(3))).isTrue();
      }
      try (var campaigns = sql.executeQuery("SELECT count(*) FROM campaign")) {
        assertThat(campaigns.next()).isTrue();
        assertThat(campaigns.getInt(1)).isZero();
      }
    }
  }

  private static long accountTableFileNode() throws SQLException {
    try (var db = connect("upgrade");
        var sql = db.createStatement();
        var row = sql.executeQuery("SELECT pg_relation_filenode('app_user'::regclass)")) {
      row.next();
      return row.getLong(1);
    }
  }

  @Test
  void freshSchemaRejectsASecondOpenSessionAndNegativeGameTime() throws SQLException {
    flyway("fresh").load().migrate();
    try (var db = connect("fresh");
        var sql = db.createStatement()) {
      sql.execute(
          """
          INSERT INTO campaign (id, name, created_at)
          VALUES ('00000000-0000-0000-0000-000000000001', 'Test campaign', CURRENT_TIMESTAMP)
          """);
      sql.execute(
          """
          INSERT INTO game_session (id, campaign_id, number, status, started_at)
          VALUES ('00000000-0000-0000-0000-000000000002',
                  '00000000-0000-0000-0000-000000000001', 1, 'PAUSED', CURRENT_TIMESTAMP)
          """);
      assertThatThrownBy(
              () ->
                  sql.execute(
                      """
          INSERT INTO game_session (id, campaign_id, number, status, started_at)
          VALUES ('00000000-0000-0000-0000-000000000003',
                  '00000000-0000-0000-0000-000000000001', 2, 'ACTIVE', CURRENT_TIMESTAMP)
          """))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("one_open_session");
      assertThatThrownBy(
              () ->
                  sql.execute(
                      """
          INSERT INTO game_clock (campaign_id, game_seconds)
          VALUES ('00000000-0000-0000-0000-000000000001', -1)
          """))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("clock_ok");
    }
  }

  private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(String schema) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema);
  }

  private static java.sql.Connection connect(String schema) throws SQLException {
    var connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    connection.setSchema(schema);
    return connection;
  }
}
