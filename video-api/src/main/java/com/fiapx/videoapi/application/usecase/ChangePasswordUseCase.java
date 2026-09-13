package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.ChangePasswordCommand;
import com.fiapx.videoapi.domain.exception.InvalidCurrentPasswordException;
import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ChangePasswordUseCase {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public ChangePasswordUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public void handle(ChangePasswordCommand command) {
    User user = userRepository.findById(command.userId())
        .orElseThrow(() -> new UserNotFoundException(command.userId()));
    if (!passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
      throw new InvalidCurrentPasswordException();
    }
    User updated = user.withPasswordChanged(passwordEncoder.encode(command.newPassword()));
    userRepository.save(updated);
  }
}
