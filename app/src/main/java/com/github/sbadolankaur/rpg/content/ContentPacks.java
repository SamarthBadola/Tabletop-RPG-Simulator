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

  public List<SrdVersion> bundledVersions(UUID campaign) {
    return jdbc.sql(
            """
        SELECT p.id, p.version, p.hash, EXISTS (
          SELECT 1 FROM campaign_pack cp WHERE cp.campaign_id = ? AND cp.pack_id = p.id
        ) AS enabled
        FROM content_pack p WHERE p.pack_key = ? ORDER BY p.loaded_at DESC, p.id DESC
        """)
        .params(campaign, BundledContent.PACK_KEY)
        .query(
            (row, index) ->
                new SrdVersion(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getString(3),
                    row.getBoolean(4)))
        .list();
  }

  public String currentBundledVersion() {
    return BundledContent.VERSION;
  }

  public java.util.Optional<UpgradeReview> reviewUpgrade(
      UUID campaign, UUID source, String targetVersion) {
    var versions = bundledVersions(campaign);
    var before =
        versions.stream()
            .filter(version -> version.id().equals(source) && version.enabled())
            .findFirst();
    var target =
        versions.stream()
            .filter(
                version ->
                    version.version().equals(targetVersion)
                        && version.version().equals(BundledContent.VERSION)
                        && !version.enabled())
            .findFirst();
    if (before.isEmpty() || target.isEmpty()) return java.util.Optional.empty();
    var from = readPack(before.get().hash());
    var to = readPack(target.get().hash());
    var indexes = new java.util.HashMap<ContentSet.Key, String>();
    to.definitions()
        .forEach(value -> indexes.put(new ContentSet.Key(value.kind(), value.key()), value.hash()));
    var removals = new java.util.ArrayList<String>();
    var changes = new java.util.ArrayList<String>();
    for (var def : from.definitions()) {
      String hash = indexes.get(new ContentSet.Key(def.kind(), def.key()));
      if (hash == null) removals.add(def.key());
      else if (!hash.equals(def.hash())) changes.add(def.key());
    }
    var groups = new java.util.TreeMap<String, List<String>>();
    if (!removals.isEmpty()) groups.put("removals", List.copyOf(removals));
    if (!changes.isEmpty()) groups.put("changed", List.copyOf(changes));
    var blockers = new java.util.ArrayList<String>();
    if (hasDependents(campaign, source)) blockers.add("An enabled pack depends on this version");
    if (!removals.isEmpty() && references(campaign).stream().anyMatch(removals::contains)) {
      blockers.add("Campaign characters still refer to removed definitions");
    }
    String reviewHash =
        PackJson.hash(
            PackJson.MAPPER.writeValueAsString(
                List.of(
                    campaign,
                    before.get().id(),
                    from.hash(),
                    target.get().id(),
                    to.hash(),
                    groups,
                    blockers)));
    return java.util.Optional.of(
        new UpgradeReview(
            source,
            target.get().id(),
            targetVersion,
            reviewHash,
            Map.copyOf(groups),
            List.copyOf(blockers),
            from.hash(),
            to.hash()));
  }

  public boolean applyReviewedUpgrade(UUID campaign, UUID actor, UpgradeReview review) {
    return jdbc.sql(
                """
        UPDATE campaign_pack SET pack_id = ?, enabled_by = ?, enabled_at = CURRENT_TIMESTAMP
        WHERE campaign_id = ? AND pack_id = ?
        """)
            .params(review.target(), actor, campaign, review.source())
            .update()
        == 1;
  }

  private boolean hasDependents(UUID campaign, UUID source) {
    return jdbc.sql(
            """
        SELECT EXISTS (SELECT 1 FROM campaign_pack cp
          JOIN content_pack_dependency d ON d.pack_id = cp.pack_id
          WHERE cp.campaign_id = ? AND d.dependency_id = ?)
        """)
        .params(campaign, source)
        .query(Boolean.class)
        .single();
  }

  private List<String> references(UUID campaign) {
    return jdbc.sql(
            """
        WITH characters AS (SELECT * FROM player_character WHERE campaign_id = ?)
        SELECT species_key FROM characters
        UNION SELECT background_key FROM characters WHERE background_key IS NOT NULL
        UNION SELECT fighting_style_key FROM characters
        UNION SELECT c.class_key FROM pc_class c JOIN characters p ON p.id = c.player_character_id
        UNION SELECT c.subclass_key FROM pc_class c JOIN characters p ON p.id = c.player_character_id WHERE c.subclass_key IS NOT NULL
        UNION SELECT i.item_key FROM inventory_item i JOIN characters p ON p.id = i.player_character_id
        UNION SELECT r.resource_key FROM pc_resource r JOIN characters p ON p.id = r.player_character_id
        UNION SELECT c.condition_key FROM pc_condition c JOIN characters p ON p.id = c.player_character_id
        UNION SELECT a.feat_key FROM pc_asi a JOIN characters p ON p.id = a.player_character_id WHERE a.feat_key IS NOT NULL
        UNION SELECT s.spell_key FROM pc_spell s JOIN characters p ON p.id = s.player_character_id
        """)
        .param(campaign)
        .query(String.class)
        .list();
  }

  public record SrdVersion(UUID id, String version, String hash, boolean enabled) {}

  public record UpgradeReview(
      UUID source,
      UUID target,
      String targetVersion,
      String hash,
      Map<String, List<String>> groups,
      List<String> blockers,
      String previousHash,
      String nextHash) {}

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
