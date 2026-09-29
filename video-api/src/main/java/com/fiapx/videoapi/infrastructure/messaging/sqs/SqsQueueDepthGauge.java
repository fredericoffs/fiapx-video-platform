package com.fiapx.videoapi.infrastructure.messaging.sqs;

import com.fiapx.videoapi.infrastructure.config.SqsProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * fiapx_queue_messages_visible{queue}: o SQS não tem exporter dentro do cluster, então o
 * próprio serviço publica a profundidade das filas que consome/produz — é o que alimenta os
 * alertas FiapxQueueDepthHigh e FiapxDeadLetterQueueNotEmpty no perfil aws.
 */
public class SqsQueueDepthGauge implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(SqsQueueDepthGauge.class);
  public static final String METRIC = "fiapx.queue.messages.visible";

  private final SqsClient sqsClient;
  private final SqsQueueUrlResolver resolver;
  private final SqsProperties properties;
  private final List<String> queueNames;
  private final Map<String, AtomicLong> depths = new ConcurrentHashMap<>();
  private volatile ScheduledExecutorService scheduler;

  public SqsQueueDepthGauge(SqsClient sqsClient, SqsQueueUrlResolver resolver, SqsProperties properties,
      MeterRegistry meterRegistry, List<String> queueNames) {
    this.sqsClient = sqsClient;
    this.resolver = resolver;
    this.properties = properties;
    this.queueNames = queueNames;
    for (String queue : queueNames) {
      AtomicLong depth = depths.computeIfAbsent(queue, q -> new AtomicLong());
      Gauge.builder(METRIC, depth, AtomicLong::get).tag("queue", queue).register(meterRegistry);
    }
  }

  @Override
  public synchronized void start() {
    if (scheduler == null) {
      // refresh() já trata a falha de cada fila, então nenhuma exceção cancela o agendamento.
      scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("sqs-depth").factory());
      scheduler.scheduleWithFixedDelay(this::refresh, 0, properties.depthPollMillis(), TimeUnit.MILLISECONDS);
    }
  }

  /** Uma leitura de todas as filas; pública para os testes. */
  public void refresh() {
    for (String queue : queueNames) {
      try {
        String value = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
            .queueUrl(resolver.urlOf(queue))
            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
            .build()).attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        depths.get(queue).set(value == null ? 0 : Long.parseLong(value));
      } catch (RuntimeException e) {
        log.warn("Não consegui ler a profundidade da fila {}: {}", queue, e.getMessage());
      }
    }
  }

  public long depthOf(String queue) {
    return depths.getOrDefault(queue, new AtomicLong()).get();
  }

  @Override
  public synchronized void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
  }

  @Override
  public boolean isRunning() {
    return scheduler != null;
  }
}
