package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.LoginCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.domain.exception.InvalidCredentialsException;
import com.fiapx.videoapi.domain.exception.LoginRateLimitExceededException;
import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.LoginRateLimiter;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.infrastructure.config.JwtProperties;
import com.fiapx.videoapi.infrastructure.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final JwtProperties jwtProperties = new JwtProperties("test-secret-min-32-characters-long!!", 15);
  private final JwtService jwtService = new JwtService(jwtProperties);
  private final LoginRateLimiter loginRateLimiter = mock(LoginRateLimiter.class);
  private final LoginUseCase useCase =
      new LoginUseCase(userRepository, passwordEncoder, jwtService, loginRateLimiter, jwtProperties);

  private final String email = "user@fiapx.com";
  private final String rawPassword = "senha-secreta-123";
  private User user;

  @BeforeEach
  void setUp() {
    user = new User(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), Role.USER, null);
  }

  @Test
  void returnsJwtAndResetsRateLimitOnSuccessfulLogin() {
    when(loginRateLimiter.isBlocked(email)).thenReturn(false);
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

    LoginResult result = useCase.handle(new LoginCommand(email, rawPassword));

    assertThat(jwtService.parseUserId(result.accessToken())).isEqualTo(user.getId());
    assertThat(result.role()).isEqualTo(Role.USER);
    assertThat(jwtService.parseMustChangePassword(result.accessToken())).isFalse();
    verify(loginRateLimiter).reset(email);
    verify(loginRateLimiter, never()).registerFailedAttempt(email);
  }

  @Test
  void embedsMustChangePasswordInTheTokenWhenTheUserIsMarkedForIt() {
    User mustChangeUser = new User(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), Role.ADMIN, null, true);
    when(loginRateLimiter.isBlocked(email)).thenReturn(false);
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(mustChangeUser));

    LoginResult result = useCase.handle(new LoginCommand(email, rawPassword));

    assertThat(result.mustChangePassword()).isTrue();
    assertThat(jwtService.parseMustChangePassword(result.accessToken())).isTrue();
  }

  @Test
  void rejectsLoginWithWrongPasswordAndRegistersFailedAttempt() {
    when(loginRateLimiter.isBlocked(email)).thenReturn(false);
    when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.handle(new LoginCommand(email, "senha-errada")))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginRateLimiter).registerFailedAttempt(email);
  }

  @Test
  void rejectsLoginForUnknownEmailAndRegistersFailedAttempt() {
    when(loginRateLimiter.isBlocked(email)).thenReturn(false);
    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.handle(new LoginCommand(email, rawPassword)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginRateLimiter).registerFailedAttempt(email);
  }

  @Test
  void rejectsLoginWhenRateLimited() {
    when(loginRateLimiter.isBlocked(email)).thenReturn(true);

    assertThatThrownBy(() -> useCase.handle(new LoginCommand(email, rawPassword)))
        .isInstanceOf(LoginRateLimitExceededException.class);
    verify(userRepository, never()).findByEmail(email);
  }
}
