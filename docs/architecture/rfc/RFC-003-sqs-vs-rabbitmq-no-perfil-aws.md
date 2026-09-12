# RFC-003 — Mensageria no perfil `aws`: Amazon SQS em vez de RabbitMQ self-hosted

| Campo | Valor |
|---|---|
| Status | Aceito — decisão registrada na emenda do [ADR-002](../hld-lld-adr-rfc.md#adr-002--broker-de-mensageria) e no [ADR-014](../hld-lld-adr-rfc.md#adr-014--serviços-gerenciados-por-perfil-de-execução) |
| Autor | Frederico Ferreira |
| Escopo | As três filas do pipeline (`processing`, `status-updates`, `notification`) e suas DLQs quando a plataforma roda no EKS do AWS Academy Learner Lab; o ambiente local continua em RabbitMQ ([RFC-001](./RFC-001-rabbitmq-vs-kafka.md)) |

## Problema

O deploy na AWS rodava o RabbitMQ dentro do EKS, com PVC em EBS, secret próprio e o exporter de management para o KEDA. O cluster do Learner Lab é recriado a cada sessão, então esse broker nunca acumula estado que valha a pena preservar, mas custa pods, volume e tempo de subida em todo deploy. O lab libera SQS sem restrição; Amazon MQ (RabbitMQ gerenciado) não está na lista.

A troca precisa manter as garantias já implementadas: outbox com confirmação de publicação, prefetch 1 no worker CPU-bound, dead-letter após retentativas, mensagem malformada sem loop, correlation-id e `eventId` de ponta a ponta, e autoscaling do `video-worker` por profundidade de fila.

## Proposta

Um adapter SQS por serviço (`infrastructure/messaging/sqs`), ativado pelo perfil Spring `aws` (`fiapx.messaging.provider=sqs`), implementando a mesma porta `MessagePublisher` e chamando os mesmos casos de uso dos listeners AMQP. Sem Spring Cloud AWS (não há versão para Spring Boot 4): AWS SDK v2 direto.

| Garantia | RabbitMQ (perfil local) | SQS (perfil `aws`) |
|---|---|---|
| Confirmação de publicação | Publisher confirms + returns; nack/return/timeout → `MessagePublishException`, outbox segue pendente | Resposta síncrona do `SendMessage`; exceção → outbox segue pendente |
| Concorrência do worker | `prefetch=1` | `MaxNumberOfMessages=1` na fila de processamento |
| Retentativa e DLQ | DLX + DLQ por fila, nack sem requeue após retries | Redrive policy `maxReceiveCount=3` → `<fila>-dlq`; mensagem não deletada volta a ficar visível |
| Timeout de consumo | Consumer ack timeout | Visibility timeout de 960 s na fila de processamento, renovado por heartbeat (`ChangeMessageVisibility`) enquanto o handler roda |
| Mensagem malformada | `AmqpRejectAndDontRequeueException` → DLQ | `SendMessage` na DLQ + `DeleteMessage` da original |
| Metadados | Headers AMQP `correlationId`, `eventId`, `contractVersion` | Message attributes com os mesmos nomes |
| Profundidade da fila | `rabbitmq_queue_messages_ready` (exporter) | `fiapx_queue_messages_visible{queue}` (`GetQueueAttributes` a cada 30 s) — o alerta `FiapxQueueDepthHigh` usa os dois |
| KEDA | Trigger `rabbitmq` (API de management) | Trigger `aws-sqs-queue` com `podIdentity: aws` (credencial do nó; o lab não permite IRSA) |
| Credenciais | Usuário/senha em Secret | Cadeia padrão do SDK (instance profile do nó, IMDS hop limit 2) — nenhum secret de mensageria |

Filas e DLQs são criadas pelo Terraform (`k8s/terraform/aws/sqs.tf`); a aplicação resolve a URL por nome (`GetQueueUrl`) no startup. Standard, não FIFO: a idempotência já é exigida pelo at-least-once do RabbitMQ (ADR-003) e FIFO limitaria throughput sem resolver nada novo.

## Alternativas consideradas

| Critério | SQS | Amazon MQ (RabbitMQ) | RabbitMQ no EKS (anterior) |
|---|---|---|---|
| Disponível no Learner Lab | Sim | Não listado | Sim |
| Mudança de código | Adapter novo por perfil | Nenhuma | Nenhuma |
| Estado no cluster | Nenhum | Nenhum | PVC + StatefulSet |
| Custo/tempo por sessão | Centavos, criação instantânea | Instância dedicada, ~15 min | Pods e volume a cada deploy |
| Semântica | Fila de trabalho, redrive nativo | AMQP completo | AMQP completo |

Amazon MQ seria a troca de menor esforço, mas fora da lista do lab. Manter o RabbitMQ no cluster foi a versão anterior deste mesmo deploy e é o que esta RFC substitui.

## Consequências

- Dois adapters de mensageria para manter, cada um com sua suíte de integração (Testcontainers RabbitMQ; LocalStack 4.0 para SQS).
- Nenhum secret de broker na AWS; `PROD_RABBITMQ_*` deixa de existir.
- Replay de DLQ muda de ferramenta: `scripts/rabbitmq-replay-dlq.sh` no local, `aws sqs start-message-move-task` na AWS.
- Ordem entre filas não é garantida (Standard); `ProcessingStarted` chegando depois de `ProcessingCompleted` é ignorado pela regra de transição do agregado (`QUEUED → PROCESSING` apenas).

## Como validar

1. `./mvnw -B verify` em `video-api`, `video-worker` e `notification-worker`: `SqsMessagingIntegrationTest` (publicar, consumir, reentregar, redrive, malformada, heartbeat, correlation-id) e `AwsProfileIntegrationTest` (contexto sobe sem RabbitMQ).
2. Na AWS, após `cd-aws.yml`: upload de vários vídeos → `kubectl get scaledobject,hpa -n fiapx` mostra o `video-worker` escalando pela fila; vídeo inválido → mensagem em `fiapx-video-processing-dlq` e status `FAILED` com notificação.
3. `kubectl get pvc -n fiapx` vazio: nenhum componente stateful de aplicação no cluster.
