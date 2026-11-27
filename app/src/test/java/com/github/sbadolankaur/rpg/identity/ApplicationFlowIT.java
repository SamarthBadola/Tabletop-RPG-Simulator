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
import com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.ClockUnit;
import com.github.sbadolankaur.rpg.content.BundledPacksFixture;
import com.github.sbadolankaur.rpg.content.ContentPack;
import com.github.sbadolankaur.rpg.content.ContentPacks;
import com.github.sbadolankaur.rpg.content.ContentPacks.UpgradeReview;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.AnswerKind;
import com.github.sbadolankaur.rpg.encounter.application.EncounterCommands.ReactionAnswer;
import com.github.sbadolankaur.rpg.encounter.application.EncounterDice;
import com.github.sbadolankaur.rpg.identity.web.RegistrationForm;
import com.github.sbadolankaur.rpg.platform.ServerIds;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.builder.SpringApplicationBuilder;
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

  @Autowired private com.github.sbadolankaur.rpg.rest.application.CampaignRests campaignRests;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private com.github.sbadolankaur.rpg.campaign.persistence.CommandStore commandStore;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ContentPacks contentPacks;
  @Autowired private com.github.sbadolankaur.rpg.campaign.persistence.ReactionStore reactions;

  @Autowired private com.github.sbadolankaur.rpg.content.PackLoader packLoader;
  @Autowired private com.github.sbadolankaur.rpg.campaign.application.CampaignPlay play;
  @Autowired private com.github.sbadolankaur.rpg.encounter.application.Encounters encounters;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private com.github.sbadolankaur.rpg.encounter.application.EncounterDice combatDice;

  @Autowired private com.github.sbadolankaur.rpg.campaign.application.Campaigns campaigns;
  @Autowired private com.github.sbadolankaur.rpg.campaign.application.CampaignCommands commands;
  @Autowired private com.github.sbadolankaur.rpg.campaign.application.CampaignSettings settings;

  @org.junit.jupiter.api.AfterEach
  void removeStepEightFixture() {
    jdbcTemplate.update(
        """
        DELETE FROM campaign_pack WHERE pack_id IN (
            SELECT id FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '99.8.0')
        """);
    jdbcTemplate.update(
        "DELETE FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '99.8.0'");
  }

  @BeforeEach
  void resetRegistrationState() {
    jdbcTemplate.update("DELETE FROM form_token");
    jdbcTemplate.update("DELETE FROM campaign");
    jdbcTemplate.update("DELETE FROM app_user");
    jdbcTemplate.update("UPDATE instance_state SET initialized = FALSE WHERE id = 1");
  }

  @Test
  void shortAndLongRestsPersistOnceAndReadEachHitDieMethodOnlyForHealing() throws Exception {
    UUID dm = characterAccount("rest-dm", true);
    UUID player = characterAccount("rest-player", false);
    UUID campaign = campaigns.create(dm, "Rest table").id();
    joinFixture(campaign, player);
    String base = "/campaigns/" + campaign;
    String rests = base + "/rests";
    UUID fighter = createCombatFighter(base + "/characters", campaign, player, "rest-player");
    jdbcTemplate.update("UPDATE player_character SET hp_current = 1 WHERE id = ?", fighter);
    jdbcTemplate.update(
        "UPDATE pc_resource SET used = 1 WHERE player_character_id = ? AND resource_key = ?",
        fighter,
        "srd-5.1:second-wind-uses");
    play.start(campaign, dm, combatCampaignEnvelope(campaign, dm));
    settings.decide(campaign, dm, combatCampaignEnvelope(campaign, dm), "hitDiceMethod", "FIXED");
    mockMvc.perform(get(rests + "/new").with(user("rest-dm"))).andExpect(status().isOk());
    mockMvc
        .perform(get(rests + "/new").with(user("rest-player")))
        .andExpect(status().isForbidden());
    long before = commands.readClock(campaign, dm).version();
    String spend = "spendCount[" + fighter + "]";
    mockMvc
        .perform(
            post(rests + "/previews")
                .with(user("rest-dm"))
                .with(csrf())
                .param("kind", "short")
                .param("durationMinutes", "60")
                .param("participantIds", fighter.toString())
                .param(spend, "1"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("1 Hit Dice available")));
    assertThat(commands.readClock(campaign, dm).version()).isEqualTo(before);
    var action = commands.newActionId();
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              post(rests)
                  .with(user("rest-dm"))
                  .with(csrf())
                  .param("kind", "short")
                  .param("durationMinutes", "60")
                  .param("participantIds", fighter.toString())
                  .param(spend, "1")
                  .param("expectedVersion", String.valueOf(before))
                  .param("actionId", action.toString()))
          .andExpect(status().isOk())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("HitDieSpent")));
    }
    mockMvc
        .perform(
            post(rests)
                .with(user("rest-dm"))
                .with(csrf())
                .accept("application/problem+json")
                .param("kind", "short")
                .param("durationMinutes", "61")
                .param("participantIds", fighter.toString())
                .param(spend, "1")
                .param("expectedVersion", String.valueOf(before))
                .param("actionId", action.toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    assertThat(commands.readClock(campaign, dm).gameSeconds()).isEqualTo(3600);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT hp_current FROM player_character WHERE id = ?", Integer.class, fighter))
        .isEqualTo(10);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT used FROM pc_resource WHERE player_character_id = ? AND resource_key = 'srd-5.1:second-wind-uses'",
                Integer.class,
                fighter))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT type FROM campaign_event WHERE action_id = ? ORDER BY seq",
                String.class,
                action))
        .containsExactly(
            "RestStarted",
            "ClockAdvanced",
            "HitDieSpent",
            "HpRegained",
            "ResourceRecharged",
            "RestCompleted");
    org.mockito.Mockito.verifyNoInteractions(combatDice);

    jdbcTemplate.update(
        "UPDATE player_character SET exhaustion = 1, hp_temp = 5 WHERE id = ?", fighter);
    mockMvc
        .perform(
            post(rests + "/previews")
                .with(user("rest-dm"))
                .with(csrf())
                .param("kind", "long")
                .param("durationMinutes", "480")
                .param("participantIds", fighter.toString()))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("Recover up to 1 spent Hit Dice")));
    mockMvc
        .perform(
            action(rests, "rest-dm", campaign, dm)
                .param("kind", "long")
                .param("durationMinutes", "480")
                .param("participantIds", fighter.toString())
                .param("ateAndDrank[" + fighter + "]", "no"))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT hp_current, hp_temp, exhaustion, last_long_rest_end_seconds FROM player_character WHERE id = ?",
                fighter))
        .containsEntry("hp_current", 13L)
        .containsEntry("hp_temp", 0L)
        .containsEntry("exhaustion", 1L)
        .containsEntry("last_long_rest_end_seconds", 32400L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT spent FROM pc_hit_dice WHERE player_character_id = ?",
                Integer.class,
                fighter))
        .isZero();
    mockMvc
        .perform(
            action(rests, "rest-dm", campaign, dm)
                .accept("application/problem+json")
                .param("kind", "long")
                .param("durationMinutes", "480")
                .param("participantIds", fighter.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("confirmWarnings"));
    assertThat(commands.readClock(campaign, dm).gameSeconds()).isEqualTo(32400);
    mockMvc
        .perform(
            action(rests, "rest-dm", campaign, dm)
                .param("kind", "long")
                .param("durationMinutes", "480")
                .param("participantIds", fighter.toString())
                .param("confirmWarnings", "true"))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT last_long_rest_end_seconds FROM player_character WHERE id = ?",
                Long.class,
                fighter))
        .isEqualTo(32400);
    mockMvc
        .perform(get(base).with(user("rest-player")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("long_rest_no_benefit"))));
  }

  @Test
  void seededShortRestsProduceTheSameOrderedAudit() throws Exception {
    UUID dm = characterAccount("rest-replay", true);
    var logs = new java.util.ArrayList<java.util.List<String>>();
    for (int attempt = 0; attempt < 2; attempt++) {
      UUID campaign = campaigns.create(dm, "Seeded table " + attempt).id();
      String base = "/campaigns/" + campaign;
      UUID fighter = createCombatFighter(base + "/characters", campaign, dm, "rest-replay");
      jdbcTemplate.update("UPDATE player_character SET hp_current = 1 WHERE id = ?", fighter);
      play.start(campaign, dm, combatCampaignEnvelope(campaign, dm));
      settings.decide(
          campaign, dm, combatCampaignEnvelope(campaign, dm), "hitDiceMethod", "ROLLED");

      var random = new java.util.SplittableRandom(105);
      org.mockito.Mockito.doAnswer(call -> random.nextInt(call.getArgument(0, Integer.class)))
          .when(combatDice)
          .nextInt(org.mockito.ArgumentMatchers.anyInt());
      var envelope = combatCampaignEnvelope(campaign, dm);
      mockMvc
          .perform(
              post(base + "/rests")
                  .with(user("rest-replay"))
                  .with(csrf())
                  .param("kind", "short")
                  .param("durationMinutes", "60")
                  .param("participantIds", fighter.toString())
                  .param("spendCount[" + fighter + "]", "1")
                  .param("expectedVersion", String.valueOf(envelope.expectedVersion()))
                  .param("actionId", envelope.actionId().toString()))
          .andExpect(status().isOk());
      logs.add(
          jdbcTemplate.queryForList(
              """
          SELECT jsonb_build_array(type, game_time_seconds, audience, payload - 'characterId')::text
          FROM campaign_event WHERE action_id = ? ORDER BY seq
          """,
              String.class,
              envelope.actionId()));
    }
    assertThat(logs.getFirst())
        .hasSize(5)
        .anyMatch(event -> event.contains("HitDieSpent") && event.contains("rolled"));
    assertThat(logs.getLast()).containsExactlyElementsOf(logs.getFirst());
  }

  @Test
  void restTransactionRollsBackAndConcurrentSubmissionsCheckTheVersion() throws Exception {
    UUID dm = characterAccount("rest-tx", true);
    UUID campaign = campaigns.create(dm, "Rest transaction").id();
    String base = "/campaigns/" + campaign;
    UUID fighter = createCombatFighter(base + "/characters", campaign, dm, "rest-tx");
    jdbcTemplate.update("UPDATE player_character SET hp_current = 1 WHERE id = ?", fighter);
    play.start(campaign, dm, combatCampaignEnvelope(campaign, dm));
    settings.decide(campaign, dm, combatCampaignEnvelope(campaign, dm), "hitDiceMethod", "FIXED");
    var input =
        new com.github.sbadolankaur.rpg.rest.application.RestInput(
            com.github.sbadolankaur.rpg.engine.rest.RestResolver.Kind.SHORT,
            60,
            java.util.List.of(fighter),
            java.util.Map.of(fighter, 1),
            java.util.Map.of(),
            false,
            java.util.Map.of(),
            java.util.Map.of(),
            java.util.Map.of(),
            java.util.List.of(),
            java.util.List.of());
    var envelope = combatCampaignEnvelope(campaign, dm);
    long nextVersion = envelope.expectedVersion() + 1;
    org.mockito.Mockito.doAnswer(
            call -> {
              call.callRealMethod();
              throw new IllegalStateException("After the clock write");
            })
        .when(commandStore)
        .updateClock(campaign, 3600, nextVersion);
    assertThatThrownBy(() -> campaignRests.submit(campaign, dm, envelope, input, false))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("After the clock write");
    assertThat(commands.readClock(campaign, dm).gameSeconds()).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM campaign_event WHERE action_id = ?",
                Integer.class,
                envelope.actionId()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM campaign_action WHERE action_id = ?",
                Integer.class,
                envelope.actionId()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT hp_current FROM player_character WHERE id = ?", Integer.class, fighter))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT spent FROM pc_hit_dice WHERE player_character_id = ?",
                Integer.class,
                fighter))
        .isZero();
    org.mockito.Mockito.doCallRealMethod()
        .when(commandStore)
        .updateClock(campaign, 3600, nextVersion);

    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var barrier = new java.util.concurrent.CyclicBarrier(2);
      java.util.concurrent.Callable<String> submit =
          () -> {
            var attempt = combatCampaignEnvelope(campaign, dm);
            barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
            try {
              campaignRests.submit(campaign, dm, attempt, input, false);
              return "complete";
            } catch (com.github.sbadolankaur.rpg.campaign.application.CampaignException failure) {
              return failure.code().name();
            }
          };
      var first = pool.submit(submit);
      var second = pool.submit(submit);
      assertThat(java.util.List.of(first.get(), second.get()))
          .containsExactlyInAnyOrder("complete", "CAMPAIGN_VERSION_CONFLICT");
    }
    assertThat(commands.readClock(campaign, dm).gameSeconds()).isEqualTo(3600);
  }

  @Test
  void resumeReadsTheSameMidEncounterStateFromPostgresAfterRestart() throws Exception {
    var bout = combatFixture(false);
    mockMvc
        .perform(
            action(
                    "/campaigns/" + bout.campaign() + "/rests",
                    "combat-dm",
                    bout.campaign(),
                    bout.dm())
                .param("kind", "long")
                .param("durationMinutes", "480")
                .param("participantIds", bout.left().toString()))
        .andExpect(status().isOk());
    org.mockito.Mockito.when(combatDice.nextInt(20)).thenReturn(18, 8);
    submitBout(bout, "/start");
    mockMvc
        .perform(
            conditionCorrection(
                    bout, bout.first(), "srd-5.1:blinded", bout.second(), "add-condition")
                .param("durationKind", "clock")
                .param("durationAmount", "600"))
        .andExpect(status().isSeeOther());
    String persistedEffect = conditionEffects(bout.left());
    assertThat(persistedEffect).contains("creationOrder", "\"seconds\": 29400");
    var before = encounters.view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 10);
    var paused = play.workspace(bout.campaign(), bout.dm());
    UUID sessionId = paused.session().id();
    play.transition(
        bout.campaign(),
        bout.dm(),
        combatCampaignEnvelope(bout.campaign(), bout.dm()),
        sessionId,
        com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.Transition.PAUSE);
    try (var restarted = restartWithDice(new EncounterDice())) {
      var resumedPlay =
          restarted.getBean(com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.class);
      var resumedCommands =
          restarted.getBean(
              com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.class);
      var resumedEncounters =
          restarted.getBean(com.github.sbadolankaur.rpg.encounter.application.Encounters.class);
      assertThat(resumedPlay.workspace(bout.campaign(), bout.dm()).session().state().name())
          .isEqualTo("PAUSED");
      var envelope =
          new com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope(
              resumedCommands.newActionId(),
              resumedCommands.readClock(bout.campaign(), bout.dm()).version());
      resumedPlay.transition(
          bout.campaign(),
          bout.dm(),
          envelope,
          sessionId,
          com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.Transition.RESUME);
      var after = resumedEncounters.view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 10);
      assertThat(after.table()).isEqualTo(before.table());
      assertThat(after.combatants()).isEqualTo(before.combatants());
      assertThat(conditionEffects(bout.left())).isEqualTo(persistedEffect);
      assertThat(resumedPlay.workspace(bout.campaign(), bout.dm()).recap())
          .isEqualTo(paused.recap());
      assertThat(resumedCommands.readClock(bout.campaign(), bout.dm()).gameSeconds())
          .isEqualTo(28800);
      assertThat(
              restarted
                  .getBean(com.github.sbadolankaur.rpg.character.persistence.CharacterStore.class)
                  .lastLongRest(bout.left()))
          .isEqualTo(28800);
    }
  }

  @Test
  void restsExpireEqualDeadlinesInPersistedCreationOrderBeforeDawn() throws Exception {
    var bout = combatFixture(false);
    settings.decide(
        bout.campaign(),
        bout.dm(),
        combatCampaignEnvelope(bout.campaign(), bout.dm()),
        "dawnTime",
        "08:05");
    UUID right =
        jdbcTemplate.queryForObject(
            "SELECT character_id FROM combatant WHERE id = ?", UUID.class, bout.second());
    org.mockito.Mockito.when(combatDice.nextInt(20)).thenReturn(18, 8);
    submitBout(bout, "/start");
    for (UUID target : java.util.List.of(bout.second(), bout.first())) {
      mockMvc
          .perform(
              conditionCorrection(bout, target, "srd-5.1:blinded", target, "add-condition")
                  .param("durationKind", "clock")
                  .param("durationAmount", "360"))
          .andExpect(status().isSeeOther());
    }
    submitBout(bout, "/end");
    var json = tools.jackson.databind.json.JsonMapper.builder().build();
    long rightOrder = json.readTree(conditionEffects(right)).get(0).path("creationOrder").asLong();
    long leftOrder =
        json.readTree(conditionEffects(bout.left())).get(0).path("creationOrder").asLong();
    assertThat(rightOrder).isPositive().isLessThan(leftOrder);

    var envelope = combatCampaignEnvelope(bout.campaign(), bout.dm());
    mockMvc
        .perform(
            post("/campaigns/" + bout.campaign() + "/rests")
                .with(user("combat-dm"))
                .with(csrf())
                .param("kind", "short")
                .param("durationMinutes", "60")
                .param("participantIds", bout.left().toString())
                .param("expectedVersion", String.valueOf(envelope.expectedVersion()))
                .param("actionId", envelope.actionId().toString()))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT type || COALESCE(':' || (payload ->> 'characterId'), '')
        FROM campaign_event WHERE action_id = ? ORDER BY seq
        """,
                String.class,
                envelope.actionId()))
        .containsExactly(
            "RestStarted",
            "ClockAdvanced",
            "EffectExpired:" + right,
            "EffectExpired:" + bout.left(),
            "DawnReached",
            "RestCompleted");
    assertThat(conditionEffects(right)).isEqualTo("[]");
    assertThat(conditionEffects(bout.left())).isEqualTo("[]");
  }

  @Test
  void manualClockExpiresNpcAndPlayerEffectsInTheSameOrderDuringCombat() throws Exception {
    var bout = combatFixture(true);
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("monsterKey", "step7-test:training-creature"))
        .andExpect(status().isSeeOther());
    UUID npc =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE encounter_id = ? AND character_id IS NULL",
            UUID.class,
            bout.id());
    org.mockito.Mockito.when(combatDice.nextInt(20)).thenReturn(18, 11, 4);
    submitBout(bout, "/start");
    for (UUID target : java.util.List.of(npc, bout.first())) {
      mockMvc
          .perform(
              conditionCorrection(bout, target, "srd-5.1:blinded", target, "add-condition")
                  .param("durationKind", "clock")
                  .param("durationAmount", "60"))
          .andExpect(status().isSeeOther());
    }
    String campaign = "/campaigns/" + bout.campaign();
    mockMvc
        .perform(
            action(campaign + "/rests", "combat-dm", bout.campaign(), bout.dm())
                .accept("application/problem+json")
                .param("kind", "short")
                .param("durationMinutes", "60")
                .param("participantIds", bout.left().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENCOUNTER_ACTIVE"));

    var envelope = combatCampaignEnvelope(bout.campaign(), bout.dm());
    long combatVersion = combatVersion(bout.id());
    play.advance(bout.campaign(), bout.dm(), envelope, 1, ClockUnit.MINUTES);
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT COALESCE(payload ->> 'combatantId', payload ->> 'characterId') FROM campaign_event
        WHERE action_id = ? AND type = 'EffectExpired' ORDER BY seq
        """,
                String.class,
                envelope.actionId()))
        .containsExactly(npc.toString(), bout.left().toString());
    assertThat(conditionEffects(bout.left())).isEqualTo("[]");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT (npc_vitals -> 'effects')::text FROM combatant WHERE id = ?",
                String.class,
                npc))
        .isEqualTo("[]");
    assertThat(combatVersion(bout.id())).isEqualTo(combatVersion + 1);
  }

  @Test
  void advancingAndUndoingACombatRoundExpiresAndRestoresAnOffRosterEffect() throws Exception {
    var bout = combatFixture(false);
    UUID outside =
        createCombatFighter(
            "/campaigns/" + bout.campaign() + "/characters",
            bout.campaign(),
            bout.dm(),
            "combat-dm");
    jdbcTemplate.update(
        """
        UPDATE player_character SET condition_effects = CAST(? AS jsonb) WHERE id = ?
        """,
        """
        [{"key":"srd-5.1:blinded","end":{"kind":"CLOCK","seconds":6},"creationOrder":1}]
        """,
        outside);
    jdbcTemplate.update(
        """
        UPDATE player_character SET condition_effects = CAST(? AS jsonb) WHERE id = ?
        """,
        """
        [{"key":"srd-5.1:deafened","end":{"kind":"CLOCK","seconds":6},"creationOrder":2}]
        """,
        bout.left());
    org.mockito.Mockito.when(combatDice.nextInt(20)).thenReturn(18, 8);
    submitBout(bout, "/start");
    for (UUID actor : java.util.List.of(bout.first(), bout.second())) {
      mockMvc
          .perform(
              combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", actor.toString()))
          .andExpect(status().isSeeOther());
    }
    assertThat(commands.readClock(bout.campaign(), bout.dm()).gameSeconds()).isEqualTo(6);
    assertThat(conditionEffects(outside)).isEqualTo("[]");
    assertThat(conditionEffects(bout.left())).isEqualTo("[]");
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT type FROM combat_event WHERE encounter_id = ?
          AND type IN ('EffectExpired','ConditionsExpired') ORDER BY seq
        """,
                String.class,
                bout.id()))
        .containsExactly("EffectExpired", "ConditionsExpired");
    UUID latest =
        encounters.view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50).reversible();
    mockMvc
        .perform(
            combatAction(bout.url() + "/overrides", bout.id())
                .param("kind", "undo-last")
                .param("entryId", latest.toString())
                .param("reason", "Replay the turn"))
        .andExpect(status().isSeeOther());
    assertThat(commands.readClock(bout.campaign(), bout.dm()).gameSeconds()).isZero();
    assertThat(conditionEffects(outside)).contains("srd-5.1:blinded", "\"creationOrder\": 1");
    assertThat(conditionEffects(bout.left())).contains("srd-5.1:deafened", "\"creationOrder\": 2");
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
                    .param("targetVersion", "0.3.1"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm upgrade")))
            .andReturn();
    var preview = (UpgradeReview) result.getModelAndView().getModel().get("review");
    assertThat(preview.groups()).isEmpty();
    assertThat(commands.readClock(campaign, dm).version()).isEqualTo(version);
    mockMvc
        .perform(
            action(apply, "upgrade-player", campaign, player)
                .param("targetVersion", "0.3.1")
                .param("reviewHash", preview.hash()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            action(apply, "upgrade-dm", campaign, dm)
                .param("targetVersion", "0.3.1")
                .param("reviewHash", "expired-review"))
        .andExpect(status().isConflict());
    assertThat(contentPacks.open(campaign).find("class", "srd-5.1:fighter")).isEmpty();
    mockMvc
        .perform(
            action(apply, "upgrade-dm", campaign, dm)
                .param("targetVersion", "0.3.1")
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
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT copper + silver * 10 + electrum * 50 + gold * 100 + platinum * 1000
                FROM player_character
                """,
                Integer.class))
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
            combatAction(url + "/overrides", encounter)
                .header("HX-Request", "true")
                .param("kind", "set-hit-points")
                .param("combatantId", leftEntry.toString())
                .param("reason", "Retain the proposed reason")
                .param("amount", "12")
                .param("visibility", "quiet"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "A player can now see this value, so the correction will be shown to them.")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Retain the proposed reason")));
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
    org.mockito.Mockito.when(combatDice.nextInt(20)).thenReturn(9, 9, 16, 19);
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
                .param("fixedInitiative", "14")
                .param("obscuredFromIds", secondEntry.toString()))
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
    UUID lastEvent =
        jdbcTemplate.queryForObject(
            """
        SELECT id FROM combat_event WHERE encounter_id = ? AND audience = 'DM' ORDER BY seq DESC LIMIT 1
        """,
            UUID.class,
            encounter);
    mockMvc
        .perform(
            combatAction(url + "/overrides", encounter)
                .param("kind", "undo-last")
                .param("entryId", lastEvent.toString())
                .param("reason", "Restore the previous turn"))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT acting_id FROM encounter", UUID.class))
        .isEqualTo(secondEntry);
    assertThat(commands.readClock(campaign, dm).gameSeconds()).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT tactics->'stealth'->'hiddenFrom' FROM combatant WHERE id = ?",
                String.class,
                lateEntry))
        .contains(secondEntry.toString());
    org.mockito.Mockito.verify(combatDice, org.mockito.Mockito.times(4)).nextInt(20);
  }

  @Test
  void combatConditionsKeepSourcesExpireAndSurviveUndo() throws Exception {
    var bout = combatFixture(false);
    forceCombatDice(18, 11, 8, 18, 5);
    submitBout(bout, "/start");
    var first = bout.first();
    var second = bout.second();
    var poison = "srd-5.1:poisoned";

    mockMvc
        .perform(
            conditionCorrection(bout, first, poison, second, "add-condition")
                .param("durationKind", "until-turn-end")
                .param("durationCreatureId", first.toString()))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            conditionCorrection(bout, first, poison, first, "add-condition")
                .param("durationKind", "clock")
                .param("durationAmount", "12"))
        .andExpect(status().isSeeOther());
    assertThat(conditionEffects(bout.left())).contains("UNTIL_TURN_END", "CLOCK");

    mockMvc
        .perform(
            combatAction(bout.url() + "/overrides", bout.id())
                .param("kind", "set-exhaustion")
                .param("combatantId", first.toString())
                .param("amount", "4")
                .param("reason", "Apply four levels"))
        .andExpect(status().isSeeOther());
    var owner =
        encounters
            .view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50)
            .combatants()
            .getFirst();
    assertThat(owner.health().current()).isEqualTo(6);
    assertThat(owner.health().maximum()).isEqualTo(6);
    assertThat(owner.speed()).isEqualTo(15);

    mockMvc
        .perform(
            combatAction(bout.url() + "/saving-throws", bout.id())
                .param("actorId", first.toString())
                .param("targetIds", second.toString())
                .param("ability", "dexterity")
                .param("dc", "12")
                .param("cover[" + second + "]", "total")
                .accept("application/problem+json"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("TOTAL_COVER"));
    var coverSaves =
        result(
                mockMvc
                    .perform(
                        combatAction(bout.url() + "/saving-throws", bout.id())
                            .header("HX-Request", "true")
                            .param("actorId", first.toString())
                            .param("ability", "dexterity")
                            .param("dc", "12")
                            .param("targetIds", first.toString(), second.toString())
                            .param("cover[" + first + "]", "half")
                            .param("cover[" + second + "]", "three-quarters"))
                    .andExpect(status().isOk())
                    .andReturn())
            .path("outcomes");
    assertThat(coverSaves.get(0).path("roll").path("naturalD20").asInt()).isEqualTo(8);
    assertThat(coverSaves.get(0).path("roll").path("total").asInt()).isEqualTo(12);
    assertThat(coverSaves.get(1).path("roll").path("total").asInt()).isEqualTo(12);
    assertThat(coverSaves.get(0).path("success").asBoolean()).isTrue();
    assertThat(coverSaves.get(1).path("success").asBoolean()).isTrue();

    mockMvc
        .perform(
            combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", first.toString()))
        .andExpect(status().isSeeOther());
    assertThat(conditionEffects(bout.left())).contains("CLOCK").doesNotContain("UNTIL_TURN_END");
    mockMvc
        .perform(conditionCorrection(bout, first, poison, first, "remove-condition"))
        .andExpect(status().isSeeOther());
    assertThat(conditionEffects(bout.left())).isEqualTo("[]");

    mockMvc
        .perform(
            conditionCorrection(bout, first, poison, second, "add-condition")
                .param("durationKind", "rounds")
                .param("durationAmount", "2"))
        .andExpect(status().isSeeOther());
    submitBout(bout, "/end");
    mockMvc
        .perform(get(bout.url()).with(user("combat-dm")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Encounter summary")));
    assertThat(conditionEffects(bout.left())).contains("CLOCK", "\"seconds\": 12");
    UUID latest =
        encounters.view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50).reversible();
    mockMvc
        .perform(
            combatAction(bout.url() + "/overrides", bout.id())
                .param("kind", "undo-last")
                .param("entryId", latest.toString())
                .param("reason", "Reopen the encounter"))
        .andExpect(status().isSeeOther());
    assertThat(conditionEffects(bout.left())).contains("ROUNDS", "\"round\": 3");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT acting_id FROM encounter WHERE id = ?", UUID.class, bout.id()))
        .isEqualTo(second);
  }

  @Test
  void stealthTiesSuggestSurpriseAndAnNpcCanUsePlayerDeathSaves() throws Exception {
    var bout = combatFixture(true);
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("monsterKey", "step7-test:training-creature")
                .param("deathMode", "player"))
        .andExpect(status().isSeeOther());
    UUID monster =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE encounter_id = ? AND monster_key IS NOT NULL",
            UUID.class,
            bout.id());
    var first = bout.first().toString();
    var second = bout.second().toString();
    forceCombatDice(11, 18, 7, 12, 7, 14, 20);
    var hide =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/hides", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", first)
                        .param("obscuredFromIds", second, monster.toString())
                        .param("opponentIds", second, monster.toString()))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(hide.path("stealth").path("total").asInt()).isEqualTo(13);
    assertThat(hide.path("stealth").path("ties").get(0).asString()).isEqualTo(second);
    assertThat(hide.path("suggestedSurprisedIds").get(0).asString()).isEqualTo(monster.toString());
    var resolved =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/stealth-ties", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", first))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(resolved.path("suggestedSurprisedIds").size()).isEqualTo(2);

    mockMvc
        .perform(combatAction(bout.url() + "/start", bout.id()).param("surprisedIds", second))
        .andExpect(status().isSeeOther());
    var current = encounters.view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50);
    assertThat(current.combatants().stream().filter(row -> row.surprised()).map(row -> row.id()))
        .containsExactly(bout.second());
    mockMvc
        .perform(combatCorrection(bout.url(), bout.id(), monster, 3, "quiet", "Set up the blow"))
        .andExpect(status().isSeeOther());
    var strike =
        result(
            mockMvc
                .perform(
                    combatStrike(
                        bout.url(),
                        commands.newActionId(),
                        combatVersion(bout.id()),
                        bout.first(),
                        monster,
                        "unarmed"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(strike.path("attack").path("roll").path("naturalD20").asInt()).isEqualTo(14);
    assertThat(strike.path("hitPointsAfter").path("state").asString()).isEqualTo("DYING");
    mockMvc
        .perform(combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", first))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            combatAction(bout.url() + "/death-saves", bout.id())
                .param("actorId", monster.toString()))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT npc_vitals->'health'->>'current' FROM combatant WHERE id = ?",
                String.class,
                monster))
        .isEqualTo("1");
  }

  @Test
  void grappleTiesFailAndAnIncapacitatedTargetRequiresNoDice() throws Exception {
    var bout = combatFixture(false);
    forceCombatDice(18, 11, 10, 10);
    submitBout(bout, "/start");
    assertThat(grapple(bout).path("success").asBoolean()).isFalse();
    var last =
        encounters.view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50).reversible();
    mockMvc
        .perform(
            combatAction(bout.url() + "/overrides", bout.id())
                .param("kind", "undo-last")
                .param("entryId", last.toString())
                .param("reason", "Restore action"))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            conditionCorrection(
                bout, bout.second(), "srd-5.1:incapacitated", bout.first(), "add-condition"))
        .andExpect(status().isSeeOther());
    assertThat(grapple(bout).path("success").asBoolean()).isTrue();
    assertThat(
            encounters
                .view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50)
                .combatants()
                .getLast()
                .speed())
        .isZero();
    mockMvc
        .perform(
            combatAction(bout.url() + "/grapple-releases", bout.id())
                .param("actorId", bout.first().toString())
                .param("targetId", bout.second().toString()))
        .andExpect(status().isSeeOther());
    assertThat(
            encounters
                .view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50)
                .combatants()
                .getLast()
                .conditions())
        .containsExactly("srd-5.1:incapacitated");
    org.mockito.Mockito.verify(combatDice, org.mockito.Mockito.times(4)).nextInt(20);
  }

  @Test
  void sceneCheckReplaysAnInspirationDieAndFreezesTheSceneAndSubject() throws Exception {
    var bout = combatFixture(false);
    var scene =
        play.saveScene(
            bout.campaign(),
            bout.dm(),
            combatCampaignEnvelope(bout.campaign(), bout.dm()),
            null,
            new com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.SceneInput(
                "Investigation",
                null,
                com.github.sbadolankaur.rpg.campaign.persistence.PlayStore.Light.BRIGHT));
    String sceneId = scene.data().path("sceneId").asString();
    String base = "/campaigns/" + bout.campaign();
    String scenePath = base + "/scenes/" + sceneId;
    jdbcTemplate.update(
        """
        UPDATE player_character SET bardic_inspiration = '{"dieFaces":6,"endsAt":600}'::jsonb WHERE id = ?
        """,
        bout.left());
    forceCombatDice(8, 6);
    var opening =
        action(scenePath + "/ability-checks", "combat-dm", bout.campaign(), bout.dm())
            .header("HX-Request", "true")
            .param("ability", "str")
            .param("dc", "15")
            .param("subjectIds", bout.left().toString());
    var waiting = result(mockMvc.perform(opening).andExpect(status().isOk()).andReturn());
    assertThat(waiting.path("reaction").path("prompt").path("point").asString())
        .isEqualTo("AFTER_CHECK_ROLL");
    mockMvc
        .perform(get(scenePath + "/reaction").with(user("combat-dm")).header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Use Bardic Inspiration")));
    mockMvc
        .perform(
            action(scenePath + "/edits", "combat-dm", bout.campaign(), bout.dm())
                .accept("application/problem+json")
                .param("name", "Frozen")
                .param("light", "BRIGHT"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REACTION_PENDING"));
    mockMvc
        .perform(
            action(
                    base + "/characters/" + bout.left() + "/edits",
                    "combat-dm",
                    bout.campaign(),
                    bout.dm())
                .accept("application/problem+json")
                .param("section", "details")
                .param("name", "Frozen"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REACTION_PENDING"));
    String answer = base + "/reactions/" + waiting.path("awaitingReaction").asString() + "/answers";
    var choice =
        action(answer, "combat-dm", bout.campaign(), bout.dm())
            .header("HX-Request", "true")
            .param("deciderId", bout.left().toString())
            .param("choice", "use")
            .param("optionKey", "bardic-inspiration");
    var completed = result(mockMvc.perform(choice).andExpect(status().isOk()).andReturn());
    assertThat(completed.path("outcomes").get(0).path("roll").path("naturalD20").asInt())
        .isEqualTo(8);
    assertThat(completed.path("outcomes").get(0).path("success").asBoolean()).isTrue();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pending_reaction", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM player_character WHERE bardic_inspiration IS NOT NULL",
                Long.class))
        .isZero();
  }

  @Test
  void bundledClassesCreateAndReachTheirApprovedLevelThreeFeatures() throws Exception {
    UUID dm = characterAccount("class-dm", true);
    UUID rogueOwner = characterAccount("class-rogue", false);
    UUID bardOwner = characterAccount("class-bard", false);
    UUID paladinOwner = characterAccount("class-paladin", false);
    UUID campaign = campaigns.create(dm, "Class rules").id();
    joinFixture(campaign, rogueOwner, bardOwner, paladinOwner);
    String base = "/campaigns/" + campaign + "/characters";

    UUID rogue =
        createClassCharacter(
            base,
            campaign,
            rogueOwner,
            "class-rogue",
            "rogue",
            new String[] {"athletics", "perception", "stealth", "investigation"},
            "athletics",
            "thieves_tools");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pc_proficiency WHERE player_character_id = ? AND kind = 'expertise'",
                Long.class,
                rogue))
        .isEqualTo(2);
    mockMvc
        .perform(get(base + "/" + rogue).with(user("class-rogue")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Sneak Attack")));

    UUID bard =
        createClassCharacter(
            base,
            campaign,
            bardOwner,
            "class-bard",
            "bard",
            new String[] {"athletics", "perception", "performance"});
    var bardSheet = base + "/" + bard;
    grantClassLevel(bardSheet, campaign, dm);
    mockMvc
        .perform(
            action(bardSheet + "/level-ups", "class-bard", campaign, bardOwner)
                .param("step", "review")
                .param("hitPointMethod", "average"))
        .andExpect(status().isSeeOther());
    grantClassLevel(bardSheet, campaign, dm);
    mockMvc
        .perform(
            action(bardSheet + "/level-ups", "class-bard", campaign, bardOwner)
                .param("step", "review")
                .param("hitPointMethod", "average")
                .param("subclassKey", "srd-5.1:college-of-lore")
                .param("bonusSkills", "stealth", "persuasion", "arcana")
                .param("expertiseChoices", "stealth", "performance"))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pc_proficiency WHERE player_character_id = ? AND source = 'srd-5.1:college-of-lore'",
                Long.class,
                bard))
        .isEqualTo(3);
    mockMvc
        .perform(get(bardSheet).with(user("class-bard")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cutting Words")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("<dt>Initiative</dt><dd>3</dd>")));

    UUID paladin =
        createClassCharacter(
            base,
            campaign,
            paladinOwner,
            "class-paladin",
            "paladin",
            new String[] {"athletics", "intimidation"});
    var paladinSheet = base + "/" + paladin;
    grantClassLevel(paladinSheet, campaign, dm);
    mockMvc
        .perform(
            action(paladinSheet + "/level-ups", "class-paladin", campaign, paladinOwner)
                .param("step", "review")
                .param("hitPointMethod", "average")
                .param("fightingStyleKey", "srd-5.1:protection"))
        .andExpect(status().isSeeOther());
    grantClassLevel(paladinSheet, campaign, dm);
    mockMvc
        .perform(
            action(paladinSheet + "/level-ups", "class-paladin", campaign, paladinOwner)
                .param("step", "review")
                .param("hitPointMethod", "average")
                .param("subclassKey", "srd-5.1:oath-of-devotion"))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT fighting_style_key FROM player_character WHERE id = ?",
                String.class,
                paladin))
        .isEqualTo("srd-5.1:protection");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pc_resource WHERE player_character_id = ? AND resource_key = 'srd-5.1:channel-divinity-uses'",
                Long.class,
                paladin))
        .isOne();
  }

  private UUID createClassCharacter(
      String url,
      UUID campaign,
      UUID actor,
      String username,
      String className,
      String[] skills,
      String... expertise)
      throws Exception {
    var request =
        action(url, username, campaign, actor)
            .param("step", "review")
            .param("speciesKey", "srd-5.1:human")
            .param("classKey", "srd-5.1:" + className)
            .param("abilityMethod", "STANDARD_ARRAY")
            .param("abilityScores", "14", "13", "15", "8", "12", "10")
            .param("skillChoices", skills)
            .param("languageChoices", "elvish")
            .param("equipmentChoices", "srd-5.1:starting-gold")
            .param("name", className);
    if (expertise.length > 0) request.param("expertiseChoices", expertise);
    String redirect =
        mockMvc
            .perform(request)
            .andExpect(status().isSeeOther())
            .andReturn()
            .getResponse()
            .getRedirectedUrl();
    return UUID.fromString(redirect.substring(redirect.lastIndexOf('/') + 1));
  }

  private void grantClassLevel(String sheet, UUID campaign, UUID dm) throws Exception {
    mockMvc
        .perform(action(sheet + "/level-grants", "class-dm", campaign, dm))
        .andExpect(status().isSeeOther());
  }

  @Test
  void cunningActionAndFastHandsSpendTheBonusActionWithoutUsingTheMainAction() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "rogue", 3, null, 0);
    jdbcTemplate.update(
        "UPDATE pc_class SET subclass_key = 'srd-5.1:thief' WHERE player_character_id = ?",
        bout.left());
    forceCombatDice(18, 12);
    submitBout(bout, "/start");
    mockMvc
        .perform(
            combatAction(bout.url() + "/dashes", bout.id())
                .param("actorId", bout.first().toString())
                .param("bonusAction", "true"))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'actions' FROM encounter", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'dashes' FROM encounter", Integer.class))
        .isEqualTo(1);
    mockMvc
        .perform(
            combatAction(bout.url() + "/object-uses", bout.id())
                .param("actorId", bout.first().toString())
                .param("bonusAction", "true")
                .param("note", "Open a chest")
                .accept("application/problem+json"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("NO_ACTIONS_LEFT"));
    for (var actor : java.util.List.of(bout.first(), bout.second())) {
      mockMvc
          .perform(
              combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", actor.toString()))
          .andExpect(status().isSeeOther());
    }
    mockMvc
        .perform(
            combatAction(bout.url() + "/object-uses", bout.id())
                .param("actorId", bout.first().toString())
                .param("bonusAction", "true")
                .param("note", "Open a chest"))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'actions' FROM encounter", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT budget->>'interactionAvailable' FROM encounter", Boolean.class))
        .isTrue();
  }

  @Test
  void divineSenseSpendsAnActionAndOneUseBeforeTheDmNarrates() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 1, "divine-sense-uses", 0);
    forceCombatDice(18, 12);
    submitBout(bout, "/start");
    String route = bout.url() + "/feature-uses";
    mockMvc
        .perform(
            combatAction(route, bout.id())
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:divine-sense")
                .param("targetId", bout.second().toString())
                .accept("application/problem+json"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.details.field").value("targetId"));
    mockMvc
        .perform(
            combatAction(route, bout.id())
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:divine-sense"))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'actions' FROM encounter", Integer.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT used FROM pc_resource WHERE player_character_id = ? AND resource_key = 'srd-5.1:divine-sense-uses'
        """,
                Integer.class,
                bout.left()))
        .isOne();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT audience FROM combat_event WHERE type = 'DivineSenseUsed'", String.class))
        .containsExactly("DM");
  }

  @Test
  void devotionTurnsAnUndeadThroughTheSavingThrowPipeline() throws Exception {
    var bout = combatFixture(false);
    devotion(bout);
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("monsterKey", "srd-5.1:undead-target"))
        .andExpect(status().isSeeOther());
    UUID undead =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE monster_key = 'srd-5.1:undead-target'", UUID.class);
    forceCombatDice(18, 12, 7, 2);
    submitBout(bout, "/start");
    String action = bout.url() + "/feature-uses";
    mockMvc
        .perform(
            combatAction(action, bout.id())
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:turn-the-unholy")
                .param("targetIds", bout.second().toString())
                .accept("application/problem+json"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.details.field").value("targetIds"));
    mockMvc
        .perform(
            combatAction(action, bout.id())
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:turn-the-unholy")
                .param("targetIds", undead.toString()))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT (npc_vitals->'effects')::text FROM combatant WHERE id = ?",
                String.class,
                undead))
        .contains("srd-5.1:turned");
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT used FROM pc_resource WHERE player_character_id = ? AND resource_key = 'srd-5.1:channel-divinity-uses'
        """,
                Integer.class,
                bout.left()))
        .isEqualTo(1);
  }

  @Test
  void sacredWeaponBoostsTheHeldAttackAndUsesChannelDivinity() throws Exception {
    var bout = combatFixture(false);
    devotion(bout);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 15, 5, 15);
    submitBout(bout, "/start");
    settings.decide(
        bout.campaign(),
        bout.dm(),
        combatCampaignEnvelope(bout.campaign(), bout.dm()),
        "divineSmiteTiming",
        "BEFORE_ATTACK");
    mockMvc
        .perform(
            combatAction(bout.url() + "/feature-uses", bout.id())
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:sacred-weapon"))
        .andExpect(status().isSeeOther());
    for (var actor : java.util.List.of(bout.first(), bout.second())) {
      mockMvc
          .perform(
              combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", actor.toString()))
          .andExpect(status().isSeeOther());
    }
    var hit =
        result(
            mockMvc
                .perform(
                    combatStrike(
                        bout.url(),
                        commands.newActionId(),
                        combatVersion(bout.id()),
                        bout.first(),
                        bout.second(),
                        "srd-5.1:longsword"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(hit.path("attack").path("roll").path("total").asInt()).isEqualTo(20);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payload->>'magical' FROM combat_event WHERE type = 'DamageTaken'",
                Boolean.class))
        .isTrue();

    endBoutTurn(bout, bout.first());
    endBoutTurn(bout, bout.second());
    jdbcTemplate.update(
        """
        UPDATE inventory_item SET equipment_slot = NULL
        WHERE player_character_id = ? AND item_key = 'srd-5.1:longsword'
        """,
        bout.left());
    var unarmed =
        result(
            mockMvc
                .perform(
                    combatStrike(
                        bout.url(),
                        commands.newActionId(),
                        combatVersion(bout.id()),
                        bout.first(),
                        bout.second(),
                        "unarmed"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(unarmed.path("attack").path("roll").path("total").asInt()).isEqualTo(19);
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT payload->>'magical' FROM combat_event WHERE type = 'DamageTaken'
        ORDER BY seq DESC LIMIT 1
        """,
                Boolean.class))
        .isFalse();
  }

  private void devotion(CombatFixture bout) throws Exception {
    stepEightClass(bout, "paladin", 3, "channel-divinity-uses", 0);
    jdbcTemplate.update(
        "UPDATE pc_class SET subclass_key = 'srd-5.1:oath-of-devotion' WHERE player_character_id = ?",
        bout.left());
  }

  @Test
  void passingTwoOpportunityAttacksAppliesTheManualMoveOnce() throws Exception {
    var bout = combatFixture(false);
    UUID secondReactor = addProtector(bout);
    forceCombatDice(18, 12, 6);
    submitBout(bout, "/start");
    var opened =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/manual-moves", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", bout.first().toString())
                        .param("distanceFeet", "10")
                        .param("leavesReachOf", secondReactor.toString(), bout.second().toString()))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(opened.path("reaction").path("prompt").path("decider").asString())
        .isEqualTo(bout.second().toString());

    var next = decideCombatReaction(bout, opened, bout.second(), null);
    assertThat(next.path("reaction").path("prompt").path("decider").asString())
        .isEqualTo(secondReactor.toString());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT (budget->>'movementSpent')::int FROM encounter", Integer.class))
        .isZero();

    var moved = decideCombatReaction(bout, next, secondReactor, null);
    assertThat(moved.path("movement").path("movementSpent").asInt()).isEqualTo(10);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT (budget->>'movementSpent')::int FROM encounter", Integer.class))
        .isEqualTo(10);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM combat_event WHERE type = 'MovementResolved'", Long.class))
        .isOne();
  }

  @Test
  void opportunityAttackStopsMovementWhenItDropsTheMover() throws Exception {
    var bout = combatFixture(false);
    jdbcTemplate.update("UPDATE player_character SET hp_current = 1 WHERE id = ?", bout.left());
    jdbcTemplate.update(
        """
        UPDATE inventory_item i SET equipment_slot = 'BOTH_HANDS'
        FROM combatant c WHERE c.id = ? AND c.character_id = i.player_character_id
          AND i.item_key = 'srd-5.1:crossbow-light'
        """,
        bout.second());
    forceCombatDice(18, 12, 19);
    submitBout(bout, "/start");
    var waiting =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/manual-moves", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", bout.first().toString())
                        .param("distanceFeet", "10")
                        .param("leavesReachOf", bout.second().toString()))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(waiting.path("reaction").path("prompt").path("point").asString())
        .isEqualTo("ON_MOVE_STEP");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT budget->>'movementSpent' FROM encounter", Integer.class))
        .isZero();
    String key =
        waiting.path("reaction").path("prompt").path("options").get(0).path("key").asString();
    String answer =
        "/campaigns/"
            + bout.campaign()
            + "/reactions/"
            + waiting.path("awaitingReaction").asString()
            + "/answers";
    mockMvc
        .perform(
            post(answer)
                .with(user("combat-dm"))
                .with(csrf())
                .header("HX-Request", "true")
                .param("actionId", commands.newActionId().toString())
                .param("expectedVersion", Long.toString(combatVersion(bout.id())))
                .param("deciderId", bout.second().toString())
                .param("choice", "use")
                .param("optionKey", key))
        .andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT hp_current FROM player_character WHERE id = ?", Integer.class, bout.left()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT budget->>'movementSpent' FROM encounter", Integer.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'actions' FROM encounter", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT tactics->>'reactionAvailable' FROM combatant WHERE id = ?",
                Boolean.class,
                bout.second()))
        .isFalse();
  }

  @Test
  void disengageAndEmptyReachListDoNotRequireAnAnswer() throws Exception {
    var bout = combatFixture(false);
    forceCombatDice(18, 12);
    submitBout(bout, "/start");
    mockMvc
        .perform(
            combatAction(bout.url() + "/manual-moves", bout.id())
                .param("actorId", bout.first().toString())
                .param("distanceFeet", "5"))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            combatAction(bout.url() + "/disengages", bout.id())
                .param("actorId", bout.first().toString()))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            combatAction(bout.url() + "/manual-moves", bout.id())
                .param("actorId", bout.first().toString())
                .param("distanceFeet", "10")
                .param("leavesReachOf", bout.second().toString()))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT budget->>'movementSpent' FROM encounter", Integer.class))
        .isEqualTo(15);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pending_reaction", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT payload->>'reason' FROM combat_event WHERE type = 'OpportunityAttackSkipped'
        """,
                String.class))
        .containsExactly("The mover used Disengage.");
  }

  @Test
  void spentReactionSkipsOpportunityAttackAndExplainsWhy() throws Exception {
    var bout = combatFixture(false);
    forceCombatDice(18, 12);
    submitBout(bout, "/start");
    jdbcTemplate.update(
        """
        UPDATE combatant SET tactics = jsonb_set(tactics, '{reactionAvailable}', 'false') WHERE id = ?
        """,
        bout.second());
    mockMvc
        .perform(
            combatAction(bout.url() + "/manual-moves", bout.id())
                .param("actorId", bout.first().toString())
                .param("distanceFeet", "5")
                .param("leavesReachOf", bout.second().toString()))
        .andExpect(status().isSeeOther());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pending_reaction", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT payload->>'reason' FROM combat_event WHERE type = 'OpportunityAttackSkipped'
        """,
                String.class))
        .containsExactly("Reaction already used this round.");
  }

  @Test
  void actionSurgeValidatesInputsAndGrantsAnAdditionalAction() throws Exception {
    var bout = combatFixture(false);
    jdbcTemplate.update("UPDATE pc_class SET level = 2 WHERE player_character_id = ?", bout.left());
    jdbcTemplate.update(
        """
        INSERT INTO pc_class_level(player_character_id, class_key, class_level, character_level, raw_hit_points)
        VALUES (?, 'srd-5.1:fighter', 2, 2, 6)
        """,
        bout.left());
    jdbcTemplate.update(
        """
        INSERT INTO pc_resource (player_character_id, resource_key)
        VALUES (?, 'srd-5.1:action-surge-uses')
        """,
        bout.left());
    forceCombatDice(18, 12);
    submitBout(bout, "/start");
    String route = bout.url() + "/feature-uses";
    mockMvc
        .perform(
            combatAction(route, bout.id())
                .accept("application/problem+json")
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:action-surge")
                .param("targetId", bout.second().toString()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("FEATURE_INPUT_INVALID"))
        .andExpect(jsonPath("$.details.field").value("targetId"));
    mockMvc
        .perform(
            combatAction(route, bout.id())
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:action-surge"))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'actions' FROM encounter", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT used FROM pc_resource WHERE resource_key = 'srd-5.1:action-surge-uses'",
                Integer.class))
        .isEqualTo(1);
    mockMvc
        .perform(
            combatAction(route, bout.id())
                .accept("application/problem+json")
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:action-surge"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("FEATURE_USES_EXHAUSTED"));
  }

  @Test
  void layOnHandsCuresOnlySelectedActiveInstancesAndKeepsThePoolOnInvalidInput() throws Exception {
    var bout = combatFixture(true);
    stepEightClass(bout, "paladin", 4, "lay-on-hands-pool", 2);
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("monsterKey", "step7-test:training-creature"))
        .andExpect(status().isSeeOther());
    UUID construct =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE monster_key = 'step7-test:training-creature'",
            UUID.class);
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("monsterKey", "srd-5.1:undead-target"))
        .andExpect(status().isSeeOther());
    UUID undead =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE monster_key = 'srd-5.1:undead-target'", UUID.class);
    UUID disease = affliction(bout.second(), "DISEASE");
    UUID poison = affliction(bout.second(), "POISON");
    UUID otherPoison = affliction(bout.second(), "POISON");
    UUID madness = affliction(bout.second(), "MADNESS_INDEFINITE");
    UUID wrongTarget = affliction(bout.first(), "DISEASE");
    jdbcTemplate.update(
        """
        UPDATE player_character p SET condition_effects = jsonb_build_array(
            jsonb_build_object('key','srd-5.1:poisoned','source', ?::uuid),
            jsonb_build_object('key','srd-5.1:poisoned','source', ?::uuid))
        FROM combatant c WHERE c.id = ? AND c.character_id = p.id
        """,
        poison,
        otherPoison,
        bout.second());
    forceCombatDice(18, 12, 7, 4);
    submitBout(bout, "/start");
    mockMvc
        .perform(get(bout.url()).with(user("combat-dm")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Lay on Hands")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString(poison.toString())));
    for (var invalid :
        java.util.List.of(
            java.util.Map.entry(layOnHands(bout, construct, 2), "targetId"),
            java.util.Map.entry(layOnHands(bout, undead, 2), "targetId"),
            java.util.Map.entry(layOnHands(bout, bout.second(), 0, wrongTarget), "cureIds"),
            java.util.Map.entry(layOnHands(bout, bout.second(), 0, madness), "cureIds"),
            java.util.Map.entry(layOnHands(bout, bout.second(), 14, disease), "healPoints"),
            java.util.Map.entry(layOnHands(bout, bout.second(), 0), "healPoints"))) {
      mockMvc
          .perform(invalid.getKey().accept("application/problem+json"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.code").value("FEATURE_INPUT_INVALID"))
          .andExpect(jsonPath("$.details.field").value(invalid.getValue()));
      assertThat(layOnHandsUsed(bout)).isEqualTo(2);
    }
    mockMvc
        .perform(layOnHands(bout, bout.second(), 0, disease, poison))
        .andExpect(status().isSeeOther());
    assertThat(layOnHandsUsed(bout)).isEqualTo(12);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM affliction WHERE state = 'ENDED'", Long.class))
        .isEqualTo(2);
    String effects =
        jdbcTemplate.queryForObject(
            """
        SELECT p.condition_effects::text FROM player_character p
        JOIN combatant c ON p.id = c.character_id WHERE c.id = ?
        """,
            String.class,
            bout.second());
    assertThat(effects).contains(otherPoison.toString()).doesNotContain(poison.toString());
  }

  @Test
  void declaredSmiteSpendsOnlyOnAHitAndDoublesOnACritical() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    equipLongsword(bout.left());
    settings.decide(
        bout.campaign(),
        bout.dm(),
        combatCampaignEnvelope(bout.campaign(), bout.dm()),
        "divineSmiteTiming",
        "BEFORE_ATTACK");
    forceCombatDice(18, 12, 1, 20, 2, 3, 1, 2, 3, 4);
    submitBout(bout, "/start");
    mockMvc
        .perform(
            combatStrike(
                    bout.url(),
                    commands.newActionId(),
                    combatVersion(bout.id()),
                    bout.first(),
                    bout.second(),
                    "srd-5.1:longsword")
                .param("smiteSlotLevel", "2")
                .accept("application/problem+json"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            combatStrike(
                    bout.url(),
                    commands.newActionId(),
                    combatVersion(bout.id()),
                    bout.first(),
                    bout.second(),
                    "srd-5.1:longsword")
                .param("smiteSlotLevel", "1"))
        .andExpect(status().isOk());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pc_slot", Long.class)).isZero();
    for (UUID actor : java.util.List.of(bout.first(), bout.second())) {
      mockMvc
          .perform(
              combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", actor.toString()))
          .andExpect(status().isSeeOther());
    }
    var hit =
        result(
            mockMvc
                .perform(
                    combatStrike(
                            bout.url(),
                            commands.newActionId(),
                            combatVersion(bout.id()),
                            bout.first(),
                            bout.second(),
                            "srd-5.1:longsword")
                        .param("smiteSlotLevel", "1"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(hit.path("damage").path("radiant").asInt()).isEqualTo(10);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT used FROM pc_slot WHERE slot_level = 1", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void bardicInspirationHasATimerAndCanBeUsedOnOneAttack() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "bard", 1, "bardic-inspiration-uses", 0);
    forceCombatDice(18, 12, 7, 6);
    submitBout(bout, "/start");
    mockMvc
        .perform(
            combatAction(bout.url() + "/feature-uses", bout.id())
                .param("actorId", bout.first().toString())
                .param("featureKey", "srd-5.1:bardic-inspiration")
                .param("targetId", bout.second().toString()))
        .andExpect(status().isSeeOther());
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT p.bardic_inspiration->>'endsAt' FROM player_character p
        JOIN combatant c ON p.id = c.character_id WHERE c.id = ?
        """,
                Long.class,
                bout.second()))
        .isEqualTo(600);
    mockMvc
        .perform(
            combatAction(bout.url() + "/end-turn", bout.id())
                .param("actorId", bout.first().toString()))
        .andExpect(status().isSeeOther());
    var waiting =
        result(
            mockMvc
                .perform(
                    combatStrike(
                        bout.url(),
                        commands.newActionId(),
                        combatVersion(bout.id()),
                        bout.second(),
                        bout.first(),
                        "unarmed"))
                .andExpect(status().isOk())
                .andReturn());
    var prompt = waiting.path("reaction").path("prompt");
    assertThat(prompt.path("point").asString()).isEqualTo("AFTER_ATTACK_ROLL");
    var outcome = answerFeatureReaction(bout, waiting, bout.second(), "bardic-inspiration");
    assertThat(outcome.path("attack").path("roll").path("total").asInt())
        .isEqualTo(prompt.path("disclosure").path("total").asInt() + 6);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM player_character WHERE bardic_inspiration IS NOT NULL",
                Long.class))
        .isZero();
  }

  @Test
  void cuttingWordsUsesSeparateAttackAndDamageWindowsAndPassingRevealsNothing() throws Exception {
    var bout = combatFixture(false);
    UUID bard = loreBard(bout.second(), 0);
    forceCombatDice(18, 12, 19, 2);
    submitBout(bout, "/start");

    var attackWindow = normalStrike(bout, "unarmed");
    assertCuttingWords(attackWindow, "AFTER_ATTACK_ROLL", bout.second());
    var damageWindow = decideCombatReaction(bout, attackWindow, bout.second(), null);
    assertCuttingWords(damageWindow, "AFTER_DAMAGE_ROLL", bout.second());
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT count(*) FROM combat_event WHERE type = 'ReactionRollRevealed'
        """,
                Long.class))
        .isZero();
    assertThat(inspirationUsesSpent(bard)).isZero();

    var resolved = decideCombatReaction(bout, damageWindow, bout.second(), "cutting-words");
    assertThat(resolved.path("damage").path("bludgeoning").asInt()).isEqualTo(1);
    assertThat(inspirationUsesSpent(bard)).isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT (tactics->>'reactionAvailable')::boolean FROM combatant WHERE id = ?
        """,
                Boolean.class,
                bout.second()))
        .isFalse();
  }

  @Test
  void cuttingWordsOffersAfterAnAbilityCheckInCombat() throws Exception {
    var bout = combatFixture(false);
    UUID bard = loreBard(bout.second(), 0);
    forceCombatDice(18, 12, 11, 5);
    submitBout(bout, "/start");

    var waiting =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/ability-checks", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", bout.first().toString())
                        .param("subjectIds", bout.first().toString())
                        .param("ability", "str"))
                .andExpect(status().isOk())
                .andReturn());
    assertCuttingWords(waiting, "AFTER_CHECK_ROLL", bout.second());
    var resolved = decideCombatReaction(bout, waiting, bout.second(), "cutting-words");
    assertThat(resolved.path("outcomes").get(0).path("roll").path("total").asInt()).isEqualTo(8);
    assertThat(inspirationUsesSpent(bard)).isOne();
  }

  @Test
  void cuttingWordsOffersInASceneAndCanTurnASuccessIntoAFailure() throws Exception {
    var bout = combatFixture(false);
    UUID bard = loreBard(bout.second(), 0);
    var scene =
        play.saveScene(
            bout.campaign(),
            bout.dm(),
            combatCampaignEnvelope(bout.campaign(), bout.dm()),
            null,
            new com.github.sbadolankaur.rpg.campaign.application.CampaignPlay.SceneInput(
                "Investigation",
                null,
                com.github.sbadolankaur.rpg.campaign.persistence.PlayStore.Light.BRIGHT));
    forceCombatDice(8, 2);
    String base = "/campaigns/" + bout.campaign();
    String scenePath = base + "/scenes/" + scene.data().path("sceneId").asString();

    var waiting =
        result(
            mockMvc
                .perform(
                    action(scenePath + "/ability-checks", "combat-dm", bout.campaign(), bout.dm())
                        .header("HX-Request", "true")
                        .param("ability", "str")
                        .param("dc", "10")
                        .param("subjectIds", bout.left().toString()))
                .andExpect(status().isOk())
                .andReturn());
    assertCuttingWords(waiting, "AFTER_CHECK_ROLL", bard);

    var resolved =
        result(
            mockMvc
                .perform(
                    action(
                            reactionRoute(bout, waiting) + "/answers",
                            "combat-dm",
                            bout.campaign(),
                            bout.dm())
                        .header("HX-Request", "true")
                        .param("deciderId", bard.toString())
                        .param("choice", "use")
                        .param("optionKey", "cutting-words"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(resolved.path("outcomes").get(0).path("roll").path("total").asInt()).isEqualTo(8);
    assertThat(resolved.path("outcomes").get(0).path("success").asBoolean()).isFalse();
    assertThat(inspirationUsesSpent(bard)).isOne();
  }

  @Test
  void bardicInspirationPrecedesTwoCuttingWordsAndEachBardUsesOneReaction() throws Exception {
    var bout = combatFixture(false);
    UUID secondBardEntry = addProtector(bout);
    UUID firstBard = loreBard(bout.second(), 0);
    UUID secondBard = loreBard(secondBardEntry, 0);
    jdbcTemplate.update(
        """
        UPDATE player_character SET bardic_inspiration = '{"dieFaces":6,"endsAt":600}'::jsonb
        WHERE id = ?
        """,
        bout.left());
    forceCombatDice(18, 12, 6, 18, 6, 1, 2);
    submitBout(bout, "/start");

    var inspiration = normalStrike(bout, "unarmed");
    assertThat(
            inspiration
                .path("reaction")
                .path("prompt")
                .path("options")
                .get(0)
                .path("key")
                .asString())
        .isEqualTo("bardic-inspiration");
    var first = decideCombatReaction(bout, inspiration, bout.first(), "bardic-inspiration");
    assertCuttingWords(first, "AFTER_ATTACK_ROLL", bout.second());
    var second = decideCombatReaction(bout, first, bout.second(), "cutting-words");
    assertCuttingWords(second, "AFTER_ATTACK_ROLL", secondBardEntry);
    var resolved = decideCombatReaction(bout, second, secondBardEntry, "cutting-words");

    assertThat(resolved.path("attack").path("roll").path("total").asInt()).isEqualTo(25);
    assertThat(resolved.path("awaitingReaction").isMissingNode()).isTrue();
    assertThat(inspirationUsesSpent(firstBard)).isOne();
    assertThat(inspirationUsesSpent(secondBard)).isOne();
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT payload->>'choice' FROM combat_event WHERE type = 'ReactionAnswered' ORDER BY seq
        """,
                String.class))
        .containsExactly("bardic-inspiration", "cutting-words", "cutting-words");
  }

  @org.junit.jupiter.params.ParameterizedTest(name = "Cutting Words is unavailable with {0}")
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "reaction spent",
        "uses exhausted",
        "incapacitated",
        "blinded",
        "deafened",
        "always pass",
        "own roll"
      })
  void cuttingWordsNeverOffersAnIneligibleBard(String scenario) throws Exception {
    var bout = combatFixture(false);
    boolean exhausted = scenario.equals("uses exhausted");
    UUID bardEntry = scenario.equals("own roll") ? bout.first() : bout.second();
    UUID bard = loreBard(bardEntry, exhausted ? 1 : 0);
    if ("incapacitated".equals(scenario) || "blinded".equals(scenario))
      jdbcTemplate.update("INSERT INTO pc_condition VALUES (?, ?)", bard, "srd-5.1:" + scenario);
    if ("deafened".equals(scenario))
      jdbcTemplate.update("INSERT INTO pc_condition VALUES (?, 'srd-5.1:deafened')", bout.left());
    if ("always pass".equals(scenario))
      jdbcTemplate.update(
          """
          INSERT INTO character_reaction_preference VALUES (?, 'srd-5.1:cutting-words')
          """,
          bard);
    forceCombatDice(18, 12, 19, 19);
    submitBout(bout, "/start");
    if ("reaction spent".equals(scenario))
      jdbcTemplate.update(
          """
          UPDATE combatant SET tactics = jsonb_set(tactics, '{reactionAvailable}', 'false')
          WHERE id = ?
          """,
          bardEntry);

    assertThat(normalStrike(bout, "unarmed").path("awaitingReaction").isMissingNode()).isTrue();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pending_reaction", Long.class))
        .isZero();
    assertThat(inspirationUsesSpent(bard)).isEqualTo(exhausted ? 1 : 0);
  }

  @Test
  void cuttingWordsDoesNotOfferAgainstACharmImmuneRoller() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "bard", 3, "bardic-inspiration-uses", 0);
    jdbcTemplate.update(
        """
        UPDATE pc_class SET subclass_key = 'srd-5.1:college-of-lore'
        WHERE player_character_id = ?
        """,
        bout.left());
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("monsterKey", "srd-5.1:undead-target"))
        .andExpect(status().isSeeOther());
    UUID immune =
        jdbcTemplate.queryForObject(
            "SELECT id FROM combatant WHERE monster_key = 'srd-5.1:undead-target'", UUID.class);
    forceCombatDice(18, 12, 6, 11);
    submitBout(bout, "/start");

    var resolved =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/ability-checks", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", bout.first().toString())
                        .param("subjectIds", immune.toString())
                        .param("ability", "str"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(resolved.path("awaitingReaction").isMissingNode()).isTrue();
    assertThat(resolved.path("outcomes").get(0).path("roll").path("naturalD20").asInt())
        .isEqualTo(11);
    assertThat(inspirationUsesSpent(bout.left())).isZero();
  }

  @Test
  void sneakAttackUsesTheManualAdjacencyOnceAndResetsOnTheNextTurn() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "rogue", 3, null, 0);
    equipDaggers(bout.left(), true);
    forceCombatDice(18, 12, 19, 1, 1, 2, 19, 1, 19, 1, 1, 2);
    submitBout(bout, "/start");

    var first = sneakStrike(bout, "srd-5.1:dagger", "true", "normal");
    assertThat(first.path("damage").path("piercing").asInt()).isEqualTo(6);
    var second = sneakStrike(bout, "srd-5.1:dagger-off-hand", "true", "normal");
    assertThat(second.path("damage").path("piercing").asInt()).isOne();
    assertThat(sneakAttackDice()).containsExactly(3);

    endBoutTurn(bout, bout.first());
    endBoutTurn(bout, bout.second());
    sneakStrike(bout, "srd-5.1:dagger", "true", "normal");
    assertThat(sneakAttackDice()).containsExactly(3, 3);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "false, normal, false",
    "false, advantage, true",
    "true, disadvantage, false"
  })
  void sneakAttackRespectsAdvantageAndManualAdjacency(String nearby, String mode, boolean sneaks)
      throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "rogue", 3, null, 0);
    equipDaggers(bout.left(), false);
    if ("normal".equals(mode)) forceCombatDice(18, 12, 19, 2);
    else forceCombatDice(18, 12, 19, 19, 2, 1, 3);
    submitBout(bout, "/start");

    var hit = sneakStrike(bout, "srd-5.1:dagger", nearby, mode);
    assertThat(hit.path("hit").asBoolean()).isTrue();
    assertThat(hit.path("damage").path("piercing").asInt()).isEqualTo(sneaks ? 8 : 4);
    assertThat(sneakAttackDice())
        .containsExactlyElementsOf(sneaks ? java.util.List.of(4) : java.util.List.of());
  }

  @Test
  void sneakAttackDoublesItsOwnDiceOnACriticalHit() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "rogue", 3, null, 0);
    equipDaggers(bout.left(), false);
    forceCombatDice(18, 12, 20, 1, 2, 1, 2, 3, 4);
    submitBout(bout, "/start");

    var hit = sneakStrike(bout, "srd-5.1:dagger", "true", "normal");
    assertThat(hit.path("outcome").asString()).isEqualTo("critical-hit");
    assertThat(sneakAttackDice()).containsExactly(10);
  }

  @Test
  void enemyNearTargetIsRefusedForAnAttackerWithoutSneakAttackEvenIfFalse() throws Exception {
    var bout = combatFixture(false);
    forceCombatDice(18, 12);
    submitBout(bout, "/start");
    for (String value : java.util.List.of("true", "false")) {
      mockMvc
          .perform(
              combatStrike(
                      bout.url(),
                      commands.newActionId(),
                      combatVersion(bout.id()),
                      bout.first(),
                      bout.second(),
                      "unarmed")
                  .param("enemyNearTarget", value)
                  .accept("application/problem+json"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("enemyNearTarget"))
          .andExpect(jsonPath("$.errors[0].code").value("INCORRECT"));
    }
  }

  private UUID loreBard(UUID entry, int used) {
    UUID character =
        jdbcTemplate.queryForObject(
            "SELECT character_id FROM combatant WHERE id = ?", UUID.class, entry);
    defineStepEightClass(character, "bard", 3, "bardic-inspiration-uses", used);
    jdbcTemplate.update(
        "UPDATE pc_class SET subclass_key = 'srd-5.1:college-of-lore' WHERE player_character_id = ?",
        character);
    return character;
  }

  private int inspirationUsesSpent(UUID character) {
    return jdbcTemplate.queryForObject(
        """
        SELECT used FROM pc_resource WHERE player_character_id = ?
          AND resource_key = 'srd-5.1:bardic-inspiration-uses'
        """,
        Integer.class,
        character);
  }

  private static void assertCuttingWords(
      tools.jackson.databind.JsonNode data, String point, UUID decider) {
    var prompt = data.path("reaction").path("prompt");
    assertThat(prompt.path("point").asString()).isEqualTo(point);
    assertThat(prompt.path("decider").asString()).isEqualTo(decider.toString());
    assertThat(prompt.path("options").get(0).path("key").asString()).isEqualTo("cutting-words");
  }

  private tools.jackson.databind.JsonNode decideCombatReaction(
      CombatFixture bout, tools.jackson.databind.JsonNode waiting, UUID decider, String option)
      throws Exception {
    var answer =
        reactionRequest(
                reactionRoute(bout, waiting) + "/answers",
                commands.newActionId(),
                combatVersion(bout.id()))
            .param("deciderId", decider.toString())
            .param("choice", option == null ? "pass" : "use");
    if (option != null) answer.param("optionKey", option);
    return result(mockMvc.perform(answer).andExpect(status().isOk()).andReturn());
  }

  private tools.jackson.databind.JsonNode normalStrike(CombatFixture bout, String key)
      throws Exception {
    return result(
        mockMvc
            .perform(
                combatStrike(
                    bout.url(),
                    commands.newActionId(),
                    combatVersion(bout.id()),
                    bout.first(),
                    bout.second(),
                    key))
            .andExpect(status().isOk())
            .andReturn());
  }

  private tools.jackson.databind.JsonNode sneakStrike(
      CombatFixture bout, String key, String nearby, String mode) throws Exception {
    return result(
        mockMvc
            .perform(
                combatStrike(
                        bout.url(),
                        commands.newActionId(),
                        combatVersion(bout.id()),
                        bout.first(),
                        bout.second(),
                        key)
                    .param("enemyNearTarget", nearby)
                    .param("rollMode", mode))
            .andExpect(status().isOk())
            .andReturn());
  }

  private java.util.List<Integer> sneakAttackDice() {
    return jdbcTemplate.queryForList(
        """
        SELECT (payload->'roll'->>'total')::int FROM combat_event
        WHERE type = 'DiceRolled' AND payload->>'purpose' = 'Sneak Attack' ORDER BY seq
        """,
        Integer.class);
  }

  private void equipDaggers(UUID character, boolean two) {
    for (String hand :
        two ? java.util.List.of("MAIN_HAND", "OFF_HAND") : java.util.List.of("MAIN_HAND")) {
      jdbcTemplate.update(
          "INSERT INTO inventory_item VALUES (?, ?, 'srd-5.1:dagger', 1, ?)",
          commands.newActionId(),
          character,
          hand);
    }
  }

  @Test
  void bardicInspirationTurnsAFailedDeathSaveIntoASuccessOnReplay() throws Exception {
    var bout = combatFixture(false);
    forceCombatDice(18, 12, 7, 4);
    submitBout(bout, "/start");
    mockMvc
        .perform(
            combatCorrection(
                bout.url(), bout.id(), bout.second(), 0, "players", "Set up a dying combatant"))
        .andExpect(status().isSeeOther());
    jdbcTemplate.update(
        """
        UPDATE player_character SET bardic_inspiration = '{"dieFaces":6,"endsAt":600}'::jsonb
        WHERE id = (SELECT character_id FROM combatant WHERE id = ?)
        """,
        bout.second());
    endBoutTurn(bout, bout.first());
    var waiting =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/death-saves", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", bout.second().toString()))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(waiting.path("reaction").path("prompt").path("point").asString())
        .isEqualTo("AFTER_SAVE_ROLL");

    answerFeatureReaction(bout, waiting, bout.second(), "bardic-inspiration");
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT death_successes FROM player_character WHERE id =
            (SELECT character_id FROM combatant WHERE id = ?)
        """,
                Integer.class,
                bout.second()))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM player_character WHERE bardic_inspiration IS NOT NULL",
                Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT payload->'roll'->>'total' FROM combat_event
        WHERE type = 'DiceRolled' AND payload->>'purpose' IN ('DeathSave', 'Bardic Inspiration')
        ORDER BY seq
        """,
                String.class))
        .containsExactly("7", "4");
  }

  @Test
  void defaultSmiteOffersAfterAHitAndUsesTheRecordedAttackRoll() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 20, 2, 3, 1, 2, 3, 4);
    submitBout(bout, "/start");
    var waiting =
        result(
            mockMvc
                .perform(
                    combatStrike(
                        bout.url(),
                        commands.newActionId(),
                        combatVersion(bout.id()),
                        bout.first(),
                        bout.second(),
                        "srd-5.1:longsword"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(waiting.path("reaction").path("prompt").path("point").asString())
        .isEqualTo("AFTER_HIT");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pc_slot", Long.class)).isZero();
    var hit = answerFeatureReaction(bout, waiting, bout.first(), "1");
    assertThat(hit.path("attack").path("roll").path("naturalD20").asInt()).isEqualTo(20);
    assertThat(hit.path("damage").path("radiant").asInt()).isEqualTo(10);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT used FROM pc_slot WHERE slot_level = 1", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void smiteAndUncannyDodgeAreRecordedOnceInAfterHitOrder() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    var rogue =
        jdbcTemplate.queryForObject(
            "SELECT character_id FROM combatant WHERE id = ?", UUID.class, bout.second());
    defineStepEightClass(rogue, "rogue", 5, null, 0);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 19, 6, 3, 4);
    submitBout(bout, "/start");

    var smite = beginSmite(bout, commands.newActionId());
    var dodge = answerFeatureReaction(bout, smite, bout.first(), "1");
    assertThat(dodge.path("reaction").path("prompt").path("options").get(0).path("key").asString())
        .isEqualTo("uncanny-dodge");
    var hit = answerFeatureReaction(bout, dodge, bout.second(), "uncanny-dodge");
    assertThat(hit.path("damage").path("slashing").asInt()).isEqualTo(4);
    assertThat(hit.path("damage").path("radiant").asInt()).isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT payload->>'choice' FROM combat_event WHERE type = 'ReactionAnswered' ORDER BY seq
        """,
                String.class))
        .containsExactly("1", "uncanny-dodge");
    assertThat(
            jdbcTemplate.queryForObject(
                """
        SELECT (tactics->>'reactionAvailable')::boolean FROM combatant WHERE id = ?
        """,
                Boolean.class,
                bout.second()))
        .isFalse();
    assertThat(jdbcTemplate.queryForObject("SELECT used FROM pc_slot", Integer.class)).isOne();
  }

  @Test
  void pendingSmiteUsesItsOriginalSettingAndWeaponDefinition() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 19, 2, 3, 4, 19, 2);
    submitBout(bout, "/start");
    var waiting = beginSmite(bout, commands.newActionId());

    settings.decide(
        bout.campaign(),
        bout.dm(),
        combatCampaignEnvelope(bout.campaign(), bout.dm()),
        "divineSmiteTiming",
        "BEFORE_ATTACK");
    var upgraded = BundledPacksFixture.piercingLongsword();
    contentPacks.seed(upgraded);
    jdbcTemplate.update(
        """
        UPDATE campaign_pack SET pack_id = (SELECT id FROM content_pack WHERE hash = ?)
        WHERE campaign_id = ? AND pack_key = 'srd-5.1'
        """,
        upgraded.hash(),
        bout.campaign());

    var originalHit = answerFeatureReaction(bout, waiting, bout.first(), "1");
    assertThat(originalHit.path("damage").path("slashing").asInt()).isPositive();
    assertThat(originalHit.path("damage").path("piercing").isMissingNode()).isTrue();
    assertThat(originalHit.path("damage").path("radiant").asInt()).isEqualTo(7);

    endBoutTurn(bout, bout.first());
    endBoutTurn(bout, bout.second());
    var nextHit = beginSmite(bout, commands.newActionId());
    assertThat(nextHit.path("damage").path("piercing").asInt()).isPositive();
    assertThat(nextHit.path("damage").path("radiant").isMissingNode()).isTrue();
    assertThat(nextHit.path("awaitingReaction").isMissingNode()).isTrue();
  }

  @Test
  void cancellingAfterTheAttackKeepsDiceAndRejectsALateAnswer() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 20);
    submitBout(bout, "/start");
    UUID attack = commands.newActionId();
    var waiting = beginSmite(bout, attack);
    UUID cancel = commands.newActionId();
    long version = combatVersion(bout.id());
    String route = reactionRoute(bout, waiting);
    var first =
        result(
            mockMvc
                .perform(cancelReaction(route, cancel, version))
                .andExpect(status().isOk())
                .andReturn());
    var repeated =
        result(
            mockMvc
                .perform(cancelReaction(route, cancel, version))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(first.path("outcome").asString()).isEqualTo("cancelled");
    assertThat(repeated.path("outcome").asString()).isEqualTo("cancelled");
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT (payload->'roll'->>'naturalD20')::int FROM combat_event
        WHERE action_id = ? AND type = 'DiceRolled'
        """,
                Integer.class,
                attack))
        .containsExactly(20);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT type FROM combat_event WHERE action_id = ?", String.class, cancel))
        .containsExactly("ActionCancelled");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pc_slot", Long.class)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM combat_event WHERE type = 'DamageTaken'", Long.class))
        .isZero();
    mockMvc
        .perform(chooseSmite(bout, route, commands.newActionId(), combatVersion(bout.id())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("REACTION_OPTION_INVALID"));
  }

  @Test
  void simultaneousAnswersForTheSameReactionHaveOneWinnerAndAStoredReceipt() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 19, 2, 3, 4);
    submitBout(bout, "/start");
    var waiting = beginSmite(bout, commands.newActionId());
    String route = reactionRoute(bout, waiting);
    long version = combatVersion(bout.id());
    UUID firstId = commands.newActionId();
    UUID secondId = commands.newActionId();

    var responses =
        combatRace(
            chooseSmite(bout, route, firstId, version),
            chooseSmite(bout, route, secondId, version));
    int firstStatus = responses.getFirst().getResponse().getStatus();
    int secondStatus = responses.getLast().getResponse().getStatus();
    assertThat(java.util.List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, 422);
    var rejected = firstStatus == 422 ? responses.getFirst() : responses.getLast();
    jsonPath("$.code").value("REACTION_OPTION_INVALID").match(rejected);
    UUID winner = firstStatus == 200 ? firstId : secondId;
    assertThat(
            result(
                    mockMvc
                        .perform(chooseSmite(bout, route, winner, version))
                        .andExpect(status().isOk())
                        .andReturn())
                .path("damage")
                .path("radiant")
                .asInt())
        .isEqualTo(7);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM combat_event WHERE type = 'ReactionAnswered'", Long.class))
        .isOne();
    assertThat(jdbcTemplate.queryForObject("SELECT used FROM pc_slot", Integer.class)).isOne();
  }

  @Test
  void cancellingAndAnsweringTheSameReactionRaceToOneResolution() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 19, 2, 3, 4);
    submitBout(bout, "/start");
    var waiting = beginSmite(bout, commands.newActionId());
    String route = reactionRoute(bout, waiting);
    long version = combatVersion(bout.id());

    var responses =
        combatRace(
            cancelReaction(route, commands.newActionId(), version),
            chooseSmite(bout, route, commands.newActionId(), version));
    int cancelledStatus = responses.getFirst().getResponse().getStatus();
    int answeredStatus = responses.getLast().getResponse().getStatus();
    assertThat(java.util.List.of(cancelledStatus, answeredStatus))
        .containsExactlyInAnyOrder(200, 422);
    var rejected = cancelledStatus == 422 ? responses.getFirst() : responses.getLast();
    jsonPath("$.code").value("REACTION_OPTION_INVALID").match(rejected);
    assertThat(
            jdbcTemplate.queryForList(
                """
        SELECT type FROM combat_event WHERE type IN ('ActionCancelled', 'ReactionAnswered')
        """,
                String.class))
        .containsExactly(cancelledStatus == 200 ? "ActionCancelled" : "ReactionAnswered");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pc_slot", Long.class))
        .isEqualTo(answeredStatus == 200 ? 1L : 0L);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pending_reaction", Long.class))
        .isZero();
  }

  private void endBoutTurn(CombatFixture bout, UUID actor) throws Exception {
    mockMvc
        .perform(
            combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", actor.toString()))
        .andExpect(status().isSeeOther());
  }

  private tools.jackson.databind.JsonNode beginSmite(CombatFixture bout, UUID action)
      throws Exception {
    return result(
        mockMvc
            .perform(
                combatStrike(
                    bout.url(),
                    action,
                    combatVersion(bout.id()),
                    bout.first(),
                    bout.second(),
                    "srd-5.1:longsword"))
            .andExpect(status().isOk())
            .andReturn());
  }

  private static String reactionRoute(CombatFixture bout, tools.jackson.databind.JsonNode waiting) {
    return "/campaigns/"
        + bout.campaign()
        + "/reactions/"
        + waiting.path("awaitingReaction").asString();
  }

  private MockHttpServletRequestBuilder chooseSmite(
      CombatFixture bout, String route, UUID action, long version) {
    return reactionRequest(route + "/answers", action, version)
        .param("deciderId", bout.first().toString())
        .param("choice", "use")
        .param("slotLevel", "1");
  }

  private MockHttpServletRequestBuilder cancelReaction(String route, UUID action, long version) {
    return reactionRequest(route + "/cancellations", action, version)
        .param("reason", "Target selected in error");
  }

  private MockHttpServletRequestBuilder reactionRequest(String route, UUID action, long version) {
    return post(route)
        .with(user("combat-dm"))
        .with(csrf())
        .header("HX-Request", "true")
        .accept("application/problem+json")
        .param("actionId", action.toString())
        .param("expectedVersion", Long.toString(version));
  }

  private tools.jackson.databind.JsonNode answerFeatureReaction(
      CombatFixture bout, tools.jackson.databind.JsonNode waiting, UUID decider, String option)
      throws Exception {
    String route =
        "/campaigns/"
            + bout.campaign()
            + "/reactions/"
            + waiting.path("awaitingReaction").asString()
            + "/answers";
    return result(
        mockMvc
            .perform(
                post(route)
                    .with(user("combat-dm"))
                    .with(csrf())
                    .header("HX-Request", "true")
                    .param("actionId", commands.newActionId().toString())
                    .param("expectedVersion", Long.toString(combatVersion(bout.id())))
                    .param("deciderId", decider.toString())
                    .param("choice", "use")
                    .param("optionKey", option))
            .andExpect(status().isOk())
            .andReturn());
  }

  private UUID affliction(UUID combatant, String kind) {
    UUID id = commands.newActionId();
    jdbcTemplate.update(
        """
        INSERT INTO affliction(id, combatant_id, kind, state, definition_key, source, started_at_clock)
        VALUES (?, ?, ?, 'ACTIVE', 'step8-test:sample', 'test', 0)
        """,
        id,
        combatant,
        kind);
    return id;
  }

  private int layOnHandsUsed(CombatFixture bout) {
    return jdbcTemplate.queryForObject(
        """
        SELECT used FROM pc_resource WHERE player_character_id = ? AND resource_key = 'srd-5.1:lay-on-hands-pool'
        """,
        Integer.class,
        bout.left());
  }

  private MockHttpServletRequestBuilder layOnHands(
      CombatFixture bout, UUID target, int heal, UUID... cures) {
    var form =
        combatAction(bout.url() + "/feature-uses", bout.id())
            .param("actorId", bout.first().toString())
            .param("featureKey", "srd-5.1:lay-on-hands")
            .param("targetId", target.toString())
            .param("healPoints", Integer.toString(heal));
    for (var cure : cures) form.param("cureIds", cure.toString());
    return form;
  }

  private void stepEightClass(
      CombatFixture bout, String className, int level, String pool, int used) throws Exception {
    var pack = BundledPacksFixture.stepEight();
    contentPacks.seed(pack);
    jdbcTemplate.update(
        """
        UPDATE campaign_pack SET pack_id = (SELECT id FROM content_pack WHERE hash = ?)
        WHERE campaign_id = ? AND pack_key = 'srd-5.1'
        """,
        pack.hash(),
        bout.campaign());
    defineStepEightClass(bout.left(), className, level, pool, used);
  }

  private void defineStepEightClass(
      UUID character, String className, int level, String pool, int used) {
    String classKey = "srd-5.1:" + className;
    jdbcTemplate.update("UPDATE player_character SET hp_current = 6 WHERE id = ?", character);
    jdbcTemplate.update("DELETE FROM pc_class_level WHERE player_character_id = ?", character);
    jdbcTemplate.update(
        "UPDATE pc_class SET class_key = ?, level = ? WHERE player_character_id = ?",
        classKey,
        level,
        character);
    for (int i = 1; i <= level; i++) {
      jdbcTemplate.update(
          "INSERT INTO pc_class_level VALUES (?, ?, ?, ?, 6)", character, classKey, i, i);
    }
    if (pool != null) {
      jdbcTemplate.update(
          "INSERT INTO pc_resource VALUES (?, ?, ?)", character, "srd-5.1:" + pool, used);
    }
  }

  private void equipLongsword(UUID character) {
    jdbcTemplate.update(
        """
        UPDATE inventory_item SET equipment_slot = 'MAIN_HAND'
        WHERE player_character_id = ? AND item_key = 'srd-5.1:longsword'
        """,
        character);
  }

  @Test
  void protectionPausesBeforeDiceAndAnAnswerIsAppliedExactlyOnce() throws Exception {
    var bout = combatFixture(false);
    UUID protector = addProtector(bout);
    forceCombatDice(18, 12, 6, 20, 2);
    submitBout(bout, "/start");
    assertThat(jdbcTemplate.queryForObject("SELECT acting_id FROM encounter", UUID.class))
        .isEqualTo(bout.first());
    long initialVersion = combatVersion(bout.id());
    UUID attackId = commands.newActionId();
    var waiting =
        result(
            mockMvc
                .perform(
                    combatStrike(
                            bout.url(),
                            attackId,
                            initialVersion,
                            bout.first(),
                            bout.second(),
                            "unarmed")
                        .param("protectingAllyIds", protector.toString()))
                .andExpect(status().isOk())
                .andExpect(
                    content().string(org.hamcrest.Matchers.containsString("Reaction decision")))
                .andReturn());
    assertThat(waiting.path("reaction").path("prompt").path("point").asString())
        .isEqualTo("BEFORE_ATTACK_ROLL");
    String window = waiting.path("reactionWindowId").asString();
    long version = combatVersion(bout.id());
    assertThat(version).isEqualTo(initialVersion + 1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM combat_event WHERE action_id = ? AND type = 'DiceRolled'",
                Long.class,
                attackId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'actions' FROM encounter", Integer.class))
        .isEqualTo(1);
    mockMvc
        .perform(get(bout.url() + "/reaction").with(user("combat-dm")).header("HX-Request", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Protection")));
    mockMvc
        .perform(
            combatAction(bout.url() + "/end-turn", bout.id())
                .accept("application/problem+json")
                .param("actorId", bout.first().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REACTION_PENDING"));
    assertThatThrownBy(
            () ->
                play.advance(
                    bout.campaign(),
                    bout.dm(),
                    combatCampaignEnvelope(bout.campaign(), bout.dm()),
                    1,
                    ClockUnit.MINUTES))
        .hasMessage("REACTION_PENDING");
    mockMvc
        .perform(
            action(
                    "/campaigns/" + bout.campaign() + "/rests",
                    "combat-dm",
                    bout.campaign(),
                    bout.dm())
                .accept("application/problem+json")
                .param("kind", "short")
                .param("durationMinutes", "60")
                .param("participantIds", bout.left().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REACTION_PENDING"));
    mockMvc
        .perform(
            action(
                    "/campaigns/" + bout.campaign() + "/characters/" + bout.left() + "/edits",
                    "combat-dm",
                    bout.campaign(),
                    bout.dm())
                .accept("application/problem+json")
                .param("section", "details")
                .param("name", "Frozen until the attack ends"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REACTION_PENDING"));
    mockMvc
        .perform(
            combatCorrection(
                bout.url(),
                bout.id(),
                bout.first(),
                1,
                "players",
                "Attempt a change while pending"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REACTION_PENDING"));
    UUID owner =
        jdbcTemplate.queryForObject(
            "SELECT owner_id FROM player_character WHERE id = ?", UUID.class, bout.left());
    mockMvc
        .perform(
            action(
                    "/campaigns/" + bout.campaign() + "/characters/" + bout.left() + "/level-ups",
                    "combat-one",
                    bout.campaign(),
                    owner)
                .param("step", "review")
                .param("hitPointMethod", "average")
                .accept("application/problem+json"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REACTION_PENDING"));
    assertThat(reactions.protectsCharacter(bout.campaign(), bout.left())).isTrue();
    mockMvc
        .perform(
            combatStrike(
                    bout.url(), attackId, initialVersion, bout.first(), bout.second(), "unarmed")
                .param("protectingAllyIds", protector.toString()))
        .andExpect(status().isOk());
    UUID answerId = commands.newActionId();
    String answer = "/campaigns/" + bout.campaign() + "/reactions/" + window + "/answers";
    mockMvc
        .perform(reactionAnswer(answer, answerId, version, protector, "combat-protector"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REACTION_NOT_PERMITTED"));
    mockMvc
        .perform(reactionAnswer(answer, answerId, initialVersion, protector, "combat-dm"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENCOUNTER_VERSION_CONFLICT"));
    var outcome =
        result(
            mockMvc
                .perform(
                    reactionAnswer(answer, answerId, version, protector, "combat-dm")
                        .accept("text/html"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(outcome.path("hit").asBoolean()).isFalse();
    assertThat(outcome.path("attack").path("roll").path("naturalD20").asInt()).isEqualTo(2);
    assertThat(reactions.protectsCharacter(bout.campaign(), bout.left())).isFalse();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pending_reaction", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT tactics->>'reactionAvailable' FROM combatant WHERE id = ?",
                Boolean.class,
                protector))
        .isFalse();
    var repeated =
        mockMvc
            .perform(
                reactionAnswer(answer, answerId, version, protector, "combat-dm")
                    .accept("text/html"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(repeated.getModelAndView().getModel().get("replay")).isEqualTo(true);
    assertThat(result(repeated)).isEqualTo(outcome);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM combat_event WHERE action_id = ? AND type = 'DiceRolled'",
                Long.class,
                answerId))
        .isOne();
  }

  @Test
  void coldInstanceCanResumePersistedProtectionPrompt() throws Exception {
    var bout = combatFixture(false);
    UUID protector = addProtector(bout);
    forceCombatDice(18, 12, 6);
    submitBout(bout, "/start");
    var waiting =
        result(
            mockMvc
                .perform(
                    combatStrike(
                            bout.url(),
                            commands.newActionId(),
                            combatVersion(bout.id()),
                            bout.first(),
                            bout.second(),
                            "unarmed")
                        .param("protectingAllyIds", protector.toString()))
                .andExpect(status().isOk())
                .andReturn());
    UUID window = UUID.fromString(waiting.path("reactionWindowId").asString());
    long version = combatVersion(bout.id());
    var restartDice = org.mockito.Mockito.mock(EncounterDice.class);
    org.mockito.Mockito.when(restartDice.nextInt(20)).thenReturn(19, 1);
    try (var restarted = restartWithDice(restartDice)) {
      var reactions = restarted.getBean(EncounterCommands.class);
      assertThat(reactions.reaction(bout.campaign(), bout.id(), bout.dm()).id()).isEqualTo(window);
      var envelope =
          new com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope(
              commands.newActionId(), version);
      var resolved =
          reactions.answer(
              bout.campaign(),
              window,
              bout.dm(),
              envelope,
              new ReactionAnswer(AnswerKind.ANSWER, protector, "protection", null));
      assertThat(resolved.data().path("attack").path("roll").path("naturalD20").asInt())
          .isEqualTo(2);
      assertThat(resolved.version()).isEqualTo(version + 1);
      org.mockito.Mockito.verify(restartDice, org.mockito.Mockito.times(2)).nextInt(20);
      assertThat(reactions.reaction(bout.campaign(), bout.id(), bout.dm())).isNull();
    }
  }

  @Test
  void coldInstanceResumesSmiteWithoutRollingTheAttackAgain() throws Exception {
    var bout = combatFixture(false);
    stepEightClass(bout, "paladin", 2, "lay-on-hands-pool", 0);
    equipLongsword(bout.left());
    forceCombatDice(18, 12, 19);
    submitBout(bout, "/start");

    UUID attack = commands.newActionId();
    var waiting = beginSmite(bout, attack);
    UUID window = UUID.fromString(waiting.path("awaitingReaction").asString());
    long version = combatVersion(bout.id());
    var dice = org.mockito.Mockito.mock(EncounterDice.class);
    org.mockito.Mockito.when(dice.nextInt(8)).thenReturn(1, 2, 3);

    try (var restarted = restartWithDice(dice)) {
      var service = restarted.getBean(EncounterCommands.class);
      assertThat(service.reaction(bout.campaign(), bout.id(), bout.dm()).prompt().point().name())
          .isEqualTo("AFTER_HIT");
      var envelope =
          new com.github.sbadolankaur.rpg.campaign.application.CampaignCommands.Envelope(
              commands.newActionId(), version);
      var answer = new ReactionAnswer(AnswerKind.ANSWER, bout.first(), "1", null);
      var hit = service.answer(bout.campaign(), window, bout.dm(), envelope, answer);
      assertThat(hit.data().path("attack").path("roll").path("naturalD20").asInt()).isEqualTo(19);
      assertThat(hit.data().path("damage").path("radiant").asInt()).isEqualTo(7);
      assertThat(service.answer(bout.campaign(), window, bout.dm(), envelope, answer).data())
          .isEqualTo(hit.data());
      org.mockito.Mockito.verify(dice, org.mockito.Mockito.times(3)).nextInt(8);
      org.mockito.Mockito.verifyNoMoreInteractions(dice);
    }
  }

  private org.springframework.context.ConfigurableApplicationContext restartWithDice(
      EncounterDice dice) {
    return new SpringApplicationBuilder(RpgApplication.class)
        .initializers(
            context -> {
              var fixedDice = new RootBeanDefinition(EncounterDice.class);
              fixedDice.setPrimary(true);
              fixedDice.setInstanceSupplier(() -> dice);
              ((BeanDefinitionRegistry) context.getBeanFactory())
                  .registerBeanDefinition("restartDice", fixedDice);
            })
        .run(
            "--server.port=0",
            "--app.jdbc-sessions=false",
            "--app.instance-key=" + INSTANCE_KEY,
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword());
  }

  @Test
  void cancellingProtectionLeavesOriginalAttackAndReactionUnspent() throws Exception {
    var bout = combatFixture(false);
    UUID protector = addProtector(bout);
    forceCombatDice(18, 12, 6);
    submitBout(bout, "/start");
    var waiting =
        result(
            mockMvc
                .perform(
                    combatStrike(
                            bout.url(),
                            commands.newActionId(),
                            combatVersion(bout.id()),
                            bout.first(),
                            bout.second(),
                            "unarmed")
                        .param("protectingAllyIds", protector.toString()))
                .andExpect(status().isOk())
                .andReturn());
    String cancel =
        "/campaigns/"
            + bout.campaign()
            + "/reactions/"
            + waiting.path("reactionWindowId").asString()
            + "/cancellations";
    UUID cancelId = commands.newActionId();
    long version = combatVersion(bout.id());
    var cancellation =
        post(cancel)
            .with(user("combat-dm"))
            .with(csrf())
            .header("HX-Request", "true")
            .param("actionId", cancelId.toString())
            .param("expectedVersion", Long.toString(version))
            .param("reason", "Target selected in error");
    var completed = result(mockMvc.perform(cancellation).andExpect(status().isOk()).andReturn());
    assertThat(completed.path("outcome").asString()).isEqualTo("cancelled");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pending_reaction", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject("SELECT budget->>'actions' FROM encounter", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT tactics->>'reactionAvailable' FROM combatant WHERE id = ?",
                Boolean.class,
                protector))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT type FROM combat_event WHERE action_id = ?", String.class, cancelId))
        .isEqualTo("ActionCancelled");
  }

  private UUID addProtector(CombatFixture bout) throws Exception {
    UUID player = characterAccount("combat-protector", false);
    joinFixture(bout.campaign(), player);
    UUID character =
        createCombatFighter(
            "/campaigns/" + bout.campaign() + "/characters",
            bout.campaign(),
            player,
            "combat-protector",
            "srd-5.1:protection");
    jdbcTemplate.update(
        "UPDATE inventory_item SET equipment_slot = 'OFF_HAND' WHERE player_character_id = ? AND item_key = ?",
        character,
        "srd-5.1:shield");
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("characterId", character.toString()))
        .andExpect(status().isSeeOther());
    return jdbcTemplate.queryForObject(
        "SELECT id FROM combatant WHERE encounter_id = ? AND character_id = ?",
        UUID.class,
        bout.id(),
        character);
  }

  private MockHttpServletRequestBuilder reactionAnswer(
      String url, UUID id, long version, UUID decider, String userName) {
    return post(url)
        .with(user(userName))
        .with(csrf())
        .header("HX-Request", "true")
        .accept("application/problem+json")
        .param("actionId", id.toString())
        .param("expectedVersion", Long.toString(version))
        .param("deciderId", decider.toString())
        .param("choice", "use")
        .param("optionKey", "protection");
  }

  private tools.jackson.databind.JsonNode grapple(CombatFixture bout) throws Exception {
    return result(
        mockMvc
            .perform(
                combatAction(bout.url() + "/grapples", bout.id())
                    .header("HX-Request", "true")
                    .param("actorId", bout.first().toString())
                    .param("targetId", bout.second().toString()))
            .andExpect(status().isOk())
            .andReturn());
  }

  @Test
  void searchDiscoversTheHiderAndAThrownInCreatureArrivesProneWithDisadvantage() throws Exception {
    var bout = combatFixture(true);
    forceCombatDice(18, 11, 12, 15, 17, 8);
    submitBout(bout, "/start");
    String first = bout.first().toString();
    String second = bout.second().toString();
    mockMvc
        .perform(
            combatAction(bout.url() + "/hides", bout.id())
                .param("actorId", first)
                .param("obscuredFromIds", second))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", first))
        .andExpect(status().isSeeOther());
    var search =
        result(
            mockMvc
                .perform(
                    combatAction(bout.url() + "/ability-checks", bout.id())
                        .header("HX-Request", "true")
                        .param("actorId", second)
                        .param("subjectIds", second)
                        .param("searchTargetId", first)
                        .param("ability", "wisdom")
                        .param("skillKey", "perception"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(search.path("outcomes").get(0).path("detected").asBoolean()).isTrue();
    mockMvc
        .perform(
            combatAction(bout.url() + "/combatants", bout.id())
                .param("monsterKey", "step7-test:training-creature")
                .param("initiativeMode", "disadvantage")
                .param("conditionKey", "srd-5.1:prone"))
        .andExpect(status().isSeeOther());
    var added =
        encounters
            .view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50)
            .combatants()
            .getLast();
    assertThat(added.initiative()).isEqualTo(8);
    assertThat(added.conditions()).contains("srd-5.1:prone");
    mockMvc
        .perform(combatAction(bout.url() + "/end-turn", bout.id()).param("actorId", second))
        .andExpect(status().isSeeOther());
    mockMvc
        .perform(
            combatAction(bout.url() + "/stands", bout.id()).param("actorId", added.id().toString()))
        .andExpect(status().isSeeOther());
    var after = encounters.view(bout.campaign(), bout.id(), bout.dm(), Long.MAX_VALUE, 50);
    assertThat(after.table().budget().movementRemaining(30)).isEqualTo(15);
    assertThat(after.combatants().getLast().conditions()).isEmpty();
  }

  @Test
  void simultaneousCombatSubmissionsCommitOnlyOnceOnPostgres() throws Exception {
    var bout = combatFixture(false);
    forceCombatDice(18, 11);
    submitBout(bout, "/start");
    long original = combatVersion(bout.id());
    var actor = bout.first().toString();
    UUID replayId = commands.newActionId();
    var repeat =
        combatRace(
            concurrentCombatPost(bout, "/dashes", replayId, original, actor),
            concurrentCombatPost(bout, "/dashes", replayId, original, actor));
    assertThat(repeat.stream().map(response -> response.getResponse().getStatus()))
        .containsExactly(200, 200);
    assertThat(repeat.stream().map(response -> response.getModelAndView().getModel().get("replay")))
        .containsExactlyInAnyOrder(true, false);
    assertThat(result(repeat.getFirst())).isEqualTo(result(repeat.getLast()));
    assertThat(combatVersion(bout.id())).isEqualTo(original + 1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM combat_event WHERE encounter_id = ? AND action_id = ?",
                Integer.class,
                bout.id(),
                replayId))
        .isEqualTo(1);

    long contested = combatVersion(bout.id());
    var race =
        combatRace(
            concurrentCombatPost(bout, "/drops-prone", commands.newActionId(), contested, actor),
            concurrentCombatPost(bout, "/end-turn", commands.newActionId(), contested, actor));
    assertThat(race.stream().map(response -> response.getResponse().getStatus()))
        .containsExactlyInAnyOrder(200, 409);
    var rejected =
        race.stream()
            .filter(response -> response.getResponse().getStatus() == 409)
            .findFirst()
            .orElseThrow();
    assertThat(rejected.getResponse().getContentAsString()).contains("ENCOUNTER_VERSION_CONFLICT");
    assertThat(combatVersion(bout.id())).isEqualTo(contested + 1);
  }

  private java.util.List<MvcResult> combatRace(
      MockHttpServletRequestBuilder first, MockHttpServletRequestBuilder second) throws Exception {
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    var timeout = java.util.concurrent.TimeUnit.SECONDS;
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                barrier.await(10, timeout);
                return mockMvc.perform(first).andReturn();
              });
      var b =
          pool.submit(
              () -> {
                barrier.await(10, timeout);
                return mockMvc.perform(second).andReturn();
              });
      return java.util.List.of(a.get(20, timeout), b.get(20, timeout));
    }
  }

  private MockHttpServletRequestBuilder concurrentCombatPost(
      CombatFixture bout, String route, UUID action, long version, String actor) {
    return post(bout.url() + route)
        .with(user("combat-dm"))
        .with(csrf())
        .header("HX-Request", "true")
        .accept("application/problem+json")
        .param("actionId", action.toString())
        .param("expectedVersion", Long.toString(version))
        .param("actorId", actor);
  }

  private static tools.jackson.databind.JsonNode result(MvcResult request) {
    return (tools.jackson.databind.JsonNode) request.getModelAndView().getModel().get("result");
  }

  private void forceCombatDice(Integer... values) {
    var faces = new java.util.ArrayDeque<>(java.util.List.of(values));
    org.mockito.Mockito.when(combatDice.nextInt(org.mockito.ArgumentMatchers.anyInt()))
        .thenAnswer(
            call -> {
              int face = faces.removeFirst();
              assertThat(face).isBetween(1, call.getArgument(0, Integer.class));
              return face - 1;
            });
  }

  private MockHttpServletRequestBuilder conditionCorrection(
      CombatFixture bout, UUID target, String condition, UUID source, String kind) {
    return combatAction(bout.url() + "/overrides", bout.id())
        .param("kind", kind)
        .param("combatantId", target.toString())
        .param("conditionKey", condition)
        .param("sourceId", source.toString())
        .param("reason", "Apply the test condition");
  }

  private String conditionEffects(UUID character) {
    return jdbcTemplate.queryForObject(
        "SELECT condition_effects::text FROM player_character WHERE id = ?",
        String.class,
        character);
  }

  private void submitBout(CombatFixture bout, String route) throws Exception {
    mockMvc.perform(combatAction(bout.url() + route, bout.id())).andExpect(status().isSeeOther());
  }

  private CombatFixture combatFixture(boolean monsterPack) throws Exception {
    UUID dm = characterAccount("combat-dm", true);
    UUID p1 = characterAccount("combat-one", false);
    UUID p2 = characterAccount("combat-two", false);
    UUID campaign = campaigns.create(dm, "Combat acceptance table").id();
    joinFixture(campaign, p1, p2);
    String base = "/campaigns/" + campaign;
    UUID left = createCombatFighter(base + "/characters", campaign, p1, "combat-one");
    UUID right = createCombatFighter(base + "/characters", campaign, p2, "combat-two");
    if (monsterPack) {
      var pack =
          packLoader.loadFolder(
              java.nio.file.Path.of("src/test/resources/content-fixtures/step7-combat"),
              java.util.List.of());
      contentPacks.seed(pack);
      jdbcTemplate.update(
          """
          INSERT INTO campaign_pack(campaign_id, pack_id, pack_key, position)
          SELECT ?, id, pack_key, 1 FROM content_pack WHERE hash = ?
          """,
          campaign,
          pack.hash());
    }
    play.start(campaign, dm, combatCampaignEnvelope(campaign, dm));
    String url =
        mockMvc
            .perform(
                action(base + "/encounters", "combat-dm", campaign, dm)
                    .param("name", "Acceptance combat"))
            .andExpect(status().isSeeOther())
            .andReturn()
            .getResponse()
            .getRedirectedUrl();
    UUID id = UUID.fromString(url.substring(url.lastIndexOf('/') + 1));
    for (UUID character : java.util.List.of(left, right)) {
      mockMvc
          .perform(combatAction(url + "/combatants", id).param("characterId", character.toString()))
          .andExpect(status().isSeeOther());
    }
    String entry = "SELECT id FROM combatant WHERE encounter_id = ? AND character_id = ?";
    return new CombatFixture(
        dm,
        campaign,
        url,
        id,
        left,
        jdbcTemplate.queryForObject(entry, UUID.class, id, left),
        jdbcTemplate.queryForObject(entry, UUID.class, id, right));
  }

  private record CombatFixture(
      UUID dm, UUID campaign, String url, UUID id, UUID left, UUID first, UUID second) {}

  private UUID createCombatFighter(String url, UUID campaign, UUID owner, String username)
      throws Exception {
    return createCombatFighter(url, campaign, owner, username, "srd-5.1:defense");
  }

  private UUID createCombatFighter(
      String url, UUID campaign, UUID owner, String username, String style) throws Exception {
    var response =
        mockMvc
            .perform(
                fighterForm(url, username, "review", style)
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
    return fighterForm(url, name, step, "srd-5.1:defense");
  }

  private MockHttpServletRequestBuilder fighterForm(
      String url, String name, String step, String style) {
    return post(url)
        .with(user(name))
        .with(csrf())
        .param("step", step)
        .param("speciesKey", "srd-5.1:human")
        .param("classKey", "srd-5.1:fighter")
        .param("fightingStyleKey", style)
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
                "SELECT count(*) FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '0.3.1'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT d.key FROM content_definition d JOIN content_pack p ON p.id = d.pack_id
                WHERE d.kind = 'class' AND p.pack_key = 'srd-5.1' AND p.version = '0.3.1'
                """,
                String.class))
        .containsExactlyInAnyOrder(
            "srd-5.1:bard", "srd-5.1:fighter", "srd-5.1:paladin", "srd-5.1:rogue");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT version FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '0.3.1'",
                String.class))
        .isEqualTo("0.3.1");
    String manifest =
        jdbcTemplate.queryForObject(
            "SELECT manifest::text FROM content_pack WHERE pack_key = 'srd-5.1' AND version = '0.3.1'",
            String.class);
    String notice = contentPacks.notice();
    var changed =
        new ContentPack(
            "srd-5.1",
            "0.3.1",
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
