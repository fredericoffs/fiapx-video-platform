package com.fiapx.videoworker.infrastructure.messaging.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fiapx.videoworker.infrastructure.config.SqsProperties;
import com.fiapx.videoworker.domain.model.OutboundMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** O adapter SQS contra um SQS real (LocalStack): confirmação, atributos, redrive, DLQ direta, heartbeat e gauge. */
class SqsMessagingIntegrationTest {

  static SqsClient sqs;
  static SqsQueueUrlResolver resolver;

  @BeforeAll
  static void setUp() {
    sqs = SqsTestSupport.client();
    resolver = new SqsQueueUrlResolver(sqs);
  }

  @Test
  void deliversBodyAndAttributesThenDeletesTheMessage() {
    String queue = "t-ok-" + System.nanoTime();
    String queueUrl = SqsTestSupport.createQueueWithDlq(sqs, queue, 5, 3);
    SqsMessagePublisher publisher = new SqsMessagePublisher(sqs, resolver);
    publisher.publish(queue, OutboundMessage.of("{\"ok\":true}", "corr-1", "evt-1"));

    List<String> bodies = new CopyOnWriteArrayList<>();
    List<Map<String, String>> attrs = new CopyOnWriteArrayList<>();
    SqsQueueConsumer consumer = consumer(queue, queueUrl, null, (body, attributes) -> {
      bodies.add(body);
      attrs.add(attributes);
    });
    consumer.pollOnce();

    assertThat(bodies).containsExactly("{\"ok\":true}");
    assertThat(attrs.getFirst()).containsEntry(SqsMessageHandler.CORRELATION_ID, "corr-1")
        .containsEntry(SqsMessageHandler.EVENT_ID, "evt-1");
    assertThat(SqsTestSupport.visibleMessages(sqs, queueUrl)).isZero();
  }

