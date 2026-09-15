package com.fiapx.videoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fiapx.videoapi.domain.model.OutboundMessage;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsMessagePublisher;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Fim a fim contra SQS real (LocalStack): resultado do worker chega por SQS e atualiza o vídeo. */
@SpringBootTest
@Testcontainers
class AwsProfileIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);

  @DynamicPropertySource
  static void awsProfile(DynamicPropertyRegistry registry) {
    SqsTestSupport.createPlainQueues(SqsTestSupport.client(), List.of(
        "fiapx-video-processing", "fiapx-video-processing-dlq", "fiapx-video-status-updates",
        "fiapx-video-status-updates-dlq", "fiapx-video-notification", "fiapx-video-notification-dlq"));
    registry.add("fiapx.sqs.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.sqs.region", SqsTestSupport.LOCALSTACK::getRegion);
    registry.add("fiapx.sqs.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.sqs.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.sqs.wait-time-seconds", () -> "1");
    // Storage continua com fake nos testes de contexto; endpoint vazio = caminho S3 real (só config).
    registry.add("fiapx.storage.endpoint", () -> "");
  }

  @Autowired
  private MessagePublisher messagePublisher;

  @Autowired
  private VideoRepository videoRepository;

  @Autowired
  private QueueProperties queueProperties;

  @Test
  void contextUsesSqsAdapters() {
    assertThat(messagePublisher).isInstanceOf(SqsMessagePublisher.class);
    assertThat(queueProperties.statusUpdates()).isEqualTo("fiapx-video-status-updates");
  }

  @Test
  void processingEventsArrivingBySqsMoveTheVideoThroughProcessingToCompleted() {
    // user_id é nullable — sem usuário evita a FK e o teste foca só no caminho SQS → status.
    Video video = Video.newQueued(UUID.randomUUID(), null, "movie.mp4", "raw/x/source.mp4");
    videoRepository.save(video);

    messagePublisher.publish(queueProperties.statusUpdates(), OutboundMessage.of(
        "{\"eventType\":\"ProcessingStarted\",\"videoId\":\"" + video.getId() + "\"}", "corr-aws", "evt-1"));
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(videoRepository.findById(video.getId()).orElseThrow().getStatus()).isEqualTo(VideoStatus.PROCESSING));

    messagePublisher.publish(queueProperties.statusUpdates(), OutboundMessage.of(
        "{\"eventType\":\"ProcessingCompleted\",\"videoId\":\"" + video.getId()
            + "\",\"zipStorageKey\":\"processed/x/x.zip\"}", "corr-aws", "evt-1"));
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      Video updated = videoRepository.findById(video.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(VideoStatus.COMPLETED);
      assertThat(updated.getZipStorageKey()).isEqualTo("processed/x/x.zip");
    });
  }
}
