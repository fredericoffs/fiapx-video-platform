package com.fiapx.notificationworker.infrastructure.messaging.sqs;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.application.usecase.SendFailureNotificationUseCase;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SqsNotificationRequestedListener implements SqsMessageHandler {

  private final SendFailureNotificationUseCase sendFailureNotificationUseCase;
  private final ObjectMapper objectMapper;

  public SqsNotificationRequestedListener(SendFailureNotificationUseCase sendFailureNotificationUseCase,
      ObjectMapper objectMapper) {
    this.sendFailureNotificationUseCase = sendFailureNotificationUseCase;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(String body, Map<String, String> attributes) {
    MDC.put("correlationId", attributes.get(CORRELATION_ID));
    try {
      NotificationRequestedMessage message;
      try {
        message = objectMapper.readValue(body, NotificationRequestedMessage.class);
      } catch (JacksonException e) {
        throw new MalformedMessageException("Mensagem de notificação malformada", e);
      }
      sendFailureNotificationUseCase.handle(message);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
