package com.fiapx.notificationworker.infrastructure.messaging.sqs;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Sinal terminal para observabilidade — os registros por canal já estão em notification_attempts. */
@Component
public class SqsNotificationDeadLetterListener implements SqsMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(SqsNotificationDeadLetterListener.class);

  private final ObjectMapper objectMapper;

  public SqsNotificationDeadLetterListener(ObjectMapper objectMapper) {
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
        log.error("Mensagem malformada na DLQ de notificação, descartada: {}", e.getMessage());
        return;
      }
      log.error("Vídeo {} esgotou as tentativas de notificação em todos os canais e caiu na DLQ", message.videoId());
    } finally {
      MDC.remove("correlationId");
    }
  }
}
