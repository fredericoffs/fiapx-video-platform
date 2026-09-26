package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.ChangePasswordCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.domain.exception.InvalidCurrentPasswordException;
import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.TokenIssuer;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.infrastructure.config.JwtProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangePasswordUseCaseTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
  private final JwtProperties jwtProperties = new JwtProperties("test-secret", 30);
  private final ChangePasswordUseCase useCase =
      new ChangePasswordUseCase(userRepository, passwordEncoder, tokenIssuer, jwtProperties);

  private final UUID userId = UUID.randomUUID();
  private final String currentPassword = "Admin@123";

  @Test
  void updatesPasswordClearsMustChangePasswordFlagAndReturnsAFreshToken() {
    User user = new User(
        userId, "admin@fiapx.local", passwordEncoder.encode(currentPassword), Role.ADMIN, Instant.now(), true
    );
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(tokenIssuer.generateToken(userId, Role.ADMIN, false)).thenReturn("novo-token");

    LoginResult result = useCase.handle(new ChangePasswordCommand(userId, currentPassword, "nova-senha-secreta-123"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.isMustChangePassword()).isFalse();
    assertThat(passwordEncoder.matches("nova-senha-secreta-123", saved.getPasswordHash())).isTrue();
    assertThat(result.accessToken()).isEqualTo("novo-token");
    assertThat(result.mustChangePassword()).isFalse();
  }

  @Test
  void rejectsChangeWhenCurrentPasswordIsWrong() {
    User user = new User(
        userId, "admin@fiapx.local", passwordEncoder.encode(currentPassword), Role.ADMIN, Instant.now(), true
    );
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.handle(new ChangePasswordCommand(userId, "senha-errada", "nova-senha-123")))
        .isInstanceOf(InvalidCurrentPasswordException.class);
  }

  @Test
  void rejectsChangeWhenUserDoesNotExist() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.handle(new ChangePasswordCommand(userId, currentPassword, "nova-senha-123")))
        .isInstanceOf(UserNotFoundException.class);
  }
}