  @Test
  void failingHandlerLeavesTheMessageForRedriveUntilItLandsInTheDlq() {
    String queue = "t-redrive-" + System.nanoTime();
    String queueUrl = SqsTestSupport.createQueueWithDlq(sqs, queue, 1, 2);
    String dlqUrl = SqsTestSupport.urlOf(sqs, queue + "-dlq");
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{}").build());

    AtomicInteger attempts = new AtomicInteger();
    // O heartbeat imediato fixa a visibilidade na extensão (1s aqui) assim que a mensagem chega;
    // é ela, e não o padrão da fila, que define quando uma falha volta pra nova tentativa.
    SqsQueueConsumer consumer = new SqsQueueConsumer(sqs, SqsTestSupport.properties(30, 1), queue, queueUrl, dlqUrl,
        10, (body, attributes) -> {
          attempts.incrementAndGet();
          throw new IllegalStateException("banco fora");
        });

    // 1ª tentativa falha → mensagem volta após 1s; 2ª tentativa falha → 3º receive move para a DLQ.
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      consumer.pollOnce();
      assertThat(SqsTestSupport.visibleMessages(sqs, dlqUrl)).isEqualTo(1);
    });
    assertThat(attempts.get()).isEqualTo(2);
    assertThat(SqsTestSupport.visibleMessages(sqs, queueUrl)).isZero();
  }

  @Test
  void malformedMessageGoesStraightToTheDlqWithoutRetries() {
    String queue = "t-malformed-" + System.nanoTime();
    String queueUrl = SqsTestSupport.createQueueWithDlq(sqs, queue, 5, 3);
    String dlqUrl = SqsTestSupport.urlOf(sqs, queue + "-dlq");
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{nao e json")
        .messageAttributes(Map.of(SqsMessageHandler.CORRELATION_ID,
            MessageAttributeValue.builder().dataType("String").stringValue("corr-x").build())).build());

    AtomicInteger attempts = new AtomicInteger();
    SqsQueueConsumer consumer = consumer(queue, queueUrl, dlqUrl, (body, attributes) -> {
      attempts.incrementAndGet();
      throw new MalformedMessageException("json inválido", new RuntimeException());
    });
    consumer.pollOnce();

    assertThat(attempts.get()).isEqualTo(1);
    assertThat(SqsTestSupport.visibleMessages(sqs, queueUrl)).isZero();
    assertThat(SqsTestSupport.visibleMessages(sqs, dlqUrl)).isEqualTo(1);
    // Atributos (correlation-id) preservados na DLQ.
    var dead = sqs.receiveMessage(b -> b.queueUrl(dlqUrl).messageAttributeNames("All")).messages().getFirst();
    assertThat(SqsQueueConsumer.attributesOf(dead)).containsEntry(SqsMessageHandler.CORRELATION_ID, "corr-x");
  }

  @Test
  void heartbeatKeepsAMessageInvisibleWhileTheHandlerRuns() {
    String queue = "t-heartbeat-" + System.nanoTime();
    String queueUrl = SqsTestSupport.createQueueWithDlq(sqs, queue, 2, 5);
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{}").build());

    AtomicInteger attempts = new AtomicInteger();
    SqsProperties fastHeartbeat = SqsTestSupport.properties(1, 10);
    SqsQueueConsumer consumer = new SqsQueueConsumer(sqs, fastHeartbeat, queue, queueUrl, null, 1,
        (body, attributes) -> {
          attempts.incrementAndGet();
          sleep(4_000); // maior que o visibility timeout (2s) — só o heartbeat evita a reentrega
        });
    consumer.pollOnce();
    consumer.pollOnce(); // se a mensagem tivesse reaparecido, o handler rodaria de novo

    assertThat(attempts.get()).isEqualTo(1);
    assertThat(SqsTestSupport.visibleMessages(sqs, queueUrl)).isZero();
  }

  @Test
  void firstHeartbeatShortensVisibilityAsSoonAsTheMessageArrives() {
    String queue = "t-first-heartbeat-" + System.nanoTime();
    // Padrão da fila longo (30s) e extensão curta (1s): só um heartbeat imediato faz a
    // mensagem reaparecer enquanto o handler ainda roda — o próximo só viria aos 30s.
    String queueUrl = SqsTestSupport.createQueueWithDlq(sqs, queue, 30, 5);
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{}").build());

    AtomicInteger visibleWhileRunning = new AtomicInteger(-1);
    SqsQueueConsumer consumer = new SqsQueueConsumer(sqs, SqsTestSupport.properties(30, 1), queue, queueUrl,
        null, 1, (body, attributes) -> {
          sleep(3_000);
          visibleWhileRunning.set(SqsTestSupport.visibleMessages(sqs, queueUrl));
        });
    consumer.pollOnce();

    assertThat(visibleWhileRunning.get()).isEqualTo(1);
  }

  @Test
  void stopWaitsForTheMessageInProgressAndDeletesIt() {
    String queue = "t-drain-" + System.nanoTime();
    SqsTestSupport.createPlainQueues(sqs, List.of(queue));
    String queueUrl = SqsTestSupport.urlOf(sqs, queue);
    java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
    SqsQueueConsumer consumer = consumer(queue, queueUrl, null, (body, attributes) -> {
      started.countDown();
      sleep(3_000); // o "ffmpeg" ainda rodando quando chega o SIGTERM
      finished.set(true);
    });

    consumer.start();
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("longo").build());
    await().atMost(Duration.ofSeconds(15)).until(() -> started.getCount() == 0);
    consumer.stop();

    assertThat(finished).isTrue();
    assertThat(sqs.getQueueAttributes(b -> b.queueUrl(queueUrl).attributeNamesWithStrings(
        "ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible")).attributesAsStrings())
        .containsEntry("ApproximateNumberOfMessages", "0")
        .containsEntry("ApproximateNumberOfMessagesNotVisible", "0");
  }

  @Test
  void messageReceivedDuringShutdownIsReleasedWithoutRunningTheHandler() {
    SqsClient client = org.mockito.Mockito.mock(SqsClient.class);
    Message message = Message.builder().messageId("m-1").receiptHandle("rh-1").body("{}").build();
    org.mockito.Mockito.when(client.receiveMessage(org.mockito.ArgumentMatchers.any(ReceiveMessageRequest.class)))
        .thenAnswer(invocation -> {
          sleep(1_000); // long polling em curso quando o stop() chega
          return ReceiveMessageResponse.builder().messages(message).build();
        });
    AtomicInteger handled = new AtomicInteger();
    SqsQueueConsumer consumer = new SqsQueueConsumer(client, SqsTestSupport.properties(30, 120), "q", "url-q",
        null, 1, (body, attributes) -> handled.incrementAndGet());

    consumer.start();
    sleep(200);
    consumer.stop();

    assertThat(handled.get()).isZero();
    org.mockito.Mockito.verify(client).changeMessageVisibility(
        ChangeMessageVisibilityRequest.builder().queueUrl("url-q").receiptHandle("rh-1").visibilityTimeout(0).build());
  }

  @Test
  void releaseFailureDuringShutdownIsOnlyLogged() {
    SqsClient client = org.mockito.Mockito.mock(SqsClient.class);
    Message message = Message.builder().messageId("m-2").receiptHandle("rh-2").body("{}").build();
    org.mockito.Mockito.when(client.receiveMessage(org.mockito.ArgumentMatchers.any(ReceiveMessageRequest.class)))
        .thenAnswer(invocation -> {
          sleep(1_000);
          return ReceiveMessageResponse.builder().messages(message).build();
        });
    org.mockito.Mockito.when(client.changeMessageVisibility(
            org.mockito.ArgumentMatchers.any(ChangeMessageVisibilityRequest.class)))
        .thenThrow(new IllegalStateException("SQS fora"));
    AtomicInteger handled = new AtomicInteger();
    SqsQueueConsumer consumer = new SqsQueueConsumer(client, drainingIn(5), "q", "url-q", null, 1,
        (body, attributes) -> handled.incrementAndGet());

    consumer.start();
    sleep(200);
    consumer.stop();

    assertThat(handled.get()).isZero();
    assertThat(consumer.isRunning()).isFalse();
  }

  @Test
  void stopInterruptsAHandlerThatOutlivesTheDrainTimeout() {
    SqsClient client = org.mockito.Mockito.mock(SqsClient.class);
    Message message = Message.builder().messageId("m-3").receiptHandle("rh-3").body("{}").build();
    org.mockito.Mockito.when(client.receiveMessage(org.mockito.ArgumentMatchers.any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
    java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
    SqsQueueConsumer consumer = new SqsQueueConsumer(client, drainingIn(1), "q", "url-q", null, 1,
        (body, attributes) -> {
          started.countDown();
          try {
            Thread.sleep(30_000);
          } catch (InterruptedException e) {
            interrupted.set(true);
          }
        });

    consumer.start();
    await().atMost(Duration.ofSeconds(5)).until(() -> started.getCount() == 0);
    long before = System.nanoTime();
    consumer.stop();

    assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(10));
    await().atMost(Duration.ofSeconds(5)).untilTrue(interrupted);
  }

  @Test
  void pollingErrorIsRetriedAndStopEndsTheBackoff() {
    SqsClient client = org.mockito.Mockito.mock(SqsClient.class);
    AtomicInteger receives = new AtomicInteger();
    org.mockito.Mockito.when(client.receiveMessage(org.mockito.ArgumentMatchers.any(ReceiveMessageRequest.class)))
        .thenAnswer(invocation -> {
          receives.incrementAndGet();
          throw new IllegalStateException("SQS indisponível");
        });
    SqsQueueConsumer consumer = new SqsQueueConsumer(client, drainingIn(1), "q", "url-q", null, 1,
        (body, attributes) -> { });

    consumer.start();
    await().atMost(Duration.ofSeconds(5)).until(() -> receives.get() >= 1);
    consumer.stop(); // o loop está nos 5s de espera: o prazo de 1s vence e o interrupt encerra

    assertThat(consumer.isRunning()).isFalse();
    assertThat(receives.get()).isEqualTo(1);
  }

  private static SqsProperties drainingIn(int seconds) {
    return new SqsProperties("us-east-1", "", "", "", 1, 30, 120, 30_000L, seconds);
  }

  @Test
  void gaugeExposesApproximateQueueDepth() {
    String queue = "t-depth-" + System.nanoTime();
    SqsTestSupport.createPlainQueues(sqs, List.of(queue));
    String queueUrl = SqsTestSupport.urlOf(sqs, queue);
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("a").build());
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("b").build());

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SqsQueueDepthGauge gauge = new SqsQueueDepthGauge(sqs, resolver, SqsTestSupport.properties(30, 120), registry,
        List.of(queue, "fila-que-nao-existe"));
    gauge.refresh();

    assertThat(gauge.depthOf(queue)).isEqualTo(2);
    assertThat(registry.get(SqsQueueDepthGauge.METRIC).tag("queue", queue).gauge().value()).isEqualTo(2.0);
    assertThat(gauge.depthOf("fila-que-nao-existe")).isZero();
  }

  @Test
  void consumerLifecycleStartsAndStopsItsPollingThread() {
    String queue = "t-lifecycle-" + System.nanoTime();
    SqsTestSupport.createPlainQueues(sqs, List.of(queue));
    String queueUrl = SqsTestSupport.urlOf(sqs, queue);
    List<String> bodies = new CopyOnWriteArrayList<>();
    SqsQueueConsumer consumer = consumer(queue, queueUrl, null, (body, attributes) -> bodies.add(body));

    consumer.start();
    assertThat(consumer.isRunning()).isTrue();
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("vivo").build());
    await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(bodies).containsExactly("vivo"));
    consumer.stop();
    assertThat(consumer.isRunning()).isFalse();
  }

  @Test
  void clientConfigUsesStaticKeysOnlyWhenProvided() {
    SqsProperties withKeys = SqsTestSupport.properties(30, 120);
    SqsProperties awsDefaults = new SqsProperties("us-east-1", "", "", "", 20, 30, 120, 30_000L, 960);

    assertThat(SqsClientConfig.credentialsProvider(withKeys)).isInstanceOf(StaticCredentialsProvider.class);
    assertThat(SqsClientConfig.credentialsProvider(awsDefaults)).isInstanceOf(DefaultCredentialsProvider.class);
    assertThat(awsDefaults.hasEndpointOverride()).isFalse();
    try (SqsClient client = SqsClientConfig.configure(SqsClient.builder(), awsDefaults).build()) {
      assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
    }
  }

  @Test
  void publishFailsWhenQueueDoesNotExist() {
    SqsMessagePublisher publisher = new SqsMessagePublisher(sqs, resolver);

    assertThatThrownBy(() -> publisher.publish("fila-inexistente-" + System.nanoTime(),
        OutboundMessage.of("{}", null, null)))
        .isInstanceOf(com.fiapx.videoworker.domain.exception.MessagePublishException.class);
  }

  private static SqsQueueConsumer consumer(String name, String queueUrl, String dlqUrl, SqsMessageHandler handler) {
    return new SqsQueueConsumer(sqs, SqsTestSupport.properties(30, 120), name, queueUrl, dlqUrl, 10, handler);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
