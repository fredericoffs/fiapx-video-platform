package com.fiapx.notificationworker.domain.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationAttemptTest {

  @Test
  void claimingFactoryCreatesAttemptWithSendingStatusAndNoError() {
    UUID videoId = UUID.randomUUID();

    NotificationAttempt attempt = NotificationAttempt.claiming(videoId, NotificationChannelType.EMAIL);

    assertThat(attempt.getVideoId()).isEqualTo(videoId);
    assertThat(attempt.getChannel()).isEqualTo(NotificationChannelType.EMAIL);
    assertThat(attempt.getStatus()).isEqualTo(NotificationStatus.SENDING);
    assertThat(attempt.getErrorMessage()).isNull();
  }
}
