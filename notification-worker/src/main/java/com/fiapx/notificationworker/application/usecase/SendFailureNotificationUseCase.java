package com.fiapx.notificationworker.application.usecase;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Meu dispatcher: tento o canal primário (e-mail); se falhar (circuito aberto, bulkhead
 * cheio ou erro real de envio), caio pro canal secundário (webhook). Só relanço — pro
 * retry/DLQ do AMQP agir — se os dois canais falharem.
 */
@Service
public class SendFailureNotificationUseCase {

  private static final Logger log = LoggerFactory.getLogger(SendFailureNotificationUseCase.class);

  private final Map<NotificationChannelType, NotificationChannel> channelsByType;
  private final NotificationAttemptRepository notificationAttemptRepository;

  public SendFailureNotificationUseCase(
      List<NotificationChannel> channels,
      NotificationAttemptRepository notificationAttemptRepository
  ) {
    this.channelsByType = channels.stream().collect(Collectors.toMap(NotificationChannel::type, Function.identity()));
    this.notificationAttemptRepository = notificationAttemptRepository;
  }

  public void handle(NotificationRequestedMessage message) {
    if (tryChannel(NotificationChannelType.EMAIL, message)) {
      return;
    }
    if (tryChannel(NotificationChannelType.WEBHOOK, message)) {
      return;
    }
    throw new NotificationDeliveryException(
        "Falha ao notificar vídeo " + message.videoId() + " por todos os canais", null);
  }

  private boolean tryChannel(NotificationChannelType type, NotificationRequestedMessage message) {
    if (notificationAttemptRepository.existsSent(message.videoId(), type)) {
      log.info("Notificação por {} já enviada para o vídeo {}, ignorando (idempotência)", type, message.videoId());
      return true;
    }

    NotificationChannel channel = channelsByType.get(type);
    try {
      channel.send(message.videoId(), message.errorMessage(), message.recipientEmail()).join();
      notificationAttemptRepository.save(NotificationAttempt.sent(message.videoId(), type));
      return true;
    } catch (RuntimeException e) {
      notificationAttemptRepository.save(NotificationAttempt.failed(message.videoId(), type, e.getMessage()));
      log.warn("Falha ao notificar vídeo {} pelo canal {}: {}", message.videoId(), type, e.getMessage());
      return false;
    }
  }
}
