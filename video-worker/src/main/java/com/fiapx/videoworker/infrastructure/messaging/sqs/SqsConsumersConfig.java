package com.fiapx.videoworker.infrastructure.messaging.sqs;

import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.config.SqsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsConsumersConfig {

  // 1 mensagem por vez: equivalente ao prefetch=1 do RabbitMQ para um trabalho CPU-bound.
  @Bean
  public SqsQueueConsumer processingConsumer(SqsClient sqsClient, SqsProperties sqsProperties,
      SqsQueueUrlResolver resolver, QueueProperties queues, SqsVideoProcessingListener listener) {
    return new SqsQueueConsumer(sqsClient, sqsProperties, "processing",
        resolver.urlOf(queues.processing()), resolver.urlOf(queues.processingDlq()), 1, listener);
  }

  // 1 mensagem por vez: o heartbeat do SqsQueueConsumer só estende a visibilidade da mensagem
  // em processamento — um lote >1 deixaria as demais perdendo visibilidade em memória.
  @Bean
  public SqsQueueConsumer processingDeadLetterConsumer(SqsClient sqsClient, SqsProperties sqsProperties,
      SqsQueueUrlResolver resolver, QueueProperties queues, SqsVideoProcessingDeadLetterListener listener) {
    return new SqsQueueConsumer(sqsClient, sqsProperties, "processing-dlq",
        resolver.urlOf(queues.processingDlq()), null, 1, listener);
  }

  @Bean
  public SqsQueueDepthGauge sqsQueueDepthGauge(SqsClient sqsClient, SqsQueueUrlResolver resolver,
      SqsProperties sqsProperties, MeterRegistry meterRegistry, QueueProperties queues) {
    return new SqsQueueDepthGauge(sqsClient, resolver, sqsProperties, meterRegistry,
        List.of(queues.processing(), queues.processingDlq(), queues.statusUpdates(), queues.statusUpdatesDlq()));
  }
}
