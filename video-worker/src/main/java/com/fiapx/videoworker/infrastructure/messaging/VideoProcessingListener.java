package com.fiapx.videoworker.infrastructure.messaging;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.application.usecase.ProcessVideoUseCase;
import com.fiapx.videoworker.domain.model.ProcessingResult;
import com.fiapx.videoworker.domain.port.MessagePublisher;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "fiapx.messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class VideoProcessingListener {

  private static final Logger log = LoggerFactory.getLogger(VideoProcessingListener.class);

  private final ProcessVideoUseCase processVideoUseCase;
  private final MessagePublisher messagePublisher;
  private final ObjectMapper objectMapper;
  private final QueueProperties queueProperties;

  public VideoProcessingListener(
      ProcessVideoUseCase processVideoUseCase,
      MessagePublisher messagePublisher,
      ObjectMapper objectMapper,
      QueueProperties queueProperties
  ) {
    this.processVideoUseCase = processVideoUseCase;
    this.messagePublisher = messagePublisher;
    this.objectMapper = objectMapper;
    this.queueProperties = queueProperties;
  }

  // O consumo só termina depois que o resultado foi publicado com confirmação: se a
  // publicação falhar, a exceção volta pro container e a mensagem entra em retry/DLQ.
  @RabbitListener(queues = "${fiapx.queues.processing}")
  public void onMessage(String rawJson, @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    MDC.put("correlationId", correlationId);
    try {
      VideoUploadRequestedPayload payload = parse(rawJson);
      ProcessingResult result = processVideoUseCase.handle(payload);
      ProcessingResultMessage message = toResultMessage(result);
      messagePublisher.publish(queueProperties.statusUpdates(), objectMapper.writeValueAsString(message), correlationId);
    } finally {
      MDC.remove("correlationId");
    }
  }

  private VideoUploadRequestedPayload parse(String rawJson) {
    try {
      return objectMapper.readValue(rawJson, VideoUploadRequestedPayload.class);
    } catch (JacksonException e) {
      log.error("Mensagem de processamento malformada, enviando direto para a DLQ: {}", e.getMessage());
      throw new AmqpRejectAndDontRequeueException("Mensagem de processamento malformada", e);
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
