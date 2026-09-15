package com.fiapx.videoapi;

import com.fiapx.videoapi.application.job.OutboxPublisherJob;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxPublisherJobIntegrationTest {

  static final SqsClient SQS = SqsTestSupport.client();

  @DynamicPropertySource
  static void sqs(DynamicPropertyRegistry registry) {
    SqsTestSupport.createPlainQueues(SQS, List.of(
        "fiapx-video-processing", "fiapx-video-processing-dlq", "fiapx-video-status-updates",
        "fiapx-video-status-updates-dlq", "fiapx-video-notification", "fiapx-video-notification-dlq"));
    registry.add("fiapx.sqs.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.sqs.region", SqsTestSupport.LOCALSTACK::getRegion);
    registry.add("fiapx.sqs.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.sqs.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.sqs.wait-time-seconds", () -> "1");
    registry.add("fiapx.storage.endpoint", () -> "");
  }

  @Autowired
  private OutboxPublisherJob outboxPublisherJob;

  @Autowired
  private SpringDataOutboxEventRepository springDataOutboxEventRepository;

  @Autowired
  private QueueProperties queueProperties;

  @Test
  void publishesUnpublishedEventAndMarksItPublished() {
    UUID videoId = UUID.randomUUID();
    OutboxEventEntity entity = new OutboxEventEntity();
    entity.setId(UUID.randomUUID());
    entity.setAggregateId(videoId);
    entity.setEventType("VideoUploadRequested");
    entity.setPayload("{\"videoId\":\"" + videoId + "\",\"storageKey\":\"raw/" + videoId
        + "/movie.mp4\",\"originalFilename\":\"movie.mp4\"}");
    entity.setPublished(false);
    entity.setCreatedAt(Instant.now());
    springDataOutboxEventRepository.save(entity);

    outboxPublisherJob.publishPending();

    Message message = receiveContaining(queueProperties.processing(), videoId.toString());
    assertThat(message).as("mensagem publicada na fila %s contendo %s", queueProperties.processing(), videoId)
        .isNotNull();

    OutboxEventEntity updated = springDataOutboxEventRepository.findById(entity.getId()).orElseThrow();
    assertThat(updated.isPublished()).isTrue();
  }

  private Message receiveContaining(String queue, String needle) {
    String url = SqsTestSupport.urlOf(SQS, queue);
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      for (Message message : SQS.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1))
          .messages()) {
        if (message.body().contains(needle)) {
          return message;
        }
      }
    }
    return null;
  }
}
