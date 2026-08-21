package com.fiapx.notificationworker.infrastructure.messaging;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationDeadLetterListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeadLetterListener.class);

  private final ObjectMapper objectMapper;

  public NotificationDeadLetterListener(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  // Sem convertAndSend: ao contrário da DLQ do video-worker (que republica pro
  // video-api agir), aqui não há próximo consumidor — os dois canais já esgotaram as
  // tentativas, e cada uma delas já deixou seu próprio registro em
  // notification_attempts (EMAIL/WEBHOOK, FAILED) dentro de SendFailureNotificationUseCase.
  // Este listener só é o sinal terminal, alto o bastante pra alertar/observabilidade.
  @RabbitListener(queues = "${fiapx.queues.notification-dlq}")
  public void onMessage(String rawJson) {
    NotificationRequestedMessage message = objectMapper.readValue(rawJson, NotificationRequestedMessage.class);
    log.error(
        "Vídeo {} esgotou as tentativas de notificação em todos os canais e caiu na DLQ de video.notification",
        message.videoId());
  }
}
