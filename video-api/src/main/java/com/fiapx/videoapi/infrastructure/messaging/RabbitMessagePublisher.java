package com.fiapx.videoapi.infrastructure.messaging;

import com.fiapx.videoapi.domain.exception.MessagePublishException;
import com.fiapx.videoapi.domain.model.OutboundMessage;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publica no exchange default (routing key = nome da fila) e só devolve depois que o broker
 * confirma (publisher confirm) e sem a mensagem ter sido devolvida (mandatory/return). Nack,
 * return ou prazo esgotado viram {@link MessagePublishException}: quem chama (outbox) não
 * marca o evento como publicado.
 */
@Component
@ConditionalOnProperty(name = "fiapx.messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitMessagePublisher implements MessagePublisher {

  public static final String EVENT_ID_HEADER = "eventId";

  private final RabbitTemplate rabbitTemplate;
  private final Duration confirmTimeout;

  public RabbitMessagePublisher(
      RabbitTemplate rabbitTemplate,
      @Value("${fiapx.messaging.confirm-timeout:5s}") Duration confirmTimeout
  ) {
    this.rabbitTemplate = rabbitTemplate;
    this.confirmTimeout = confirmTimeout;
  }

  @Override
  public void publish(String queueName, OutboundMessage message) {
    CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
    rabbitTemplate.convertAndSend("", queueName, message.payloadJson(), amqpMessage -> {
      if (message.correlationId() != null) {
        amqpMessage.getMessageProperties().setCorrelationId(message.correlationId());
      }
      if (message.eventId() != null) {
        amqpMessage.getMessageProperties().setHeader(EVENT_ID_HEADER, message.eventId());
      }
      return amqpMessage;
    }, correlationData);
    awaitConfirmation(queueName, correlationData);
  }

  private void awaitConfirmation(String queueName, CorrelationData correlationData) {
    CorrelationData.Confirm confirm;
    try {
      confirm = correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new MessagePublishException(
          "Broker não confirmou a publicação em " + queueName + " dentro de " + confirmTimeout, e);
    } catch (ExecutionException e) {
      throw new MessagePublishException("Falha ao aguardar confirmação da publicação em " + queueName, e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MessagePublishException("Interrompido aguardando confirmação da publicação em " + queueName, e);
    }
    if (correlationData.getReturned() != null) {
      throw new MessagePublishException("Mensagem devolvida pelo broker (fila " + queueName + " não roteável): "
          + correlationData.getReturned().getReplyText());
    }
    if (!confirm.isAck()) {
      throw new MessagePublishException("Broker recusou (nack) a publicação em " + queueName + ": " + confirm.getReason());
    }
  }
}
