package com.fiapx.notificationworker.infrastructure.messaging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationDeadLetterListenerTest {

  private final NotificationDeadLetterListener listener = new NotificationDeadLetterListener(new ObjectMapper());
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void attachLogAppender() {
    logAppender = new ListAppender<>();
    logAppender.start();
    ((Logger) LoggerFactory.getLogger(NotificationDeadLetterListener.class)).addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    ((Logger) LoggerFactory.getLogger(NotificationDeadLetterListener.class)).detachAppender(logAppender);
  }

  @Test
  void logsTerminalFailureWithoutThrowingForAValidPayload() {
    UUID videoId = UUID.randomUUID();
    String rawJson = "{\"videoId\":\"" + videoId
        + "\",\"errorMessage\":\"e-mail e webhook indisponíveis\",\"recipientEmail\":\"dono@example.com\"}";

    assertThatCode(() -> listener.onMessage(rawJson, "corr-id")).doesNotThrowAnyException();
  }

  @Test
  void putsTheCorrelationIdInTheLogEventAndClearsMdcAfterProcessing() {
    UUID videoId = UUID.randomUUID();
    String rawJson = "{\"videoId\":\"" + videoId
        + "\",\"errorMessage\":\"e-mail e webhook indisponíveis\",\"recipientEmail\":\"dono@example.com\"}";

    listener.onMessage(rawJson, "corr-id");

    assertThat(logAppender.list).hasSize(1);
    assertThat(logAppender.list.getFirst().getMDCPropertyMap()).containsEntry("correlationId", "corr-id");
    assertThat(MDC.get("correlationId")).isNull();
  }
}
