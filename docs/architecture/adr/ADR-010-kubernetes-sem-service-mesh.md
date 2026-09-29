# ADR-010 — Deploy em Kubernetes gerenciado, sem service mesh

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Kubernetes e escala |

## Contexto

O requisito de escalabilidade pede prova, não apenas desenho. O processamento é intensivo em CPU e acontece em rajadas; a API é leve e constante.

## Decisão

Kubernetes gerenciado (EKS, [ADR-012](ADR-012-aws-eks.md)) com probes, limites de recursos e duas formas de escala:

- **`video-api`:** HPA por CPU (alvo 70%, 1 a 3 réplicas).
- **`video-worker`:** KEDA pela fila SQS, **1 mensagem por réplica** (cada réplica processa um vídeo por vez), 1 a 3 réplicas, consulta a cada 15 s. A contagem inclui mensagens em processamento.
- **Desligamento gracioso do worker:** no SIGTERM (scale-down ou deploy), o pod para de buscar mensagens e termina o vídeo em andamento; `terminationGracePeriodSeconds` de 1.020 s cobre o timeout de 15 min do `ffmpeg`.

Sem service mesh: a comunicação entre serviços de negócio é por fila, e retry/circuit breaker já são resolvidos em código (Resilience4j).

## Alternativas consideradas

- **Service mesh (Istio/Linkerd)** — rejeitado: mTLS e sidecars sem resolver requisito novo; complexidade operacional desproporcional.
- **Escala do worker por CPU** — rejeitada: um único vídeo já satura a CPU do pod; a métrica certa é a fila.
- **Mínimo de 2 workers** — rejeitado: garantiria simultaneidade sem demonstrar a escala; com mínimo 1 o ciclo de expansão e redução fica visível.

## Consequências

- **Positivas:** escala real e observável; o scale-down não descarta trabalho em andamento.
- **Negativas:** o cluster tem teto de capacidade (3 nós `t3.large`, CPU reservada acima de 70%); um pod em término pode ficar vários minutos em `Terminating`; sem mTLS entre serviços.

## Evidência

Demo de concorrência em 27/09/2026: réplicas de 1 → 3 → 2 → 1, dois vídeos em `PROCESSING` simultâneos, ambos concluídos em 279 s. Um SIGTERM enviado ao único worker, no meio de um vídeo, resultou no pod terminando o vídeo em 3 min 29 s e saindo com `Graceful shutdown complete`.

## Histórico de revisões

- **Armazenamento efêmero do worker** — vídeo, frames e ZIP ficam juntos no disco do pod; sem limite, um vídeo longo podia encher o disco do nó. Foram definidos `ephemeral-storage` 1 GiB (request) e 8 GiB (limit).
- **27/09/2026 · meta do KEDA** — com `queueLength: 5`, dois vídeos davam ⌈2/5⌉ = 1 réplica e o segundo esperava na fila. A meta passou a 1, a capacidade real de cada réplica, e a redução passou a esperar 60 s em vez dos 300 s padrão do HPA.
- **27/09/2026 · desligamento gracioso** — a demo mostrou o HPA encerrando justamente o pod que processava um vídeo, refeito do zero em outro pod (+5 min). O consumidor passou a terminar o trabalho antes de sair; a demo seguinte caiu de 577 s para 279 s, sem reprocessamento.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
