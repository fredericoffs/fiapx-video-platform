package com.fiapx.notificationworker.infrastructure.messaging.sqs;

import com.fiapx.notificationworker.infrastructure.config.SqsProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Consumo de uma fila SQS por long polling numa virtual thread. Enquanto o handler roda, um heartbeat estende a visibilidade da mensagem; a mensagem
 * só é apagada depois do handler retornar. Exceção comum: não apaga — o SQS reentrega e, após maxReceiveCount, move para a DLQ (redrive). Corpo
 * malformado vai direto para a DLQ, sem ocupar tentativas.
 */
public class SqsQueueConsumer implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(SqsQueueConsumer.class);

  private final SqsClient sqsClient;
  private final SqsProperties properties;
  private final String name;
  private final String queueUrl;
  private final String deadLetterQueueUrl;
  private final int maxMessages;
  private final SqsMessageHandler handler;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final ScheduledExecutorService heartbeats =
      Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("sqs-heartbeat-", 0).factory());
  private Thread loop;

  public SqsQueueConsumer(SqsClient sqsClient, SqsProperties properties, String name, String queueUrl,
      String deadLetterQueueUrl, int maxMessages, SqsMessageHandler handler) {
    this.sqsClient = sqsClient;
    this.properties = properties;
    this.name = name;
    this.queueUrl = queueUrl;
    this.deadLetterQueueUrl = deadLetterQueueUrl;
    this.maxMessages = maxMessages;
    this.handler = handler;
  }

  static Map<String, String> attributesOf(Message message) {
    Map<String, String> attributes = new HashMap<>();
    for (Map.Entry<String, MessageAttributeValue> entry : message.messageAttributes().entrySet()) {
      if (entry.getValue().stringValue() != null) {
        attributes.put(entry.getKey(), entry.getValue().stringValue());
      }
    }
    return attributes;
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void start() {
    if (running.compareAndSet(false, true)) {
      loop = Thread.ofVirtual().name("sqs-consumer-" + name).start(this::pollLoop);
      log.info("Consumer SQS '{}' iniciado em {}", name, queueUrl);
    }
  }

  @Override
  public void stop() {
    running.set(false);
    if (loop != null) {
      loop.interrupt();
      try {
        loop.join(TimeUnit.SECONDS.toMillis(30));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    heartbeats.shutdownNow();
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  private void pollLoop() {
    while (running.get()) {
      try {
        pollOnce();
      } catch (Exception e) {
        if (!running.get()) {
          return;
        }
        log.error("Falha no long polling de '{}', tentando de novo em 5s", name, e);
        sleepQuietly(5_000);
      }
    }
  }

  /**
   * Um ciclo de receive + processamento; público para os testes exercitarem sem a thread.
   */
  public void pollOnce() {
    List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .maxNumberOfMessages(maxMessages)
        .waitTimeSeconds(properties.waitTimeSeconds())
        .messageAttributeNames("All")
        .build()).messages();
    for (Message message : messages) {
      if (!running.get() && loop != null) {
        return;
      }
      process(message);
    }
  }

  private void process(Message message) {
    ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(() -> extendVisibility(message),
        properties.heartbeatSeconds(), properties.heartbeatSeconds(), TimeUnit.SECONDS);
    try {
      handler.handle(message.body(), attributesOf(message));
      delete(message);
    } catch (MalformedMessageException e) {
      log.error("Mensagem malformada em '{}', movendo direto para a DLQ: {}", name, e.getMessage());
      moveToDeadLetter(message);
    } catch (RuntimeException e) {
      log.error("Falha ao processar mensagem {} de '{}' — fica para reentrega/redrive", message.messageId(),
          name, e);
    } finally {
      heartbeat.cancel(false);
    }
  }

  private void extendVisibility(Message message) {
    try {
      sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.receiptHandle())
          .visibilityTimeout(properties.visibilityExtensionSeconds())
          .build());
    } catch (RuntimeException e) {
      log.warn("Heartbeat de visibilidade falhou para {} em '{}': {}", message.messageId(), name, e.getMessage());
    }
  }

  private void delete(Message message) {
    sqsClient.deleteMessage(DeleteMessageRequest.builder()
        .queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
  }

  private void moveToDeadLetter(Message message) {
    if (deadLetterQueueUrl != null) {
      sqsClient.sendMessage(SendMessageRequest.builder()
          .queueUrl(deadLetterQueueUrl)
          .messageBody(message.body())
          .messageAttributes(message.messageAttributes())
          .build());
    }
    delete(message);
  }
}
