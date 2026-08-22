package com.fiapx.videoworker.infrastructure.messaging;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.application.usecase.ProcessVideoUseCase;
import com.fiapx.videoworker.domain.model.ProcessingResult;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import org.slf4j.MDC;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class VideoProcessingListener {

  private final ProcessVideoUseCase processVideoUseCase;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final QueueProperties queueProperties;

  public VideoProcessingListener(
      ProcessVideoUseCase processVideoUseCase,
      RabbitTemplate rabbitTemplate,
      ObjectMapper objectMapper,
      QueueProperties queueProperties
  ) {
    this.processVideoUseCase = processVideoUseCase;
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.queueProperties = queueProperties;
  }

  @RabbitListener(queues = "${fiapx.queues.processing}")
  public void onMessage(String rawJson, @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    MDC.put("correlationId", correlationId);
    try {
      VideoUploadRequestedPayload payload = objectMapper.readValue(rawJson, VideoUploadRequestedPayload.class);
      ProcessingResult result = processVideoUseCase.handle(payload);
      ProcessingResultMessage message = toResultMessage(result);
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

  private ProcessingResultMessage toResultMessage(ProcessingResult result) {
    if (result.isSuccess()) {
      return new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, result.getVideoId(),
          result.getZipStorageKey(), null);
    }
    return new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, result.getVideoId(), null,
        result.getErrorMessage());
  }
}
