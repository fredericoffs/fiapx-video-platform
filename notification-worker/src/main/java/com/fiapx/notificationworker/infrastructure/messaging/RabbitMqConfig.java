package com.fiapx.notificationworker.infrastructure.messaging;

import com.fiapx.notificationworker.infrastructure.config.QueueProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

  @Bean
  public DirectExchange notificationDeadLetterExchange(QueueProperties queueProperties) {
    return new DirectExchange(queueProperties.notificationDlx());
  }

  @Bean
  public Queue notificationDeadLetterQueue(QueueProperties queueProperties) {
    return QueueBuilder.durable(queueProperties.notificationDlq()).build();
  }

  @Bean
  public Binding notificationDeadLetterBinding(
      Queue notificationDeadLetterQueue,
      DirectExchange notificationDeadLetterExchange,
      QueueProperties queueProperties
  ) {
    return BindingBuilder.bind(notificationDeadLetterQueue)
        .to(notificationDeadLetterExchange)
        .with(queueProperties.notificationDlq());
  }

  @Bean
  public Queue notificationQueue(QueueProperties queueProperties) {
    return QueueBuilder.durable(queueProperties.notification())
        .withArgument("x-dead-letter-exchange", queueProperties.notificationDlx())
        .withArgument("x-dead-letter-routing-key", queueProperties.notificationDlq())
        .build();
  }
}
