package com.fiapx.videoapi;

import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoStatusUpdateListenerIntegrationTest {

  @Autowired
  private SpringDataVideoRepository videoRepository;

  @Autowired
  private RabbitTemplate rabbitTemplate;

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
    rabbitTemplate.convertAndSend(queueProperties.statusUpdates(), objectMapper.writeValueAsString(message));

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
    rabbitTemplate.convertAndSend(queueProperties.statusUpdates(), objectMapper.writeValueAsString(firstMessage));

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
    rabbitTemplate.convertAndSend(queueProperties.statusUpdates(),
        objectMapper.writeValueAsString(duplicateMessage));

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
    rabbitTemplate.convertAndSend(queueProperties.statusUpdates(), objectMapper.writeValueAsString(failedMessage),
        m -> {
          m.getMessageProperties().setCorrelationId("status-listener-test-correlation-id");
          return m;
        });

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
}
