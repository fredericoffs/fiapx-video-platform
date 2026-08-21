package com.fiapx.notificationworker.domain.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationAttemptTest {

  @Test
  void sentFactoryCreatesAttemptWithSentStatusAndNoError() {
    UUID videoId = UUID.randomUUID();

    NotificationAttempt attempt = NotificationAttempt.sent(videoId, NotificationChannelType.EMAIL);

    assertThat(attempt.getVideoId()).isEqualTo(videoId);
    assertThat(attempt.getChannel()).isEqualTo(NotificationChannelType.EMAIL);
    assertThat(attempt.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(attempt.getErrorMessage()).isNull();
  }

  @Test
  void failedFactoryCreatesAttemptWithFailedStatusAndErrorMessage() {
    UUID videoId = UUID.randomUUID();

    NotificationAttempt attempt =
        NotificationAttempt.failed(videoId, NotificationChannelType.WEBHOOK, "conexão recusada");

    assertThat(attempt.getVideoId()).isEqualTo(videoId);
    assertThat(attempt.getChannel()).isEqualTo(NotificationChannelType.WEBHOOK);
    assertThat(attempt.getStatus()).isEqualTo(NotificationStatus.FAILED);
    assertThat(attempt.getErrorMessage()).isEqualTo("conexão recusada");
  }
}
