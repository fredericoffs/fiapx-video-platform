package com.fiapx.notificationworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.infrastructure.config.QueueProperties;
import com.fiapx.notificationworker.infrastructure.messaging.sqs.SqsTestSupport;
import com.fiapx.notificationworker.infrastructure.persistence.repository.SpringDataNotificationAttemptRepository;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Fim a fim contra SQS real (LocalStack): pedido de notificação chega por SQS, e-mail sai e
 * a tentativa fica registrada. {@code smtp.auth=false}: o default de produção virou
 * {@code true} (hardening — exige credenciais reais), mas o GreenMail aqui não configura
 * nenhum usuário, então autenticar contra ele falha com "no password specified".
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.mail.host=127.0.0.1", "spring.mail.port=3025", "spring.mail.properties.mail.smtp.auth=false"
})
class AwsProfileIntegrationTest extends AbstractSqsIntegrationTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  @Autowired
  private QueueProperties queueProperties;

  @Autowired
  private SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository;

  @Test
  void contextResolvesTheNotificationQueueName() {
    assertThat(queueProperties.notification()).isEqualTo("fiapx-video-notification");
  }

  @Test
  void notificationRequestedBySqsSendsEmailAndRecordsAttempt() {
    UUID videoId = UUID.randomUUID();
    SQS.sendMessage(SendMessageRequest.builder()
        .queueUrl(SqsTestSupport.urlOf(SQS, queueProperties.notification()))
        .messageBody("{\"videoId\":\"" + videoId + "\",\"errorMessage\":\"ffmpeg falhou\","
            + "\"recipientEmail\":\"dono@example.com\",\"eventId\":\"" + UUID.randomUUID() + "\",\"contractVersion\":1}")
        .build());

    assertThat(greenMail.waitForIncomingEmail(20_000, 1)).isTrue();
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        assertThat(springDataNotificationAttemptRepository.findAll())
            .anyMatch(attempt -> attempt.getVideoId().equals(videoId)
                && attempt.getChannel() == NotificationChannelType.EMAIL
                && attempt.getStatus() == NotificationStatus.SENT));
  }
}
