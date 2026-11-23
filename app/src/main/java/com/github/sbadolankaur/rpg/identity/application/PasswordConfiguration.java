package com.github.sbadolankaur.rpg.identity.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    return new PasswordEncoder() {
      @Override
      public String encode(CharSequence password) {
        if (!PasswordPolicy.hasValidLength(password)) {
          throw new IllegalArgumentException("Password length is outside the allowed range");
        }
        return argon2.encode(password);
      }

      @Override
      public boolean matches(CharSequence password, String hash) {
        return PasswordPolicy.hasValidLength(password)
            && hash != null
            && hash.startsWith("$argon2id$")
            && argon2.matches(password, hash);
      }

      @Override
      public boolean upgradeEncoding(String hash) {
        return hash != null && hash.startsWith("$argon2id$") && argon2.upgradeEncoding(hash);
      }
    };
  }
}
