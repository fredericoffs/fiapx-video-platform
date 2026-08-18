package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acesso emitido após login bem-sucedido")
public record LoginResponse(
    @Schema(description = "JWT HS256 assinado", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI...") String accessToken,
    @Schema(description = "Sempre \"Bearer\"", example = "Bearer") String tokenType,
    @Schema(description = "Validade do token em segundos", example = "1800") long expiresIn
) {

  public static LoginResponse from(LoginResult result) {
    return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
  }
}
