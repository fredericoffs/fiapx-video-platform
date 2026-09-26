package com.fiapx.videoworker.infrastructure.messaging.sqs;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.application.usecase.ProcessVideoUseCase;
import com.fiapx.videoworker.domain.model.OutboundMessage;
import com.fiapx.videoworker.domain.model.ProcessingResult;
import com.fiapx.videoworker.domain.port.MessagePublisher;
import com.fiapx.videoworker.domain.port.ProcessingLease;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SqsVideoProcessingListener implements SqsMessageHandler {

  private final ProcessVideoUseCase processVideoUseCase;
  private final MessagePublisher messagePublisher;
  private final ObjectMapper objectMapper;
  private final QueueProperties queueProperties;
  private final ProcessingLease leases;

  public SqsVideoProcessingListener(ProcessVideoUseCase processVideoUseCase, MessagePublisher messagePublisher,
      ObjectMapper objectMapper, QueueProperties queueProperties, ProcessingLease leases) {
    this.processVideoUseCase = processVideoUseCase;
    this.messagePublisher = messagePublisher;
    this.objectMapper = objectMapper;
    this.queueProperties = queueProperties;
    this.leases = leases;
  }

  private static ProcessingResultMessage toResultMessage(ProcessingResult result, UUID eventId) {
    if (result.isSuccess()) {
      return new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, result.getVideoId(),
          result.getZipStorageKey(), null, eventId);
    }
    return new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, result.getVideoId(), null,
        result.getErrorMessage(), eventId);
  }

  // A mensagem só é apagada da fila depois que o resultado foi publicado (o consumer cuida disso).
  @Override
  public void handle(String body, Map<String, String> attributes) {
    String correlationId = attributes.get(CORRELATION_ID);
    MDC.put("correlationId", correlationId);
    try {
      VideoUploadRequestedPayload payload;
      try {
        payload = objectMapper.readValue(body, VideoUploadRequestedPayload.class);
      } catch (JacksonException e) {
        throw new MalformedMessageException("Mensagem de processamento malformada", e);
      }
      try (var lease = leases.acquire(payload.videoId())) {
        publish(new ProcessingResultMessage(ProcessingEventType.PROCESSING_STARTED, payload.videoId(), null, null,
            payload.eventId()), correlationId);
        ProcessingResult result = processVideoUseCase.handle(payload, lease::check);
        lease.check();
        publish(toResultMessage(result, payload.eventId()), correlationId);
      }
    } finally {
      MDC.remove("correlationId");
    }
  }

  private void publish(ProcessingResultMessage message, String correlationId) {
    String eventId = message.eventId() != null ? message.eventId().toString() : null;
    messagePublisher.publish(queueProperties.statusUpdates(),
        OutboundMessage.of(objectMapper.writeValueAsString(message), correlationId, eventId));
  }
}
