package com.github.sbadolankaur.rpg.identity.application;

import com.github.sbadolankaur.rpg.identity.domain.AppUser;
import com.github.sbadolankaur.rpg.identity.persistence.AppUserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDirectory {
  private final AppUserRepository users;

  public AccountDirectory(AppUserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public Optional<UUID> idFor(String username) {
    return users.findByUsername(username).map(AppUser::getId);
  }
}
