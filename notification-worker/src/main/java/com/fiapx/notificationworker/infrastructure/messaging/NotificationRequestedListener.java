package com.fiapx.notificationworker.infrastructure.messaging;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.application.usecase.SendFailureNotificationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationRequestedListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationRequestedListener.class);

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
  public void onMessage(String rawJson, @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    MDC.put("correlationId", correlationId);
    try {
      NotificationRequestedMessage message = parse(rawJson);
      sendFailureNotificationUseCase.handle(message);
    } finally {
      MDC.remove("correlationId");
    }
  }

  // JSON inválido nunca vai passar em retry: vai direto pra DLQ (sem requeue), sem loop.
  private NotificationRequestedMessage parse(String rawJson) {
    try {
      return objectMapper.readValue(rawJson, NotificationRequestedMessage.class);
    } catch (JacksonException e) {
      log.error("Mensagem de notificação malformada, enviando direto para a DLQ: {}", e.getMessage());
      throw new AmqpRejectAndDontRequeueException("Mensagem de notificação malformada", e);
    }
  }
}
