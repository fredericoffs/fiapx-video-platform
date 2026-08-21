package com.fiapx.notificationworker.application.usecase;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SendFailureNotificationUseCase {

  private static final Logger log = LoggerFactory.getLogger(SendFailureNotificationUseCase.class);

  private final NotificationChannel emailChannel;
  private final NotificationAttemptRepository notificationAttemptRepository;

  public SendFailureNotificationUseCase(
      NotificationChannel emailChannel,
      NotificationAttemptRepository notificationAttemptRepository
  ) {
    this.emailChannel = emailChannel;
    this.notificationAttemptRepository = notificationAttemptRepository;
  }

  public void handle(NotificationRequestedMessage message) {
    if (notificationAttemptRepository.existsSent(message.videoId(), NotificationChannelType.EMAIL)) {
      log.info("Notificação por e-mail já enviada para o vídeo {}, ignorando (idempotência)", message.videoId());
      return;
    }

    try {
      emailChannel.send(message.videoId(), message.errorMessage(), message.recipientEmail()).join();
      notificationAttemptRepository.save(NotificationAttempt.sent(message.videoId(), NotificationChannelType.EMAIL));
    } catch (RuntimeException e) {
      notificationAttemptRepository.save(
          NotificationAttempt.failed(message.videoId(), NotificationChannelType.EMAIL, e.getMessage()));
      throw new NotificationDeliveryException("Falha ao notificar vídeo " + message.videoId(), e);
    }
  }
}
