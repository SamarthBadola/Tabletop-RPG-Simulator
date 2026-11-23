package com.github.sbadolankaur.rpg.campaign.web;

import com.github.sbadolankaur.rpg.campaign.application.FormTokens;
import com.github.sbadolankaur.rpg.campaign.application.InviteAttempts;
import com.github.sbadolankaur.rpg.campaign.application.Invites;
import com.github.sbadolankaur.rpg.identity.application.DuplicateUsernameException;
import com.github.sbadolankaur.rpg.identity.application.PasswordPolicy;
import com.github.sbadolankaur.rpg.identity.application.RegistrationService;
import com.github.sbadolankaur.rpg.identity.web.RegistrationForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
class RegistrationController {
  private static final String VIEW = "demo-register";
  private static final String PURPOSE = "register";
  private final AuthenticationManager authentication;
  private final RegistrationService registration;
  private final FormTokens tokens;
  private final InviteAttempts attempts;
  private final Invites invites;

  RegistrationController(
      AuthenticationManager authentication,
      RegistrationService registration,
      FormTokens tokens,
      InviteAttempts attempts,
      Invites invites) {
    this.authentication = authentication;
    this.registration = registration;
    this.tokens = tokens;
    this.attempts = attempts;
    this.invites = invites;
  }

  @GetMapping("/register")
  String form(Model model, HttpSession session) {
    var form = new RegistrationForm();
    form.setFormToken(tokens.issue(CampaignWeb.formSession(session), PURPOSE));
    model.addAttribute("form", form);
    model.addAttribute("firstAccount", registration.isRegistrationOpen());
    return VIEW;
  }

  @PostMapping("/register")
  Object register(
      @Valid @ModelAttribute("form") RegistrationForm form,
      BindingResult errors,
      Model model,
      HttpServletRequest request,
      HttpServletResponse response,
      HttpSession session) {
    if (!errors.hasFieldErrors("password") && PasswordPolicy.isCommon(form.getPassword())) {
      errors.rejectValue("password", "TOO_COMMON", "Choose a less common password.");
    }
    boolean firstAccount = registration.isRegistrationOpen();
    model.addAttribute("firstAccount", firstAccount);
    if (errors.hasErrors()) {
      return invalid(model, response);
    }
    String username = form.getUsername().toLowerCase(Locale.ROOT);
    String displayName = Objects.toString(form.getDisplayName(), "").strip();
    String invite = Objects.toString(form.getInviteCode(), "").toUpperCase(Locale.ROOT);
    String password = form.getPassword();
    String canonical = encode(username) + encode(displayName) + encode(invite) + encode(password);
    String formSession = CampaignWeb.formSession(session);
    try {
      String location =
          attempts.check(
              request.getRemoteAddr(),
              () ->
                  tokens.createOnce(
                      formSession,
                      PURPOSE,
                      form.getFormToken(),
                      canonical,
                      () -> {
                        if (!registration.tryFirstAccount(username, displayName, password)) {
                          invites.register(invite, username, displayName, password);
                        }
                        return "/campaigns";
                      }));
      if (request.getUserPrincipal() == null
          || !username.equals(request.getUserPrincipal().getName())) {
        var authenticated =
            authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        request.changeSessionId();
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
      }
      return CampaignWeb.go(request, location);
    } catch (DuplicateUsernameException e) {
      errors.rejectValue("username", "NOT_UNIQUE", e.getMessage());
      return invalid(model, response);
    }
  }

  private static String encode(String text) {
    return text.length() + ":" + text;
  }

  private static String invalid(Model model, HttpServletResponse response) {
    response.setStatus(HttpStatus.BAD_REQUEST.value());
    model.addAttribute("validationCode", "VALIDATION_FAILED");
    return VIEW;
  }
}
