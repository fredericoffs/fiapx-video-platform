package com.fiapx.videoapi.infrastructure.messaging.sqs;

import com.fiapx.videoapi.domain.exception.MessagePublishException;
import com.fiapx.videoapi.domain.model.OutboundMessage;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * SendMessage é síncrono: a resposta com messageId é a confirmação; qualquer erro do SDK vira exceção.
 */
@Component
public class SqsMessagePublisher implements MessagePublisher {

  private final SqsClient sqsClient;
  private final SqsQueueUrlResolver resolver;

  public SqsMessagePublisher(SqsClient sqsClient, SqsQueueUrlResolver resolver) {
    this.sqsClient = sqsClient;
    this.resolver = resolver;
  }

  private static MessageAttributeValue stringAttribute(String value) {
    return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
  }

  @Override
  public void publish(String queueName, OutboundMessage message) {
    Map<String, MessageAttributeValue> attributes = new HashMap<>();
    if (message.correlationId() != null) {
      attributes.put(SqsMessageHandler.CORRELATION_ID, stringAttribute(message.correlationId()));
    }
    if (message.eventId() != null) {
      attributes.put(SqsMessageHandler.EVENT_ID, stringAttribute(message.eventId()));
    }
    try {
      sqsClient.sendMessage(SendMessageRequest.builder()
          .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(10)))
          .queueUrl(resolver.urlOf(queueName))
          .messageBody(message.payloadJson())
          .messageAttributes(attributes)
          .build());
    } catch (SdkException e) {
      throw new MessagePublishException("Falha ao publicar na fila SQS " + queueName + ": " + e.getMessage(), e);
    }
  }
}
