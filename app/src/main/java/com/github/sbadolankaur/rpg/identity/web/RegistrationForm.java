package com.github.sbadolankaur.rpg.identity.web;

import com.github.sbadolankaur.rpg.identity.application.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.hibernate.validator.constraints.CodePointLength;

public class RegistrationForm {
  @NotBlank
  @Size(min = 3, max = 32)
  @Pattern(
      regexp = "^[A-Za-z0-9._-]+$",
      message = "Use only letters, numbers, periods, underscores, and hyphens.")
  private String username;

  @Size(max = 80)
  private String displayName;

  @NotNull
  @CodePointLength(
      min = PasswordPolicy.MIN_CHARACTERS,
      max = PasswordPolicy.MAX_CHARACTERS,
      message = "Password must be between 10 and 128 characters.")
  private String password;

  private String inviteCode;
  @NotNull private UUID formToken;

  public String getInviteCode() {
    return inviteCode;
  }

  public void setInviteCode(String inviteCode) {
    this.inviteCode = inviteCode;
  }

  public UUID getFormToken() {
    return formToken;
  }

  public void setFormToken(UUID formToken) {
    this.formToken = formToken;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
