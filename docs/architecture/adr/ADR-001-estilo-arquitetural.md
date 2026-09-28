# ADR-001 — Estilo arquitetural para o hackathon

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Decomposição em serviços |

## Contexto

O enunciado pede a demonstração de microsserviços, mas o domínio é linear e simples: upload → processar → notificar. Um domínio com vários bounded contexts e transações distribuídas com compensação justificaria mais serviços; não é o caso. A aplicação de referência mostra o custo do extremo oposto: um processo único em que o `ffmpeg` bloqueia a requisição ([RFC](../02-rfc.md#problema)).

## Decisão

Três serviços de negócio implantáveis de forma independente — `video-api`, `video-worker` e `notification-worker` — mais um gateway (`video-gateway`) e um frontend (`web`). Identidade e ingestão ficam no mesmo processo (`video-api`), porque compartilham o mesmo dono de dados. Cada serviço é um projeto Maven independente, sem reactor multi-módulo, para poder ser extraído para um repositório próprio sem mudança de código.

Não há Saga: não existe passo de negócio a compensar. Uma falha marca o vídeo como `FAILED` e dispara a notificação; a limpeza de objetos no S3 é uma compensação técnica, não de negócio.

## Alternativas consideradas

- **Monólito único** — rejeitado: não isola o processamento intensivo em CPU do tráfego da API.
- **Decomposição mais granular** (5+ serviços, Lambda, orquestrador explícito) — rejeitada: custo de montagem e operação desproporcional ao domínio.

## Consequências

- **Positivas:** o processamento escala e falha de forma isolada; a decomposição é justificável pelo perfil de carga de cada parte.
- **Negativas:** o consumidor SQS e partes de configuração se repetem nos serviços — custo aceito para manter a independência dos projetos (ver [ADR-013](ADR-013-sem-cqrs.md#histórico-de-revisões)).

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
