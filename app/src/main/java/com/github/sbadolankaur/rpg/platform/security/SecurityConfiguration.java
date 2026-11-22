package com.github.sbadolankaur.rpg.platform.security;

import jakarta.servlet.DispatcherType;
import java.net.URI;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@Configuration
public class SecurityConfiguration {
  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, LoginAttemptLimiter loginAttemptLimiter) throws Exception {
    LoginRateLimitFilter loginRateLimitFilter = new LoginRateLimitFilter(loginAttemptLimiter);
    var requestCache = new HttpSessionRequestCache();

    http.authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/login",
                        "/register",
                        "/css/**",
                        "/favicon.ico",
                        "/actuator/health/**",
                        "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginPage("/login")
                    .failureHandler(
                        (request, response, exception) -> {
                          loginAttemptLimiter.recordFailure(
                              request.getParameter("username"), request.getRemoteAddr());
                          response.sendRedirect("/login?error");
                        })
                    .successHandler(
                        (request, response, authentication) -> {
                          loginAttemptLimiter.recordSuccess(
                              request.getParameter("username"), request.getRemoteAddr());
                          var saved = requestCache.getRequest(request, response);
                          requestCache.removeRequest(request, response);
                          String fallback = request.getContextPath() + "/campaigns";
                          String path =
                              saved == null
                                  ? fallback
                                  : safeReturn(saved.getRedirectUrl(), fallback);
                          response.setStatus(303);
                          response.setHeader("Location", response.encodeRedirectURL(path));
                        })
                    .permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
        .sessionManagement(Customizer.withDefaults())
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    policy ->
                        policy.policyDirectives(
                            "default-src 'self'; base-uri 'self'; frame-ancestors 'none'; "
                                + "form-action 'self'; object-src 'none'; "
                                + "style-src 'self'; script-src 'self'; img-src 'self' data:")))
        .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private static String safeReturn(String address, String fallback) {
    try {
      URI uri = URI.create(address);
      String path = uri.getRawPath();
      String folded = path == null ? "" : path.toLowerCase(Locale.ROOT);
      if (!folded.startsWith("/")
          || folded.startsWith("//")
          || folded.contains("\\")
          || folded.contains("%2f")
          || folded.contains("%5c")
          || "/".equals(folded)) {
        return fallback;
      }
      return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}
