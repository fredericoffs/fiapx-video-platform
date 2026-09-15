package com.fiapx.notificationworker;

import com.fiapx.notificationworker.domain.exception.DuplicateNotificationException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.infrastructure.persistence.NotificationAttemptRepositoryAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class NotificationAttemptRepositoryAdapterIntegrationTest extends AbstractSqsIntegrationTest {

  @Autowired
  private NotificationAttemptRepositoryAdapter repository;

  @Test
  void existsSentIsTrueOnlyAfterASentAttemptIsSaved() {
    UUID videoId = UUID.randomUUID();

    assertThat(repository.existsSent(videoId, NotificationChannelType.EMAIL)).isFalse();

    repository.save(NotificationAttempt.failed(videoId, NotificationChannelType.EMAIL, "smtp indisponível"));
    assertThat(repository.existsSent(videoId, NotificationChannelType.EMAIL))
        .as("uma tentativa FAILED não conta como enviada")
        .isFalse();

    repository.save(NotificationAttempt.sent(videoId, NotificationChannelType.EMAIL));
    assertThat(repository.existsSent(videoId, NotificationChannelType.EMAIL)).isTrue();
  }

  @Test
  void existsSentIsScopedByChannel() {
    UUID videoId = UUID.randomUUID();

    repository.save(NotificationAttempt.sent(videoId, NotificationChannelType.EMAIL));

    assertThat(repository.existsSent(videoId, NotificationChannelType.EMAIL)).isTrue();
    assertThat(repository.existsSent(videoId, NotificationChannelType.WEBHOOK)).isFalse();
  }

  @Test
  void secondSentAttemptForSameVideoAndChannelIsRejectedByTheDatabase() {
    UUID videoId = UUID.randomUUID();
    repository.save(NotificationAttempt.sent(videoId, NotificationChannelType.WEBHOOK));

    assertThatThrownBy(() -> repository.save(NotificationAttempt.sent(videoId, NotificationChannelType.WEBHOOK)))
        .isInstanceOf(DuplicateNotificationException.class);

    // Tentativas FAILED continuam livres: só o SENT é único por (video_id, channel).
    repository.save(NotificationAttempt.failed(videoId, NotificationChannelType.WEBHOOK, "timeout"));
    assertThat(repository.existsSent(videoId, NotificationChannelType.WEBHOOK)).isTrue();
  }
}
