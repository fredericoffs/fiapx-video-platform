package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.Role;
import java.time.Instant;
import java.util.UUID;

public interface TokenIssuer {

  String generateToken(UUID userId, Role role, boolean mustChangePassword);

  UUID parseUserId(String token);

  Role parseRole(String token);

  boolean parseMustChangePassword(String token);

  /** Usado pra checar revogação (User#hasValidToken) — um token emitido antes da última
   * troca de senha não deve mais ser aceito, mesmo que ainda não tenha expirado. */
  Instant parseIssuedAt(String token);
}
