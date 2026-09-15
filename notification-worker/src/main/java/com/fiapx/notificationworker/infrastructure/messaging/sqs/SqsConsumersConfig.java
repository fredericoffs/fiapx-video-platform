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

  @Bean
  public SqsQueueConsumer notificationConsumer(SqsClient sqsClient, SqsProperties sqsProperties,
      SqsQueueUrlResolver resolver, QueueProperties queues, SqsNotificationRequestedListener listener) {
    return new SqsQueueConsumer(sqsClient, sqsProperties, "notification",
        resolver.urlOf(queues.notification()), resolver.urlOf(queues.notificationDlq()),
        sqsProperties.maxMessages(), listener);
  }

  @Bean
  public SqsQueueConsumer notificationDeadLetterConsumer(SqsClient sqsClient, SqsProperties sqsProperties,
      SqsQueueUrlResolver resolver, QueueProperties queues, SqsNotificationDeadLetterListener listener) {
    return new SqsQueueConsumer(sqsClient, sqsProperties, "notification-dlq",
        resolver.urlOf(queues.notificationDlq()), null, sqsProperties.maxMessages(), listener);
  }

  @Bean
  public SqsQueueDepthGauge sqsQueueDepthGauge(SqsClient sqsClient, SqsQueueUrlResolver resolver,
      SqsProperties sqsProperties, MeterRegistry meterRegistry, QueueProperties queues) {
    return new SqsQueueDepthGauge(sqsClient, resolver, sqsProperties, meterRegistry,
        List.of(queues.notification(), queues.notificationDlq()));
  }
}
