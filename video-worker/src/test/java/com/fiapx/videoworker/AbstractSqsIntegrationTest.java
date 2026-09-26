package com.fiapx.videoworker;

import com.fiapx.videoworker.infrastructure.messaging.sqs.SqsTestSupport;
import java.util.List;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Base pra qualquer {@code @SpringBootTest} do video-worker: desde que SQS deixou de ser condicional (fiapx.messaging.provider removido), todo
 * contexto completo precisa de filas reais pra o {@code SqsConsumersConfig} resolver URL na criação dos beans — sem isso o contexto nem sobe. Spring
 * processa {@code @DynamicPropertySource} também em superclasses, então basta estender esta classe em vez de repetir o bloco em cada teste. Redrive
 * rápido (visibilidade 1s, 2 recebimentos) na fila de processamento pra quem testar DLQ terminar em segundos, sem afetar quem só precisa do contexto
 * de pé.
 */
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
abstract class AbstractSqsIntegrationTest {

  static final SqsClient SQS = SqsTestSupport.client();

  @DynamicPropertySource
  static void sqs(DynamicPropertyRegistry registry) {
    SqsTestSupport.createQueueWithDlq(SQS, "fiapx-video-processing", 1, 2);
    SqsTestSupport.createPlainQueues(SQS, List.of("fiapx-video-status-updates", "fiapx-video-status-updates-dlq"));
    registry.add("fiapx.sqs.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.sqs.region", SqsTestSupport.LOCALSTACK::getRegion);
    registry.add("fiapx.sqs.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.sqs.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.sqs.wait-time-seconds", () -> "1");
    registry.add("fiapx.storage.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.storage.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.storage.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.storage.path-style", () -> "true");
    try (var s3 = S3Client.builder()
        .endpointOverride(SqsTestSupport.LOCALSTACK.getEndpoint()).forcePathStyle(true)
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))).build()) {
      s3.createBucket(b -> b.bucket("videos-processed"));
    }

  }
}
