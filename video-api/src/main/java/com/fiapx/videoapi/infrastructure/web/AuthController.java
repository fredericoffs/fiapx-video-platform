package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.application.dto.LoginCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.application.dto.RegisterUserCommand;
import com.fiapx.videoapi.application.dto.RegisterUserResult;
import com.fiapx.videoapi.application.usecase.LoginUseCase;
import com.fiapx.videoapi.application.usecase.RegisterUserUseCase;
import com.fiapx.videoapi.infrastructure.web.dto.LoginRequest;
import com.fiapx.videoapi.infrastructure.web.dto.LoginResponse;
import com.fiapx.videoapi.infrastructure.web.dto.RegisterRequest;
import com.fiapx.videoapi.infrastructure.web.dto.RegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Cadastro e login. Únicos endpoints do video-api que não exigem Bearer JWT.")
@SecurityRequirements
public class AuthController {

  private final RegisterUserUseCase registerUserUseCase;
  private final LoginUseCase loginUseCase;

  public AuthController(RegisterUserUseCase registerUserUseCase, LoginUseCase loginUseCase) {
    this.registerUserUseCase = registerUserUseCase;
    this.loginUseCase = loginUseCase;
  }

  @Operation(
      summary = "Cria um novo usuário",
      description = "Senha é armazenada com hash BCrypt. E-mail precisa ser único."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Usuário criado",
          content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
      @ApiResponse(responseCode = "400", description = "Payload inválido (e-mail malformado, senha curta, etc.)",
          content = @Content),
      @ApiResponse(responseCode = "409", description = "E-mail já cadastrado", content = @Content)
  })
  @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterUserResult result = registerUserUseCase.handle(new RegisterUserCommand(request.email(), request.password()));
    return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(result));
  }

  @Operation(
      summary = "Autentica e retorna um JWT",
      description = "Rate limit de 5 tentativas falhas por e-mail a cada 60s (Redis, INCR+TTL); ao atingir o "
          + "limite, retorna 429 mesmo com credenciais corretas até a janela expirar."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Autenticado",
          content = @Content(schema = @Schema(implementation = LoginResponse.class))),
      @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos", content = @Content),
      @ApiResponse(responseCode = "429", description = "Rate limit de tentativas de login excedido",
          content = @Content)
  })
  @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = loginUseCase.handle(new LoginCommand(request.email(), request.password()));
    return LoginResponse.from(result);
  }
}
