package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.Role;
import java.util.UUID;

public interface TokenIssuer {

  String generateToken(UUID userId, Role role, boolean mustChangePassword);

  UUID parseUserId(String token);

  Role parseRole(String token);

  boolean parseMustChangePassword(String token);
}
