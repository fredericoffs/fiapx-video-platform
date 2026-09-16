package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.application.dto.ChangePasswordCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.application.usecase.ChangePasswordUseCase;
import com.fiapx.videoapi.infrastructure.web.dto.ChangePasswordRequest;
import com.fiapx.videoapi.infrastructure.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
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
          + "\"deve trocar a senha\" do usuário admin semeado (ver LoginResponse.mustChangePassword). "
          + "Retorna um token novo: a troca de senha revoga qualquer token emitido antes dela "
          + "(incluindo o que autenticou esta própria requisição), então o chamador precisa trocar "
          + "pelo token da resposta pra continuar autenticado."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Senha alterada — token novo na resposta",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginResponse.class))),
      @ApiResponse(responseCode = "400", description = "Nova senha fora do padrão (8 a 100 caracteres)",
          content = @Content),
      @ApiResponse(responseCode = "401", description = "Senha atual incorreta", content = @Content)
  })
  @PutMapping(value = "/me/password", produces = MediaType.APPLICATION_JSON_VALUE)
  public LoginResponse changePassword(
      @Parameter(hidden = true) @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody ChangePasswordRequest request
  ) {
    LoginResult result = changePasswordUseCase.handle(
        new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword()));
    return LoginResponse.from(result);
  }
}
