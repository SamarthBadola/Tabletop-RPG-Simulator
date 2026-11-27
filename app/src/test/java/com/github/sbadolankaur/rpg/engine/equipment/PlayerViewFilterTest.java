package com.github.sbadolankaur.rpg.engine.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.sbadolankaur.rpg.engine.equipment.ItemEffects.ActiveItem;
import com.github.sbadolankaur.rpg.engine.equipment.ItemEffects.Bonus;
import com.github.sbadolankaur.rpg.engine.equipment.PlayerViewFilter.Audience;
import com.github.sbadolankaur.rpg.engine.equipment.PlayerViewFilter.ItemFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerViewFilterTest {
  private static final ItemFacts SWORD =
      new ItemFacts(
          "Flame Tongue", "Longsword", false, 0, true, List.of("extra fire damage 2d6"), null);

  @Test
  void T_61_thePlayerViewHidesAnUnidentifiedItemsProperties() {
    var view = PlayerViewFilter.view(SWORD, Audience.PLAYER, true);
    assertThat(view.name()).isEqualTo("Longsword");
    assertThat(view.magicBonus()).isNull();
    assertThat(view.requiresAttunement()).isNull();
    assertThat(view.effects()).isEmpty();
    assertThat(view.charges()).isNull();
    assertThat(view.toString().contains("Flame Tongue")).isFalse();
    assertThat(view.toString().contains("fire")).isFalse();
  }

  @Test
  void T_61_theDmViewShowsEverything() {
    var view = PlayerViewFilter.view(SWORD, Audience.DM, true);
    assertThat(view.name()).isEqualTo("Flame Tongue");
    assertThat(view.requiresAttunement()).isTrue();
    assertThat(view.effects()).hasSize(1);
  }

  @Test
  void T_61_anIdentifiedItemIsShownToPlayersAndTheSettingCanShowAllItems() {
    var identified =
        new ItemFacts(
            "Flame Tongue", "Longsword", true, 0, true, List.of("extra fire damage 2d6"), null);
    assertThat(PlayerViewFilter.view(identified, Audience.PLAYER, true).name())
        .isEqualTo("Flame Tongue");
    assertThat(PlayerViewFilter.view(SWORD, Audience.PLAYER, false).name())
        .isEqualTo("Flame Tongue");
  }

  @Test
  void T_61_theEffectsStillApplyWhileTheItemIsUnidentified() {
    var ring =
        new ActiveItem("ring-of-protection", "r1", true, true, true, List.of(new Bonus("ac", 1)));
    assertThat(ItemEffects.gather(List.of(ring)).get("ac")).isEqualTo(1);
  }
}
