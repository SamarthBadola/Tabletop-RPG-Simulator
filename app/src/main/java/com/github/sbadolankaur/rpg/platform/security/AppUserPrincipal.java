package com.github.sbadolankaur.rpg.platform.security;

import com.github.sbadolankaur.rpg.identity.domain.AppUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AppUserPrincipal implements UserDetails, CredentialsContainer {
  @java.io.Serial private static final long serialVersionUID = 1L;
  private final UUID id;
  private final String username;
  private transient String passwordHash;
  private final String displayName;
  private final boolean instanceAdmin;
  private final List<GrantedAuthority> authorities;

  public AppUserPrincipal(AppUser user) {
    id = user.getId();
    username = user.getUsername();
    passwordHash = user.getPasswordHash();
    displayName = user.getDisplayName();
    instanceAdmin = user.isInstanceAdmin();

    List<GrantedAuthority> granted = new ArrayList<>();
    granted.add(new SimpleGrantedAuthority("AUTHENTICATED"));
    if (instanceAdmin) {
      granted.add(new SimpleGrantedAuthority("ROLE_INSTANCE_ADMIN"));
    }
    authorities = List.copyOf(granted);
  }

  public UUID getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isInstanceAdmin() {
    return instanceAdmin;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
