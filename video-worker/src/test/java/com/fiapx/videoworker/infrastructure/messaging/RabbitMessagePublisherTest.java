package com.fiapx.videoworker.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.fiapx.videoworker.domain.exception.MessagePublishException;
import com.fiapx.videoworker.domain.model.OutboundMessage;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitMessagePublisherTest {

  private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
  private final RabbitMessagePublisher publisher = new RabbitMessagePublisher(rabbitTemplate, Duration.ofMillis(300));

  @Test
  void returnsNormallyWhenBrokerAcks() {
    stubSend(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(true, null)));

    assertThatCode(() -> publisher.publish("video.status-updates", OutboundMessage.of("{}", "corr-1", "evt-1"))).doesNotThrowAnyException();
  }

  @Test
  void failsWhenBrokerNacks() {
    stubSend(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(false, "queue full")));

    assertThatThrownBy(() -> publisher.publish("video.status-updates", OutboundMessage.of("{}", null, "evt-1")))
        .isInstanceOf(MessagePublishException.class)
        .hasMessageContaining("nack")
        .hasMessageContaining("queue full");
  }

  @Test
  void failsWhenMessageIsReturnedAsUnroutable() {
    stubSend(correlation -> {
      Message body = new Message("{}".getBytes(), new MessageProperties());
      correlation.setReturned(new ReturnedMessage(body, 312, "NO_ROUTE", "", "nao-existe"));
      correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
    });

    assertThatThrownBy(() -> publisher.publish("nao-existe", OutboundMessage.of("{}", null, "evt-1")))
        .isInstanceOf(MessagePublishException.class)
        .hasMessageContaining("devolvida")
        .hasMessageContaining("NO_ROUTE");
  }

  @Test
  void failsWhenConfirmationNeverArrives() {
    stubSend(correlation -> { /* broker mudo: future nunca completa */ });

    assertThatThrownBy(() -> publisher.publish("video.status-updates", OutboundMessage.of("{}", null, "evt-1")))
        .isInstanceOf(MessagePublishException.class)
        .hasMessageContaining("não confirmou");
  }

  private void stubSend(java.util.function.Consumer<CorrelationData> brokerBehaviour) {
    doAnswer(invocation -> {
      CorrelationData correlation = invocation.getArgument(4);
      brokerBehaviour.accept(correlation);
      return null;
    }).when(rabbitTemplate).convertAndSend(eq(""), any(String.class), any(Object.class), any(), any(CorrelationData.class));
  }
}
