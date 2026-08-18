package com.fiapx.videoapi.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais de login")
public record LoginRequest(
    @Schema(description = "E-mail cadastrado", example = "user@fiapx.com") @NotBlank @Email String email,
    @Schema(description = "Senha em texto puro (nunca logada nem persistida em claro)",
        example = "senha-secreta-123") @NotBlank String password
) {

}
