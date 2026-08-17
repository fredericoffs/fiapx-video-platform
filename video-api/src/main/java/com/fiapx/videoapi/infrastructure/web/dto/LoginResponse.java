package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.LoginResult;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {

  public static LoginResponse from(LoginResult result) {
    return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
  }
}
