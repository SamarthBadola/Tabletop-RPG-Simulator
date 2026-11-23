package com.github.sbadolankaur.rpg.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.jdbc-sessions", matchIfMissing = true)
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 28800)
class BrowserSessions {
  @Bean
  CookieSerializer sessionCookie(@Value("${APP_COOKIE_SECURE:false}") boolean secure) {
    var cookie = new DefaultCookieSerializer();
    cookie.setUseHttpOnlyCookie(true);
    cookie.setUseSecureCookie(secure);
    cookie.setSameSite("Lax");
    return cookie;
  }

  @Bean
  FilterRegistrationBean<OncePerRequestFilter> absoluteSessionLimit() {
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            var session = request.getSession(false);
            if (session != null
                && System.currentTimeMillis() - session.getCreationTime()
                    >= Duration.ofDays(14).toMillis()) {
              session.invalidate();
            }
            chain.doFilter(request, response);
          }
        };
    var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
    registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
    return registration;
  }
}
