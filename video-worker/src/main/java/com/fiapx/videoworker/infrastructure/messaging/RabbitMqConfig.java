package com.fiapx.videoworker.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fiapx.videoworker.infrastructure.config.QueueProperties;

@Configuration
@ConditionalOnProperty(name = "fiapx.messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitMqConfig {

	// Argumentos de dead-letter precisam bater com os do video-api (dono da DLQ de resultados).
	@Bean
	public Queue statusUpdatesQueue(QueueProperties queueProperties) {
		return QueueBuilder.durable(queueProperties.statusUpdates())
				.withArgument("x-dead-letter-exchange", queueProperties.statusUpdatesDlx())
				.withArgument("x-dead-letter-routing-key", queueProperties.statusUpdatesDlq())
				.build();
	}

	@Bean
	public DirectExchange processingDeadLetterExchange(QueueProperties queueProperties) {
		return new DirectExchange(queueProperties.processingDlx());
	}

	@Bean
	public Queue processingDeadLetterQueue(QueueProperties queueProperties) {
		return QueueBuilder.durable(queueProperties.processingDlq()).build();
	}

	@Bean
	public Binding processingDeadLetterBinding(Queue processingDeadLetterQueue,
			DirectExchange processingDeadLetterExchange, QueueProperties queueProperties) {
		return BindingBuilder.bind(processingDeadLetterQueue)
				.to(processingDeadLetterExchange)
				.with(queueProperties.processingDlq());
	}

	@Bean
	public Queue processingQueue(QueueProperties queueProperties) {
		return QueueBuilder.durable(queueProperties.processing())
				.withArgument("x-dead-letter-exchange", queueProperties.processingDlx())
				.withArgument("x-dead-letter-routing-key", queueProperties.processingDlq())
				.build();
	}
}
