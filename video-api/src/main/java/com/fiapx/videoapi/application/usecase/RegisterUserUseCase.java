package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.RegisterUserCommand;
import com.fiapx.videoapi.application.dto.RegisterUserResult;
import com.fiapx.videoapi.domain.exception.EmailAlreadyRegisteredException;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterUserUseCase {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public RegisterUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public RegisterUserResult handle(RegisterUserCommand command) {
    if (userRepository.existsByEmail(command.email())) {
      throw new EmailAlreadyRegisteredException(command.email());
    }

    User user = User.newUser(UUID.randomUUID(), command.email(), passwordEncoder.encode(command.rawPassword()));
    User saved = userRepository.save(user);
    return new RegisterUserResult(saved.getId(), saved.getEmail());
  }
}
