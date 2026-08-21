package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.exception.VideoNotFoundException;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.StorageClient;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteVideoUseCaseTest {

  private VideoRepository videoRepository;
  private StorageClient storageClient;
  private DeleteVideoUseCase useCase;

  @BeforeEach
  void setUp() {
    videoRepository = mock(VideoRepository.class);
    storageClient = mock(StorageClient.class);
    StorageProperties storageProperties = new StorageProperties("http://localhost:9000", "key", "secret",
        "videos-raw", "videos-processed");
    useCase = new DeleteVideoUseCase(videoRepository, storageClient, storageProperties);
  }

  @Test
  void ownerDeletesQueuedVideoAndOnlyTheRawFile() {
    UUID userId = UUID.randomUUID();
    Video video = Video.newQueued(UUID.randomUUID(), userId, "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(video.getId(), userId, false);

    verify(storageClient, times(1)).delete(anyString(), any());
    verify(storageClient).delete("videos-raw", "raw/movie.mp4");
    verify(videoRepository).deleteById(video.getId());
  }

  @Test
  void ownerDeletesCompletedVideoAndBothFiles() {
    UUID userId = UUID.randomUUID();
    Video video = Video.newQueued(UUID.randomUUID(), userId, "movie.mp4", "raw/movie.mp4");
    video.complete("processed/movie.zip");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(video.getId(), userId, false);

    verify(storageClient).delete("videos-raw", "raw/movie.mp4");
    verify(storageClient).delete("videos-processed", "processed/movie.zip");
    verify(videoRepository).deleteById(video.getId());
  }

  @Test
  void nonOwnerNonAdminGetsNotFoundAndNothingIsDeleted() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    assertThatThrownBy(() -> useCase.handle(video.getId(), UUID.randomUUID(), false))
        .isInstanceOf(VideoNotFoundException.class);

    verify(storageClient, never()).delete(anyString(), any());
    verify(videoRepository, never()).deleteById(video.getId());
  }

  @Test
  void adminDeletesAnyUsersVideo() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(video.getId(), UUID.randomUUID(), true);

    verify(storageClient).delete("videos-raw", "raw/movie.mp4");
    verify(videoRepository).deleteById(video.getId());
  }

  @Test
  void nonExistentVideoThrowsNotFound() {
    UUID videoId = UUID.randomUUID();
    when(videoRepository.findById(videoId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.handle(videoId, UUID.randomUUID(), false))
        .isInstanceOf(VideoNotFoundException.class);
  }
}
