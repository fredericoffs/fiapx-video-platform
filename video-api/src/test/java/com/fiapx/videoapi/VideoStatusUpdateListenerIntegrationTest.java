package com.fiapx.videoapi;

import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsMessageHandler;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.time.Duration;
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
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoStatusUpdateListenerIntegrationTest {

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
  private SpringDataVideoRepository videoRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private QueueProperties queueProperties;

  @Autowired
  private SpringDataOutboxEventRepository outboxEventRepository;

  @Test
  void appliesCompletedStatusWhenProcessingCompletedEventArrives() {
    VideoEntity entity = new VideoEntity();
    entity.setId(UUID.randomUUID());
    entity.setOriginalFilename("movie.mp4");
    entity.setStorageKey("raw/movie.mp4");
    entity.setStatus(VideoStatus.QUEUED);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    videoRepository.save(entity);

    ProcessingResultMessage message = new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED,
        entity.getId(), "processed/" + entity.getId() + ".zip", null);
    sendStatusUpdate(objectMapper.writeValueAsString(message), null);

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      VideoEntity updated = videoRepository.findById(entity.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(VideoStatus.COMPLETED);
      assertThat(updated.getZipStorageKey()).isEqualTo("processed/" + entity.getId() + ".zip");
    });
  }

  @Test
  void ignoresRedeliveredResultEventOnceVideoIsAlreadyTerminal() throws InterruptedException {
    VideoEntity entity = new VideoEntity();
    entity.setId(UUID.randomUUID());
    entity.setOriginalFilename("movie.mp4");
    entity.setStorageKey("raw/movie.mp4");
    entity.setStatus(VideoStatus.QUEUED);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    videoRepository.save(entity);

    String firstZipKey = "processed/" + entity.getId() + "-first.zip";
    ProcessingResultMessage firstMessage = new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED,
        entity.getId(), firstZipKey, null);
    sendStatusUpdate(objectMapper.writeValueAsString(firstMessage), null);

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      VideoEntity updated = videoRepository.findById(entity.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(VideoStatus.COMPLETED);
      assertThat(updated.getZipStorageKey()).isEqualTo(firstZipKey);
    });

    // Simulo um redelivery com payload divergente (não apenas duplicado) pra provar que o
    // guard de estado terminal em ApplyProcessingResultUseCase realmente ignora o evento,
    // e não apenas coincide por os dois payloads serem idênticos.
    ProcessingResultMessage duplicateMessage = new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED,
        entity.getId(), null, "erro-nao-deveria-ser-aplicado");
    sendStatusUpdate(objectMapper.writeValueAsString(duplicateMessage), null);

    // Uso uma espera fixa aqui: provo ausência de mudança, não presença — não há uma condição
    // positiva para o Awaitility aguardar.
    Thread.sleep(2_000);

    VideoEntity afterRedelivery = videoRepository.findById(entity.getId()).orElseThrow();
    assertThat(afterRedelivery.getStatus()).isEqualTo(VideoStatus.COMPLETED);
    assertThat(afterRedelivery.getZipStorageKey()).isEqualTo(firstZipKey);
    assertThat(afterRedelivery.getErrorMessage()).isNull();
  }

  @Test
  void propagatesTheCorrelationIdToTheNotificationRequestedOutboxEvent() {
    VideoEntity entity = new VideoEntity();
    entity.setId(UUID.randomUUID());
    entity.setOriginalFilename("movie.mp4");
    entity.setStorageKey("raw/movie.mp4");
    entity.setStatus(VideoStatus.QUEUED);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    videoRepository.save(entity);

    ProcessingResultMessage failedMessage = new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED,
        entity.getId(), null, "ffmpeg falhou");
    sendStatusUpdate(objectMapper.writeValueAsString(failedMessage), "status-listener-test-correlation-id");

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      VideoEntity updated = videoRepository.findById(entity.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(VideoStatus.FAILED);
    });

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      OutboxEventEntity event = outboxEventRepository.findAll().stream()
          .filter(e -> e.getAggregateId().equals(entity.getId()))
          .findFirst()
          .orElseThrow();
      assertThat(event.getCorrelationId()).isEqualTo("status-listener-test-correlation-id");
    });
  }

  private void sendStatusUpdate(String body, String correlationId) {
    String url = SqsTestSupport.urlOf(SQS, queueProperties.statusUpdates());
    SQS.sendMessage(b -> {
      b.queueUrl(url).messageBody(body);
      if (correlationId != null) {
        b.messageAttributes(java.util.Map.of(SqsMessageHandler.CORRELATION_ID,
            MessageAttributeValue.builder().dataType("String").stringValue(correlationId).build()));
      }
    });
  }
}
