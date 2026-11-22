package com.github.sbadolankaur.rpg.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.sbadolankaur.rpg.RpgApplication;
import com.github.sbadolankaur.rpg.content.ContentPack;
import com.github.sbadolankaur.rpg.content.ContentPacks;
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

  @BeforeEach
  void resetRegistrationState() {
    jdbcTemplate.update("DELETE FROM form_token");
    jdbcTemplate.update("DELETE FROM campaign");
    jdbcTemplate.update("DELETE FROM app_user");
    jdbcTemplate.update("UPDATE instance_state SET initialized = FALSE WHERE id = 1");
  }

  @Test
  void applicationStartupSeedsOneRealDefinitionAndRejectsChangesUnderTheSameVersion()
      throws Exception {
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM content_pack", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT key FROM content_definition", String.class))
        .isEqualTo("srd-5.1:prone");
    assertThat(jdbcTemplate.queryForObject("SELECT version FROM content_pack", String.class))
        .isEqualTo("0.1.0");
    String manifest =
        jdbcTemplate.queryForObject("SELECT manifest::text FROM content_pack", String.class);
    String notice = contentPacks.notice();
    var changed =
        new ContentPack(
            "srd-5.1",
            "0.1.0",
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
