package com.github.sbadolankaur.rpg.encounter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import com.github.sbadolankaur.rpg.engine.dice.ForcedDice;
import com.github.sbadolankaur.rpg.engine.dice.ReplayingRandomSource;
import com.github.sbadolankaur.rpg.engine.monster.MonsterHp;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-63: hit points of a spawned monster (RS-MON-012). */
class MonsterSpawnerTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static JsonNode troll() {
    return JSON.readTree("{\"size\":\"large\",\"hp\":{\"dice\":\"8d10+40\",\"average\":84}}");
  }

  private static JsonNode rat() {
    return JSON.readTree("{\"size\":\"tiny\",\"hp\":{\"dice\":\"1d4-5\",\"average\":1}}");
  }

  @Test
  void averageUsesThePrintedAverageAndRollsNothing() {
    var source = new ForcedDice();
    var start = MonsterSpawner.startingHitPoints(troll(), MonsterHp.Mode.AVERAGE, source);

    assertThat(start.hitPoints()).isEqualTo(84);
    assertThat(start.roll()).isNull();
    source.assertExhausted();
  }

  @Test
  void rolledRollsTheDiceOnceAndKeepsTheRoll() {
    var source = new ForcedDice(10, 1, 5, 5, 5, 5, 5, 5);
    var start = MonsterSpawner.startingHitPoints(troll(), MonsterHp.Mode.ROLLED, source);

    assertThat(start.hitPoints()).isEqualTo(10 + 1 + 5 * 6 + 40);
    assertThat(start.roll()).isNotNull();
    assertThat(start.roll().total()).isEqualTo(start.hitPoints());
    source.assertExhausted();
  }

  @Test
  void aRolledTotalBelowOneBecomesOne() {
    var start = MonsterSpawner.startingHitPoints(rat(), MonsterHp.Mode.ROLLED, new ForcedDice(1));

    assertThat(start.roll().total()).isEqualTo(-4);
    assertThat(start.hitPoints()).isEqualTo(1);
  }

  @Test
  void monstersFromOneDefinitionRollSeparately() {
    var source = new ForcedDice(1, 1, 1, 1, 1, 1, 1, 1, 10, 10, 10, 10, 10, 10, 10, 10);
    var first = MonsterSpawner.startingHitPoints(troll(), MonsterHp.Mode.ROLLED, source);
    var second = MonsterSpawner.startingHitPoints(troll(), MonsterHp.Mode.ROLLED, source);

    assertThat(first.hitPoints()).isEqualTo(8 + 40);
    assertThat(second.hitPoints()).isEqualTo(80 + 40);
    source.assertExhausted();
  }

  @Test
  void aRecordedRollReplaysUnchanged() {
    var live = new ReplayingRandomSource(List.of(), new ForcedDice(7, 2, 9, 4, 6, 1, 3, 8));
    var first = MonsterSpawner.startingHitPoints(troll(), MonsterHp.Mode.ROLLED, live);

    var replay =
        new ReplayingRandomSource(
            live.draws(),
            bound -> {
              throw new AssertionError("A replay must not draw fresh dice");
            });
    var again = MonsterSpawner.startingHitPoints(troll(), MonsterHp.Mode.ROLLED, replay);

    assertThat(again.hitPoints()).isEqualTo(first.hitPoints());
    assertThat(again.roll()).isEqualTo(first.roll());
  }

  @Test
  void theDefinitionIsNeverChanged() {
    var definition = troll();
    var before = definition.toString();
    MonsterSpawner.startingHitPoints(
        definition, MonsterHp.Mode.ROLLED, new ForcedDice(1, 1, 1, 1, 1, 1, 1, 1));

    assertThat(definition.toString()).isEqualTo(before);
  }

  @Test
  void unreadableHitDiceAreRejectedWhenRolling() {
    var broken = JSON.readTree("{\"size\":\"large\",\"hp\":{\"dice\":\"lots\",\"average\":84}}");

    assertThatThrownBy(
            () -> MonsterSpawner.startingHitPoints(broken, MonsterHp.Mode.ROLLED, new ForcedDice()))
        .isInstanceOf(CampaignException.class);
  }
}
