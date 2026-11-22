package com.github.sbadolankaur.rpg.platform.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, LoginAttemptLimiter loginAttemptLimiter) throws Exception {
    LoginRateLimitFilter loginRateLimitFilter = new LoginRateLimitFilter(loginAttemptLimiter);

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
                          response.sendRedirect("/");
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
}
