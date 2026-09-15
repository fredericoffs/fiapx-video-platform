package com.fiapx.videoworker;

import com.fiapx.videoworker.infrastructure.messaging.sqs.SqsTestSupport;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base pra qualquer {@code @SpringBootTest} do video-worker: desde que SQS deixou de ser
 * condicional (fiapx.messaging.provider removido), todo contexto completo precisa de filas
 * reais pra o {@code SqsConsumersConfig} resolver URL na criação dos beans — sem isso o
 * contexto nem sobe. Spring processa {@code @DynamicPropertySource} também em superclasses,
 * então basta estender esta classe em vez de repetir o bloco em cada teste. Redrive rápido
 * (visibilidade 1s, 2 recebimentos) na fila de processamento pra quem testar DLQ terminar em
 * segundos, sem afetar quem só precisa do contexto de pé.
 */
abstract class AbstractSqsIntegrationTest {

  static final software.amazon.awssdk.services.sqs.SqsClient SQS = SqsTestSupport.client();

  @DynamicPropertySource
  static void sqs(DynamicPropertyRegistry registry) {
    SqsTestSupport.createQueueWithDlq(SQS, "fiapx-video-processing", 1, 2);
    SqsTestSupport.createPlainQueues(SQS, List.of("fiapx-video-status-updates", "fiapx-video-status-updates-dlq"));
    registry.add("fiapx.sqs.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.sqs.region", SqsTestSupport.LOCALSTACK::getRegion);
    registry.add("fiapx.sqs.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.sqs.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.sqs.wait-time-seconds", () -> "1");
    registry.add("fiapx.storage.endpoint", () -> "");
  }
}
