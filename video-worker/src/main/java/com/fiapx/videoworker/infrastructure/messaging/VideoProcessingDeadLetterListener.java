package com.fiapx.videoworker.infrastructure.messaging;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class VideoProcessingDeadLetterListener {

  private static final Logger log = LoggerFactory.getLogger(VideoProcessingDeadLetterListener.class);

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final QueueProperties queueProperties;

  public VideoProcessingDeadLetterListener(
      RabbitTemplate rabbitTemplate,
      ObjectMapper objectMapper,
      QueueProperties queueProperties
  ) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.queueProperties = queueProperties;
  }

  @RabbitListener(queues = "${fiapx.queues.processing-dlq}")
  public void onMessage(String rawJson, @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    MDC.put("correlationId", correlationId);
    try {
      VideoUploadRequestedPayload payload = objectMapper.readValue(rawJson, VideoUploadRequestedPayload.class);
      log.warn("Vídeo {} esgotou as tentativas de processamento e caiu na DLQ", payload.videoId());

      ProcessingResultMessage message = new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED,
          payload.videoId(), null, "Processamento falhou após esgotar as tentativas");
      rabbitTemplate.convertAndSend(queueProperties.statusUpdates(), objectMapper.writeValueAsString(message), m -> {
        if (correlationId != null) {
          m.getMessageProperties().setCorrelationId(correlationId);
        }
        return m;
      });
    } finally {
      MDC.remove("correlationId");
    }
  }
}
