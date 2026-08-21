package com.fiapx.notificationworker;

import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.infrastructure.persistence.NotificationAttemptRepositoryAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationAttemptRepositoryAdapterIntegrationTest {

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
}
