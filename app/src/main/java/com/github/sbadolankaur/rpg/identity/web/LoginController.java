package com.github.sbadolankaur.rpg.identity.web;

import com.github.sbadolankaur.rpg.identity.application.RegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
  private final RegistrationService registration;

  public LoginController(RegistrationService registration) {
    this.registration = registration;
  }

  @GetMapping("/login")
  public String login() {
    if (registration.isRegistrationOpen()) {
      return "redirect:/register";
    }
    return "login";
  }
}
