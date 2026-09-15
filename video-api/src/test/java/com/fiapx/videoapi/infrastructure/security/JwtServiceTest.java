package com.fiapx.videoapi.infrastructure.security;

import com.fiapx.videoapi.domain.exception.InvalidTokenException;
import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.infrastructure.config.JwtProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

  private final JwtProperties jwtProperties = new JwtProperties("test-secret-min-32-characters-long!!", 15);
  private final JwtService jwtService = new JwtService(jwtProperties);

  @Test
  void generatedTokenRoundTripsToTheSameUserId() {
    UUID userId = UUID.randomUUID();

    String token = jwtService.generateToken(userId, Role.USER, false);

    assertThat(jwtService.parseUserId(token)).isEqualTo(userId);
  }

  @Test
  void generatedTokenRoundTripsToTheSameRole() {
    UUID userId = UUID.randomUUID();

    String token = jwtService.generateToken(userId, Role.ADMIN, false);

    assertThat(jwtService.parseRole(token)).isEqualTo(Role.ADMIN);
  }

  @Test
  void generatedTokenRoundTripsMustChangePasswordTrue() {
    String token = jwtService.generateToken(UUID.randomUUID(), Role.ADMIN, true);

    assertThat(jwtService.parseMustChangePassword(token)).isTrue();
  }

  @Test
  void generatedTokenRoundTripsMustChangePasswordFalse() {
    String token = jwtService.generateToken(UUID.randomUUID(), Role.USER, false);

    assertThat(jwtService.parseMustChangePassword(token)).isFalse();
  }

  @Test
  void parsingGarbageTokenThrowsInvalidTokenException() {
    assertThatThrownBy(() -> jwtService.parseUserId("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void parsingExpiredTokenThrowsInvalidTokenException() {
    JwtService expiringService = new JwtService(new JwtProperties(jwtProperties.secret(), -1));
    String token = expiringService.generateToken(UUID.randomUUID(), Role.USER, false);

    assertThatThrownBy(() -> jwtService.parseUserId(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void tokenSignedWithDifferentSecretIsRejected() {
    JwtService otherService = new JwtService(new JwtProperties("another-secret-min-32-characters!!!", 15));
    String token = otherService.generateToken(UUID.randomUUID(), Role.USER, false);

    assertThatThrownBy(() -> jwtService.parseUserId(token)).isInstanceOf(InvalidTokenException.class);
  }
}
