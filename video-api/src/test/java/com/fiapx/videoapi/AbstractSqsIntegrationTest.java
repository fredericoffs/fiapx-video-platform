package com.fiapx.videoapi;

import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import java.util.List;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base pra qualquer {@code @SpringBootTest} do video-api: desde que SQS deixou de ser
 * condicional (fiapx.messaging.provider removido), todo contexto completo precisa de filas
 * reais pra o {@code SqsConsumersConfig} resolver URL na criação dos beans — sem isso o
 * contexto nem sobe. Spring processa {@code @DynamicPropertySource} também em superclasses,
 * então basta estender esta classe em vez de repetir o bloco em cada teste.
 */
@Import(TestcontainersConfiguration.class)
abstract class AbstractSqsIntegrationTest {

  @DynamicPropertySource
  static void sqs(DynamicPropertyRegistry registry) {
    SqsTestSupport.createPlainQueues(SqsTestSupport.client(), List.of(
        "fiapx-video-processing", "fiapx-video-processing-dlq", "fiapx-video-status-updates",
        "fiapx-video-status-updates-dlq", "fiapx-video-notification", "fiapx-video-notification-dlq"));
    registry.add("fiapx.sqs.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.sqs.region", SqsTestSupport.LOCALSTACK::getRegion);
    registry.add("fiapx.sqs.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.sqs.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.sqs.wait-time-seconds", () -> "1");
    // Storage continua com fake nos testes de contexto; endpoint vazio = caminho S3 real (só config).
    registry.add("fiapx.storage.endpoint", () -> "");
  }
}
