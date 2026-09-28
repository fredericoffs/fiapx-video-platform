# ADR-007 — Linguagem e versão de runtime dos serviços

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Runtime |

## Contexto

Serviços implantados de forma independente permitem linguagens diferentes. Como o `ffmpeg` roda como subprocesso externo, a linguagem não afeta a velocidade da extração; afeta a maturidade do ecossistema (SQS, JWT, PostgreSQL, testes), o custo de montar CI e imagens, e o risco de execução no prazo.

## Decisão

Java 21 e Spring Boot 4.1 nos quatro serviços JVM, com **virtual threads** habilitadas (`spring.threads.virtual.enabled=true`) para que uploads concorrentes e esperas de I/O não esgotem um pool fixo de threads. Frontend em React 19 com TypeScript.

> [!TIP]
> A escolha de linguagem pesa menos do que parece neste desafio: a parte computacionalmente cara roda no `ffmpeg`, fora do runtime. A decisão real foi sobre ecossistema de mensageria, persistência e testes, e sobre risco de prazo.

## Alternativas consideradas

- **Go** — boa opção para o worker (goroutines, binário pequeno, partida rápida), mas mais código manual para outbox e idempotência, sem prática prévia validada.
- **Python (FastAPI + Celery)** — iteração rápida, mas o Celery sobre SQS diverge do modelo de fila explícita (exclusão manual, DLQ) adotado aqui.
- **Node.js/TypeScript** — adequado ao I/O da API, mas orquestrar subprocessos `ffmpeg` concorrentes de forma controlada exigiria código próprio.
- **Poliglota** (ex.: Java na API, Go no worker) — dobraria CI, imagens, logging e testes sem atender a requisito adicional.

## Consequências

- **Positivas:** reaproveitamento de padrões já validados (outbox, idempotência, JWT, Resilience4j, Testcontainers); menor risco na demonstração.
- **Negativas:** não demonstra poliglotismo; a JVM tem partida e memória maiores por pod, relevantes só se a escala precisasse ser muito rápida. Virtual threads não aumentam a capacidade de CPU do `ffmpeg`.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
