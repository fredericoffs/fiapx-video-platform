package com.fiapx.videoapi.application.job;

import com.fiapx.videoapi.application.usecase.ApplyProcessingResultUseCase;
import com.fiapx.videoapi.application.usecase.RequestVideoProcessingUseCase;
import com.fiapx.videoapi.domain.model.OutboundMessage;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.infrastructure.config.OutboxProperties;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relay da outbox: reserva um evento por vez (lease, SKIP LOCKED), publica fora de transação e só marca publicado após a confirmação do broker. Falha
 * libera a reserva e conta a tentativa; o evento volta no próximo ciclo (entrega at-least-once — o consumidor precisa ser idempotente).
 */
@Component
public class OutboxPublisherJob {

  static final Duration LEASE = Duration.ofSeconds(30);
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);
  private static final String CORRELATION_ID = "correlationId";
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
    for (int i = 0; i < outboxProperties.batchSize(); i++) {
      List<OutboxEvent> claimed = outboxEventRepository.claimUnpublished(1, LEASE);
      if (claimed.isEmpty()) {
        return;
      }
      OutboxEvent event = claimed.getFirst();
      // O job roda fora de qualquer requisição: restauro o correlationId gravado com o evento
      // para os logs da publicação entrarem no mesmo rastro do upload.
      if (event.getCorrelationId() != null) {
        MDC.put(CORRELATION_ID, event.getCorrelationId());
      }
      try {
        String targetQueue = resolveQueue(event.getEventType());
        messagePublisher.publish(targetQueue,
            OutboundMessage.of(event.getPayload(), event.getCorrelationId(), event.getId().toString()));
        outboxEventRepository.markPublished(event.getId(), event.getLockToken());
        log.info("Evento {} do vídeo {} publicado em {}", event.getEventType(), event.getAggregateId(), targetQueue);
      } catch (Exception e) {
        log.error("Falha ao publicar outbox event {} ({}), tentativa {}", event.getId(), event.getEventType(), event.getAttempts() + 1, e);
        outboxEventRepository.releaseAfterFailure(event.getId(), event.getLockToken());
        return;
      } finally {
        MDC.remove(CORRELATION_ID);
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
