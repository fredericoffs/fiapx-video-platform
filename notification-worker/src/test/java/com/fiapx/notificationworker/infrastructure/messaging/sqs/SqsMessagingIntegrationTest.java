package com.fiapx.notificationworker.infrastructure.messaging.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fiapx.notificationworker.infrastructure.config.SqsProperties;
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
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
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
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{\"ok\":true}")
        .messageAttributes(Map.of(
            SqsMessageHandler.CORRELATION_ID, MessageAttributeValue.builder().dataType("String").stringValue("corr-1").build(),
            SqsMessageHandler.EVENT_ID, MessageAttributeValue.builder().dataType("String").stringValue("evt-1").build()))
        .build());

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
    SqsQueueConsumer consumer = consumer(queue, queueUrl, dlqUrl, (body, attributes) -> {
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
  void depthGaugeLifecycleRefreshesOnStartAndStopsItsScheduler() {
    String queue = "t-depth-lifecycle-" + System.nanoTime();
    SqsTestSupport.createPlainQueues(sqs, List.of(queue));
    sqs.sendMessage(SendMessageRequest.builder().queueUrl(SqsTestSupport.urlOf(sqs, queue)).messageBody("m").build());
    SqsQueueDepthGauge gauge = new SqsQueueDepthGauge(sqs, resolver, SqsTestSupport.properties(30, 120),
        new SimpleMeterRegistry(), List.of(queue));

    gauge.start();
    gauge.start();
    assertThat(gauge.isRunning()).isTrue();
    await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(gauge.depthOf(queue)).isEqualTo(1));
    gauge.stop();
    gauge.stop();
    assertThat(gauge.isRunning()).isFalse();
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
    SqsProperties awsDefaults = new SqsProperties("us-east-1", "", "", "", 20, 30, 120, 30_000L);

    assertThat(SqsClientConfig.credentialsProvider(withKeys)).isInstanceOf(StaticCredentialsProvider.class);
    assertThat(SqsClientConfig.credentialsProvider(awsDefaults)).isInstanceOf(DefaultCredentialsProvider.class);
    assertThat(awsDefaults.hasEndpointOverride()).isFalse();
    try (SqsClient client = SqsClientConfig.configure(SqsClient.builder(), awsDefaults).build()) {
      assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
    }
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
