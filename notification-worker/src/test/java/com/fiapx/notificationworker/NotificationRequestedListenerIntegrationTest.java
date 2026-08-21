package com.fiapx.notificationworker;

import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.infrastructure.config.QueueProperties;
import com.fiapx.notificationworker.infrastructure.persistence.entity.NotificationAttemptEntity;
import com.fiapx.notificationworker.infrastructure.persistence.repository.SpringDataNotificationAttemptRepository;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=3025"})
class NotificationRequestedListenerIntegrationTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private QueueProperties queueProperties;

  @Autowired
  private SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository;

  @Test
  void deliversEmailAndRecordsSentAttempt() throws Exception {
    UUID videoId = UUID.randomUUID();
    String rawJson = "{\"videoId\":\"" + videoId
        + "\",\"errorMessage\":\"ffmpeg falhou\",\"recipientEmail\":\"dono@example.com\"}";

    rabbitTemplate.convertAndSend(queueProperties.notification(), rawJson);

    assertThat(greenMail.waitForIncomingEmail(10_000, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("dono@example.com");
    assertThat(GreenMailUtil.getBody(received[0])).contains(videoId.toString()).contains("ffmpeg falhou");

    NotificationAttemptEntity attempt = waitForAttempt(videoId);
    assertThat(attempt.getChannel()).isEqualTo(NotificationChannelType.EMAIL);
    assertThat(attempt.getStatus()).isEqualTo(NotificationStatus.SENT);
  }

  private NotificationAttemptEntity waitForAttempt(UUID videoId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      List<NotificationAttemptEntity> attempts = springDataNotificationAttemptRepository.findAll();
      for (NotificationAttemptEntity attempt : attempts) {
        if (attempt.getVideoId().equals(videoId)) {
          return attempt;
        }
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Nenhuma tentativa de notificação persistida para o vídeo " + videoId);
  }
}
