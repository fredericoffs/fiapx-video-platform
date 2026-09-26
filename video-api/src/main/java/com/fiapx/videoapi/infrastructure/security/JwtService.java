package com.fiapx.videoapi.infrastructure.security;

import com.fiapx.videoapi.domain.exception.InvalidTokenException;
import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.port.TokenIssuer;
import com.fiapx.videoapi.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService implements TokenIssuer {

  private static final String ROLE_CLAIM = "role";
  private static final String MUST_CHANGE_PASSWORD_CLAIM = "mustChangePassword";

  private final JwtProperties jwtProperties;
  private final SecretKey signingKey;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String generateToken(UUID userId, Role role, boolean mustChangePassword) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(Duration.ofMinutes(jwtProperties.expirationMinutes()));
    return Jwts.builder()
        .subject(userId.toString())
        .claim(ROLE_CLAIM, role.name())
        .claim(MUST_CHANGE_PASSWORD_CLAIM, mustChangePassword)
        .claim("issuedAtPrecise", now.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(signingKey)
        .compact();
  }

  @Override
  public UUID parseUserId(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }

  @Override
  public Role parseRole(String token) {
    return Role.valueOf(parseClaims(token).get(ROLE_CLAIM, String.class));
  }

  @Override
  public boolean parseMustChangePassword(String token) {
    return Boolean.TRUE.equals(parseClaims(token).get(MUST_CHANGE_PASSWORD_CLAIM, Boolean.class));
  }

  @Override
  public Instant parseIssuedAt(String token) {
    Claims claims = parseClaims(token);
    String precise = claims.get("issuedAtPrecise", String.class);
    return precise == null ? claims.getIssuedAt().toInstant() : Instant.parse(precise);
  }

  private Claims parseClaims(String token) {
    try {
      return Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidTokenException("Token JWT inválido ou expirado", e);
    }
  }
}
