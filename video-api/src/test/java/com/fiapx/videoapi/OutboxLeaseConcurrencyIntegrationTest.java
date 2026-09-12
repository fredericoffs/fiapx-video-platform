package com.fiapx.videoapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiapx.videoapi.application.job.OutboxPublisherJob;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** C05 contra Postgres + RabbitMQ reais: duas "APIs" publicando ao mesmo tempo não duplicam eventos. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxLeaseConcurrencyIntegrationTest {

  @Autowired
  private OutboxPublisherJob outboxPublisherJob;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private SpringDataOutboxEventRepository springDataOutboxEventRepository;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private QueueProperties queueProperties;

  @Test
  void twoConcurrentPublishersDeliverEachEventExactlyOnce() throws Exception {
    String marker = "lease-" + UUID.randomUUID();
    Set<UUID> eventIds = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      UUID videoId = UUID.randomUUID();
      OutboxEvent event = OutboxEvent.newEvent(videoId, "VideoUploadRequested",
          "{\"videoId\":\"" + videoId + "\",\"storageKey\":\"raw/" + videoId + "/source.mp4\","
              + "\"originalFilename\":\"" + marker + ".mp4\"}", null);
      outboxEventRepository.save(event);
      eventIds.add(event.getId());
    }

    // Duas threads = duas réplicas disputando o mesmo lote; SKIP LOCKED reparte sem sobreposição.
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    for (int i = 0; i < 2; i++) {
      pool.submit(() -> {
        start.await();
        for (int round = 0; round < 3; round++) {
          outboxPublisherJob.publishPending();
        }
        return null;
      });
    }
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

    List<String> received = drainContaining(queueProperties.processing(), marker);
    assertThat(received).as("cada evento publicado exatamente uma vez").hasSize(20);
    assertThat(received.stream().distinct().count()).isEqualTo(20);

    for (UUID eventId : eventIds) {
      OutboxEventEntity entity = springDataOutboxEventRepository.findById(eventId).orElseThrow();
      assertThat(entity.isPublished()).isTrue();
      assertThat(entity.getLockedUntil()).isNull();
    }
  }

  @Test
  void activeLeaseHidesTheEventFromOtherReplicasUntilItExpires() {
    UUID videoId = UUID.randomUUID();
    OutboxEventEntity leased = new OutboxEventEntity();
    leased.setId(UUID.randomUUID());
    leased.setAggregateId(videoId);
    leased.setEventType("VideoUploadRequested");
    leased.setPayload("{\"videoId\":\"" + videoId + "\"}");
    leased.setPublished(false);
    leased.setCreatedAt(Instant.now().minusSeconds(60));
    leased.setLockedUntil(Instant.now().plusSeconds(120));
    springDataOutboxEventRepository.save(leased);

    List<OutboxEvent> claimed = outboxEventRepository.claimUnpublished(100, Duration.ofSeconds(30));
    assertThat(claimed).extracting(OutboxEvent::getId).doesNotContain(leased.getId());

    leased.setLockedUntil(Instant.now().minusSeconds(1));
    springDataOutboxEventRepository.save(leased);

    List<OutboxEvent> claimedAfterExpiry = outboxEventRepository.claimUnpublished(100, Duration.ofSeconds(30));
    assertThat(claimedAfterExpiry).extracting(OutboxEvent::getId).contains(leased.getId());

    outboxEventRepository.releaseAfterFailure(leased.getId());
    OutboxEventEntity released = springDataOutboxEventRepository.findById(leased.getId()).orElseThrow();
    assertThat(released.getLockedUntil()).isNull();
    assertThat(released.getAttempts()).isEqualTo(1);
    assertThat(released.isPublished()).isFalse();
  }

  private List<String> drainContaining(String queue, String needle) {
    List<String> bodies = new java.util.ArrayList<>();
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      Message message = rabbitTemplate.receive(queue, 500);
      if (message == null) {
        if (bodies.size() >= 20) {
          break;
        }
        continue;
      }
      String body = new String(message.getBody());
      if (body.contains(needle)) {
        bodies.add(body);
      }
    }
    return bodies;
  }
}
