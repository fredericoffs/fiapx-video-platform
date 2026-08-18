package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.VideoUploadCommand;
import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.domain.exception.UnsupportedVideoFormatException;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.domain.port.StorageClient;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestVideoProcessingUseCaseTest {

  private VideoRepository videoRepository;
  private OutboxEventRepository outboxEventRepository;
  private StorageClient storageClient;
  private RequestVideoProcessingUseCase useCase;

  @BeforeEach
  void setUp() {
    videoRepository = mock(VideoRepository.class);
    outboxEventRepository = mock(OutboxEventRepository.class);
    storageClient = mock(StorageClient.class);
    ObjectMapper objectMapper = JsonMapper.builder().build();
    StorageProperties storageProperties = new StorageProperties("http://localhost:9000", "key", "secret",
        "videos-raw", "videos-processed");

    when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> invocation.getArgument(0));

    useCase = new RequestVideoProcessingUseCase(videoRepository, outboxEventRepository, storageClient,
        storageProperties, objectMapper);
  }

  @Test
  void uploadsToRawBucketAndPersistsQueuedVideoWithOutboxEvent() {
    InputStream content = new ByteArrayInputStream("fake-bytes".getBytes());
    UUID userId = UUID.randomUUID();
    VideoUploadCommand command = new VideoUploadCommand(userId, "movie.mp4", content, 10L, "video/mp4");

    VideoUploadResult result = useCase.handle(command);

    assertThat(result.status()).isEqualTo(VideoStatus.QUEUED);

    verify(storageClient).upload(eq("videos-raw"), anyString(), eq(content), eq(10L), eq("video/mp4"));

    ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
    verify(videoRepository).save(videoCaptor.capture());
    Video savedVideo = videoCaptor.getValue();
    assertThat(savedVideo.getUserId()).isEqualTo(userId);
    assertThat(savedVideo.getOriginalFilename()).isEqualTo("movie.mp4");
    assertThat(savedVideo.getStatus()).isEqualTo(VideoStatus.QUEUED);
    assertThat(savedVideo.getStorageKey()).contains(savedVideo.getId().toString()).contains("movie.mp4");

    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(eventCaptor.capture());
    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getEventType()).isEqualTo("VideoUploadRequested");
    assertThat(savedEvent.getAggregateId()).isEqualTo(savedVideo.getId());
    assertThat(savedEvent.getPayload()).contains(savedVideo.getId().toString()).contains("movie.mp4");
    assertThat(savedEvent.isPublished()).isFalse();
  }

  @Test
  void rejectsUnsupportedFormatWithoutUploadingOrPersisting() {
    InputStream content = new ByteArrayInputStream("fake-bytes".getBytes());
    VideoUploadCommand command = new VideoUploadCommand(UUID.randomUUID(), "movie.txt", content, 10L, "text/plain");

    assertThatThrownBy(() -> useCase.handle(command)).isInstanceOf(UnsupportedVideoFormatException.class);

    verify(storageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
    verify(videoRepository, never()).save(any(Video.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }
}
