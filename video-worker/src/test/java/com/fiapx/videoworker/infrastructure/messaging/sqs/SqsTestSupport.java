package com.fiapx.videoworker.infrastructure.messaging.sqs;

import com.fiapx.videoworker.infrastructure.config.SqsProperties;
import java.util.List;
import java.util.Map;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/** LocalStack compartilhado pelos testes de SQS (community 4.0 — a tag latest exige licença). */
public final class SqsTestSupport {

  public static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.0"));

  static {
    LOCALSTACK.start();
  }

  private SqsTestSupport() {
  }

  public static SqsProperties properties(int heartbeatSeconds, int visibilityExtensionSeconds) {
    return new SqsProperties(LOCALSTACK.getRegion(), LOCALSTACK.getEndpoint().toString(), LOCALSTACK.getAccessKey(),
        LOCALSTACK.getSecretKey(), 1, heartbeatSeconds, visibilityExtensionSeconds, 30_000L, 60);
  }

  public static SqsClient client() {
    return SqsClientConfig.configure(SqsClient.builder(), properties(30, 120)).build();
  }

  /** Fila principal com redrive para a DLQ após {@code maxReceiveCount} recebimentos. */
  public static String createQueueWithDlq(SqsClient sqs, String name, int visibilityTimeoutSeconds, int maxReceiveCount) {
    String dlqUrl = sqs.createQueue(CreateQueueRequest.builder().queueName(name + "-dlq").build()).queueUrl();
    String dlqArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(dlqUrl)
        .attributeNames(QueueAttributeName.QUEUE_ARN).build()).attributes().get(QueueAttributeName.QUEUE_ARN);
    return sqs.createQueue(CreateQueueRequest.builder().queueName(name).attributes(Map.of(
        QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(visibilityTimeoutSeconds),
        QueueAttributeName.REDRIVE_POLICY,
        "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"" + maxReceiveCount + "\"}"
    )).build()).queueUrl();
  }

  public static void createPlainQueues(SqsClient sqs, List<String> names) {
    for (String name : names) {
      sqs.createQueue(CreateQueueRequest.builder().queueName(name).build());
    }
  }

  public static String urlOf(SqsClient sqs, String name) {
    return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
  }

  public static int visibleMessages(SqsClient sqs, String queueUrl) {
    return Integer.parseInt(sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(queueUrl)
        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).build())
        .attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
  }
}
