package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.ChangePasswordCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.domain.exception.InvalidCurrentPasswordException;
import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.TokenIssuer;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.infrastructure.config.JwtProperties;
import java.time.Duration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ChangePasswordUseCase {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokenIssuer;
  private final JwtProperties jwtProperties;

  public ChangePasswordUseCase(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TokenIssuer tokenIssuer,
      JwtProperties jwtProperties
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenIssuer = tokenIssuer;
    this.jwtProperties = jwtProperties;
  }

  // Retorna um token novo: withPasswordChanged revoga qualquer token emitido antes de agora
  // (item 14), incluindo o que autenticou esta própria requisição — sem reemitir aqui, o
  // usuário sairia "deslogado" na mesma hora que trocou a senha com sucesso.
  public LoginResult handle(ChangePasswordCommand command) {
    User user = userRepository.findById(command.userId())
        .orElseThrow(() -> new UserNotFoundException(command.userId()));
    if (!passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
      throw new InvalidCurrentPasswordException();
    }
    User updated = user.withPasswordChanged(passwordEncoder.encode(command.newPassword()));
    userRepository.save(updated);

    String token = tokenIssuer.generateToken(updated.getId(), updated.getRole(), updated.isMustChangePassword());
    long expiresInSeconds = Duration.ofMinutes(jwtProperties.expirationMinutes()).toSeconds();
    return new LoginResult(token, expiresInSeconds, updated.getRole(), updated.isMustChangePassword());
  }
}
