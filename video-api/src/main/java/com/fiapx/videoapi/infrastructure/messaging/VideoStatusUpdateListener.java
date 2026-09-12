package com.fiapx.videoapi.infrastructure.messaging;

import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.application.usecase.ApplyProcessingResultUseCase;
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
public class VideoStatusUpdateListener {

  private static final Logger log = LoggerFactory.getLogger(VideoStatusUpdateListener.class);

  private final ApplyProcessingResultUseCase applyProcessingResultUseCase;
  private final ObjectMapper objectMapper;

  public VideoStatusUpdateListener(
      ApplyProcessingResultUseCase applyProcessingResultUseCase,
      ObjectMapper objectMapper
  ) {
    this.applyProcessingResultUseCase = applyProcessingResultUseCase;
    this.objectMapper = objectMapper;
  }

  @RabbitListener(queues = "${fiapx.queues.status-updates}")
  public void onMessage(String rawJson, @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    MDC.put("correlationId", correlationId);
    try {
      ProcessingResultMessage message = parse(rawJson);
      applyProcessingResultUseCase.handle(message);
    } finally {
      MDC.remove("correlationId");
    }
  }

  // JSON inválido nunca vai passar em retry: vai direto pra DLQ (sem requeue), sem loop.
  private ProcessingResultMessage parse(String rawJson) {
    try {
      return objectMapper.readValue(rawJson, ProcessingResultMessage.class);
    } catch (JacksonException e) {
      log.error("Mensagem de resultado malformada, enviando direto para a DLQ: {}", e.getMessage());
      throw new AmqpRejectAndDontRequeueException("Mensagem de resultado malformada", e);
    }
  }
}
