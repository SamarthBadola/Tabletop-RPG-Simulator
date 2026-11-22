package com.github.sbadolankaur.rpg.character.persistence;

import com.github.sbadolankaur.rpg.engine.character.AbilityGeneration;
import com.github.sbadolankaur.rpg.engine.kernel.Ability;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CharacterStore {
  private static final String HEADER =
      """
      SELECT id, campaign_id, owner_id, name, species_key, background_key, fighting_style_key,
             hp_current, hp_temp, copper, xp
      FROM player_character
      """;
  private final JdbcClient jdbc;
  private final JsonMapper json;

  CharacterStore(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<Header> list(UUID campaign, UUID actor, boolean dm) {
    return jdbc.sql(HEADER + " WHERE campaign_id = ? AND (? OR owner_id = ?) ORDER BY name, id")
        .params(campaign, dm, actor)
        .query(CharacterStore::header)
        .list();
  }

  public Optional<Header> find(UUID campaign, UUID character) {
    return jdbc.sql(HEADER + " WHERE campaign_id = ? AND id = ?")
        .params(campaign, character)
        .query(CharacterStore::header)
        .optional();
  }

  public State read(Header header) {
    UUID id = header.id();
    var abilities = new EnumMap<Ability, Integer>(Ability.class);
    jdbc.sql("SELECT ability, score FROM pc_ability WHERE player_character_id = ?")
        .param(id)
        .query(
            (row, index) ->
                Map.entry(Ability.valueOf(row.getString("ability")), row.getInt("score")))
        .list()
        .forEach(score -> abilities.put(score.getKey(), score.getValue()));
    var earned = new EnumMap<Ability, Integer>(Ability.class);
    jdbc.sql(
            "SELECT increases::text FROM pc_asi WHERE player_character_id = ? ORDER BY class_level")
        .param(id)
        .query(String.class)
        .list()
        .forEach(
            value ->
                json.readValue(value, new TypeReference<Map<Ability, Integer>>() {})
                    .forEach((ability, amount) -> earned.merge(ability, amount, Integer::sum)));
    List<ClassLevel> classes =
        jdbc.sql(
                """
        SELECT class_key, subclass_key, level FROM pc_class WHERE player_character_id = ? ORDER BY class_key
        """)
            .param(id)
            .query(
                (row, index) -> new ClassLevel(row.getString(1), row.getString(2), row.getInt(3)))
            .list();
    List<Integer> levels =
        jdbc.sql(
                """
        SELECT raw_hit_points FROM pc_class_level WHERE player_character_id = ? ORDER BY character_level
        """)
            .param(id)
            .query(Integer.class)
            .list();
    var spentHitDice =
        jdbc.sql(
                """
        SELECT die_size, spent FROM pc_hit_dice WHERE player_character_id = ? ORDER BY die_size
        """)
            .param(id)
            .query((row, index) -> new SpentHitDice(row.getInt(1), row.getInt(2)))
            .list();
    List<Proficiency> proficiencies =
        jdbc.sql(
                """
        SELECT kind, key, source FROM pc_proficiency WHERE player_character_id = ? ORDER BY kind, key, source
        """)
            .param(id)
            .query(
                (row, index) ->
                    new Proficiency(row.getString(1), row.getString(2), row.getString(3)))
            .list();
    List<Item> inventory =
        jdbc.sql(
                """
        SELECT id, item_key, quantity, equipment_slot FROM inventory_item
        WHERE player_character_id = ? ORDER BY item_key, id
        """)
            .param(id)
            .query(
                (row, index) ->
                    new Item(
                        row.getObject(1, UUID.class),
                        row.getString(2),
                        row.getInt(3),
                        row.getString(4)))
            .list();
    List<Resource> resources =
        jdbc.sql(
                """
        SELECT resource_key, used FROM pc_resource WHERE player_character_id = ? ORDER BY resource_key
        """)
            .param(id)
            .query((row, index) -> new Resource(row.getString(1), row.getInt(2)))
            .list();
    List<String> conditions =
        jdbc.sql(
                """
        SELECT condition_key FROM pc_condition WHERE player_character_id = ? ORDER BY condition_key
        """)
            .param(id)
            .query(String.class)
            .list();
    List<String> feats =
        jdbc.sql(
                """
            SELECT feat_key FROM pc_asi
            WHERE player_character_id = ? AND feat_key IS NOT NULL ORDER BY feat_key
            """)
            .param(id)
            .query(String.class)
            .list();
    return new State(
        header,
        Map.copyOf(abilities),
        Map.copyOf(earned),
        classes,
        levels,
        spentHitDice,
        proficiencies,
        inventory,
        resources,
        conditions,
        feats,
        grantFor(id).orElse(null));
  }

  public void create(
      Header character,
      String classKey,
      int hitDie,
      Map<Ability, Integer> scores,
      List<Proficiency> proficiencies,
      List<Item> inventory,
      List<String> resources) {
    jdbc.sql(
            """
        INSERT INTO player_character (id,campaign_id,owner_id,name,species_key,background_key,
            fighting_style_key,hp_current,copper)
        VALUES (:id,:campaign,:owner,:name,:species,:background,:style,:hp,:money)
        """)
        .param("id", character.id())
        .param("campaign", character.campaign())
        .param("owner", character.owner())
        .param("name", character.name())
        .param("species", character.species())
        .param("background", character.background())
        .param("style", character.style())
        .param("hp", character.hp())
        .param("money", character.copper())
        .update();
    UUID id = character.id();
    scores.forEach(
        (ability, value) ->
            jdbc.sql(
                    """
        INSERT INTO pc_ability VALUES (?, ?, ?)
        """)
                .params(id, ability.name(), value)
                .update());
    jdbc.sql("INSERT INTO pc_class(player_character_id,class_key,level) VALUES (?, ?, 1)")
        .params(id, classKey)
        .update();
    addLevel(id, classKey, 1, 1, hitDie);
    jdbc.sql("INSERT INTO pc_hit_dice(player_character_id,die_size) VALUES (?, ?)")
        .params(id, hitDie)
        .update();
    for (var proficiency : proficiencies)
      jdbc.sql(
              """
        INSERT INTO pc_proficiency VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
        """)
          .params(id, proficiency.kind(), proficiency.key(), proficiency.source())
          .update();
    inventory.forEach(
        item ->
            jdbc.sql(
                    """
        INSERT INTO inventory_item(id,player_character_id,item_key,quantity) VALUES (?, ?, ?, ?)
        """)
                .params(item.id(), id, item.key(), item.quantity())
                .update());
    addResources(id, resources);
  }

  public Optional<Rolled> rolled(UUID campaign, UUID actor) {
    return jdbc.sql(
            "SELECT method, values_json::text FROM rolled_set WHERE campaign_id = ? AND user_id = ?")
        .params(campaign, actor)
        .query(
            (row, index) ->
                new Rolled(
                    AbilityGeneration.valueOf(row.getString(1)),
                    json.readValue(row.getString(2), new TypeReference<List<Integer>>() {})))
        .optional();
  }

  public void rememberRoll(
      UUID campaign, UUID actor, AbilityGeneration method, List<Integer> values) {
    jdbc.sql("INSERT INTO rolled_set VALUES (?, ?, ?, CAST(? AS JSONB))")
        .params(campaign, actor, method.name(), json.writeValueAsString(values))
        .update();
  }

  public void consumeRoll(UUID campaign, UUID actor) {
    jdbc.sql("DELETE FROM rolled_set WHERE campaign_id = ? AND user_id = ?")
        .params(campaign, actor)
        .update();
  }

  public Optional<String> grantFor(UUID character) {
    return jdbc.sql("SELECT class_key FROM pc_level_grant WHERE player_character_id = ?")
        .param(character)
        .query(String.class)
        .optional();
  }

  public void grant(UUID character, String classKey, UUID dm) {
    jdbc.sql(
            """
        INSERT INTO pc_level_grant(player_character_id,class_key,granted_by) VALUES (?, ?, ?)
        ON CONFLICT (player_character_id) DO NOTHING
        """)
        .params(character, classKey, dm)
        .update();
  }

  public void advance(
      UUID id,
      ClassLevel level,
      int totalLevel,
      int raw,
      int hp,
      Map<Ability, Integer> improvements,
      String feat,
      List<String> resources) {
    jdbc.sql(
            """
        UPDATE pc_class SET level = :level, subclass_key = :subclass
        WHERE player_character_id = :id AND class_key = :class
        """)
        .param("level", level.level())
        .param("subclass", level.subclass())
        .param("id", id)
        .param("class", level.key())
        .update();
    addLevel(id, level.key(), level.level(), totalLevel, raw);
    if (!improvements.isEmpty() || feat != null)
      jdbc.sql(
              """
        INSERT INTO pc_asi(player_character_id,class_key,class_level,increases,feat_key)
        VALUES (:id,:class,:level,CAST(:increases AS JSONB),:feat)
        """)
          .param("id", id)
          .param("class", level.key())
          .param("level", level.level())
          .param("increases", json.writeValueAsString(improvements))
          .param("feat", feat)
          .update();
    addResources(id, resources);
    jdbc.sql("UPDATE player_character SET hp_current = ? WHERE id = ?").params(hp, id).update();
    jdbc.sql("DELETE FROM pc_level_grant WHERE player_character_id = ?").param(id).update();
  }

  public void rename(UUID id, String name) {
    jdbc.sql("UPDATE player_character SET name = ? WHERE id = ?").params(name, id).update();
  }

  public void hitPoints(UUID id, int current, int temporary) {
    jdbc.sql("UPDATE player_character SET hp_current = ?, hp_temp = ? WHERE id = ?")
        .params(current, temporary, id)
        .update();
  }

  public void equip(UUID id, Map<UUID, String> slots) {
    jdbc.sql("UPDATE inventory_item SET equipment_slot = NULL WHERE player_character_id = ?")
        .param(id)
        .update();
    slots.forEach(
        (item, slot) ->
            jdbc.sql(
                    """
        UPDATE inventory_item SET equipment_slot = ? WHERE id = ? AND player_character_id = ?
        """)
                .params(slot, item, id)
                .update());
  }

  public void updateAbilities(UUID id, Map<Ability, Integer> scores) {
    scores.forEach(
        (ability, score) ->
            jdbc.sql(
                    """
        UPDATE pc_ability SET score = ? WHERE player_character_id = ? AND ability = ?
        """)
                .params(score, id, ability.name())
                .update());
  }

  public void condition(UUID id, String key, boolean add) {
    String statement =
        add
            ? "INSERT INTO pc_condition VALUES (?, ?) ON CONFLICT DO NOTHING"
            : "DELETE FROM pc_condition WHERE player_character_id = ? AND condition_key = ?";
    jdbc.sql(statement).params(id, key).update();
  }

  public void resource(UUID id, String key, int used) {
    jdbc.sql("UPDATE pc_resource SET used = ? WHERE player_character_id = ? AND resource_key = ?")
        .params(used, id, key)
        .update();
  }

  private void addLevel(UUID id, String key, int classLevel, int totalLevel, int raw) {
    jdbc.sql("INSERT INTO pc_class_level VALUES (?, ?, ?, ?, ?)")
        .params(id, key, classLevel, totalLevel, raw)
        .update();
  }

  private void addResources(UUID id, List<String> resources) {
    for (String resource : resources)
      jdbc.sql(
              """
        INSERT INTO pc_resource(player_character_id,resource_key) VALUES (?, ?) ON CONFLICT DO NOTHING
        """)
          .params(id, resource)
          .update();
  }

  private static Header header(ResultSet row, int index) throws SQLException {
    return new Header(
        row.getObject("id", UUID.class),
        row.getObject("campaign_id", UUID.class),
        row.getObject("owner_id", UUID.class),
        row.getString("name"),
        row.getString("species_key"),
        row.getString("background_key"),
        row.getString("fighting_style_key"),
        row.getInt("hp_current"),
        row.getInt("hp_temp"),
        row.getLong("copper"),
        row.getLong("xp"));
  }

  public record Header(
      UUID id,
      UUID campaign,
      UUID owner,
      String name,
      String species,
      String background,
      String style,
      int hp,
      int temporaryHp,
      long copper,
      long xp) {}

  public record ClassLevel(String key, String subclass, int level) {}

  public record Proficiency(String kind, String key, String source) {}

  public record Item(UUID id, String key, int quantity, String slot) {}

  public record Resource(String key, int used) {}

  public record Rolled(AbilityGeneration method, List<Integer> values) {}

  public record SpentHitDice(int size, int spent) {}

  public record State(
      Header header,
      Map<Ability, Integer> base,
      Map<Ability, Integer> improvements,
      List<ClassLevel> classes,
      List<Integer> rawHealth,
      List<SpentHitDice> spentHitDice,
      List<Proficiency> proficiencies,
      List<Item> inventory,
      List<Resource> resources,
      List<String> conditions,
      List<String> feats,
      String grantedClass) {}
}
