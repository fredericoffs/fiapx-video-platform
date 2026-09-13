package com.fiapx.videoapi.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Troca de senha do próprio usuário autenticado")
public record ChangePasswordRequest(
    @Schema(description = "Senha atual, pra confirmar que é o dono da conta", example = "Admin@123")
    @NotBlank String currentPassword,
    @Schema(description = "8 a 100 caracteres — armazenada com hash BCrypt, nunca em texto puro",
        example = "nova-senha-secreta-123") @NotBlank @Size(min = 8, max = 100) String newPassword
) {

}
