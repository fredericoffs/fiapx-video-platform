package com.fiapx.videoapi.infrastructure.messaging;

import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "fiapx.messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitMqConfig {

  @Bean
  public Queue processingQueue(QueueProperties queueProperties) {
    // Argumentos de dead-letter precisam bater com os do video-worker (dono da DLQ).
    return QueueBuilder.durable(queueProperties.processing())
        .withArgument("x-dead-letter-exchange", queueProperties.processingDlx())
        .withArgument("x-dead-letter-routing-key", queueProperties.processingDlq())
        .build();
  }

  // A API é dona da DLQ de resultados: um resultado que esgota as tentativas (ex.: banco fora)
  // fica retido aqui para replay, em vez de sumir. Argumentos precisam bater com o video-worker.
  @Bean
  public DirectExchange statusUpdatesDeadLetterExchange(QueueProperties queueProperties) {
    return new DirectExchange(queueProperties.statusUpdatesDlx());
  }

  @Bean
  public Queue statusUpdatesDeadLetterQueue(QueueProperties queueProperties) {
    return QueueBuilder.durable(queueProperties.statusUpdatesDlq()).build();
  }

  @Bean
  public Binding statusUpdatesDeadLetterBinding(
      Queue statusUpdatesDeadLetterQueue,
      DirectExchange statusUpdatesDeadLetterExchange,
      QueueProperties queueProperties
  ) {
    return BindingBuilder.bind(statusUpdatesDeadLetterQueue)
        .to(statusUpdatesDeadLetterExchange)
        .with(queueProperties.statusUpdatesDlq());
  }

  @Bean
  public Queue statusUpdatesQueue(QueueProperties queueProperties) {
    return QueueBuilder.durable(queueProperties.statusUpdates())
        .withArgument("x-dead-letter-exchange", queueProperties.statusUpdatesDlx())
        .withArgument("x-dead-letter-routing-key", queueProperties.statusUpdatesDlq())
        .build();
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
