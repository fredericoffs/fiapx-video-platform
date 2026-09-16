package com.fiapx.notificationworker;

import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.infrastructure.persistence.NotificationAttemptRepositoryAdapter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationAttemptRepositoryAdapterIntegrationTest extends AbstractSqsIntegrationTest {

  @Autowired
  private NotificationAttemptRepositoryAdapter repository;

  @Test
  void secondClaimForSameVideoAndChannelIsRejectedWhileTheFirstIsPending() {
    UUID videoId = UUID.randomUUID();

    Optional<NotificationAttempt> first = repository.tryClaim(videoId, NotificationChannelType.EMAIL);
    assertThat(first).as("a primeira reivindicação consegue a linha SENDING").isPresent();

    Optional<NotificationAttempt> second = repository.tryClaim(videoId, NotificationChannelType.EMAIL);
    assertThat(second).as("uma segunda reivindicação concorrente não pode reservar a mesma linha").isEmpty();
  }

  @Test
  void claimIsFreedAgainAfterMarkedFailed() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt attempt = repository.tryClaim(videoId, NotificationChannelType.EMAIL).orElseThrow();

    repository.markFailed(attempt.getId(), "smtp indisponível");

    Optional<NotificationAttempt> retry = repository.tryClaim(videoId, NotificationChannelType.EMAIL);
    assertThat(retry).as("depois de FAILED, uma nova tentativa pode reivindicar de novo").isPresent();
  }

  @Test
  void claimCannotBeRepeatedAfterMarkedSent() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt attempt = repository.tryClaim(videoId, NotificationChannelType.EMAIL).orElseThrow();

    repository.markSent(attempt.getId());

    Optional<NotificationAttempt> retry = repository.tryClaim(videoId, NotificationChannelType.EMAIL);
    assertThat(retry).as("depois de SENT, reentrega não reivindica de novo (idempotência)").isEmpty();
  }

  @Test
  void claimIsScopedByChannel() {
    UUID videoId = UUID.randomUUID();
    repository.tryClaim(videoId, NotificationChannelType.EMAIL);

    Optional<NotificationAttempt> webhookClaim = repository.tryClaim(videoId, NotificationChannelType.WEBHOOK);
    assertThat(webhookClaim).as("canais diferentes disputam linhas diferentes").isPresent();
  }

  // C05/item 8: reproduz a corrida "consultar se já enviou -> enviar" com concorrência real —
  // só uma entre N reivindicações concorrentes pode vencer, fechando a janela em que o canal
  // seria chamado duas vezes antes de qualquer execução registrar sucesso.
  @Test
  void onlyOneOfManyConcurrentClaimsForTheSameVideoAndChannelWins() throws Exception {
    UUID videoId = UUID.randomUUID();
    int attempts = 10;
    ExecutorService pool = Executors.newFixedThreadPool(attempts);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Optional<NotificationAttempt>>> tasks = IntStream.range(0, attempts)
        .<Callable<Optional<NotificationAttempt>>>mapToObj(i -> () -> {
          start.await();
          return repository.tryClaim(videoId, NotificationChannelType.EMAIL);
        })
        .toList();

    List<Future<Optional<NotificationAttempt>>> futures = tasks.stream().map(pool::submit).toList();
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

    long wins = futures.stream().map(this::result).filter(Optional::isPresent).count();
    assertThat(wins).as("exatamente uma reivindicação concorrente pode vencer").isEqualTo(1);
  }

  private Optional<NotificationAttempt> result(Future<Optional<NotificationAttempt>> future) {
    try {
      return future.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
