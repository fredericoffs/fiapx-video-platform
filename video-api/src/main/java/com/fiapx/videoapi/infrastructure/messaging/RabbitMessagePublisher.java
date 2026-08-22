package com.fiapx.videoapi.infrastructure.messaging;

import com.fiapx.videoapi.domain.port.MessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMessagePublisher implements MessagePublisher {

  private final RabbitTemplate rabbitTemplate;

  public RabbitMessagePublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void publish(String queueName, String payloadJson, String correlationId) {
    rabbitTemplate.convertAndSend(queueName, payloadJson, message -> {
      if (correlationId != null) {
        message.getMessageProperties().setCorrelationId(correlationId);
      }
      return message;
    });
  }
}
