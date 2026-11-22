package com.github.sbadolankaur.rpg.content;

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
class ContentSchemaIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.16"));

  @Test
  void migratedSchemaEnforcesPackIdentityImmutabilityAndDeletionGuards() throws SQLException {
    var migration =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    migration.target("8").load().migrate();
    migration.target("9").load().migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var sql = connection.createStatement()) {
      sql.execute(
          """
          INSERT INTO campaign(id, name, created_at) VALUES
            ('00000000-0000-0000-0000-000000000001', 'Content test', now())
          """);
      sql.execute(
          """
          INSERT INTO content_pack(id, pack_key, version, hash, ruleset, manifest, notice)
          VALUES
            ('00000000-0000-0000-0000-000000000001','base-pack','1.0.0','sha256:' || repeat('1',64),'srd-5.1','{"license":{"id":"custom"},"version":"1.0.0","ruleset":"srd-5.1","id":"base-pack"}',''),
            ('00000000-0000-0000-0000-000000000002','base-pack','1.0.1','sha256:' || repeat('2',64),'srd-5.1','{"license":{"id":"custom"},"version":"1.0.1","ruleset":"srd-5.1","id":"base-pack"}',''),
            ('00000000-0000-0000-0000-000000000003','child-pack','1.0.0','sha256:' || repeat('3',64),'srd-5.1','{"license":{"id":"custom"},"version":"1.0.0","ruleset":"srd-5.1","id":"child-pack"}','')
          """);
      sql.execute(
          """
          INSERT INTO content_definition(id, pack_id, kind, key, name, ruleset, license, hash, body)
          VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',
            'ruleTable', 'base-pack:example','Example','srd-5.1','custom','sha256:' || repeat('4',64),'{}')
          """);
      sql.execute(
          """
          INSERT INTO content_pack_dependency(pack_id, dependency_id)
            VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002')
          """);
      sql.execute(
          """
          INSERT INTO campaign_pack(campaign_id, pack_id, pack_key, position)
            VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','base-pack',0)
          """);
      assertThatThrownBy(() -> sql.execute("UPDATE content_definition SET name = 'New name'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("immutable");
      assertThatThrownBy(() -> sql.execute("UPDATE content_pack SET version = '9.0.0'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("immutable");
      sql.execute("UPDATE content_pack SET state = 'archived' WHERE version = '1.0.1'");
      assertThatThrownBy(
              () ->
                  sql.execute(
                      """
          INSERT INTO campaign_pack(campaign_id, pack_id, pack_key, position)
            VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','base-pack',1)
          """))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("campaign_pack_one_version");
      assertThatThrownBy(() -> sql.execute("DELETE FROM content_pack WHERE version = '1.0.1'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("content_pack_dependency_dependency");
      assertThatThrownBy(
              () ->
                  sql.execute("DELETE FROM content_pack WHERE hash = 'sha256:' || repeat('1',64)"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("campaign_pack_pack");
    }
  }
}
