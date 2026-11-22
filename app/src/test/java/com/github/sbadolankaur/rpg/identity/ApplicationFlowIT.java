package com.github.sbadolankaur.rpg.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.sbadolankaur.rpg.RpgApplication;
import com.github.sbadolankaur.rpg.content.BundledPacksFixture;
import com.github.sbadolankaur.rpg.content.ContentPack;
import com.github.sbadolankaur.rpg.content.ContentPacks;
import com.github.sbadolankaur.rpg.content.ContentPacks.UpgradeReview;
import com.github.sbadolankaur.rpg.identity.web.RegistrationForm;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = RpgApplication.class, properties = "app.jdbc-sessions=false")
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class ApplicationFlowIT {
  private static final String PASSWORD = "Walking-Skeleton-Pass-27!";
  private static final String INSTANCE_KEY =
      HexFormat.of().formatHex(new SecureRandom().generateSeed(32));

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.15"))
          .withDatabaseName("rpg")
          .withUsername("rpg")
          .withPassword("test-container-password");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.instance-key", () -> INSTANCE_KEY);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ContentPacks contentPacks;

  @Autowired private com.github.sbadolankaur.rpg.content.PackLoader packLoader;
  @Autowired private com.github.sbadolankaur.rpg.campaign.application.CampaignPlay play;
  @Autowired private com.github.sbadolankaur.rpg.encounter.application.Encounters encounters;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private com.github.sbadolankaur.rpg.encounter.application.EncounterDice combatDice;

  @Autowired private com.github.sbadolankaur.rpg.campaign.application.Campaigns campaigns;
  @Autowired private com.github.sbadolankaur.rpg.campaign.application.CampaignCommands commands;
  @Autowired private com.github.sbadolankaur.rpg.campaign.application.CampaignSettings settings;

  @BeforeEach
  void resetRegistrationState() {
    jdbcTemplate.update("DELETE FROM form_token");
    jdbcTemplate.update("DELETE FROM campaign");
    jdbcTemplate.update("DELETE FROM app_user");
    jdbcTemplate.update("UPDATE instance_state SET initialized = FALSE WHERE id = 1");
  }

  @Test
  void fighterCreationLevelingAndAccessFollowTheStepSixContract() throws Exception {
    UUID dm = characterAccount("dm-one", true);
    UUID owner = characterAccount("player-one", false);
    UUID peer = characterAccount("player-two", false);
    UUID campaign = campaigns.create(dm, "Fighter table").id();
    joinFixture(campaign, owner, peer);
    String collection = "/campaigns/" + campaign + "/characters";
    mockMvc
        .perform(get(collection + "/new").with(user("player-one")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Human")));
    for (String stage :
        java.util.List.of("species", "class", "abilities", "background", "equipment")) {
      mockMvc.perform(fighterForm(collection, "player-one", stage)).andExpect(status().isOk());
    }
    UUID createId = commands.newActionId();
    long before = commands.readClock(campaign, owner).version();
    var created =
        mockMvc
            .perform(
                fighterForm(collection, "player-one", "review")
                    .param("actionId", createId.toString())
                    .param("expectedVersion", String.valueOf(before)))
            .andExpect(status().isSeeOther())
            .andReturn();
    String sheet = created.getResponse().getRedirectedUrl();
    UUID character = jdbcTemplate.queryForObject("SELECT id FROM player_character", UUID.class);
    assertThat(sheet).isEqualTo(collection + "/" + character);
    mockMvc
        .perform(
            fighterForm(collection, "player-one", "review")
                .param("actionId", createId.toString())
                .param("expectedVersion", String.valueOf(before)))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl(sheet));
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM player_character", Integer.class))
        .isEqualTo(1);
    mockMvc
        .perform(get(sheet).with(user("player-one")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("13 / 13")));
    mockMvc.perform(get(sheet).with(user("player-two"))).andExpect(status().isForbidden());
    mockMvc
        .perform(post(sheet + "/edits").with(user("player-two")).with(csrf()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get(collection).with(user("player-two")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Mira"))));
    UUID other = campaigns.create(dm, "Separate table").id();
    joinFixture(other, peer);
    mockMvc
        .perform(get("/campaigns/" + other + "/characters/" + character).with(user("player-two")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            action(sheet + "/edits", "dm-one", campaign, dm)
                .param("section", "details")
                .param("name", "Mira the Fighter"))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            action(sheet + "/edits", "dm-one", campaign, dm)
                .param("section", "ability-scores")
                .param("abilityScores", "18", "13", "15", "8", "12", "10"))
        .andExpect(status().isSeeOther());

    UUID armor = inventory(character, "srd-5.1:chain-mail");
    UUID shield = inventory(character, "srd-5.1:shield");
    UUID sword = inventory(character, "srd-5.1:longsword");
    mockMvc
        .perform(
            action(sheet + "/edits", "player-one", campaign, owner)
                .param("section", "inventory")
                .param("equipment", armor + "=ARMOR", shield + "=OFF_HAND", sword + "=MAIN_HAND"))
        .andExpect(status().isSeeOther());
    for (int level = 2; level <= 5; level++) {
      mockMvc
          .perform(action(sheet + "/level-grants", "dm-one", campaign, dm))
          .andExpect(status().isSeeOther());
      mockMvc
          .perform(get(sheet + "/level-ups").with(user("player-one")))
          .andExpect(status().isOk());
      if (level == 4) {
        mockMvc
            .perform(levelAction(sheet, campaign, owner, 4, "ability-scores", false))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("1 points lost")));
        mockMvc
            .perform(levelAction(sheet, campaign, owner, 4, "review", false))
            .andExpect(status().isBadRequest());
      }
      mockMvc
          .perform(levelAction(sheet, campaign, owner, level, "review", true))
          .andExpect(status().isSeeOther());
    }
    mockMvc
        .perform(get(sheet).with(user("player-one")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Champion")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("49 / 49")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("0 / 5 d10")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "8 to hit, 1d8+5 slashing, critical on 19+")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Extra Attack")));
    assertThat(jdbcTemplate.queryForObject("SELECT level FROM pc_class", Integer.class))
        .isEqualTo(5);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pc_resource", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void installedCampaignStaysPinnedUntilTheDmConfirmsAReviewedUpgrade() throws Exception {
    UUID dm = characterAccount("upgrade-dm", true);
    UUID player = characterAccount("upgrade-player", false);
    UUID campaign = campaigns.create(dm, "Existing table").id();
    joinFixture(campaign, player);
    var previous = BundledPacksFixture.stepFive();
    contentPacks.seed(previous);
    UUID pack =
        jdbcTemplate.queryForObject(
            "SELECT id FROM content_pack WHERE hash = ?", UUID.class, previous.hash());
    jdbcTemplate.update(
        "UPDATE campaign_pack SET pack_id = ? WHERE campaign_id = ?", pack, campaign);

    String packs = "/campaigns/" + campaign + "/packs";
    String review = packs + "/" + pack + "/reviews";
    String apply = packs + "/" + pack + "/upgrades";
    mockMvc.perform(get(packs).with(user("upgrade-player"))).andExpect(status().isForbidden());
    mockMvc
        .perform(get(packs).with(user("upgrade-dm")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Review upgrade")));
    assertThat(contentPacks.open(campaign).find("class", "srd-5.1:fighter")).isEmpty();
    mockMvc
        .perform(fighterForm("/campaigns/" + campaign + "/characters", "upgrade-player", "species"))
        .andExpect(status().isUnprocessableContent());

    long version = commands.readClock(campaign, dm).version();
    var result =
        mockMvc
            .perform(
                post(review)
                    .with(user("upgrade-dm"))
                    .with(csrf())
                    .header("HX-Request", "true")
                    .param("action", "upgrade")
                    .param("targetVersion", "0.2.0"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm upgrade")))
            .andReturn();
    var preview = (UpgradeReview) result.getModelAndView().getModel().get("review");
    assertThat(preview.groups()).isEmpty();
    assertThat(commands.readClock(campaign, dm).version()).isEqualTo(version);
    mockMvc
        .perform(
            action(apply, "upgrade-player", campaign, player)
                .param("targetVersion", "0.2.0")
                .param("reviewHash", preview.hash()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            action(apply, "upgrade-dm", campaign, dm)
                .param("targetVersion", "0.2.0")
                .param("reviewHash", "expired-review"))
        .andExpect(status().isConflict());
    assertThat(contentPacks.open(campaign).find("class", "srd-5.1:fighter")).isEmpty();
    mockMvc
        .perform(
            action(apply, "upgrade-dm", campaign, dm)
                .param("targetVersion", "0.2.0")
                .param("reviewHash", preview.hash()))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl(packs));
    assertThat(contentPacks.open(campaign).find("class", "srd-5.1:fighter")).isPresent();
    assertThat(commands.readClock(campaign, dm).version()).isEqualTo(version + 1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM campaign_event WHERE type = 'ContentPackUpgraded'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void rolledAbilityScoresAreRetainedAcrossRequestsAndConsumedAtCreation() throws Exception {
    UUID dm = characterAccount("rolling-dm", true);
    UUID owner = characterAccount("player-one", false);
    UUID campaign = campaigns.create(dm, "Rolled table").id();
    joinFixture(campaign, owner);
    settings.decide(
        campaign,
        dm,
        new com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope(
            commands.newActionId(), commands.readClock(campaign, dm).version()),
        "abilityScoreGenerationMethod",
        "ROLL_3D6");
    String collection = "/campaigns/" + campaign + "/characters";
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              action(collection, "player-one", campaign, owner)
                  .param("step", "abilities")
                  .param("rollSet", "true")
                  .param("speciesKey", "srd-5.1:human")
                  .param("classKey", "srd-5.1:fighter")
                  .param("fightingStyleKey", "srd-5.1:defense")
                  .param("abilityMethod", "ROLL_3D6"))
          .andExpect(status().isOk());
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM campaign_event WHERE type = 'AbilityScoresRolled'",
                Integer.class))
        .isEqualTo(1);
    mockMvc
        .perform(
            action(collection, "player-one", campaign, owner)
                .param("step", "abilities")
                .param("rollSet", "true")
                .param("speciesKey", "srd-5.1:human")
                .param("classKey", "srd-5.1:fighter")
                .param("fightingStyleKey", "srd-5.1:defense")
                .param("abilityMethod", "ROLL_4D6_DROP_LOWEST")
                .accept("application/problem+json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("abilityMethod"))
        .andExpect(jsonPath("$.errors[0].code").value("INCORRECT"));

    String scores =
        jdbcTemplate.queryForObject("SELECT values_json::text FROM rolled_set", String.class);
    String[] split = scores.substring(1, scores.length() - 1).split(", ");
    mockMvc
        .perform(
            action(collection, "player-one", campaign, owner)
                .param("step", "review")
                .param("speciesKey", "srd-5.1:human")
                .param("classKey", "srd-5.1:fighter")
                .param("fightingStyleKey", "srd-5.1:two-weapon-fighting")
                .param("abilityMethod", "ROLL_3D6")
                .param("abilityScores", split)
                .param("skillChoices", "athletics", "perception")
                .param("languageChoices", "elvish")
                .param("equipmentChoices", "srd-5.1:starting-gold")
                .param("name", "Rolled Fighter"))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM rolled_set", Integer.class))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_item", Integer.class))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT copper FROM player_character", Integer.class))
        .isBetween(5000, 20000);
  }

  @Test
  void fighterCanTakeGrapplerAtLevelFourAndHoldTwoCopiesOfTheSameWeapon() throws Exception {
    UUID dm = characterAccount("feat-dm", true);
    UUID owner = characterAccount("feat-player", false);
    UUID campaign = campaigns.create(dm, "Feat table").id();
    joinFixture(campaign, owner);
    String collection = "/campaigns/" + campaign + "/characters";
    var created =
        mockMvc
            .perform(
                action(collection, "feat-player", campaign, owner)
                    .param("step", "review")
                    .param("speciesKey", "srd-5.1:human")
                    .param("classKey", "srd-5.1:fighter")
                    .param("fightingStyleKey", "srd-5.1:two-weapon-fighting")
                    .param("abilityMethod", "STANDARD_ARRAY")
                    .param("abilityScores", "12", "14", "15", "8", "13", "10")
                    .param("skillChoices", "athletics", "perception")
                    .param("languageChoices", "elvish")
                    .param(
                        "equipmentChoices",
                        "srd-5.1:fighter-kit-0-0",
                        "srd-5.1:fighter-kit-1-0",
                        "srd-5.1:fighter-kit-2-1",
                        "srd-5.1:fighter-kit-3-0")
                    .param("name", "Ari"))
            .andExpect(status().isSeeOther())
            .andReturn();
    String sheet = created.getResponse().getRedirectedUrl();
    var axes =
        jdbcTemplate.queryForList(
            "SELECT id FROM inventory_item WHERE item_key = 'srd-5.1:handaxe' ORDER BY id",
            UUID.class);
    assertThat(axes).hasSize(2);
    mockMvc
        .perform(
            action(sheet + "/edits", "feat-player", campaign, owner)
                .param("section", "inventory")
                .param("equipment", axes.get(0) + "=MAIN_HAND", axes.get(1) + "=OFF_HAND"))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(get(sheet).with(user("feat-player")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Two-Weapon Fighting")));
    for (int tier = 2; tier <= 4; tier++) {
      mockMvc
          .perform(action(sheet + "/level-grants", "feat-dm", campaign, dm))
          .andExpect(status().isSeeOther());
      var level =
          action(sheet + "/level-ups", "feat-player", campaign, owner)
              .param("step", "review")
              .param("hitPointMethod", "average");
      if (tier == 3) level.param("subclassKey", "srd-5.1:champion");
      if (tier == 4) level.param("featKey", "srd-5.1:grappler");
      mockMvc.perform(level).andExpect(status().isSeeOther());
    }
    mockMvc
        .perform(get(sheet).with(user("feat-player")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Grappler")));
    assertThat(jdbcTemplate.queryForObject("SELECT feat_key FROM pc_asi", String.class))
        .isEqualTo("srd-5.1:grappler");
  }

  @Test
  void scriptedFightReplaysOriginalDiceAndKeepsDmAuditDetailsPrivate() throws Exception {
    UUID dm = characterAccount("combat-dm", true);
    UUID first = characterAccount("combat-first", false);
    UUID second = characterAccount("combat-second", false);
    UUID campaign = campaigns.create(dm, "Combat table").id();
    joinFixture(campaign, first, second);
    String collection = "/campaigns/" + campaign + "/encounters";
    String characters = "/campaigns/" + campaign + "/characters";
    UUID left = createCombatFighter(characters, campaign, first, "combat-first");
    UUID right = createCombatFighter(characters, campaign, second, "combat-second");
    var testPack =
        packLoader.loadFolder(
            java.nio.file.Path.of("src/test/resources/content-fixtures/step7-combat"),
            java.util.List.of());
    contentPacks.seed(testPack);
    jdbcTemplate.update(
        """
        INSERT INTO campaign_pack(campaign_id, pack_id, pack_key, position)
        SELECT ?, id, pack_key, 1 FROM content_pack WHERE hash = ?
        """,
        campaign,
        testPack.hash());
    settings.decide(
        campaign, dm, combatCampaignEnvelope(campaign, dm), "criticalDiceMode", "REJECT");
    play.start(campaign, dm, combatCampaignEnvelope(campaign, dm));

    mockMvc.perform(get(collection).with(user("combat-dm"))).andExpect(status().isOk());
    var created =
        mockMvc
            .perform(action(collection, "combat-dm", campaign, dm).param("name", "Practice bout"))
            .andExpect(status().isSeeOther())
            .andReturn();
    String url = created.getResponse().getRedirectedUrl();
    UUID encounter = UUID.fromString(url.substring(url.lastIndexOf('/') + 1));
    mockMvc
        .perform(get(url).with(user("combat-dm")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Training creature")));
    mockMvc.perform(get(url).with(user("combat-first"))).andExpect(status().isForbidden());
    for (UUID character : java.util.List.of(left, right)) {
      mockMvc
          .perform(
              combatAction(url + "/combatants", encounter)
                  .param("characterId", character.toString()))
          .andExpect(status().isSeeOther());
    }
    mockMvc
        .perform(
            combatAction(url + "/combatants", encounter)
                .param("monsterKey", "step7-test:training-creature"))
        .andExpect(status().isSeeOther());
    UUID leftEntry =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE character_id = ?", UUID.class, left);
    UUID rightEntry =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE character_id = ?", UUID.class, right);
    UUID goblin =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE monster_key IS NOT NULL", UUID.class);
    var faces = new java.util.ArrayDeque<>(java.util.List.of(18, 10, 13, 20, 20, 1, 4, 20));
    org.mockito.Mockito.when(combatDice.nextInt(org.mockito.ArgumentMatchers.anyInt()))
        .thenAnswer(
            invocation -> {
              int face = faces.removeFirst();
              assertThat(face).isBetween(1, invocation.getArgument(0, Integer.class));
              return face - 1;
            });
    mockMvc.perform(combatAction(url + "/start", encounter)).andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT acting_id FROM encounter", UUID.class))
        .isEqualTo(leftEntry);
    mockMvc
        .perform(get(url).with(user("combat-dm")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Unarmed strike")));

    mockMvc
        .perform(
            get(url + "/overrides/new")
                .with(user("combat-dm"))
                .header("HX-Request", "true")
                .param("kind", "set-hit-points")
                .param("combatantId", leftEntry.toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("override-panel")));
    mockMvc
        .perform(
            combatCorrection(url, encounter, leftEntry, 12, "quiet", "Private correction reason"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("visibility"));
    mockMvc
        .perform(
            combatCorrection(url, encounter, leftEntry, 12, "players", "Private correction reason"))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(combatCorrection(url, encounter, goblin, 4, "quiet", "Hidden creature HP reason"))
        .andExpect(status().isSeeOther());
    assertThat(encounters.visibleEvents(campaign, encounter, first))
        .hasSize(1)
        .allSatisfy(
            event ->
                assertThat(event.text())
                    .contains("12 of 13")
                    .doesNotContain("Private correction reason"));
    assertThat(encounters.visibleEvents(campaign, encounter, second))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.text())
                    .contains("12 of 13")
                    .doesNotContain("Private correction reason", "Hidden creature HP reason"));

    UUID attackId = commands.newActionId();
    long version = combatVersion(encounter);
    var hit =
        mockMvc
            .perform(combatStrike(url, attackId, version, leftEntry, goblin, "unarmed"))
            .andExpect(status().isOk())
            .andReturn();
    var replay =
        mockMvc
            .perform(combatStrike(url, attackId, version, leftEntry, goblin, "unarmed"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(replay.getModelAndView().getModel().get("replay")).isEqualTo(true);
    assertThat(replay.getModelAndView().getModel().get("result"))
        .isEqualTo(hit.getModelAndView().getModel().get("result"));
    mockMvc
        .perform(
            combatStrike(url, commands.newActionId(), version, leftEntry, goblin, "unarmed")
                .accept("application/problem+json"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENCOUNTER_VERSION_CONFLICT"));
    mockMvc
        .perform(
            combatStrike(url, attackId, version, leftEntry, rightEntry, "unarmed")
                .accept("application/problem+json"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    mockMvc
        .perform(combatAction(url + "/end-turn", encounter).param("actorId", leftEntry.toString()))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT acting_id FROM encounter", UUID.class))
        .isEqualTo(goblin);
    mockMvc
        .perform(
            combatStrike(
                url,
                commands.newActionId(),
                combatVersion(encounter),
                goblin,
                leftEntry,
                "step7-test:training-creature-attack-0"))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT count(*) FROM combat_event
        WHERE audience = 'DM' AND type = 'DiceRolled'
        AND payload @? '$.roll.terms[*] ? (@.critical_not_applied == true)'
        """,
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT hp_current FROM player_character WHERE id = ?", Integer.class, left))
        .isEqualTo(8);
    UUID attackEvent =
        jdbcTemplate.queryForObject(
            """
        SELECT id FROM combat_event WHERE encounter_id = ? AND type = 'AttackResolved' ORDER BY seq DESC LIMIT 1
        """,
            UUID.class,
            encounter);
    mockMvc
        .perform(
            combatAction(url + "/overrides", encounter)
                .param("kind", "undo-last")
                .param("entryId", attackEvent.toString())
                .param("reason", "Corrected target"))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT hp_current FROM player_character WHERE id = ?", Integer.class, left))
        .isEqualTo(12);
    mockMvc
        .perform(get(url + "/log").with(user("combat-dm")).header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Undone")));
    assertThat(encounters.visibleEvents(campaign, encounter, first).getFirst().text())
        .contains("undid the last attack")
        .doesNotContain("Corrected target");
    mockMvc
        .perform(combatAction(url + "/end-turn", encounter).param("actorId", goblin.toString()))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            combatStrike(
                url,
                commands.newActionId(),
                combatVersion(encounter),
                rightEntry,
                goblin,
                "unarmed"))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT npc_vitals->'health'->>'state' FROM combatant WHERE id = ?",
                String.class,
                goblin))
        .isEqualTo("DEAD");
    mockMvc
        .perform(
            combatAction(url + "/overrides", encounter)
                .param("kind", "add-condition")
                .param("combatantId", rightEntry.toString())
                .param("conditionKey", "srd-5.1:deafened")
                .param("reason", "Condition remains after the fight"))
        .andExpect(status().isSeeOther());
    mockMvc.perform(combatAction(url + "/end", encounter)).andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT state FROM encounter", String.class))
        .isEqualTo("FINISHED");
    assertThat(faces).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT count(*) FROM pc_condition WHERE player_character_id = ? AND condition_key = 'srd-5.1:deafened'
        """,
                Integer.class,
                right))
        .isEqualTo(1);
    assertThat(encounters.visibleEvents(campaign, encounter, first))
        .allSatisfy(
            event ->
                assertThat(event.text())
                    .doesNotContain("critical_not_applied", "Training creature", "reason"));
  }

  @Test
  void initiativeTiesReinforcementsSurpriseAndDeathSavesRespectTurnOrder() throws Exception {
    UUID dm = characterAccount("combat-dm", true);
    UUID first = characterAccount("initiative-first", false);
    UUID second = characterAccount("initiative-second", false);
    UUID campaign = campaigns.create(dm, "Initiative table").id();
    joinFixture(campaign, first, second);
    String characters = "/campaigns/" + campaign + "/characters";
    UUID left = createCombatFighter(characters, campaign, first, "initiative-first");
    UUID right = createCombatFighter(characters, campaign, second, "initiative-second");
    UUID arrival = createCombatFighter(characters, campaign, first, "initiative-first");
    play.start(campaign, dm, combatCampaignEnvelope(campaign, dm));
    var created =
        mockMvc
            .perform(
                action("/campaigns/" + campaign + "/encounters", "combat-dm", campaign, dm)
                    .param("name", "Initiative practice"))
            .andExpect(status().isSeeOther())
            .andReturn();
    String url = created.getResponse().getRedirectedUrl();
    UUID encounter = UUID.fromString(url.substring(url.lastIndexOf('/') + 1));
    for (UUID character : java.util.List.of(left, right)) {
      mockMvc
          .perform(
              combatAction(url + "/combatants", encounter)
                  .param("characterId", character.toString()))
          .andExpect(status().isSeeOther());
    }
    UUID firstEntry =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE character_id = ?", UUID.class, left);
    UUID secondEntry =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE character_id = ?", UUID.class, right);
    org.mockito.Mockito.when(combatDice.nextInt(20)).thenReturn(9, 9, 19);
    mockMvc
        .perform(
            combatAction(url + "/start", encounter).param("surprisedIds", secondEntry.toString()))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT state FROM encounter", String.class))
        .isEqualTo("INITIATIVE");
    mockMvc
        .perform(
            combatAction(url + "/initiative-ties", encounter)
                .param("order", firstEntry.toString(), secondEntry.toString()))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT acting_id FROM encounter", UUID.class))
        .isEqualTo(firstEntry);

    mockMvc
        .perform(
            combatAction(url + "/combatants", encounter)
                .param("characterId", arrival.toString())
                .param("fixedInitiative", "14"))
        .andExpect(status().isSeeOther());
    UUID lateEntry =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE character_id = ?", UUID.class, arrival);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT tactics->>'firstRound' FROM combatant WHERE id = ?",
                String.class,
                lateEntry))
        .isEqualTo("2");
    mockMvc
        .perform(
            combatCorrection(url, encounter, secondEntry, 0, "players", "Set up a dying combatant"))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(combatAction(url + "/end-turn", encounter).param("actorId", firstEntry.toString()))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT acting_id FROM encounter", UUID.class))
        .isEqualTo(secondEntry);
    mockMvc
        .perform(
            combatAction(url + "/death-saves", encounter).param("actorId", secondEntry.toString()))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT hp_current FROM player_character WHERE id = ?", Integer.class, right))
        .isEqualTo(1);
    mockMvc
        .perform(
            combatAction(url + "/dashes", encounter)
                .param("actorId", secondEntry.toString())
                .accept("application/problem+json"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("NO_ACTIONS_LEFT"));
    mockMvc
        .perform(
            combatAction(url + "/end-turn", encounter).param("actorId", secondEntry.toString()))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT acting_id FROM encounter", UUID.class))
        .isEqualTo(lateEntry);
    assertThat(commands.readClock(campaign, dm).gameSeconds()).isEqualTo(6);
    org.mockito.Mockito.verify(combatDice, org.mockito.Mockito.times(3)).nextInt(20);
  }

  private UUID createCombatFighter(String url, UUID campaign, UUID owner, String username)
      throws Exception {
    var response =
        mockMvc
            .perform(
                fighterForm(url, username, "review")
                    .param("actionId", commands.newActionId().toString())
                    .param(
                        "expectedVersion",
                        String.valueOf(commands.readClock(campaign, owner).version())))
            .andExpect(status().isSeeOther())
            .andReturn();
    String redirect = response.getResponse().getRedirectedUrl();
    return UUID.fromString(redirect.substring(redirect.lastIndexOf('/') + 1));
  }

  private com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope
      combatCampaignEnvelope(UUID campaign, UUID dm) {
    return new com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope(
        commands.newActionId(), commands.readClock(campaign, dm).version());
  }

  private long combatVersion(UUID encounter) {
    return jdbcTemplate.queryForObject(
        "SELECT version FROM encounter WHERE id = ?", Long.class, encounter);
  }

  private MockHttpServletRequestBuilder combatAction(String url, UUID encounter) {
    return post(url)
        .with(user("combat-dm"))
        .with(csrf())
        .param("actionId", commands.newActionId().toString())
        .param("expectedVersion", String.valueOf(combatVersion(encounter)));
  }

  private MockHttpServletRequestBuilder combatCorrection(
      String url, UUID encounter, UUID target, int amount, String visibility, String reason) {
    return combatAction(url + "/overrides", encounter)
        .param("kind", "set-hit-points")
        .param("combatantId", target.toString())
        .param("amount", String.valueOf(amount))
        .param("reason", reason)
        .param("visibility", visibility)
        .accept("application/problem+json");
  }

  private MockHttpServletRequestBuilder combatStrike(
      String url, UUID id, long version, UUID actor, UUID target, String attack) {
    return post(url + "/attacks")
        .with(user("combat-dm"))
        .with(csrf())
        .header("HX-Request", "true")
        .param("actionId", id.toString())
        .param("expectedVersion", String.valueOf(version))
        .param("actorId", actor.toString())
        .param("targetId", target.toString())
        .param("attackKey", attack);
  }

  private MockHttpServletRequestBuilder fighterForm(String url, String name, String step) {
    return post(url)
        .with(user(name))
        .with(csrf())
        .param("step", step)
        .param("speciesKey", "srd-5.1:human")
        .param("classKey", "srd-5.1:fighter")
        .param("fightingStyleKey", "srd-5.1:defense")
        .param("abilityMethod", "STANDARD_ARRAY")
        .param("abilityScores", "14", "13", "15", "8", "12", "10")
        .param("skillChoices", "athletics", "perception")
        .param("languageChoices", "elvish")
        .param(
            "equipmentChoices",
            "srd-5.1:fighter-kit-0-0",
            "srd-5.1:fighter-kit-1-0",
            "srd-5.1:fighter-kit-2-0",
            "srd-5.1:fighter-kit-3-0")
        .param("name", "Mira");
  }

  private MockHttpServletRequestBuilder levelAction(
      String sheet, UUID campaign, UUID actor, int tier, String stage, boolean acknowledgeLoss) {
    var request =
        action(sheet + "/level-ups", "player-one", campaign, actor)
            .param("step", stage)
            .param("hitPointMethod", "average");
    if (tier == 3) request.param("subclassKey", "srd-5.1:champion");
    if (tier == 4)
      request
          .param("abilityImprovements[0].ability", "STR")
          .param("abilityImprovements[0].amount", "2");
    if (acknowledgeLoss) request.param("acknowledgeLoss", "true");
    return request;
  }

  private MockHttpServletRequestBuilder action(
      String url, String username, UUID campaign, UUID actor) {
    return post(url)
        .with(user(username))
        .with(csrf())
        .param("actionId", commands.newActionId().toString())
        .param("expectedVersion", String.valueOf(commands.readClock(campaign, actor).version()));
  }

  private UUID characterAccount(String username, boolean admin) {
    UUID id = ServerIds.at(Instant.now());
    jdbcTemplate.update(
        "INSERT INTO app_user VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        id,
        username,
        "test-fixture-hash",
        username,
        admin);
    return id;
  }

  private void joinFixture(UUID campaign, UUID... players) {
    for (UUID player : players) {
      jdbcTemplate.update(
          "INSERT INTO campaign_member VALUES (?, ?, 'PLAYER', CURRENT_TIMESTAMP)",
          campaign,
          player);
    }
  }

  private UUID inventory(UUID actor, String key) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM inventory_item WHERE player_character_id = ? AND item_key = ?",
        UUID.class,
        actor,
        key);
  }

  @Test
  void applicationStartupSeedsFighterContentAndRejectsChangesUnderTheSameVersion()
      throws Exception {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '0.2.0'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT key FROM content_definition WHERE kind = 'class'", String.class))
        .isEqualTo("srd-5.1:fighter");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT version FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '0.2.0'",
                String.class))
        .isEqualTo("0.2.0");
    String manifest =
        jdbcTemplate.queryForObject(
            "SELECT manifest::text FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '0.2.0'",
            String.class);
    String notice = contentPacks.notice();
    var changed =
        new ContentPack(
            "srd-5.1",
            "0.2.0",
            "sha256:" + "1".repeat(64),
            "srd-5.1",
            "CC-BY-4.0",
            manifest,
            notice,
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of());
    assertThatThrownBy(() -> contentPacks.seed(changed))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("differs from the installed hash");
    mockMvc.perform(get("/help")).andExpect(status().is3xxRedirection());
    mockMvc
        .perform(get("/help").with(user("owner")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("Creative Commons Attribution 4.0")));
  }

  @Test
  void campaignCreationAddsDmAndClockAndReplaysOnlyForTheIssuingSession() throws Exception {
    registerAndReadHash("owner");
    var session = new MockHttpSession();
    var form =
        mockMvc
            .perform(get("/campaigns").with(user("owner")).session(session))
            .andExpect(status().isOk())
            .andReturn();
    UUID token = (UUID) form.getModelAndView().getModel().get("formToken");

    var create =
        mockMvc
            .perform(
                post("/campaigns")
                    .with(csrf())
                    .with(user("owner"))
                    .session(session)
                    .param("name", "Campaign A")
                    .param("formToken", token.toString()))
            .andExpect(status().isSeeOther())
            .andReturn();
    String location = create.getResponse().getRedirectedUrl();
    UUID campaign = jdbcTemplate.queryForObject("SELECT id FROM campaign", UUID.class);
    assertThat(campaign.version()).isEqualTo(7);
    assertThat(location).isEqualTo("/campaigns/" + campaign + "/setup");
    assertThat(jdbcTemplate.queryForObject("SELECT role FROM campaign_member", String.class))
        .isEqualTo("DM");
    assertThat(jdbcTemplate.queryForObject("SELECT game_seconds FROM game_clock", Long.class))
        .isZero();
    long started = System.nanoTime();
    var loaded = contentPacks.open(campaign);
    System.out.printf(
        "Step 5 cold SRD seed load: %.1f ms%n", (System.nanoTime() - started) / 1_000_000.0);
    assertThat(loaded.find("condition", "srd-5.1:prone")).isPresent();
    assertThat(contentPacks.open(campaign)).isSameAs(contentPacks.open(campaign));
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM campaign_pack", Integer.class))
        .isEqualTo(1);

    mockMvc
        .perform(
            post("/campaigns")
                .with(csrf())
                .with(user("owner"))
                .session(session)
                .param("name", "Campaign A")
                .param("formToken", token.toString()))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl(location));
    mockMvc
        .perform(
            post("/campaigns")
                .with(csrf())
                .with(user("owner"))
                .session(session)
                .param("name", "Different name")
                .param("formToken", token.toString()))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/campaigns")
                .with(csrf())
                .with(user("owner"))
                .param("name", "Campaign A")
                .param("formToken", token.toString()))
        .andExpect(status().isBadRequest());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM campaign", Integer.class))
        .isEqualTo(1);

    mockMvc.perform(get(location).with(user("owner"))).andExpect(status().isOk());
    mockMvc.perform(get("/campaigns/" + campaign).with(user("owner"))).andExpect(status().isOk());
  }

  @Test
  void anExpiredCampaignFormCannotCreateAndIsRemovedWhenANewFormIsIssued() throws Exception {
    registerAndReadHash("owner");
    var session = new MockHttpSession();
    var form =
        mockMvc
            .perform(get("/campaigns").with(user("owner")).session(session))
            .andExpect(status().isOk())
            .andReturn();
    UUID token = (UUID) form.getModelAndView().getModel().get("formToken");
    jdbcTemplate.update(
        "UPDATE form_token SET issued_at = CURRENT_TIMESTAMP - INTERVAL '25 hours' WHERE token = ?",
        token);

    mockMvc
        .perform(
            post("/campaigns")
                .with(csrf())
                .with(user("owner"))
                .session(session)
                .param("name", "Expired creation")
                .param("formToken", token.toString()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/campaigns").with(user("owner")).session(session))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM form_token WHERE token = ?", Integer.class, token))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM campaign", Integer.class))
        .isZero();
  }

  @Test
  void aNonmemberGets404AndDoesNotSeeTheCampaignInTheirList() throws Exception {
    registerAndReadHash("owner");
    UUID owner = jdbcTemplate.queryForObject("SELECT id FROM app_user", UUID.class);
    UUID outsider = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO app_user (id, username, password_hash, display_name, instance_admin, created_at)
        VALUES (?, 'outsider', 'test-hash', 'Outsider', FALSE, CURRENT_TIMESTAMP)
        """,
        outsider);
    UUID campaign = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO campaign (id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
        campaign,
        "Private campaign");
    jdbcTemplate.update("INSERT INTO game_clock (campaign_id) VALUES (?)", campaign);
    jdbcTemplate.update(
        """
        INSERT INTO campaign_member (campaign_id, user_id, role, joined_at)
        VALUES (?, ?, 'DM', CURRENT_TIMESTAMP)
        """,
        campaign,
        owner);

    mockMvc
        .perform(get("/campaigns/" + campaign).with(user("outsider")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/campaigns/" + campaign + "/setup").with(user("outsider")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/campaigns").with(user("outsider")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Private campaign"))));

    jdbcTemplate.update(
        """
        INSERT INTO campaign_member (campaign_id, user_id, role, joined_at)
        VALUES (?, ?, 'PLAYER', CURRENT_TIMESTAMP)
        """,
        campaign,
        outsider);
    mockMvc
        .perform(get("/campaigns/" + campaign).with(user("outsider")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/campaigns/" + campaign + "/setup").with(user("outsider")))
        .andExpect(status().isForbidden());
  }

  @Test
  void readinessIsPublicAndReportsDatabaseHealth() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  void inviteIssuedOnceLetsASecondAccountRegisterAndKeepsCampaignsPrivate() throws Exception {
    registerAndReadHash("owner");
    UUID otherCampaign = newCampaign("owner", "Other campaign");
    UUID campaign = newCampaign("owner", "Invite campaign");
    var session = new MockHttpSession();
    String base = "/campaigns/" + campaign;
    var form =
        mockMvc
            .perform(get(base + "/players").with(user("owner")).session(session))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("No invites yet.")))
            .andReturn();
    String token = form.getModelAndView().getModel().get("formToken").toString();
    var issue =
        mockMvc
            .perform(
                post(base + "/invites")
                    .with(user("owner"))
                    .with(csrf())
                    .session(session)
                    .param("formToken", token))
            .andExpect(status().isOk())
            .andReturn();
    String code = (String) issue.getModelAndView().getModel().get("inviteCode");
    assertThat(code.length()).isEqualTo(10);
    assertThat(issue.getResponse().getRedirectedUrl()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT octet_length(code_hash) FROM invite", Integer.class))
        .isEqualTo(32);
    mockMvc
        .perform(
            post(base + "/invites")
                .with(user("owner"))
                .with(csrf())
                .session(session)
                .param("formToken", token))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("The code was shown once.")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(code))));

    var register =
        mockMvc
            .perform(registration("guest", PASSWORD, code))
            .andExpect(status().isSeeOther())
            .andExpect(redirectedUrl("/campaigns"))
            .andReturn();
    var guestSession = (MockHttpSession) register.getRequest().getSession(false);
    mockMvc.perform(get(base).session(guestSession)).andExpect(status().isOk());
    mockMvc
        .perform(get("/campaigns/" + otherCampaign).session(guestSession))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(base + "/players").session(guestSession)).andExpect(status().isForbidden());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT instance_admin FROM app_user WHERE username = 'guest'", Boolean.class))
        .isFalse();
    assertThat(jdbcTemplate.queryForObject("SELECT uses_left FROM invite", Long.class)).isZero();
    mockMvc.perform(registration("third", PASSWORD, code)).andExpect(status().isForbidden());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Long.class))
        .isEqualTo(2);
    var hashes = jdbcTemplate.queryForList("SELECT password_hash FROM app_user", String.class);
    assertThat(hashes.get(0).equals(hashes.get(1))).isFalse();

    var join =
        mockMvc.perform(get("/join").session(guestSession)).andExpect(status().isOk()).andReturn();
    mockMvc
        .perform(
            post("/join")
                .session(guestSession)
                .with(csrf())
                .param("inviteCode", code)
                .param("formToken", join.getModelAndView().getModel().get("formToken").toString()))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl(base));
    assertThat(jdbcTemplate.queryForObject("SELECT uses_left FROM invite", Long.class)).isZero();
  }

  @Test
  void pausedSessionRejectsPlayAndClockRetriesDoNotApplyTwice() throws Exception {
    registerAndReadHash("owner");
    UUID campaign = newCampaign("owner", "Session campaign");
    String base = "/campaigns/" + campaign;
    mockMvc.perform(command(base + "/sessions", 0)).andExpect(status().isOk());
    UUID session = jdbcTemplate.queryForObject("SELECT id FROM game_session", UUID.class);
    mockMvc
        .perform(command(base + "/sessions/" + session + "/pause", 1))
        .andExpect(status().isOk());
    UUID blocked = ServerIds.at(Instant.now());
    mockMvc
        .perform(
            command(base + "/clock/advances", 2, blocked)
                .param("amount", "1")
                .param("unit", "minutes"))
        .andExpect(status().isConflict())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("SESSION_NOT_ACTIVE")));
    assertThat(jdbcTemplate.queryForObject("SELECT game_seconds FROM game_clock", Long.class))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM campaign_action", Long.class))
        .isEqualTo(2);

    mockMvc
        .perform(command(base + "/sessions/" + session + "/resume", 2))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            command(base + "/clock/advances", 3, blocked)
                .param("amount", "1")
                .param("unit", "minutes"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            command(base + "/clock/advances", 3, blocked)
                .param("amount", "1")
                .param("unit", "minutes"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            command(base + "/clock/advances", 3, blocked)
                .param("amount", "2")
                .param("unit", "minutes"))
        .andExpect(status().isConflict())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("IDEMPOTENCY_KEY_CONFLICT")));
    assertThat(jdbcTemplate.queryForObject("SELECT game_seconds FROM game_clock", Long.class))
        .isEqualTo(60);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM campaign_event WHERE type = 'ClockAdvanced'", Long.class))
        .isEqualTo(1);
  }

  @Test
  void wizardSkipLeavesInvitesEmptyAndSettingsHaveAnOrderedAuditTrail() throws Exception {
    registerAndReadHash("owner");
    UUID campaign = newCampaign("owner", "House rules campaign");
    String base = "/campaigns/" + campaign;
    mockMvc
        .perform(command(base + "/setup", 0).param("step", "1").param("skip", "true"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("HX-Redirect", base + "/players"));
    mockMvc
        .perform(get(base + "/players").with(user("owner")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("No invites yet.")));
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM invite", Long.class)).isZero();
    mockMvc.perform(get(base + "/settings").with(user("owner"))).andExpect(status().isOk());

    mockMvc
        .perform(
            command(base + "/settings/criticalDiceMode/decisions", 1).param("value", "UNSUPPORTED"))
        .andExpect(status().isUnprocessableContent());
    mockMvc
        .perform(
            command(base + "/settings/criticalDiceMode/decisions", 1)
                .param("value", "EXTRA_DIE_ONLY"))
        .andExpect(status().isOk());
    mockMvc
        .perform(command(base + "/settings/criticalDiceMode/resets", 2))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT payload->>'newValue' FROM campaign_event WHERE type = 'SettingDecided' ORDER BY seq",
                String.class))
        .containsExactly("EXTRA_DIE_ONLY", "ROLL_AGAIN_ADD");
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT settings_read->>'criticalDiceMode' FROM campaign_action
        WHERE kind IN ('SETTING_DECIDE', 'SETTING_RESET') ORDER BY created_at
        """,
                String.class))
        .containsExactly("ROLL_AGAIN_ADD", "EXTRA_DIE_ONLY");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM campaign_setting", Long.class))
        .isZero();
  }

  private UUID newCampaign(String owner, String name) throws Exception {
    var session = new MockHttpSession();
    var form =
        mockMvc
            .perform(get("/campaigns").with(user(owner)).session(session))
            .andExpect(status().isOk())
            .andReturn();
    mockMvc
        .perform(
            post("/campaigns")
                .with(user(owner))
                .with(csrf())
                .session(session)
                .param("name", name)
                .param("formToken", form.getModelAndView().getModel().get("formToken").toString()))
        .andExpect(status().isSeeOther());
    return jdbcTemplate.queryForObject("SELECT id FROM campaign WHERE name = ?", UUID.class, name);
  }

  @Test
  void sceneActivationIsCampaignScopedAndPlayerRecapKeepsDmNotesPrivate() throws Exception {
    registerAndReadHash("owner");
    UUID foreign = newCampaign("owner", "Foreign campaign");
    UUID campaign = newCampaign("owner", "Scene campaign");
    String base = "/campaigns/" + campaign;
    mockMvc
        .perform(
            command("/campaigns/" + foreign + "/scenes", 0)
                .param("name", "Foreign scene")
                .param("light", "bright"))
        .andExpect(status().isOk());
    UUID foreignScene =
        jdbcTemplate.queryForObject(
            "SELECT id FROM scene WHERE campaign_id = ?", UUID.class, foreign);
    mockMvc.perform(command(base + "/sessions", 0)).andExpect(status().isOk());
    mockMvc
        .perform(
            command(base + "/scenes", 1)
                .param("name", "Public scene")
                .param("notes", "DM secret notes")
                .param("light", "dark"))
        .andExpect(status().isOk());
    UUID scene =
        jdbcTemplate.queryForObject(
            "SELECT id FROM scene WHERE campaign_id = ?", UUID.class, campaign);
    mockMvc
        .perform(command(base + "/scenes/" + scene + "/activations", 2))
        .andExpect(status().isOk());
    mockMvc
        .perform(command(base + "/scenes/" + foreignScene + "/activations", 3))
        .andExpect(status().isNotFound());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT id FROM scene WHERE campaign_id = ? AND active", UUID.class, campaign))
        .isEqualTo(scene);

    UUID viewer = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO app_user (id, username, password_hash, display_name, instance_admin, created_at)
        VALUES (?, 'viewer', 'test-hash', 'Viewer', FALSE, CURRENT_TIMESTAMP)
        """,
        viewer);
    jdbcTemplate.update(
        """
        INSERT INTO campaign_member (campaign_id, user_id, role, joined_at)
        VALUES (?, ?, 'PLAYER', CURRENT_TIMESTAMP)
        """,
        campaign,
        viewer);
    String playerPage =
        mockMvc
            .perform(get(base).with(user("viewer")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(playerPage)
        .contains("Public scene", "SceneActivated")
        .doesNotContain("DM secret notes", "SceneCreated");
    mockMvc.perform(get(base + "/scenes").with(user("viewer"))).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(base + "/clock/advances")
                .with(user("viewer"))
                .with(csrf())
                .param("actionId", ServerIds.at(Instant.now()).toString())
                .param("expectedVersion", "3")
                .param("amount", "1")
                .param("unit", "hours"))
        .andExpect(status().isForbidden());
  }

  @Test
  void inviteFailuresUseTheSameErrorAndThrottlingDoesNotConsumeAnInvite() throws Exception {
    registerAndReadHash("owner");
    String base = "/campaigns/" + newCampaign("owner", "Rate limit campaign");
    var session = new MockHttpSession();
    var form =
        mockMvc
            .perform(get(base + "/players").with(user("owner")).session(session))
            .andExpect(status().isOk())
            .andReturn();
    var issue =
        mockMvc
            .perform(
                post(base + "/invites")
                    .with(user("owner"))
                    .with(csrf())
                    .session(session)
                    .param(
                        "formToken", form.getModelAndView().getModel().get("formToken").toString()))
            .andExpect(status().isOk())
            .andReturn();
    String valid = (String) issue.getModelAndView().getModel().get("inviteCode");
    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(
              registration("another", PASSWORD, "INVALID-CODE")
                  .with(
                      request -> {
                        request.setRemoteAddr("192.0.2.42");
                        return request;
                      }))
          .andExpect(status().isForbidden())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("INVITE_INVALID")));
    }
    mockMvc
        .perform(
            registration("another", PASSWORD, valid)
                .with(
                    request -> {
                      request.setRemoteAddr("192.0.2.42");
                      return request;
                    }))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Retry-After"));
    assertThat(jdbcTemplate.queryForObject("SELECT uses_left FROM invite", Long.class))
        .isEqualTo(1);
  }

  private static MockHttpServletRequestBuilder command(String path, long version) {
    return command(path, version, ServerIds.at(Instant.now()));
  }

  private static MockHttpServletRequestBuilder command(String path, long version, UUID actionId) {
    return post(path)
        .with(user("owner"))
        .with(csrf())
        .header("HX-Request", "true")
        .param("actionId", actionId.toString())
        .param("expectedVersion", Long.toString(version));
  }

  @Test
  void registrationRequiresCsrf() throws Exception {
    mockMvc
        .perform(
            post("/register")
                .param("username", "admin-user")
                .param("displayName", "Instance Admin")
                .param("password", PASSWORD))
        .andExpect(status().isForbidden());
  }

  @Test
  void firstRegistrationCreatesAnAdminAndLaterRegistrationRequiresAnInvite() throws Exception {
    mockMvc
        .perform(registration("Admin_User", PASSWORD, null))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl("/campaigns"));

    Boolean isAdmin =
        jdbcTemplate.queryForObject(
            "SELECT instance_admin FROM app_user WHERE username = ?", Boolean.class, "admin_user");
    String passwordHash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM app_user WHERE username = ?", String.class, "admin_user");

    assertThat(isAdmin).isTrue();
    assertThat(PASSWORD.equals(passwordHash)).isFalse();
    assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT initialized FROM instance_state WHERE id = 1", Boolean.class))
        .isTrue();

    mockMvc
        .perform(registration("second-user", PASSWORD, null))
        .andExpect(status().isForbidden())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("INVITE_INVALID")));

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void registeredAdminCanAuthenticateAndAccessTheProtectedHome() throws Exception {
    mockMvc
        .perform(registration("admin_user", PASSWORD, null))
        .andExpect(status().is3xxRedirection());

    mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/login")
                    .with(csrf())
                    .param("username", "ADMIN_USER")
                    .param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/campaigns"))
            .andReturn();

    MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
    assertThat(session).isNotNull();

    mockMvc
        .perform(get("/").session(session))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "Instance administrator account is ready.")));
  }

  @Test
  void registrationAndSignInAccept128CharactersWithoutCompositionRules() throws Exception {
    for (String password :
        new String[] {"a".repeat(127) + "Z", "🔑".repeat(127) + "ß", " ".repeat(128)}) {
      resetRegistrationState();
      mockMvc.perform(registration("owner", password, null)).andExpect(status().is3xxRedirection());

      mockMvc
          .perform(
              post("/login").with(csrf()).param("username", "owner").param("password", password))
          .andExpect(redirectedUrl("/campaigns"));
      mockMvc
          .perform(
              post("/login")
                  .with(csrf())
                  .param("username", "owner")
                  .param("password", "wrong password"))
          .andExpect(redirectedUrl("/login?error"));
    }
  }

  @Test
  void registrationRejects129CharactersAsAValidationErrorOnPassword() throws Exception {
    mockMvc
        .perform(registration("owner", "a".repeat(129), null))
        .andExpect(status().isBadRequest())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                .attributeHasFieldErrors("form", "password"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("VALIDATION_FAILED")));
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class))
        .isZero();
  }

  @Test
  void independentlyRegisteredAccountsWithTheSamePasswordHaveDistinctArgon2idHashes()
      throws Exception {
    String first = registerAndReadHash("first");
    resetRegistrationState();
    String second = registerAndReadHash("second");

    assertThat(first.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")).isTrue();
    assertThat(second.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")).isTrue();
    assertThat(first.equals(second)).isFalse();
    assertThat(passwordEncoder.matches(PASSWORD, first)).isTrue();
    assertThat(passwordEncoder.matches(PASSWORD, second)).isTrue();
  }

  private String registerAndReadHash(String username) throws Exception {
    mockMvc.perform(registration(username, PASSWORD, null)).andExpect(status().is3xxRedirection());
    return jdbcTemplate.queryForObject(
        "SELECT password_hash FROM app_user WHERE username = ?", String.class, username);
  }

  @Test
  void invalidRegistrationInputIsRejected() throws Exception {
    mockMvc.perform(registration("x", "short", null)).andExpect(status().isBadRequest());

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class))
        .isZero();
  }

  private MockHttpServletRequestBuilder registration(String username, String password, String code)
      throws Exception {
    var session = new MockHttpSession();
    var result =
        mockMvc.perform(get("/register").session(session)).andExpect(status().isOk()).andReturn();
    var form = (RegistrationForm) result.getModelAndView().getModel().get("form");
    var request =
        post("/register")
            .with(csrf())
            .session(session)
            .param("username", username)
            .param("password", password)
            .param("formToken", form.getFormToken().toString());
    if (code != null) {
      request.param("inviteCode", code);
    }
    return request;
  }
}
