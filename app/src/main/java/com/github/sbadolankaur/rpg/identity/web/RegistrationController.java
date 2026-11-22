package com.github.sbadolankaur.rpg.identity.web;

import com.github.sbadolankaur.rpg.identity.application.DuplicateUsernameException;
import com.github.sbadolankaur.rpg.identity.application.RegistrationClosedException;
import com.github.sbadolankaur.rpg.identity.application.RegistrationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class RegistrationController {
  private static final String REGISTRATION_VIEW = "demo-register";
  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @GetMapping("/register")
  public String registerForm(Model model) {
    model.addAttribute("form", new RegistrationForm());
    model.addAttribute("registrationOpen", registrationService.isRegistrationOpen());
    return REGISTRATION_VIEW;
  }

  @PostMapping("/register")
  public String register(
      @Valid @ModelAttribute("form") RegistrationForm form,
      BindingResult result,
      Model model,
      HttpServletResponse response) {
    if (result.hasErrors()) {
      model.addAttribute("registrationOpen", registrationService.isRegistrationOpen());
      response.setStatus(HttpStatus.BAD_REQUEST.value());
      model.addAttribute("validationCode", "VALIDATION_FAILED");
      return REGISTRATION_VIEW;
    }

    try {
      registrationService.register(form.getUsername(), form.getDisplayName(), form.getPassword());
    } catch (RegistrationClosedException exception) {
      model.addAttribute("registrationOpen", false);
      response.setStatus(HttpStatus.CONFLICT.value());
      return REGISTRATION_VIEW;
    } catch (DuplicateUsernameException exception) {
      result.rejectValue("username", "username.duplicate", exception.getMessage());
      model.addAttribute("registrationOpen", registrationService.isRegistrationOpen());
      response.setStatus(HttpStatus.BAD_REQUEST.value());
      model.addAttribute("validationCode", "VALIDATION_FAILED");
      return REGISTRATION_VIEW;
    }

    return "redirect:/login?registered";
  }
}
