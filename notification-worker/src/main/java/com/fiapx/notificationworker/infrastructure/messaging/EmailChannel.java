package com.fiapx.notificationworker.infrastructure.messaging;

import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import com.fiapx.notificationworker.infrastructure.config.NotificationProperties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailChannel implements NotificationChannel {

  private final JavaMailSender mailSender;
  private final NotificationProperties notificationProperties;

  public EmailChannel(JavaMailSender mailSender, NotificationProperties notificationProperties) {
    this.mailSender = mailSender;
    this.notificationProperties = notificationProperties;
  }

  @Override
  public NotificationChannelType type() {
    return NotificationChannelType.EMAIL;
  }

  @Override
  public CompletableFuture<Void> send(UUID videoId, String errorMessage, String recipientEmail) {
    if (recipientEmail == null || recipientEmail.isBlank()) {
      throw new NotificationDeliveryException(
          "Vídeo " + videoId + " sem e-mail de destinatário resolvido", null);
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(notificationProperties.fromAddress());
    message.setTo(recipientEmail);
    message.setSubject("Falha ao processar seu vídeo");
    message.setText("O processamento do vídeo " + videoId + " falhou: " + errorMessage);

    try {
      mailSender.send(message);
    } catch (MailException e) {
      throw new NotificationDeliveryException("Falha ao enviar e-mail para o vídeo " + videoId, e);
    }

    return CompletableFuture.completedFuture(null);
  }
}
