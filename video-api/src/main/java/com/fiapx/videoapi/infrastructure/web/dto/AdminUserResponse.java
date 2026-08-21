package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Usuário cadastrado — nunca inclui o hash da senha")
public record AdminUserResponse(
    @Schema(description = "ID do usuário") UUID id,
    @Schema(description = "E-mail de login") String email,
    @Schema(description = "Papel do usuário") Role role,
    @Schema(description = "Data/hora do cadastro") Instant createdAt
) {

  public static AdminUserResponse from(User user) {
    return new AdminUserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
  }
}
