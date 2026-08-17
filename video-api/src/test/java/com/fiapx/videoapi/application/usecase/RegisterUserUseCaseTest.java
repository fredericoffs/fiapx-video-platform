package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.RegisterUserCommand;
import com.fiapx.videoapi.application.dto.RegisterUserResult;
import com.fiapx.videoapi.domain.exception.EmailAlreadyRegisteredException;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterUserUseCaseTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final RegisterUserUseCase useCase = new RegisterUserUseCase(userRepository, passwordEncoder);

  @Test
  void registersNewUserWithHashedPassword() {
    when(userRepository.existsByEmail("new@fiapx.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    RegisterUserResult result = useCase.handle(new RegisterUserCommand("new@fiapx.com", "senha-secreta-123"));

    assertThat(result.email()).isEqualTo("new@fiapx.com");
  }

  @Test
  void rejectsRegistrationWhenEmailAlreadyExists() {
    when(userRepository.existsByEmail("existing@fiapx.com")).thenReturn(true);

    assertThatThrownBy(() -> useCase.handle(new RegisterUserCommand("existing@fiapx.com", "senha-secreta-123")))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }
}
