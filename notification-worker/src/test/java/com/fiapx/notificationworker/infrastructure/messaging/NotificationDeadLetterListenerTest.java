package com.fiapx.notificationworker.infrastructure.messaging;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationDeadLetterListenerTest {

  private final NotificationDeadLetterListener listener = new NotificationDeadLetterListener(new ObjectMapper());

  @Test
  void logsTerminalFailureWithoutThrowingForAValidPayload() {
    UUID videoId = UUID.randomUUID();
    String rawJson = "{\"videoId\":\"" + videoId
        + "\",\"errorMessage\":\"e-mail e webhook indisponíveis\",\"recipientEmail\":\"dono@example.com\"}";

    assertThatCode(() -> listener.onMessage(rawJson)).doesNotThrowAnyException();
  }
}
