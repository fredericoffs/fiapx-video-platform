package com.fiapx.videoworker.infrastructure.messaging;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.application.usecase.ProcessVideoUseCase;
import com.fiapx.videoworker.domain.model.ProcessingResult;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
  public void onMessage(String rawJson) {
    VideoUploadRequestedPayload payload = objectMapper.readValue(rawJson, VideoUploadRequestedPayload.class);
    ProcessingResult result = processVideoUseCase.handle(payload);
    ProcessingResultMessage message = toResultMessage(result);
    rabbitTemplate.convertAndSend(queueProperties.statusUpdates(), objectMapper.writeValueAsString(message));
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
