package com.github.sbadolankaur.rpg.identity.application;

import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {
  private static final Set<String> COMMON =
      Set.of(
          "password123",
          "password1234",
          "1234567890",
          "12345678910",
          "qwertyuiop",
          "qwerty12345",
          "letmein123",
          "iloveyou123",
          "adminadmin",
          "administrator",
          "passwordpassword");
  public static final int MIN_CHARACTERS = 10;
  public static final int MAX_CHARACTERS = 128;

  private PasswordPolicy() {}

  public static boolean isCommon(String password) {
    return password != null
        && password.length() <= MAX_CHARACTERS
        && COMMON.contains(password.toLowerCase(Locale.ROOT));
  }

  public static boolean hasValidLength(CharSequence password) {
    if (password == null || password.length() > MAX_CHARACTERS * 2) {
      return false;
    }
    int characters = Character.codePointCount(password, 0, password.length());
    return characters >= MIN_CHARACTERS && characters <= MAX_CHARACTERS;
  }
}
