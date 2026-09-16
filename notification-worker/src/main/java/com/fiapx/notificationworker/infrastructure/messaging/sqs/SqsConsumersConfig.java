package com.fiapx.notificationworker.infrastructure.messaging.sqs;

import com.fiapx.notificationworker.infrastructure.config.QueueProperties;
import com.fiapx.notificationworker.infrastructure.config.SqsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsConsumersConfig {

  // 1 mensagem por vez: o heartbeat do SqsQueueConsumer só estende a visibilidade da mensagem
  // em processamento — um lote >1 deixaria as demais perdendo visibilidade em memória.
  @Bean
  public SqsQueueConsumer notificationConsumer(SqsClient sqsClient, SqsProperties sqsProperties,
      SqsQueueUrlResolver resolver, QueueProperties queues, SqsNotificationRequestedListener listener) {
    return new SqsQueueConsumer(sqsClient, sqsProperties, "notification",
        resolver.urlOf(queues.notification()), resolver.urlOf(queues.notificationDlq()), 1, listener);
  }

  // A DLQ de notificação fica sem consumer de propósito (mesmo padrão da DLQ de resultados
  // no video-api): mensagem retida até replay manual (`aws sqs start-message-move-task`).
  // Um consumer aqui que só loga e retorna faria o SqsQueueConsumer entender "sucesso" e
  // apagar a mensagem — perdendo de vez o único registro de uma notificação que esgotou
  // todos os canais.

  @Bean
  public SqsQueueDepthGauge sqsQueueDepthGauge(SqsClient sqsClient, SqsQueueUrlResolver resolver,
      SqsProperties sqsProperties, MeterRegistry meterRegistry, QueueProperties queues) {
    return new SqsQueueDepthGauge(sqsClient, resolver, sqsProperties, meterRegistry,
        List.of(queues.notification(), queues.notificationDlq()));
  }
}
