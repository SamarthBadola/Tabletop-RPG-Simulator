package com.github.sbadolankaur.rpg.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "username", nullable = false, unique = true, length = 50)
  private String username;

  @Column(name = "password_hash", nullable = false, columnDefinition = "text")
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 80)
  private String displayName;

  @Column(name = "instance_admin", nullable = false)
  private boolean instanceAdmin;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AppUser() {}

  public AppUser(
      String username,
      String passwordHash,
      String displayName,
      boolean instanceAdmin,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.username = username;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.instanceAdmin = instanceAdmin;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isInstanceAdmin() {
    return instanceAdmin;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
