package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ListAllUsersUseCase {

  private final UserRepository userRepository;

  public ListAllUsersUseCase(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public PageResult<User> handle(String emailFilter, int page, int size) {
    return userRepository.findAll(emailFilter, page, size);
  }
}
