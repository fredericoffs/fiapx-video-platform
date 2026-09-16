package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

  /** Diferente de {@link #withPasswordChanged}: mantém mustChangePassword — é uma senha
   * provisória injetada por provisionamento (AdminPasswordSeeder), não uma troca voluntária. */
  public User withSeededPassword(String newPasswordHash) {
    return new User(id, email, newPasswordHash, role, createdAt, true, tokensValidAfter);
  }

  /**
   * Um token só é aceito se foi emitido depois da última revogação (troca de senha, etc.).
   * Trunco tokensValidAfter pro segundo porque o claim "iat" do JWT só tem precisão de
   * segundo (NumericDate) — sem isso, um token reemitido na mesma requisição que revoga os
   * anteriores podia nascer "já revogado" por causa da fração de segundo perdida no iat.
   */
  public boolean hasValidToken(Instant issuedAt) {
    return tokensValidAfter == null || !issuedAt.isBefore(tokensValidAfter.truncatedTo(ChronoUnit.SECONDS));
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
