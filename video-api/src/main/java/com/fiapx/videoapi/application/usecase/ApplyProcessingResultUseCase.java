package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.event.NotificationRequestedPayload;
import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ApplyProcessingResultUseCase {

  public static final String EVENT_TYPE_NOTIFICATION_REQUESTED = "NotificationRequested";

  private static final Logger log = LoggerFactory.getLogger(ApplyProcessingResultUseCase.class);

  private final VideoRepository videoRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public ApplyProcessingResultUseCase(
      VideoRepository videoRepository,
      OutboxEventRepository outboxEventRepository,
      ObjectMapper objectMapper
  ) {
    this.videoRepository = videoRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
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

    if (message.eventType() == ProcessingEventType.PROCESSING_FAILED) {
      NotificationRequestedPayload payload = new NotificationRequestedPayload(video.getId(), video.getErrorMessage());
      OutboxEvent event = OutboxEvent.newEvent(video.getId(), EVENT_TYPE_NOTIFICATION_REQUESTED, writeJson(payload));
      outboxEventRepository.save(event);
    }
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      throw new IllegalStateException("Falha ao serializar payload do evento", e);
    }
  }
}
