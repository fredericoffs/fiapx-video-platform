package com.fiapx.videoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiapx.videoapi.domain.exception.MessagePublishException;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** C03/C04 contra um RabbitMQ real: return de fila inexistente e DLQ de resultados. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StatusUpdatesDeadLetterIntegrationTest {

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private MessagePublisher messagePublisher;

  @Autowired
  private QueueProperties queueProperties;

  @Test
  void publishingToAnUnroutableQueueFailsInsteadOfSilentlySucceeding() {
    assertThatThrownBy(() -> messagePublisher.publish("fila-que-nao-existe-" + System.nanoTime(), "{}", "corr-x"))
        .isInstanceOf(MessagePublishException.class)
        .hasMessageContaining("devolvida");
  }

  @Test
  void malformedResultMessageLandsInTheStatusUpdatesDeadLetterQueue() {
    String marker = "malformed-" + System.nanoTime();
    rabbitTemplate.convertAndSend(queueProperties.statusUpdates(), "{not json " + marker, m -> {
      m.getMessageProperties().setCorrelationId("dlq-corr");
      return m;
    });

    Message deadLettered = receiveContaining(queueProperties.statusUpdatesDlq(), marker);

    assertThat(deadLettered).as("mensagem malformada retida em %s", queueProperties.statusUpdatesDlq()).isNotNull();
    assertThat(deadLettered.getMessageProperties().getCorrelationId()).isEqualTo("dlq-corr");
  }

  private Message receiveContaining(String queue, String needle) {
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      Message message = rabbitTemplate.receive(queue, 500);
      if (message != null && new String(message.getBody()).contains(needle)) {
        return message;
      }
    }
    return null;
  }
}
