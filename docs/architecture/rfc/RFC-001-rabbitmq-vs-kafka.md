# RFC-001 — Broker de mensageria: RabbitMQ em vez de Kafka

| Campo | Valor |
|---|---|
| Status | Aceito — decisão registrada no [ADR-002](../hld-lld-adr-rfc.md#adr-002--broker-de-mensageria) |
| Autor | Frederico Ferreira |
| Escopo | Fila entre `video-api` (produtor) e `video-worker` (consumidor); eventos `video.status-updates` e `video.notification` |

## Problema

O enunciado exige processar vários vídeos ao mesmo tempo e não perder requisição em pico. Isso pede um buffer durável entre a aceitação do upload e a extração de frames pelo `ffmpeg`, com consumo concorrente por vários workers e retentativa/descarte controlado quando o processamento falha. O enunciado sugere RabbitMQ ou Kafka.

## Proposta

Usar **RabbitMQ** (AMQP 0-9-1, Spring AMQP) como fila de trabalho:

- Uma fila durável por caso de uso (`video.processing`, `video.status-updates`, `video.notification`), cada uma com dead-letter exchange e DLQ própria.
- Ack manual após o `ffmpeg` terminar; nack sem requeue envia para a DLQ após as retentativas.
- `prefetch=1` por worker: cada réplica pega um vídeo por vez, o que combina com um processo CPU-bound.
- KEDA escala o `video-worker` pela profundidade da fila (`queueLength`), lendo a API de management do RabbitMQ.
- Publicação pelo padrão Outbox transacional no `video-api` (ADR-003), então a mensagem só existe se o registro do vídeo foi gravado.

## Alternativa considerada: Kafka

| Critério | RabbitMQ | Kafka |
|---|---|---|
| Modelo | Fila de trabalho: cada mensagem é consumida por um worker e some | Log particionado: mensagens ficam retidas, consumidores mantêm offset |
| Ack por mensagem e DLQ | Nativos (ack/nack, dead-letter exchange) | Commit de offset por partição; DLQ é convenção da aplicação |
| Concorrência | Qualquer número de workers na mesma fila | Limitada ao número de partições; rebalanceamento a cada escala |
| Autoscaling por backlog | Fila tem tamanho direto; KEDA usa `queueLength` | KEDA usa lag por consumer group, mais indireto |
| Replay / múltiplos leitores do mesmo evento | Não | Sim, é o ponto forte |
| Operação local e em Kubernetes | Um container, sem coordenação externa | Broker + controller (KRaft), mais memória, mais tuning |

O ganho central do Kafka é replay e múltiplos consumidores independentes do mesmo stream. Este sistema tem um consumidor por evento e processamento único por vídeo. O custo de partições, consumer groups e rebalanceamento não compra nada aqui e complica o KEDA.

## Consequências

- Positivas: semântica de fila direta para o caso de uso, DLQ e retentativa sem código próprio, escala horizontal do worker por profundidade de fila, um container só no Compose e no cluster.
- Negativas: se surgir a necessidade de reprocessar histórico ou de vários serviços reagirem ao mesmo evento com ritmos independentes, migrar para Kafka exigiria rever a topologia. O código de negócio não muda, porque a publicação passa pela porta `MessagePublisher` e o consumo pelos listeners na camada de infraestrutura.

## Como validar

Teste de carga em `scripts/k8s-loadtest.sh`: 20 uploads simultâneos, fila cresce, KEDA escala o `video-worker` de 1 para 3, todos os vídeos terminam em `COMPLETED`, nenhuma mensagem perdida. Falha forçada (arquivo inválido) termina na DLQ e gera notificação.
