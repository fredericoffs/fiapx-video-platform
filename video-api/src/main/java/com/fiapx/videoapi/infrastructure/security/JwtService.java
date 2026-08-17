package com.fiapx.videoapi.infrastructure.security;

import com.fiapx.videoapi.domain.exception.InvalidTokenException;
import com.fiapx.videoapi.domain.port.TokenIssuer;
import com.fiapx.videoapi.infrastructure.config.JwtProperties;
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

  private final JwtProperties jwtProperties;
  private final SecretKey signingKey;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String generateToken(UUID userId) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(Duration.ofMinutes(jwtProperties.expirationMinutes()));
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(signingKey)
        .compact();
  }

  @Override
  public UUID parseUserId(String token) {
    try {
      String subject = Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getSubject();
      return UUID.fromString(subject);
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidTokenException("Token JWT inválido ou expirado", e);
    }
  }
}
