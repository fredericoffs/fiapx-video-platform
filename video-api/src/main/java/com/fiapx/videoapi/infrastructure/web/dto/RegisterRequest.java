package com.fiapx.videoapi.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de cadastro de um novo usuário")
public record RegisterRequest(
    @Schema(description = "E-mail único — cadastro é rejeitado se já existir", example = "user@fiapx.com")
    @NotBlank @Email String email,
    @Schema(description = "8 a 100 caracteres — armazenada com hash BCrypt, nunca em texto puro",
        example = "senha-secreta-123") @NotBlank @Size(min = 8, max = 100) String password
) {

}
