package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acesso emitido após login bem-sucedido")
public record LoginResponse(
    @Schema(description = "JWT HS256 assinado", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI...") String accessToken,
    @Schema(description = "Sempre \"Bearer\"", example = "Bearer") String tokenType,
    @Schema(description = "Validade do token em segundos", example = "1800") long expiresIn,
    @Schema(description = "Papel do usuário autenticado", example = "USER") Role role,
    @Schema(description = "true quando o usuário precisa trocar a senha antes de continuar — o frontend deve "
        + "redirecionar direto pra tela de troca de senha, sem deixar navegar pro resto da aplicação",
        example = "false") boolean mustChangePassword
) {

  public static LoginResponse from(LoginResult result) {
    return new LoginResponse(
        result.accessToken(), "Bearer", result.expiresInSeconds(), result.role(), result.mustChangePassword()
    );
  }
}
