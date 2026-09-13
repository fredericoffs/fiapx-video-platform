package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.application.dto.ChangePasswordCommand;
import com.fiapx.videoapi.application.usecase.ChangePasswordUseCase;
import com.fiapx.videoapi.infrastructure.web.dto.ChangePasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Ações do próprio usuário autenticado sobre a conta dele.")
public class UserController {

  private final ChangePasswordUseCase changePasswordUseCase;

  public UserController(ChangePasswordUseCase changePasswordUseCase) {
    this.changePasswordUseCase = changePasswordUseCase;
  }

  @Operation(
      summary = "Troca a senha do usuário autenticado",
      description = "Exige a senha atual. Usado tanto pra troca voluntária quanto pra sair do estado "
          + "\"deve trocar a senha\" do usuário admin semeado (ver LoginResponse.mustChangePassword)."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Senha alterada"),
      @ApiResponse(responseCode = "400", description = "Nova senha fora do padrão (8 a 100 caracteres)",
          content = @Content),
      @ApiResponse(responseCode = "401", description = "Senha atual incorreta", content = @Content)
  })
  @PutMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @Parameter(hidden = true) @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody ChangePasswordRequest request
  ) {
    changePasswordUseCase.handle(new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword()));
    return ResponseEntity.noContent().build();
  }
}
