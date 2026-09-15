package com.fiapx.videoworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.port.MessagePublisher;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.config.StorageProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import com.fiapx.videoworker.infrastructure.messaging.sqs.SqsMessagePublisher;
import com.fiapx.videoworker.infrastructure.messaging.sqs.SqsTestSupport;
import com.fiapx.videoworker.support.FakeStorageClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

/** Fim a fim contra SQS real (LocalStack): pedido chega por SQS, worker publica Started + resultado; redrive vira FAILED. */
@SpringBootTest
class AwsProfileIntegrationTest extends AbstractSqsIntegrationTest {

  @Autowired
  private MessagePublisher messagePublisher;

  @Autowired
  private QueueProperties queueProperties;

  @Autowired
  private StorageProperties storageProperties;

  @Autowired
  private FakeStorageClient fakeStorageClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void contextUsesSqsAdapters() {
    assertThat(messagePublisher).isInstanceOf(SqsMessagePublisher.class);
  }

  @Test
  void processesRequestFromSqsAndPublishesStartedThenCompleted() {
    UUID videoId = UUID.randomUUID();
    String storageKey = "raw/" + videoId + "/source.mp4";
    fakeStorageClient.seed(storageProperties.bucketRaw(), storageKey, "fake-video-bytes".getBytes());
    send(new VideoUploadRequestedPayload(videoId, storageKey, "movie.mp4", UUID.randomUUID(), 1), "corr-sqs");

    List<ProcessingResultMessage> results = collectResultsFor(videoId, 2);

    assertThat(results).extracting(ProcessingResultMessage::eventType)
        .containsExactly(ProcessingEventType.PROCESSING_STARTED, ProcessingEventType.PROCESSING_COMPLETED);
    assertThat(results.get(1).zipStorageKey()).isEqualTo("processed/" + videoId + "/" + videoId + ".zip");
  }

  @Test
  void requestThatExhaustsRedriveEndsAsProcessingFailed() {
    UUID videoId = UUID.randomUUID();
    send(new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/never-seeded.mp4", "never-seeded.mp4",
        UUID.randomUUID(), 1), "corr-dlq");

    await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
      List<ProcessingResultMessage> results = collectResultsFor(videoId, 1);
      assertThat(results).extracting(ProcessingResultMessage::eventType)
          .contains(ProcessingEventType.PROCESSING_FAILED);
    });
  }

  private void send(VideoUploadRequestedPayload payload, String correlationId) {
    SQS.sendMessage(SendMessageRequest.builder()
        .queueUrl(SqsTestSupport.urlOf(SQS, queueProperties.processing()))
        .messageBody(objectMapper.writeValueAsString(payload))
        .messageAttributes(java.util.Map.of("correlationId",
            MessageAttributeValue.builder().dataType("String").stringValue(correlationId).build()))
        .build());
  }

  private final List<ProcessingResultMessage> seen = new CopyOnWriteArrayList<>();

  /** Drena a fila de resultados (várias mensagens de vários testes) e devolve as do vídeo pedido. */
  private List<ProcessingResultMessage> collectResultsFor(UUID videoId, int expected) {
    String url = SqsTestSupport.urlOf(SQS, queueProperties.statusUpdates());
    await().atMost(Duration.ofSeconds(30)).until(() -> {
      SQS.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1)).messages()
          .forEach(m -> {
            seen.add(objectMapper.readValue(m.body(), ProcessingResultMessage.class));
            SQS.deleteMessage(d -> d.queueUrl(url).receiptHandle(m.receiptHandle()));
          });
      return seen.stream().filter(r -> videoId.equals(r.videoId())).count() >= expected;
    });
    return seen.stream().filter(r -> videoId.equals(r.videoId()))
        .filter(r -> r.eventType() != ProcessingEventType.PROCESSING_STARTED
            || expected > 1)
        .toList();
  }
}
