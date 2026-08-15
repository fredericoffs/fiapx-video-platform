# Documentação — FIAP X Video Platform

Índice da documentação de arquitetura do hackathon. Para instruções de "como rodar", ver o [README da raiz](../README.md).

## Enunciado

- [`enunciado.md`](./enunciado.md) — enunciado original do desafio (Hackathon Fase 5), com os requisitos funcionais/técnicos e a lista de entregáveis.

## Arquitetura

- [`architecture/hld-lld-adr-rfc.md`](./architecture/hld-lld-adr-rfc.md) — documento principal: RFC (motivação e proposta), HLD (visão de containers e topologia de implantação), LLD (modelo de dados, contratos de API, diagramas de sequência) e os 13 ADRs técnicos (broker, outbox, storage, autenticação, observabilidade, linguagem/runtime, comunicação entre serviços, API Gateway, Kubernetes, notificação multicanal, política de nuvem, consulta de status).

## DDD (Domain-Driven Design)

- [`ddd/linguagem-ubiqua.md`](./ddd/linguagem-ubiqua.md) — glossário de termos do domínio de processamento de vídeo.
- [`ddd/event-storming.md`](./ddd/event-storming.md) — eventos de domínio, comandos, agregados, políticas e read models.
- [`ddd/domain-storytelling.md`](./ddd/domain-storytelling.md) — jornada do usuário narrada (caminho feliz e caminho de falha).
- [`ddd/context-map.md`](./ddd/context-map.md) — bounded contexts e como se relacionam (Published Language, Customer/Supplier, Conformist).
