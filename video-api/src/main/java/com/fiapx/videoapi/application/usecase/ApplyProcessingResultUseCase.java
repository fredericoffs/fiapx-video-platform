package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.event.NotificationRequestedPayload;
import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ApplyProcessingResultUseCase {

  public static final String EVENT_TYPE_NOTIFICATION_REQUESTED = "NotificationRequested";

  private static final Logger log = LoggerFactory.getLogger(ApplyProcessingResultUseCase.class);

  private final VideoRepository videoRepository;
  private final UserRepository userRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public ApplyProcessingResultUseCase(
      VideoRepository videoRepository,
      UserRepository userRepository,
      OutboxEventRepository outboxEventRepository,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry
  ) {
    this.videoRepository = videoRepository;
    this.userRepository = userRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public void handle(ProcessingResultMessage message) {
    Optional<Video> maybeVideo = videoRepository.findById(message.videoId());
    if (maybeVideo.isEmpty()) {
      log.warn("Evento de resultado recebido para vídeo inexistente: {}", message.videoId());
      return;
    }

    Video video = maybeVideo.get();
    if (video.isTerminal()) {
      log.info("Ignorando evento de resultado para vídeo {} já em estado terminal ({})", video.getId(), video.getStatus());
      return;
    }

    switch (message.eventType()) {
      case PROCESSING_COMPLETED -> video.complete(message.zipStorageKey());
      case PROCESSING_FAILED -> video.fail(message.errorMessage());
    }

    videoRepository.save(video);
    recordProcessingMetrics(video);

    if (message.eventType() == ProcessingEventType.PROCESSING_FAILED) {
      String recipientEmail = resolveRecipientEmail(video);
      NotificationRequestedPayload payload =
          new NotificationRequestedPayload(video.getId(), video.getErrorMessage(), recipientEmail);
      OutboxEvent event = OutboxEvent.newEvent(
          video.getId(), EVENT_TYPE_NOTIFICATION_REQUESTED, writeJson(payload), MDC.get("correlationId")
      );
      outboxEventRepository.save(event);
    }
  }

  // Não uso um Timer.Sample vivo aqui: ele não sobreviveria o vídeo atravessar processos (video-api
  // -> fila -> video-worker -> fila -> video-api) via JVMs diferentes. Calculo a duração direto a
  // partir do createdAt já persistido, que é a mesma informação, sem depender de estado em memória.
  private void recordProcessingMetrics(Video video) {
    String status = video.getStatus().name();
    meterRegistry.counter("fiapx.video.processed", "status", status).increment();
    meterRegistry.timer("fiapx.video.processing.duration", "status", status)
        .record(Duration.between(video.getCreatedAt(), Instant.now()));
  }

  private String resolveRecipientEmail(Video video) {
    if (video.getUserId() == null) {
      return null;
    }
    return userRepository.findById(video.getUserId()).map(User::getEmail).orElse(null);
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      throw new IllegalStateException("Falha ao serializar payload do evento", e);
    }
  }
}
