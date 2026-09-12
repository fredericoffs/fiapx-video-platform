package com.fiapx.notificationworker.infrastructure.messaging.sqs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

/** As filas continuam configuradas por nome (QUEUE_*); a URL é resolvida uma vez e cacheada. */
@Component
@ConditionalOnProperty(name = "fiapx.messaging.provider", havingValue = "sqs")
public class SqsQueueUrlResolver {

  private final SqsClient sqsClient;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  public SqsQueueUrlResolver(SqsClient sqsClient) {
    this.sqsClient = sqsClient;
  }

  public String urlOf(String queueName) {
    return cache.computeIfAbsent(queueName,
        name -> sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl());
  }
}
