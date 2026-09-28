# ADR-014 — Serviços gerenciados AWS como única topologia de dados

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Dados e mensageria fora do cluster |

## Contexto

Rodar object storage, broker, banco e cache em containers significaria PVCs, backups e estado a operar num cluster recriado a cada sessão do laboratório. A conta permite S3, SQS, RDS (classes até *medium*, gp2, sem Enhanced Monitoring) e ElastiCache.

## Decisão

Toda a infraestrutura de dados e mensageria é gerenciada pela AWS, sem alternativa dentro do cluster:

- **S3** para vídeos e ZIPs ([ADR-004](ADR-004-armazenamento.md)); os buckets são criados por script porque uma política do laboratório nega uma permissão que o provider Terraform usa ao ler buckets.
- **SQS** para as três filas e DLQs ([ADR-002](ADR-002-broker.md)).
- **RDS PostgreSQL 17** com `sslmode=require` e um schema por serviço.
- **ElastiCache Redis 7.1** para os limites de login e de borda.

## Alternativas consideradas

- **Tudo no EKS (PostgreSQL, Redis, broker e object storage em containers com PVC)** — rejeitado: estado recriado a cada sessão e quatro componentes com estado para operar.
- **Amazon MQ** — rejeitado: fora da lista liberada do laboratório e mais caro que SQS para uma fila de trabalho.
- **Spring Cloud AWS** — inviável: sem versão para Spring Boot 4; o adapter usa o SDK diretamente.

## Consequências

- **Positivas:** nenhum PVC de aplicação; TLS no banco; redrive e métricas nativas.
- **Negativas:** o `apply` do Terraform fica mais lento (~13 min); sem IRSA, pods e o operador do KEDA usam a role do nó; `video-api` e `notification-worker` ainda compartilham a credencial do banco.

## Evidência

Migração Flyway contra o RDS com TLS, escala do KEDA disparada pela fila SQS real e leitura das métricas desses serviços pelo CloudWatch, todas observadas em 27/09/2026.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
