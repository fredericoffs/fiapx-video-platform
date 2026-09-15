package com.fiapx.videoapi.infrastructure.messaging.sqs;

import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.application.usecase.ApplyProcessingResultUseCase;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SqsVideoStatusUpdateListener implements SqsMessageHandler {

  private final ApplyProcessingResultUseCase applyProcessingResultUseCase;
  private final ObjectMapper objectMapper;

  public SqsVideoStatusUpdateListener(ApplyProcessingResultUseCase applyProcessingResultUseCase,
      ObjectMapper objectMapper) {
    this.applyProcessingResultUseCase = applyProcessingResultUseCase;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(String body, Map<String, String> attributes) {
    MDC.put("correlationId", attributes.get(CORRELATION_ID));
    try {
      ProcessingResultMessage message;
      try {
        message = objectMapper.readValue(body, ProcessingResultMessage.class);
      } catch (JacksonException e) {
        throw new MalformedMessageException("Mensagem de resultado malformada", e);
      }
      applyProcessingResultUseCase.handle(message);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
