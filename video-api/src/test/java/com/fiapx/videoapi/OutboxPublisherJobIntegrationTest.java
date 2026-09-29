package com.fiapx.videoapi;

import com.fiapx.videoapi.application.job.OutboxPublisherJob;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class OutboxPublisherJobIntegrationTest extends AbstractSqsIntegrationTest {

  static final SqsClient SQS = SqsTestSupport.client();

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

    // O @Scheduled do próprio job também roda neste contexto e pode ter reservado o evento antes
    // da chamada acima: aí a mensagem chega à fila antes de o agendador gravar o markPublished.
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        assertThat(springDataOutboxEventRepository.findById(entity.getId()).orElseThrow().isPublished()).isTrue());
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
