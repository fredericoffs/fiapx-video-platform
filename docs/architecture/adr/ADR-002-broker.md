# ADR-002 — Broker de mensageria

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Amazon SQS Standard |

## Contexto

O enunciado sugere RabbitMQ ou Kafka. A implantação é na AWS EKS ([ADR-012](ADR-012-aws-eks.md)), então o broker precisa ser gerenciado, sem estado a operar dentro de um cluster que é recriado a cada sessão do laboratório. O padrão de uso é uma **fila de trabalho**: cada vídeo é processado uma vez por um worker.

## Decisão

Amazon SQS Standard com três filas (processamento, resultados, notificações), cada uma com redrive para DLQ após 3 recebimentos. O adapter é próprio, com o AWS SDK v2 atrás da porta `MessagePublisher` — não existe Spring Cloud AWS para Spring Boot 4.

- Long polling de 20 s e **uma mensagem por vez** por consumidor (o worker é limitado por CPU).
- Visibilidade padrão de 960 s na fila de processamento (cobre o timeout de 15 min do `ffmpeg`), encurtada para 120 s por um heartbeat imediato e renovada enquanto o trabalho dura ([ADR-003](ADR-003-entrega-e-idempotencia.md)).

## Alternativas consideradas

- **Kafka** — rejeitado: o ganho central é replay de log e múltiplos consumidores do mesmo stream, que não é o requisito; partições e consumer groups seriam operação desproporcional.
- **RabbitMQ (no cluster ou Amazon MQ)** — rejeitado: exigiria operar um broker com estado no cluster ou um serviço fora da lista liberada do laboratório, sem ganho para uma fila de trabalho.

## Consequências

- **Positivas:** nenhuma infraestrutura de mensageria para operar; redrive nativo; métricas de profundidade e de idade da mensagem no CloudWatch.
- **Negativas:** entrega pelo menos uma vez e sem ordem garantida — consumidores precisam ser idempotentes e tolerar eventos fora de ordem. Se o projeto passar a exigir replay de eventos, Kafka passa a fazer sentido.

## Histórico de revisões

- **Remoção do caminho local** — RabbitMQ, que chegou a ser o broker da execução local, foi removido junto com esse caminho ([ADR-012](ADR-012-aws-eks.md)); SQS passou a ser o único broker.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
