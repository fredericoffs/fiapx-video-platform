package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListAllUsersUseCaseTest {

  @Test
  void delegatesPaginationToTheRepository() {
    UserRepository userRepository = mock(UserRepository.class);
    PageResult<User> expected = new PageResult<>(List.of(), 1, 10, 0);
    when(userRepository.findAll(null, 1, 10)).thenReturn(expected);

    ListAllUsersUseCase useCase = new ListAllUsersUseCase(userRepository);

    assertThat(useCase.handle(null, 1, 10)).isSameAs(expected);
  }

  @Test
  void delegatesEmailFilterToTheRepository() {
    UserRepository userRepository = mock(UserRepository.class);
    PageResult<User> expected = new PageResult<>(List.of(), 0, 20, 0);
    when(userRepository.findAll("fiapx", 0, 20)).thenReturn(expected);

    ListAllUsersUseCase useCase = new ListAllUsersUseCase(userRepository);

    assertThat(useCase.handle("fiapx", 0, 20)).isSameAs(expected);
  }
}
