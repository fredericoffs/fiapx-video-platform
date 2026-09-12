package com.fiapx.videoworker.infrastructure.messaging;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.port.MessagePublisher;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "fiapx.messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class VideoProcessingDeadLetterListener {

  private static final Logger log = LoggerFactory.getLogger(VideoProcessingDeadLetterListener.class);

  private final MessagePublisher messagePublisher;
  private final ObjectMapper objectMapper;
  private final QueueProperties queueProperties;

  public VideoProcessingDeadLetterListener(
      MessagePublisher messagePublisher,
      ObjectMapper objectMapper,
      QueueProperties queueProperties
  ) {
    this.messagePublisher = messagePublisher;
    this.objectMapper = objectMapper;
    this.queueProperties = queueProperties;
  }

  @RabbitListener(queues = "${fiapx.queues.processing-dlq}")
  public void onMessage(String rawJson, @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    MDC.put("correlationId", correlationId);
    try {
      VideoUploadRequestedPayload payload;
      try {
        payload = objectMapper.readValue(rawJson, VideoUploadRequestedPayload.class);
      } catch (JacksonException e) {
        // Malformada já na origem: não há videoId para marcar como FAILED — só registro e descarto.
        log.error("Mensagem malformada na DLQ de processamento, descartada: {}", e.getMessage());
        return;
      }
      log.warn("Vídeo {} esgotou as tentativas de processamento e caiu na DLQ", payload.videoId());

      ProcessingResultMessage message = new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED,
          payload.videoId(), null, "Processamento falhou após esgotar as tentativas");
      messagePublisher.publish(queueProperties.statusUpdates(), objectMapper.writeValueAsString(message), correlationId);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
