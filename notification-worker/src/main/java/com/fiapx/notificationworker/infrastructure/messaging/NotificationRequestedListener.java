package com.fiapx.notificationworker.infrastructure.messaging;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.application.usecase.SendFailureNotificationUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationRequestedListener {

  private final SendFailureNotificationUseCase sendFailureNotificationUseCase;
  private final ObjectMapper objectMapper;

  public NotificationRequestedListener(
      SendFailureNotificationUseCase sendFailureNotificationUseCase,
      ObjectMapper objectMapper
  ) {
    this.sendFailureNotificationUseCase = sendFailureNotificationUseCase;
    this.objectMapper = objectMapper;
  }

  @RabbitListener(queues = "${fiapx.queues.notification}")
  public void onMessage(String rawJson) {
    NotificationRequestedMessage message = objectMapper.readValue(rawJson, NotificationRequestedMessage.class);
    sendFailureNotificationUseCase.handle(message);
  }
}
