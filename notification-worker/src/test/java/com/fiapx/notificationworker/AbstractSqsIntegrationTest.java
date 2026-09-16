package com.fiapx.notificationworker;

import com.fiapx.notificationworker.infrastructure.messaging.sqs.SqsTestSupport;
import java.util.List;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base pra qualquer {@code @SpringBootTest} do notification-worker: desde que SQS deixou de
 * ser condicional (fiapx.messaging.provider removido), todo contexto completo precisa da fila
 * real pra o {@code SqsConsumersConfig} resolver URL na criação dos beans — sem isso o
 * contexto nem sobe. Spring processa {@code @DynamicPropertySource} também em superclasses,
 * então basta estender esta classe em vez de repetir o bloco em cada teste.
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}: mesmo problema encontrado e corrigido no
 * video-api — todo subtipo reusa a mesma fila 'fiapx-video-notification' no LocalStack
 * compartilhado do módulo. Sem isso, o Spring mantém o {@code SqsQueueConsumer} de uma
 * classe já terminada vivo no cache de testes, concorrendo pelas mensagens publicadas por
 * classes seguintes.
 */
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractSqsIntegrationTest {

  static final software.amazon.awssdk.services.sqs.SqsClient SQS = SqsTestSupport.client();

  @DynamicPropertySource
  static void sqs(DynamicPropertyRegistry registry) {
    SqsTestSupport.createPlainQueues(SQS, List.of("fiapx-video-notification", "fiapx-video-notification-dlq"));
    registry.add("fiapx.sqs.endpoint", () -> SqsTestSupport.LOCALSTACK.getEndpoint().toString());
    registry.add("fiapx.sqs.region", SqsTestSupport.LOCALSTACK::getRegion);
    registry.add("fiapx.sqs.access-key", SqsTestSupport.LOCALSTACK::getAccessKey);
    registry.add("fiapx.sqs.secret-key", SqsTestSupport.LOCALSTACK::getSecretKey);
    registry.add("fiapx.sqs.wait-time-seconds", () -> "1");
  }
}
