package com.fiapx.videoapi.infrastructure.messaging;

import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

  @Bean
  public Queue processingQueue(QueueProperties queueProperties) {
    // Argumentos de dead-letter precisam bater com os do video-worker (dono da DLQ).
    return QueueBuilder.durable(queueProperties.processing())
        .withArgument("x-dead-letter-exchange", queueProperties.processingDlx())
        .withArgument("x-dead-letter-routing-key", queueProperties.processingDlq())
        .build();
  }

  @Bean
  public Queue statusUpdatesQueue(QueueProperties queueProperties) {
    return QueueBuilder.durable(queueProperties.statusUpdates()).build();
  }

  @Bean
  public Queue notificationQueue(QueueProperties queueProperties) {
    // Argumentos de dead-letter precisam bater com os do notification-worker (dono da DLQ).
    return QueueBuilder.durable(queueProperties.notification())
        .withArgument("x-dead-letter-exchange", queueProperties.notificationDlx())
        .withArgument("x-dead-letter-routing-key", queueProperties.notificationDlq())
        .build();
  }
}
