package com.fiapx.videoapi.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fiapx.videoapi.application.dto.VideoUploadCommand;
import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.domain.exception.UnsupportedVideoFormatException;
import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.domain.port.StorageCleanup;
import com.fiapx.videoapi.domain.port.StorageClient;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RequestVideoProcessingUseCaseTest {

  private VideoRepository videoRepository;
  private OutboxEventRepository outboxEventRepository;
  private StorageClient storageClient;
  private StorageCleanup storageCleanup;
  private UserRepository users;
  private RequestVideoProcessingUseCase useCase;

  @BeforeEach
  void setUp() {
    videoRepository = mock(VideoRepository.class);
    outboxEventRepository = mock(OutboxEventRepository.class);
    storageClient = mock(StorageClient.class);
    ObjectMapper objectMapper = JsonMapper.builder().build();
    StorageProperties storageProperties = new StorageProperties("http://localhost:9000", "key", "secret",
        "videos-raw", "videos-processed", "us-east-1", true);

    when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> invocation.getArgument(0));

    users = mock(UserRepository.class);
    when(users.findById(any())).thenAnswer(i -> Optional.of(User.newUser(i.getArgument(0), "user@example.com", "hash")));
    when(users.findByIdForUpdate(any())).thenAnswer(i -> Optional.of(User.newUser(i.getArgument(0), "user@example.com", "hash")));
    storageCleanup = mock(StorageCleanup.class);
    when(storageCleanup.cancel(anyString(), anyString())).thenReturn(true);
    // TransactionTemplate real sobre um gerenciador mock: executa o callback sem banco.
    TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
    useCase = new RequestVideoProcessingUseCase(videoRepository, outboxEventRepository, storageClient,
        storageProperties, objectMapper, users, storageCleanup, transactionTemplate);
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
    assertThat(savedVideo.getFileSizeBytes()).isEqualTo(10L);
    assertThat(savedVideo.getStatus()).isEqualTo(VideoStatus.QUEUED);
    // Chave de storage com nome interno: o nome original do usuário nunca vira caminho.
    assertThat(savedVideo.getStorageKey()).isEqualTo("raw/" + savedVideo.getId() + "/source.mp4");

    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(eventCaptor.capture());
    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getEventType()).isEqualTo("VideoUploadRequested");
    assertThat(savedEvent.getAggregateId()).isEqualTo(savedVideo.getId());
    assertThat(savedEvent.getPayload()).contains(savedVideo.getId().toString()).contains("movie.mp4");
    assertThat(savedEvent.isPublished()).isFalse();
  }

  @Test
  void schedulesOrphanCleanupBeforeUploadAndCancelsItOnlyAfterLockingTheUser() {
    UUID userId = UUID.randomUUID();
    VideoUploadCommand command = new VideoUploadCommand(userId, "movie.mp4",
        new ByteArrayInputStream("fake-bytes".getBytes()), 10L, "video/mp4");

    VideoUploadResult result = useCase.handle(command);

    String key = "raw/" + result.id() + "/source.mp4";
    InOrder order = inOrder(users, storageCleanup, storageClient, videoRepository);
    order.verify(users).findById(userId);
    order.verify(storageCleanup).schedule("videos-raw", key, RequestVideoProcessingUseCase.ORPHAN_CLEANUP_DELAY);
    order.verify(storageClient).upload(eq("videos-raw"), eq(key), any(), eq(10L), eq("video/mp4"));
    order.verify(users).findByIdForUpdate(userId);
    order.verify(storageCleanup).cancel("videos-raw", key);
    order.verify(videoRepository).save(any(Video.class));
  }

  @Test
  void keepsTheCleanupReservationWhenTheUserIsDeletedDuringTheUpload() {
    UUID userId = UUID.randomUUID();
    when(users.findByIdForUpdate(userId)).thenReturn(Optional.empty());
    VideoUploadCommand command = new VideoUploadCommand(userId, "movie.mp4",
        new ByteArrayInputStream("fake-bytes".getBytes()), 10L, "video/mp4");

    assertThatThrownBy(() -> useCase.handle(command)).isInstanceOf(UserNotFoundException.class);

    verify(storageCleanup).schedule(eq("videos-raw"), anyString(), any());
    verify(storageCleanup, never()).cancel(anyString(), anyString());
    verify(videoRepository, never()).save(any(Video.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }

  @Test
  void keepsTheCleanupReservationWhenTheStorageUploadFails() {
    doThrow(new IllegalStateException("S3 indisponível"))
        .when(storageClient).upload(anyString(), anyString(), any(), anyLong(), anyString());
    VideoUploadCommand command = new VideoUploadCommand(UUID.randomUUID(), "movie.mp4",
        new ByteArrayInputStream("fake-bytes".getBytes()), 10L, "video/mp4");

    assertThatThrownBy(() -> useCase.handle(command)).hasMessageContaining("S3 indisponível");

    verify(storageCleanup).schedule(eq("videos-raw"), anyString(), any());
    verify(storageCleanup, never()).cancel(anyString(), anyString());
    verify(videoRepository, never()).save(any(Video.class));
  }

  @Test
  void rejectsTheUploadWhenTheCleanupAlreadyConsumedTheReservation() {
    when(storageCleanup.cancel(anyString(), anyString())).thenReturn(false);
    VideoUploadCommand command = new VideoUploadCommand(UUID.randomUUID(), "movie.mp4",
        new ByteArrayInputStream("fake-bytes".getBytes()), 10L, "video/mp4");

    assertThatThrownBy(() -> useCase.handle(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("venceu antes do fim do upload");

    verify(videoRepository, never()).save(any(Video.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }

  @Test
  void rejectsUnknownUserBeforeReservingOrUploading() {
    UUID userId = UUID.randomUUID();
    when(users.findById(userId)).thenReturn(Optional.empty());
    VideoUploadCommand command = new VideoUploadCommand(userId, "movie.mp4",
        new ByteArrayInputStream("fake-bytes".getBytes()), 10L, "video/mp4");

    assertThatThrownBy(() -> useCase.handle(command)).isInstanceOf(UserNotFoundException.class);

    verify(storageCleanup, never()).schedule(anyString(), anyString(), any());
    verify(storageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
  }

  @Test
  void rejectsUnsupportedFormatWithoutUploadingOrPersisting() {
    InputStream content = new ByteArrayInputStream("fake-bytes".getBytes());
    VideoUploadCommand command = new VideoUploadCommand(UUID.randomUUID(), "movie.txt", content, 10L, "text/plain");

    assertThatThrownBy(() -> useCase.handle(command)).isInstanceOf(UnsupportedVideoFormatException.class);

    verify(storageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
    verify(storageCleanup, never()).schedule(anyString(), anyString(), any());
    verify(videoRepository, never()).save(any(Video.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }
}
