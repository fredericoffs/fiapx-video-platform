package com.fiapx.videoapi.infrastructure.messaging.sqs;

import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.config.SqsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

/** A DLQ de resultados fica sem consumer de propósito: mensagem retida até replay manual. */
@Configuration
public class SqsConsumersConfig {

  @Bean
  public SqsQueueConsumer statusUpdatesConsumer(SqsClient sqsClient, SqsProperties sqsProperties,
      SqsQueueUrlResolver resolver, QueueProperties queues, SqsVideoStatusUpdateListener listener) {
    return new SqsQueueConsumer(sqsClient, sqsProperties, "status-updates",
        resolver.urlOf(queues.statusUpdates()), resolver.urlOf(queues.statusUpdatesDlq()),
        sqsProperties.maxMessages(), listener);
  }

  @Bean
  public SqsQueueDepthGauge sqsQueueDepthGauge(SqsClient sqsClient, SqsQueueUrlResolver resolver,
      SqsProperties sqsProperties, MeterRegistry meterRegistry, QueueProperties queues) {
    return new SqsQueueDepthGauge(sqsClient, resolver, sqsProperties, meterRegistry, List.of(
        queues.processing(), queues.processingDlq(), queues.statusUpdates(), queues.statusUpdatesDlq(),
        queues.notification(), queues.notificationDlq()));
  }
}
