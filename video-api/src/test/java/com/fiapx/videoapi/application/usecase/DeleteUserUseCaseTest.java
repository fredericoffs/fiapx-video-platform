package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteUserUseCaseTest {

  private UserRepository userRepository;
  private VideoRepository videoRepository;
  private DeleteVideoUseCase deleteVideoUseCase;
  private DeleteUserUseCase useCase;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    videoRepository = mock(VideoRepository.class);
    deleteVideoUseCase = mock(DeleteVideoUseCase.class);
    useCase = new DeleteUserUseCase(userRepository, videoRepository, deleteVideoUseCase);
    userId = UUID.randomUUID();
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(new User(userId, "dono@example.com", "hash", Role.USER, Instant.now())));
  }

  @Test
  void deletesEachVideoThenTheUser() {
    Video video1 = Video.newQueued(UUID.randomUUID(), userId, "a.mp4", "raw/a.mp4");
    Video video2 = Video.newQueued(UUID.randomUUID(), userId, "b.mp4", "raw/b.mp4");
    when(videoRepository.findByUserId(userId, null, 0, 50))
        .thenReturn(new PageResult<>(List.of(video1, video2), 0, 50, 2))
        .thenReturn(new PageResult<>(List.of(), 0, 50, 0));

    useCase.handle(userId);

    verify(deleteVideoUseCase).handle(video1.getId(), userId, true);
    verify(deleteVideoUseCase).handle(video2.getId(), userId, true);
    verify(userRepository).deleteById(userId);
  }

  @Test
  void userWithNoVideosIsJustDeleted() {
    when(videoRepository.findByUserId(userId, null, 0, 50)).thenReturn(new PageResult<>(List.of(), 0, 50, 0));

    useCase.handle(userId);

    verify(deleteVideoUseCase, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean());
    verify(userRepository).deleteById(userId);
  }

  @Test
  void nonExistentUserThrowsNotFoundAndDeletesNothing() {
    UUID missingId = UUID.randomUUID();
    when(userRepository.findById(missingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.handle(missingId)).isInstanceOf(UserNotFoundException.class);

    verify(userRepository, never()).deleteById(missingId);
    verify(videoRepository, never()).findByUserId(org.mockito.ArgumentMatchers.eq(missingId), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void keepsPagingUntilNoVideosAreLeft() {
    Video video1 = Video.newQueued(UUID.randomUUID(), userId, "a.mp4", "raw/a.mp4");
    when(videoRepository.findByUserId(userId, null, 0, 50))
        .thenReturn(new PageResult<>(List.of(video1), 0, 50, 1))
        .thenReturn(new PageResult<>(List.of(video1), 0, 50, 1))
        .thenReturn(new PageResult<>(List.of(), 0, 50, 0));

    useCase.handle(userId);

    verify(deleteVideoUseCase, times(2)).handle(video1.getId(), userId, true);
    verify(videoRepository, times(3)).findByUserId(userId, null, 0, 50);
  }
}
