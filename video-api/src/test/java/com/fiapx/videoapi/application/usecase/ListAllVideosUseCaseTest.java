package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListAllVideosUseCaseTest {

  @Test
  void delegatesPaginationAndStatusFilterToTheRepository() {
    VideoRepository videoRepository = mock(VideoRepository.class);
    PageResult<Video> expected = new PageResult<>(List.of(), 0, 20, 0);
    when(videoRepository.findAll(VideoStatus.FAILED, 0, 20)).thenReturn(expected);

    ListAllVideosUseCase useCase = new ListAllVideosUseCase(videoRepository);

    assertThat(useCase.handle(VideoStatus.FAILED, 0, 20)).isSameAs(expected);
  }
}
