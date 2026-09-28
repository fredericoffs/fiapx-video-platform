# ADR-008 — Comunicação de status entre video-worker e video-api

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Resultados por evento, não escrita direta |

## Contexto

O desenho inicial previa o worker atualizando o status diretamente no PostgreSQL do `video-api`. Isso daria ao worker credenciais e conhecimento do schema de outro serviço, contrariando a regra de que nenhum serviço acessa o banco de outro.

## Decisão

O `video-worker` não tem acesso a banco. Ao começar e ao terminar, publica `PROCESSING_STARTED`, `PROCESSING_COMPLETED` ou `PROCESSING_FAILED` na fila de resultados. O `video-api` aplica o estado com optimistic locking e, em caso de falha, grava o pedido de notificação na própria outbox — é a API que decide e confirma a transição que dispara o aviso.

## Alternativas consideradas

- **Manter a escrita direta** — rejeitado: acoplamento de schema entre serviços.
- **Réplica somente leitura para o worker** — rejeitado: complexidade desproporcional quando um evento resolve o problema.

## Consequências

- **Positivas:** o worker é sem estado e fácil de escalar e extrair; o ciclo de vida do vídeo tem um único dono.
- **Negativas:** uma fila a mais; latência entre o fim do `ffmpeg` e o status no banco; eventos podem chegar fora de ordem (tratado pelos estados terminais). A DLQ de resultados exige investigação e replay controlado.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
