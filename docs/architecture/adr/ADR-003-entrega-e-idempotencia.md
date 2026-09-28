# ADR-003 — Garantia de entrega e idempotência

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Outbox, leases e idempotência |

## Contexto

Um evento de upload não pode se perder entre o commit do vídeo e a publicação na fila (problema de *dual write*). Como o SQS entrega pelo menos uma vez, os consumidores podem receber a mesma mensagem mais de uma vez, fora de ordem, ou depois de um pod morrer no meio do trabalho.

## Decisão

- **Outbox transacional no `video-api`:** o pedido de processamento (e, depois, o de notificação) é gravado na mesma transação do estado do vídeo. Um job publica fora da transação e só então confirma, usando lease e token de propriedade ([LLD](../04-lld.md#outbox-e-publicação)).
- **Upload em três fases:** reserva de limpeza → envio ao S3 fora de transação → transação curta que confirma vídeo e outbox ([LLD](../04-lld.md#upload-em-três-fases)).
- **Idempotência por construção:** o worker usa lease no S3 e reaproveita um ZIP já existente; a API ignora eventos para vídeos em estado terminal; a notificação reivindica o envio por vídeo e canal antes de chamar o canal.
- **Heartbeat imediato:** ao receber a mensagem, a visibilidade cai para 120 s e é renovada durante o processamento.

## Alternativas consideradas

- **Publicar direto na fila dentro da transação** — rejeitado: é o próprio problema de *dual write*.
- **Exatamente uma vez via transações Kafka** — rejeitado junto com o Kafka ([ADR-002](ADR-002-broker.md)).
- **Tabela genérica de chaves de idempotência** — desnecessária: os estados terminais e os leases já tornam os consumidores idempotentes.

## Consequências

- **Positivas:** nenhum upload aceito se perde; falhas deixam trabalho retomável; reentregas não duplicam efeitos externos (`ffmpeg`, e-mail).
- **Negativas:** latência do polling da outbox (3 s); a janela entre publicar e confirmar ainda pode duplicar uma mensagem, o que os consumidores absorvem. O worker não tem outbox em banco: publica o resultado diretamente.

## Evidência

Resiliência em 27/09/2026: com a fila bloqueada por policy, 3 uploads ficaram `QUEUED` (20 falhas de publicação registradas pela outbox) e concluíram após a liberação; um worker derrubado à força teve o vídeo concluído pelo pod substituto 161 s depois ([detalhes](../05-riscos-e-resiliencia.md#3-o-que-os-testes-revelaram)).

## Histórico de revisões

- **Lease na outbox** — `SELECT … FOR UPDATE SKIP LOCKED` com `locked_until` de 30 s, publicação fora da transação e confirmação só após o SQS; duas réplicas da API não publicam o mesmo evento, e um lease de pod morto é reaproveitado. `eventId` e versão de contrato passaram a viajar em cada mensagem.
- **Token de propriedade** — o lease sozinho não dizia *quem* o detinha: uma réplica lenta podia confirmar ou liberar a reserva que já pertencia a outra. A coluna `locked_by` (migração V9) passou a ser exigida para confirmar ou liberar.
- **Efeitos externos idempotentes** — o worker passou a verificar o ZIP existente antes de rodar o `ffmpeg` de novo; a notificação trocou "consultar se já enviou → enviar" por reivindicação atômica (`SENDING`) antes de chamar o canal, eliminando e-mails duplicados em execuções concorrentes (migração V3 do `notification-worker`).
- **Heartbeat por mensagem** — os consumidores recebiam lotes de até 10 mensagens, mas o heartbeat só cobria a que estava em processamento; as demais podiam ser reentregues a outro consumidor. Todos os consumidores passaram a receber uma mensagem por vez.
- **27/09/2026 · upload sem transação aberta** — o upload mantinha a transação (e a trava do usuário) aberta durante o envio de até 500 MB ao S3, serializando uploads do mesmo usuário, prendendo conexões do pool e podendo deixar objeto órfão. Passou a ter três fases com reserva de limpeza.
- **27/09/2026 · heartbeat imediato** — o teste de resiliência mostrou recuperação de ~18 min após crash: o primeiro heartbeat só vinha após 30 s, e antes disso valia a visibilidade padrão de 960 s. O heartbeat passou a sair assim que a mensagem chega; a recuperação caiu para 161 s.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
