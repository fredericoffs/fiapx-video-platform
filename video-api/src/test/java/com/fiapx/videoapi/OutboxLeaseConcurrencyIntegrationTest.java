package com.fiapx.videoapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiapx.videoapi.application.job.OutboxPublisherJob;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/** C05 contra Postgres + SQS reais (LocalStack): duas "APIs" publicando ao mesmo tempo não duplicam eventos. */
@SpringBootTest
class OutboxLeaseConcurrencyIntegrationTest extends AbstractSqsIntegrationTest {

  static final SqsClient SQS = SqsTestSupport.client();

  @Autowired
  private OutboxPublisherJob outboxPublisherJob;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private SpringDataOutboxEventRepository springDataOutboxEventRepository;

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
    OutboxEvent reclaimed = claimedAfterExpiry.stream()
        .filter(e -> e.getId().equals(leased.getId()))
        .findFirst()
        .orElseThrow();

    outboxEventRepository.releaseAfterFailure(reclaimed.getId(), reclaimed.getLockToken());
    OutboxEventEntity released = springDataOutboxEventRepository.findById(leased.getId()).orElseThrow();
    assertThat(released.getLockedUntil()).isNull();
    assertThat(released.getAttempts()).isEqualTo(1);
    assertThat(released.isPublished()).isFalse();
  }

  // C05/item 7: conclusão tardia de uma reivindicação já expirada (a réplica "antiga" só
  // termina de publicar depois de outra réplica já ter reciclado a mesma linha) não pode
  // interferir na reserva nova nem contar tentativa que não é dela.
  @Test
  void staleLeaseCompletionDoesNotInterfereWithNewerReservation() {
    UUID videoId = UUID.randomUUID();
    OutboxEvent event = OutboxEvent.newEvent(videoId, "VideoUploadRequested",
        "{\"videoId\":\"" + videoId + "\"}", null);
    outboxEventRepository.save(event);

    List<OutboxEvent> firstClaim = outboxEventRepository.claimUnpublished(100, Duration.ofSeconds(30));
    OutboxEvent staleClaim = firstClaim.stream()
        .filter(e -> e.getId().equals(event.getId()))
        .findFirst()
        .orElseThrow();

    // simula o lease da primeira "réplica" expirando antes dela terminar de publicar
    OutboxEventEntity entity = springDataOutboxEventRepository.findById(event.getId()).orElseThrow();
    entity.setLockedUntil(Instant.now().minusSeconds(1));
    springDataOutboxEventRepository.save(entity);

    List<OutboxEvent> secondClaim = outboxEventRepository.claimUnpublished(100, Duration.ofSeconds(30));
    OutboxEvent freshClaim = secondClaim.stream()
        .filter(e -> e.getId().equals(event.getId()))
        .findFirst()
        .orElseThrow();
    assertThat(freshClaim.getLockToken()).isNotEqualTo(staleClaim.getLockToken());

    // a "réplica" antiga só agora termina de publicar e tenta concluir com o token velho
    outboxEventRepository.markPublished(staleClaim.getId(), staleClaim.getLockToken());
    outboxEventRepository.releaseAfterFailure(staleClaim.getId(), staleClaim.getLockToken());

    OutboxEventEntity afterStaleCompletion = springDataOutboxEventRepository.findById(event.getId()).orElseThrow();
    assertThat(afterStaleCompletion.isPublished())
        .as("conclusão da reivindicação antiga não pode marcar publicado o que a réplica nova ainda processa")
        .isFalse();
    assertThat(afterStaleCompletion.getAttempts())
        .as("conclusão da reivindicação antiga não pode contar tentativa na reserva da réplica nova")
        .isZero();
    assertThat(afterStaleCompletion.getLockedUntil())
        .as("a reserva da réplica nova continua intacta")
        .isNotNull();

    // conclusão legítima, feita por quem de fato detém o token atual
    outboxEventRepository.markPublished(freshClaim.getId(), freshClaim.getLockToken());
    assertThat(springDataOutboxEventRepository.findById(event.getId()).orElseThrow().isPublished()).isTrue();
  }

  private List<String> drainContaining(String queue, String needle) {
    String url = SqsTestSupport.urlOf(SQS, queue);
    List<String> bodies = new ArrayList<>();
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      List<Message> messages = SQS.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1))
          .messages();
      if (messages.isEmpty()) {
        if (bodies.size() >= 20) {
          break;
        }
        continue;
      }
      for (Message message : messages) {
        SQS.deleteMessage(d -> d.queueUrl(url).receiptHandle(message.receiptHandle()));
        if (message.body().contains(needle)) {
          bodies.add(message.body());
        }
      }
    }
    return bodies;
  }
}
