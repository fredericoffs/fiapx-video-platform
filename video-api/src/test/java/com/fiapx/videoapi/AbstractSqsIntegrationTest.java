package com.fiapx.videoapi;

import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import java.util.List;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base pra qualquer {@code @SpringBootTest} do video-api: desde que SQS deixou de ser
 * condicional (fiapx.messaging.provider removido), todo contexto completo precisa de filas
 * reais pra o {@code SqsConsumersConfig} resolver URL na criação dos beans — sem isso o
 * contexto nem sobe. Spring processa {@code @DynamicPropertySource} também em superclasses,
 * então basta estender esta classe em vez de repetir o bloco em cada teste.
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}: todo subtipo compartilha as mesmas filas do
 * LocalStack ({@link SqsTestSupport#LOCALSTACK}, um único container pra todo o módulo). Sem
 * isso, o Spring mantém o contexto de uma classe (e o {@code SqsQueueConsumer} dela, que
 * roda em background) vivo no cache de testes depois que ela termina — esse consumidor
 * "esquecido" concorre pelas mensagens publicadas por outras classes nas mesmas filas e pode
 * roubar/descartar silenciosamente uma que não é dele (achando, por exemplo, um vídeo que só
 * existe no banco de teste de outra classe). Encerrar o contexto ao fim de cada classe evita
 * ter mais de um consumidor vivo por fila ao mesmo tempo — o custo é reiniciar o Spring a
 * cada classe (containers do Testcontainers continuam reaproveitados, só o contexto reinicia).
 */
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
