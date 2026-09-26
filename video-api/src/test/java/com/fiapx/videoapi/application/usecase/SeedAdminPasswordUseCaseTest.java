package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedAdminPasswordUseCaseTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final SeedAdminPasswordUseCase useCase = new SeedAdminPasswordUseCase(userRepository, passwordEncoder);

  @Test
  void doesNothingWhenSeedPasswordIsBlank() {
    useCase.handle("");

    verify(userRepository, never()).findByEmail(any());
  }

  @Test
  void doesNothingWhenSeedPasswordIsNull() {
    useCase.handle(null);

    verify(userRepository, never()).findByEmail(any());
  }

  @Test
  void appliesSeedPasswordWhenAdminStillMustChangeIt() {
    User admin = new User(UUID.randomUUID(), "admin@fiapx.local", "placeholder-hash", Role.ADMIN, null, true);
    when(userRepository.findByEmail("admin@fiapx.local")).thenReturn(Optional.of(admin));

    useCase.handle("senha-vinda-do-ssm");

    org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.isMustChangePassword()).isTrue();
    assertThat(passwordEncoder.matches("senha-vinda-do-ssm", saved.getPasswordHash())).isTrue();
  }

  @Test
  void doesNotTouchAdminThatAlreadyChangedThePassword() {
    User admin = new User(UUID.randomUUID(), "admin@fiapx.local", "real-hash-chosen-by-user", Role.ADMIN, null, false);
    when(userRepository.findByEmail("admin@fiapx.local")).thenReturn(Optional.of(admin));

    useCase.handle("senha-vinda-do-ssm");

    verify(userRepository, never()).save(any());
  }

  @Test
  void doesNothingWhenAdminUserDoesNotExist() {
    when(userRepository.findByEmail("admin@fiapx.local")).thenReturn(Optional.empty());

    useCase.handle("senha-vinda-do-ssm");

    verify(userRepository, never()).save(any());
  }
}
