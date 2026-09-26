package com.fiapx.videoworker.infrastructure.messaging.sqs;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.model.OutboundMessage;
import com.fiapx.videoworker.domain.port.MessagePublisher;
import com.fiapx.videoworker.domain.port.ProcessingLease;
import com.fiapx.videoworker.domain.port.StorageClient;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.config.StorageProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * DLQ de processamento: o vídeo esgotou o redrive — gera FAILED de forma confiável, como no RabbitMQ.
 */
@Component
public class SqsVideoProcessingDeadLetterListener implements SqsMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(SqsVideoProcessingDeadLetterListener.class);

  private final MessagePublisher messagePublisher;
  private final ObjectMapper objectMapper;
  private final QueueProperties queueProperties;
  private final ProcessingLease leases;
  private final StorageClient storage;
  private final StorageProperties storageProperties;

  public SqsVideoProcessingDeadLetterListener(
      MessagePublisher messagePublisher,
      ObjectMapper objectMapper,
      QueueProperties queueProperties,
      ProcessingLease leases,
      StorageClient storage,
      StorageProperties storageProperties) {
    this.messagePublisher = messagePublisher;
    this.objectMapper = objectMapper;
    this.queueProperties = queueProperties;
    this.leases = leases;
    this.storage = storage;
    this.storageProperties = storageProperties;
  }

  @Override
  public void handle(String body, Map<String, String> attributes) {
    String correlationId = attributes.get(CORRELATION_ID);
    MDC.put("correlationId", correlationId);
    try {
      VideoUploadRequestedPayload payload;
      try {
        payload = objectMapper.readValue(body, VideoUploadRequestedPayload.class);
      } catch (JacksonException e) {
        log.error("Mensagem malformada na DLQ de processamento, descartada: {}", e.getMessage());
        return;
      }
      try (var lease = leases.acquire(payload.videoId())) {
        lease.check();
        String zipKey = "processed/" + payload.videoId() + "/" + payload.videoId() + ".zip";
        boolean completed = storage.exists(storageProperties.bucketProcessed(), zipKey);
        log.warn("Vídeo {} esgotou as tentativas de processamento e caiu na DLQ", payload.videoId());
        ProcessingResultMessage message = new ProcessingResultMessage(
            completed ? ProcessingEventType.PROCESSING_COMPLETED : ProcessingEventType.PROCESSING_FAILED,
            payload.videoId(), completed ? zipKey : null, completed ? null : "Processamento falhou após esgotar as tentativas", payload.eventId());
        String eventId = payload.eventId() != null ? payload.eventId().toString() : null;
        messagePublisher.publish(queueProperties.statusUpdates(),
            OutboundMessage.of(objectMapper.writeValueAsString(message), correlationId, eventId));
      }
    } finally {
      MDC.remove("correlationId");
    }
  }
}
