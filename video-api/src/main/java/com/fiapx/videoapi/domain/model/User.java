package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public class User {

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final Role role;
  private final Instant createdAt;
  private final boolean mustChangePassword;
  private final Instant tokensValidAfter;

  public User(UUID id, String email, String passwordHash, Role role, Instant createdAt) {
    this(id, email, passwordHash, role, createdAt, false);
  }

  public User(
      UUID id, String email, String passwordHash, Role role, Instant createdAt, boolean mustChangePassword
  ) {
    this(id, email, passwordHash, role, createdAt, mustChangePassword, createdAt);
  }

  public User(
      UUID id, String email, String passwordHash, Role role, Instant createdAt, boolean mustChangePassword,
      Instant tokensValidAfter
  ) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = createdAt;
    this.mustChangePassword = mustChangePassword;
    this.tokensValidAfter = tokensValidAfter;
  }

  public static User newUser(UUID id, String email, String passwordHash) {
    Instant now = Instant.now();
    return new User(id, email, passwordHash, Role.USER, now, false, now);
  }

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }

  // Revoga qualquer token emitido antes de agora — item 14 da revisão crítica: sem isso, um
  // JWT emitido com a senha antiga continuava válido até expirar sozinho.
  public User withPasswordChanged(String newPasswordHash) {
    return new User(id, email, newPasswordHash, role, createdAt, false, Instant.now());
  }

  /**
   * Diferente de {@link #withPasswordChanged}: mantém mustChangePassword — é uma senha provisória injetada por provisionamento (AdminPasswordSeeder),
   * não uma troca voluntária.
   */
  public User withSeededPassword(String newPasswordHash) {
    return new User(id, email, newPasswordHash, role, createdAt, true, tokensValidAfter);
  }

  /**
   * Compara a emissão precisa assinada com a última revogação, inclusive no mesmo segundo.
   */
  public boolean hasValidToken(Instant issuedAt) {
    return tokensValidAfter == null || !issuedAt.isBefore(tokensValidAfter);
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

  public Role getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getTokensValidAfter() {
    return tokensValidAfter;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }
}
