package com.fiapx.videoapi.domain.port;

import java.util.UUID;

public interface TokenIssuer {

  String generateToken(UUID userId);

  UUID parseUserId(String token);
}
