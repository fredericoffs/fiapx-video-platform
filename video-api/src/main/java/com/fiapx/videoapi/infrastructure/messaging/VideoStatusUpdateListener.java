package com.fiapx.videoapi.infrastructure.messaging;

import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.application.usecase.ApplyProcessingResultUseCase;
import org.slf4j.MDC;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class VideoStatusUpdateListener {

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
      ProcessingResultMessage message = objectMapper.readValue(rawJson, ProcessingResultMessage.class);
      applyProcessingResultUseCase.handle(message);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
