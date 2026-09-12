package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static com.fiapx.videoapi.domain.model.Role.*;
import static java.time.Instant.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplyProcessingResultUseCaseTest {

  private final VideoRepository videoRepository = mock(VideoRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ApplyProcessingResultUseCase useCase = new ApplyProcessingResultUseCase(
      videoRepository, userRepository, outboxEventRepository, objectMapper, meterRegistry);

  @Test
  void ignoresEventForUnknownVideo() {
    UUID videoId = UUID.randomUUID();
    when(videoRepository.findById(videoId)).thenReturn(Optional.empty());

    useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, videoId, "zip-key", null));

    verify(videoRepository, never()).save(any());
  }

  @Test
  void ignoresEventForVideoAlreadyInTerminalStatus() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    video.complete("already-processed.zip");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "erro"));

    verify(videoRepository, never()).save(any());
  }

  @Test
  void processingStartedMovesQueuedVideoToProcessingWithoutMetricsOrNotification() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_STARTED, video.getId(), null, null));

    ArgumentCaptor<Video> captor = ArgumentCaptor.forClass(Video.class);
    verify(videoRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(VideoStatus.PROCESSING);
    verify(outboxEventRepository, never()).save(any());
    assertThat(meterRegistry.find("fiapx.video.processed").counter()).isNull();
  }

  @Test
  void repeatedProcessingStartedDoesNotSaveAgain() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    video.startProcessing();
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_STARTED, video.getId(), null, null));

    verify(videoRepository, never()).save(any());
  }

  @Test
  void lateProcessingStartedIsIgnoredForTerminalVideo() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    video.complete("zip-key");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_STARTED, video.getId(), null, null));

    verify(videoRepository, never()).save(any());
    assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
  }

  @Test
  void appliesCompletedStatusFromProcessingCompletedEvent() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(
        new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, video.getId(), "zip-key", null));

    ArgumentCaptor<Video> captor = ArgumentCaptor.forClass(Video.class);
    verify(videoRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(VideoStatus.COMPLETED);
    assertThat(captor.getValue().getZipStorageKey()).isEqualTo("zip-key");
  }

  @Test
  void appliesFailedStatusFromProcessingFailedEvent() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(
        new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "ffmpeg falhou"));

    ArgumentCaptor<Video> captor = ArgumentCaptor.forClass(Video.class);
    verify(videoRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(VideoStatus.FAILED);
    assertThat(captor.getValue().getErrorMessage()).isEqualTo("ffmpeg falhou");
  }

  @Test
  void createsNotificationOutboxEventOnFailureWithRecipientEmail() {
    UUID userId = UUID.randomUUID();
    Video video = Video.newQueued(UUID.randomUUID(), userId, "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(
            new User(userId, "dono@example.com", "hash", USER, now())));

    useCase.handle(
        new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "ffmpeg falhou"));

    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(eventCaptor.capture());
    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getEventType()).isEqualTo("NotificationRequested");
    assertThat(savedEvent.getAggregateId()).isEqualTo(video.getId());
    assertThat(savedEvent.getPayload())
        .contains(video.getId().toString())
        .contains("ffmpeg falhou")
        .contains("dono@example.com");
    assertThat(savedEvent.isPublished()).isFalse();
  }

  @Test
  void createsNotificationOutboxEventWithoutRecipientEmailWhenUserCannotBeResolved() {
    UUID userId = UUID.randomUUID();
    Video video = Video.newQueued(UUID.randomUUID(), userId, "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    useCase.handle(
        new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "ffmpeg falhou"));

    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getPayload()).contains("\"recipientEmail\":null");
  }

  @Test
  void recordsProcessedCounterAndDurationTimerOnCompleted() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(
        new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, video.getId(), "zip-key", null));

    assertThat(meterRegistry.counter("fiapx.video.processed", "status", "COMPLETED").count()).isEqualTo(1.0);
    assertThat(meterRegistry.timer("fiapx.video.processing.duration", "status", "COMPLETED").count()).isEqualTo(1L);
  }

  @Test
  void recordsProcessedCounterAndDurationTimerOnFailed() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(
        new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "ffmpeg falhou"));

    assertThat(meterRegistry.counter("fiapx.video.processed", "status", "FAILED").count()).isEqualTo(1.0);
    assertThat(meterRegistry.timer("fiapx.video.processing.duration", "status", "FAILED").count()).isEqualTo(1L);
  }

  @Test
  void doesNotRecordMetricsForIgnoredEvents() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    video.complete("already-processed.zip");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "erro"));

    assertThat(meterRegistry.find("fiapx.video.processed").counter()).isNull();
    assertThat(meterRegistry.find("fiapx.video.processing.duration").timer()).isNull();
  }

  @Test
  void doesNotCreateNotificationOutboxEventOnCompleted() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    useCase.handle(
        new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, video.getId(), "zip-key", null));

    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }
}
