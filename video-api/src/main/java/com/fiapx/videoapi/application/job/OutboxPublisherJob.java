package com.fiapx.videoapi.application.job;

import com.fiapx.videoapi.application.usecase.ApplyProcessingResultUseCase;
import com.fiapx.videoapi.application.usecase.RequestVideoProcessingUseCase;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.infrastructure.config.OutboxProperties;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisherJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

  private final OutboxEventRepository outboxEventRepository;
  private final MessagePublisher messagePublisher;
  private final OutboxProperties outboxProperties;
  private final QueueProperties queueProperties;

  public OutboxPublisherJob(
      OutboxEventRepository outboxEventRepository,
      MessagePublisher messagePublisher,
      OutboxProperties outboxProperties,
      QueueProperties queueProperties
  ) {
    this.outboxEventRepository = outboxEventRepository;
    this.messagePublisher = messagePublisher;
    this.outboxProperties = outboxProperties;
    this.queueProperties = queueProperties;
  }

  @Scheduled(fixedDelayString = "${fiapx.outbox.publish-interval-ms:3000}")
  public void publishPending() {
    List<OutboxEvent> pending = outboxEventRepository.findUnpublished(outboxProperties.batchSize());
    for (OutboxEvent event : pending) {
      try {
        String targetQueue = resolveQueue(event.getEventType());
        // TODO(Sprint 7, increment 3.2): passar o correlation_id real da coluna do outbox.
        messagePublisher.publish(targetQueue, event.getPayload(), null);
        outboxEventRepository.markPublished(event.getId());
      } catch (Exception e) {
        log.error("Falha ao publicar outbox event {} ({})", event.getId(), event.getEventType(), e);
      }
    }
  }

  private String resolveQueue(String eventType) {
    if (RequestVideoProcessingUseCase.EVENT_TYPE_VIDEO_UPLOAD_REQUESTED.equals(eventType)) {
      return queueProperties.processing();
    }
    if (ApplyProcessingResultUseCase.EVENT_TYPE_NOTIFICATION_REQUESTED.equals(eventType)) {
      return queueProperties.notification();
    }
    throw new IllegalStateException("Nenhuma fila mapeada para o tipo de evento: " + eventType);
  }
}
