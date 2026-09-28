# ADR-013 — Sem CQRS/Event Sourcing para consulta de status

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Consulta direta de status |

## Contexto

A listagem de status por usuário é uma consulta simples sobre poucos campos, com volume baixo de escrita em relação às leituras de polling.

## Decisão

`GET /videos` consulta o PostgreSQL do `video-api` com paginação e escopo pelo JWT, sem modelo de leitura separado, event store ou cache.

## Alternativas consideradas

- **CQRS + Event Sourcing** — rejeitado: volume e complexidade não justificam separar leitura e escrita.
- **Cache Redis da listagem** — rejeitado: adicionaria invalidação a cada mudança de status para economizar uma consulta indexada barata.

## Consequências

- **Positivas:** consistência simples; um componente a menos.
- **Negativas:** só o estado atual é persistido; um histórico completo de transições (auditoria) precisaria ser desenhado à parte.

## Histórico de revisões

- **Dívida de manutenção registrada** — a revisão crítica apontou cinco pontos: 
    - 1. consumidor SQS duplicado nos serviços — mantido de propósito, para preservar a independência dos projetos; 
    - 2. versões não fixadas em imagens de teste e charts; 
    - 3. um Secret com todas as chaves para todos os serviços — separado por serviço; 
    - 4. credencial de banco compartilhada — registrada como próximo passo (roles PostgreSQL por schema); 
    - 5. documentação misturando estados — alerta geral.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
