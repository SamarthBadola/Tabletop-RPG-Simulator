package com.github.sbadolankaur.rpg.identity.application;

public final class PasswordPolicy {
  public static final int MIN_CHARACTERS = 10;
  public static final int MAX_CHARACTERS = 128;

  private PasswordPolicy() {}

  public static boolean hasValidLength(CharSequence password) {
    if (password == null || password.length() > MAX_CHARACTERS * 2) {
      return false;
    }
    int characters = Character.codePointCount(password, 0, password.length());
    return characters >= MIN_CHARACTERS && characters <= MAX_CHARACTERS;
  }
}
