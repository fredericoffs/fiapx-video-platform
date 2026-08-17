package com.fiapx.videoapi;

import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
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
}
