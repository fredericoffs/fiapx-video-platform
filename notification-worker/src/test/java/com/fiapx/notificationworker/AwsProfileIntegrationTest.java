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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Fim a fim contra SQS real (LocalStack): pedido de notificação chega por SQS, e-mail sai e a tentativa fica registrada. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=3025"})
class AwsProfileIntegrationTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  static final SqsClient SQS = SqsTestSupport.client();

  @DynamicPropertySource
  static void awsProfile(DynamicPropertyRegistry registry) {
    SqsTestSupport.createPlainQueues(SQS, List.of("fiapx-video-notification", "fiapx-video-notification-dlq"));
    registry.add("fiapx.sqs.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.sqs.region", SqsTestSupport.LOCALSTACK::getRegion);
    registry.add("fiapx.sqs.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.sqs.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.sqs.wait-time-seconds", () -> "1");
  }

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
        assertThat(springDataNotificationAttemptRepository.existsByVideoIdAndChannelAndStatus(
            videoId, NotificationChannelType.EMAIL, NotificationStatus.SENT)).isTrue());
  }
}
