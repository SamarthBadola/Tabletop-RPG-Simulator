package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.CampaignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class CampaignRequestPolicy implements WebMvcConfigurer {
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.addHeader("Vary", "HX-Request");
                response.setHeader("Cache-Control", "no-store");
                if ("POST".equals(request.getMethod())) {
                  checkOrigin(request);
                  checkType(request.getContentType());
                }
                return true;
              }
            })
        .addPathPatterns("/campaigns", "/campaigns/**", "/join", "/register");
  }

  private static void checkOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin == null) {
      return;
    }
    try {
      URI uri = URI.create(origin);
      int port = uri.getPort() >= 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
      if (request.getScheme().equalsIgnoreCase(uri.getScheme())
          && request.getServerName().equalsIgnoreCase(uri.getHost())
          && request.getServerPort() == port
          && uri.getUserInfo() == null
          && uri.getRawPath().isEmpty()
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null) {
        return;
      }
    } catch (IllegalArgumentException ignored) {
      // Do not copy a malformed Origin into the response.
    }
    throw new CampaignException(CampaignException.Code.CSRF_INVALID);
  }

  private static void checkType(String contentType) {
    if (contentType == null) {
      return;
    }
    try {
      if (MediaType.APPLICATION_FORM_URLENCODED.includes(MediaType.parseMediaType(contentType))) {
        return;
      }
    } catch (IllegalArgumentException ignored) {
      // Treat malformed and unsupported values alike.
    }
    throw new CampaignException(CampaignException.Code.VALIDATION_FAILED);
  }
}
