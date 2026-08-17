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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final RegisterUserUseCase registerUserUseCase;
  private final LoginUseCase loginUseCase;

  public AuthController(RegisterUserUseCase registerUserUseCase, LoginUseCase loginUseCase) {
    this.registerUserUseCase = registerUserUseCase;
    this.loginUseCase = loginUseCase;
  }

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterUserResult result = registerUserUseCase.handle(new RegisterUserCommand(request.email(), request.password()));
    return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(result));
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = loginUseCase.handle(new LoginCommand(request.email(), request.password()));
    return LoginResponse.from(result);
  }
}
