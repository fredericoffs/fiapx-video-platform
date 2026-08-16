package com.fiapx.videoapi.infrastructure.messaging;

import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.application.usecase.ApplyProcessingResultUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
  public void onMessage(String rawJson) {
    ProcessingResultMessage message = objectMapper.readValue(rawJson, ProcessingResultMessage.class);
    applyProcessingResultUseCase.handle(message);
  }
}
