package com.fiapx.videoapi;

import com.fiapx.videoapi.application.job.OutboxPublisherJob;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxPublisherJobIntegrationTest {

  @Autowired
  private OutboxPublisherJob outboxPublisherJob;

  @Autowired
  private SpringDataOutboxEventRepository springDataOutboxEventRepository;

  @Autowired
  private RabbitTemplate rabbitTemplate;

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
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      Message message = rabbitTemplate.receive(queue, 500);
      if (message != null && new String(message.getBody()).contains(needle)) {
        return message;
      }
    }
    return null;
  }
}
