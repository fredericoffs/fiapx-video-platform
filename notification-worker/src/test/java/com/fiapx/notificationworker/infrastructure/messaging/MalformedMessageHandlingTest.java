package com.fiapx.notificationworker.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fiapx.notificationworker.application.usecase.SendFailureNotificationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

class MalformedMessageHandlingTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SendFailureNotificationUseCase useCase = mock(SendFailureNotificationUseCase.class);

  @Test
  void notificationListenerRejectsMalformedJsonWithoutRequeue() {
    NotificationRequestedListener listener = new NotificationRequestedListener(useCase, objectMapper);

    assertThatThrownBy(() -> listener.onMessage("{isto nao e json", "corr-1"))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);

    verifyNoInteractions(useCase);
  }

  @Test
  void deadLetterListenerDiscardsMalformedJsonQuietly() {
    NotificationDeadLetterListener listener = new NotificationDeadLetterListener(objectMapper);

    assertThatCode(() -> listener.onMessage("{isto nao e json", null)).doesNotThrowAnyException();
  }
}
