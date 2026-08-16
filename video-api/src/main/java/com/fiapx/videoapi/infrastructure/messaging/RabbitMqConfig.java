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
    return QueueBuilder.durable(queueProperties.processing()).build();
  }

  @Bean
  public Queue statusUpdatesQueue(QueueProperties queueProperties) {
    return QueueBuilder.durable(queueProperties.statusUpdates()).build();
  }
}
