package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public class User {

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final Instant createdAt;

  public User(UUID id, String email, String passwordHash, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  public static User newUser(UUID id, String email, String passwordHash) {
    return new User(id, email, passwordHash, Instant.now());
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
