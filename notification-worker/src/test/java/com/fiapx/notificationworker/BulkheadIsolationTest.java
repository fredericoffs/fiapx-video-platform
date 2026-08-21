package com.fiapx.notificationworker;

import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova isolamento REAL de thread pool entre os dois canais (DoD: "falha isolada de
 * um canal não afeta o outro") — não uma asserção de que a configuração existe.
 * Satura a única thread do pool do email-channel (rebaixado pra 1 thread/fila zero
 * só neste teste) com uma tarefa bloqueada por {@link CountDownLatch}; enquanto ela
 * está presa, uma tarefa no webhook-channel precisa continuar respondendo
 * normalmente, porque tem pool próprio. Sincronização só por latch — nenhum
 * {@code Thread.sleep}/timing de wall-clock.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "resilience4j.thread-pool-bulkhead.instances.email-channel.max-thread-pool-size=1",
    "resilience4j.thread-pool-bulkhead.instances.email-channel.core-thread-pool-size=1",
    "resilience4j.thread-pool-bulkhead.instances.email-channel.queue-capacity=0"
})
class BulkheadIsolationTest {

  @Autowired
  private ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry;

  @Test
  void saturatingEmailChannelPoolDoesNotBlockWebhookChannelPool() throws Exception {
    ThreadPoolBulkhead emailBulkhead = threadPoolBulkheadRegistry.bulkhead("email-channel");
    ThreadPoolBulkhead webhookBulkhead = threadPoolBulkheadRegistry.bulkhead("webhook-channel");

    CountDownLatch emailTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseEmailTask = new CountDownLatch(1);

    CompletionStage<String> emailTask = emailBulkhead.submit(() -> {
      emailTaskStarted.countDown();
      releaseEmailTask.await(10, TimeUnit.SECONDS);
      return "email-done";
    });

    assertThat(emailTaskStarted.await(5, TimeUnit.SECONDS)).as("tarefa do e-mail começou a rodar").isTrue();

    // A única thread do pool do e-mail está ocupada — o webhook usa outro pool, então
    // isso precisa completar normalmente em vez de ficar preso atrás do e-mail.
    CompletionStage<String> webhookTask = webhookBulkhead.submit(() -> "webhook-done");
    assertThat(toFuture(webhookTask).get(5, TimeUnit.SECONDS)).isEqualTo("webhook-done");

    releaseEmailTask.countDown();
    assertThat(toFuture(emailTask).get(5, TimeUnit.SECONDS)).isEqualTo("email-done");
  }

  private static <T> CompletableFuture<T> toFuture(CompletionStage<T> stage) {
    return stage.toCompletableFuture();
  }
}
