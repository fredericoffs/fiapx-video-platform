package com.fiapx.notificationworker.infrastructure.messaging;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationDeadLetterListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeadLetterListener.class);

  private final ObjectMapper objectMapper;

  public NotificationDeadLetterListener(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  // Sem convertAndSend: diferente da DLQ do video-worker, aqui não há próximo consumidor —
  // só o sinal terminal pra observabilidade, os registros já ficaram em notification_attempts.
  @RabbitListener(queues = "${fiapx.queues.notification-dlq}")
  public void onMessage(String rawJson, @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    MDC.put("correlationId", correlationId);
    try {
      NotificationRequestedMessage message = objectMapper.readValue(rawJson, NotificationRequestedMessage.class);
      log.error(
          "Vídeo {} esgotou as tentativas de notificação em todos os canais e caiu na DLQ de video.notification",
          message.videoId());
    } finally {
      MDC.remove("correlationId");
    }
  }
}
