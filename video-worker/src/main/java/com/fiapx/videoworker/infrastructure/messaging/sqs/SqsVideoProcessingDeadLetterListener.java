package com.fiapx.videoworker.infrastructure.messaging.sqs;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.model.OutboundMessage;
import com.fiapx.videoworker.domain.port.MessagePublisher;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** DLQ de processamento: o vídeo esgotou o redrive — gera FAILED de forma confiável, como no RabbitMQ. */
@Component
public class SqsVideoProcessingDeadLetterListener implements SqsMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(SqsVideoProcessingDeadLetterListener.class);

  private final MessagePublisher messagePublisher;
  private final ObjectMapper objectMapper;
  private final QueueProperties queueProperties;

  public SqsVideoProcessingDeadLetterListener(MessagePublisher messagePublisher, ObjectMapper objectMapper,
      QueueProperties queueProperties) {
    this.messagePublisher = messagePublisher;
    this.objectMapper = objectMapper;
    this.queueProperties = queueProperties;
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
      log.warn("Vídeo {} esgotou as tentativas de processamento e caiu na DLQ", payload.videoId());
      ProcessingResultMessage message = new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED,
          payload.videoId(), null, "Processamento falhou após esgotar as tentativas", payload.eventId());
      String eventId = payload.eventId() != null ? payload.eventId().toString() : null;
      messagePublisher.publish(queueProperties.statusUpdates(),
          OutboundMessage.of(objectMapper.writeValueAsString(message), correlationId, eventId));
    } finally {
      MDC.remove("correlationId");
    }
  }
}
