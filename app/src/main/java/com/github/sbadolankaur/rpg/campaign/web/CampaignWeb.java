package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.identity.application.AccountDirectory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

final class CampaignWeb {
  private static final String FORM_SESSION = CampaignWeb.class.getName() + ".formSession";

  private CampaignWeb() {}

  static UUID actor(AccountDirectory accounts, Principal principal) {
    return accounts
        .idFor(principal.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  static String formSession(HttpSession session) {
    synchronized (session) {
      if (!(session.getAttribute(FORM_SESSION) instanceof String key)) {
        String newKey = UUID.randomUUID().toString();
        session.setAttribute(FORM_SESSION, newKey);
        return newKey;
      } else {
        return key;
      }
    }
  }

  static RedirectView seeOther(String location) {
    var redirect = new RedirectView(location, true);
    redirect.setStatusCode(HttpStatus.SEE_OTHER);
    redirect.setExposeModelAttributes(false);
    return redirect;
  }

  static boolean htmx(HttpServletRequest request) {
    return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
  }

  static Object go(HttpServletRequest request, String location) {
    if (htmx(request)) {
      return ResponseEntity.ok().header("HX-Redirect", request.getContextPath() + location).build();
    }
    return seeOther(location);
  }

  static Object region(HttpServletRequest request, String fragment, String page) {
    return htmx(request) ? fragment : seeOther(page);
  }
}
