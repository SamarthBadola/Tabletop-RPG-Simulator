package com.github.sbadolankaur.rpg.content;

import com.github.sbadolankaur.rpg.engine.content.ContentSet;
import com.github.sbadolankaur.rpg.engine.content.Definition;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentPacks {
  private static final int CACHE_ENTRIES = 64;
  private final JdbcClient jdbc;
  private final Map<List<String>, ContentSet> cache = new LinkedHashMap<>(16, 0.75f, true);

  ContentPacks(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void seed(ContentPack bundled) {
    UUID packId = ServerIds.at(Instant.now());
    var inserted =
        jdbc.sql(
                """
        INSERT INTO content_pack(id, pack_key, version, hash, ruleset, manifest, notice, warnings)
        VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?, CAST(? AS JSONB))
        ON CONFLICT (pack_key, version) DO NOTHING
        RETURNING id
        """)
            .params(
                packId,
                bundled.key(),
                bundled.version(),
                bundled.hash(),
                bundled.ruleset(),
                bundled.manifestJson(),
                bundled.notice(),
                PackJson.MAPPER.writeValueAsString(bundled.warnings()))
            .query(UUID.class)
            .optional();
    if (inserted.isEmpty()) {
      String installed =
          jdbc.sql("SELECT hash FROM content_pack WHERE pack_key = ? AND version = ?")
              .params(bundled.key(), bundled.version())
              .query(String.class)
              .single();
      if (!installed.equals(bundled.hash())) {
        throw new IllegalStateException(
            "Bundled SRD 5.1 "
                + bundled.version()
                + " differs from the installed hash. Publish the changed pack under a new version.");
      }
      return;
    }
    for (Definition definition : bundled.definitions()) {
      jdbc.sql(
              """
          INSERT INTO content_definition
              (id, pack_id, kind, key, name, ruleset, license, hash, overrides, body)
          VALUES (:id,:pack,:kind,:key,:name,:ruleset,:license,:hash,:overrides,CAST(:body AS JSONB))
          """)
          .param("id", ServerIds.at(Instant.now()))
          .param("pack", packId)
          .param("kind", definition.kind())
          .param("key", definition.key())
          .param("name", definition.name())
          .param("ruleset", bundled.ruleset())
          .param("license", bundled.license())
          .param("hash", definition.hash())
          .param("overrides", definition.overrides())
          .param("body", PackJson.MAPPER.writeValueAsString(definition.fields()))
          .update();
    }
    // Seed installation is atomic with backfilling campaigns created before Step 5.
    jdbc.sql(
            """
        INSERT INTO campaign_pack(campaign_id, pack_id, pack_key, position)
        SELECT c.id, ?, ?, 0 FROM campaign c
        WHERE NOT EXISTS (SELECT 1 FROM campaign_pack p WHERE p.campaign_id = c.id)
        """)
        .params(packId, bundled.key())
        .update();
  }

  public void enableSeed(UUID campaign, UUID actor) {
    int added =
        jdbc.sql(
                """
        INSERT INTO campaign_pack (campaign_id, pack_id, pack_key, position, enabled_by)
        SELECT ?, id, pack_key, 0, ? FROM content_pack
        WHERE pack_key = ? AND version = ?
        """)
            .params(campaign, actor, BundledContent.PACK_KEY, BundledContent.VERSION)
            .update();
    if (added != 1) throw new IllegalStateException("The bundled SRD pack was not seeded");
  }

  public String notice() {
    return jdbc.sql(
            """
        SELECT notice FROM content_pack WHERE pack_key = ? AND version = ?
        """)
        .params(BundledContent.PACK_KEY, BundledContent.VERSION)
        .query(String.class)
        .single();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ContentSet open(UUID campaign) {
    var hashes =
        List.copyOf(
            jdbc.sql(
                    """
        SELECT p.hash FROM content_pack p JOIN campaign_pack cp ON cp.pack_id = p.id
        WHERE cp.campaign_id = ? ORDER BY cp.position
        """)
                .param(campaign)
                .query(String.class)
                .list());
    synchronized (cache) {
      var available = cache.get(hashes);
      if (available != null) return available;
    }
    var snapshot = ContentResolution.resolve(hashes.stream().map(this::readPack).toList());
    synchronized (cache) {
      var result = cache.computeIfAbsent(hashes, ignored -> snapshot);
      if (cache.size() > CACHE_ENTRIES) cache.remove(cache.keySet().iterator().next());
      return result;
    }
  }

  private ContentPack readPack(String hash) {
    var pack =
        jdbc.sql(
                """
        SELECT id, pack_key, version, ruleset, license, manifest::text, notice
        FROM content_pack WHERE hash = ?
        """)
            .param(hash)
            .query(
                (rs, index) ->
                    new PackRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("pack_key"),
                        rs.getString("version"),
                        rs.getString("ruleset"),
                        rs.getString("license"),
                        rs.getString("manifest"),
                        rs.getString("notice")))
            .single();
    var definitions =
        jdbc.sql(
                """
        SELECT hash, body::text FROM content_definition
        WHERE pack_id = ? ORDER BY kind, key
        """)
            .param(pack.id())
            .query(
                (rs, index) ->
                    ContentPack.definition(
                        pack.key(), rs.getString("hash"), PackJson.parse(rs.getString("body"))))
            .list();
    var dependencies =
        jdbc.sql(
                """
        SELECT p.hash FROM content_pack p
        JOIN content_pack_dependency d ON p.id = d.dependency_id WHERE d.pack_id = ?
        ORDER BY p.pack_key
        """)
            .param(pack.id())
            .query(String.class)
            .list();
    return new ContentPack(
        pack.key(),
        pack.version(),
        hash,
        pack.ruleset(),
        pack.license(),
        pack.manifest(),
        pack.notice(),
        definitions,
        dependencies,
        List.of());
  }

  private record PackRow(
      UUID id,
      String key,
      String version,
      String ruleset,
      String license,
      String manifest,
      String notice) {}
}
