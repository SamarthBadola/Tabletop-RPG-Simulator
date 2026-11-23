package com.github.sbadolankaur.rpg.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrowserSessionIT {
  private static final String INSTANCE_KEY =
      HexFormat.of().formatHex(new SecureRandom().generateSeed(32));

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.instance-key", () -> INSTANCE_KEY);
  }

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void persistsSignInInPostgresAndAppliesTheAbsoluteAndIdleLimits() throws Exception {
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    String base = "http://127.0.0.1:" + port;
    var form =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/register")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(form.statusCode()).isEqualTo(200);
    String payload =
        "username=browserowner&password=Browser-test-pass-29"
            + "&_csrf="
            + encode(input(form.body(), "_csrf"))
            + "&formToken="
            + encode(input(form.body(), "formToken"));

    var created =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/register"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(created.statusCode()).isEqualTo(303);
    assertThat(created.headers().firstValue("Location")).contains("/campaigns");
    assertThat(
            created.headers().allValues("Set-Cookie").stream()
                .anyMatch(value -> value.contains("HttpOnly") && value.contains("SameSite=Lax")))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT max_inactive_interval FROM spring_session WHERE principal_name = 'browserowner'",
                Long.class))
        .isEqualTo(28800);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "/campaigns")).GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);

    jdbc.update(
        "UPDATE spring_session SET creation_time = creation_time - ? WHERE principal_name = 'browserowner'",
        Duration.ofDays(15).toMillis());
    var expired =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/campaigns")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(expired.statusCode()).isEqualTo(302);
    assertThat(expired.headers().firstValue("Location").orElseThrow()).endsWith("/login");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = 'browserowner'",
                Long.class))
        .isZero();
  }

  private static String input(String body, String name) {
    var tags = Pattern.compile("<input\\b[^>]*>").matcher(body);
    while (tags.find()) {
      if (tags.group().contains("name=\"" + name + "\"")) {
        var value = Pattern.compile("value=\"([^\"]+)\"").matcher(tags.group());
        if (value.find()) return value.group(1);
      }
    }
    throw new AssertionError("Form input is missing: " + name);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
