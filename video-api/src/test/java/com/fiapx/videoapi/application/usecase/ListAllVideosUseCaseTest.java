package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.VideoWithOwner;
import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoFilter;
import com.fiapx.videoapi.domain.model.VideoSort;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListAllVideosUseCaseTest {

  private final VideoRepository videoRepository = mock(VideoRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final ListAllVideosUseCase useCase = new ListAllVideosUseCase(videoRepository, userRepository);

  @Test
  void delegatesPaginationAndStatusFilterToTheRepositoryAndResolvesOwnerEmail() {
    UUID userId = UUID.randomUUID();
    Video video = Video.newQueued(UUID.randomUUID(), userId, "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findAll(VideoFilter.byStatus(VideoStatus.FAILED), VideoSort.NEWEST_FIRST, 0, 20))
        .thenReturn(new PageResult<>(List.of(video), 0, 20, 1));
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(new User(userId, "dono@example.com", "hash", Role.USER, Instant.now())));

    PageResult<VideoWithOwner> result =
        useCase.handle(VideoFilter.byStatus(VideoStatus.FAILED), VideoSort.NEWEST_FIRST, 0, 20);

    assertThat(result.items()).hasSize(1);
    VideoWithOwner item = result.items().get(0);
    assertThat(item.video()).isSameAs(video);
    assertThat(item.ownerEmail()).isEqualTo("dono@example.com");
    assertThat(result.page()).isEqualTo(0);
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  void ownerEmailIsNullWhenUserCannotBeResolved() {
    UUID userId = UUID.randomUUID();
    Video video = Video.newQueued(UUID.randomUUID(), userId, "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findAll(VideoFilter.NONE, VideoSort.NEWEST_FIRST, 0, 20))
        .thenReturn(new PageResult<>(List.of(video), 0, 20, 1));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    PageResult<VideoWithOwner> result = useCase.handle(VideoFilter.NONE, VideoSort.NEWEST_FIRST, 0, 20);

    assertThat(result.items().get(0).ownerEmail()).isNull();
  }

  @Test
  void delegatesFilenameFilterToTheRepository() {
    UUID userId = UUID.randomUUID();
    Video video = Video.newQueued(UUID.randomUUID(), userId, "ferias.mp4", "raw/ferias.mp4");
    when(videoRepository.findAll(new VideoFilter(null, "ferias", null, null), VideoSort.NEWEST_FIRST, 0, 20))
        .thenReturn(new PageResult<>(List.of(video), 0, 20, 1));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    PageResult<VideoWithOwner> result =
        useCase.handle(new VideoFilter(null, "ferias", null, null), VideoSort.NEWEST_FIRST, 0, 20);

    assertThat(result.items()).hasSize(1);
  }
}
