package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.RegisterUserResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Usuário recém-criado")
public record RegisterResponse(
    @Schema(description = "ID gerado do usuário") UUID id,
    @Schema(description = "E-mail cadastrado", example = "user@fiapx.com") String email
) {

  public static RegisterResponse from(RegisterUserResult result) {
    return new RegisterResponse(result.id(), result.email());
  }
}
