package com.github.sbadolankaur.rpg.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.sbadolankaur.rpg.RpgApplication;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = RpgApplication.class)
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class ApplicationFlowIT {
  private static final String PASSWORD = "Walking-Skeleton-Pass-27!";

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
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void resetRegistrationState() {
    jdbcTemplate.update("DELETE FROM app_user");
    jdbcTemplate.update("UPDATE instance_state SET initialized = FALSE WHERE id = 1");
  }

  @Test
  void readinessIsPublicAndReportsDatabaseHealth() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
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
  void firstRegistrationCreatesAnAdminWithAHashedPasswordAndClosesRegistration() throws Exception {
    mockMvc
        .perform(
            post("/register")
                .with(csrf())
                .param("username", "Admin_User")
                .param("displayName", "Instance Admin")
                .param("password", PASSWORD))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    Boolean isAdmin =
        jdbcTemplate.queryForObject(
            "SELECT instance_admin FROM app_user WHERE username = ?", Boolean.class, "admin_user");
    String passwordHash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM app_user WHERE username = ?", String.class, "admin_user");

    assertThat(isAdmin).isTrue();
    assertThat(passwordHash).isNotEqualTo(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT initialized FROM instance_state WHERE id = 1", Boolean.class))
        .isTrue();

    mockMvc
        .perform(
            post("/register")
                .with(csrf())
                .param("username", "second-user")
                .param("displayName", "Second User")
                .param("password", PASSWORD))
        .andExpect(status().isConflict());

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void registeredAdminCanAuthenticateAndAccessTheProtectedHome() throws Exception {
    mockMvc
        .perform(
            post("/register")
                .with(csrf())
                .param("username", "admin_user")
                .param("displayName", "Instance Admin")
                .param("password", PASSWORD))
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
            .andExpect(redirectedUrl("/"))
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
  void registrationAndSignInAccept128AsciiAndUnicodeCharacters() throws Exception {
    for (String password : new String[] {"a".repeat(127) + "Z", "🔑".repeat(127) + "ß"}) {
      resetRegistrationState();
      mockMvc
          .perform(
              post("/register")
                  .with(csrf())
                  .param("username", "owner")
                  .param("displayName", "Owner")
                  .param("password", password))
          .andExpect(status().is3xxRedirection());

      mockMvc
          .perform(
              post("/login").with(csrf()).param("username", "owner").param("password", password))
          .andExpect(redirectedUrl("/"));
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
        .perform(
            post("/register")
                .with(csrf())
                .param("username", "owner")
                .param("displayName", "Owner")
                .param("password", "a".repeat(129)))
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
    mockMvc
        .perform(
            post("/register")
                .with(csrf())
                .param("username", username)
                .param("displayName", username)
                .param("password", PASSWORD))
        .andExpect(status().is3xxRedirection());
    return jdbcTemplate.queryForObject(
        "SELECT password_hash FROM app_user WHERE username = ?", String.class, username);
  }

  @Test
  void invalidRegistrationInputIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/register")
                .with(csrf())
                .param("username", "x")
                .param("displayName", "Name")
                .param("password", "short"))
        .andExpect(status().isBadRequest());

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class))
        .isZero();
  }
}
