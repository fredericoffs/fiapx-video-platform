# ADR-006 — Observabilidade

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Métricas, logs e rastreio |

## Contexto

O enunciado sugere Prometheus/Grafana ou ELK. O sinal mais crítico do sistema — não perder requisições em pico — é observado por métricas de fila e de escala; o diagnóstico de um vídeo específico, porém, exige seguir seus logs por quatro serviços.

## Decisão

Stack no cluster, sem plataforma paga:

- **Métricas:** Micrometer em todos os serviços → Prometheus (`kube-prometheus-stack`), mais `kube-state-metrics`, `node-exporter`, ingress-nginx e KEDA. Métricas de negócio próprias (`fiapx_video_processed_total`, duração de ponta a ponta, profundidade de fila) e histograma de latência HTTP.
- **Logs:** JSON estruturado (logstash) com `correlationId` no MDC em todos os serviços; coletados pelo Alloy e guardados no Loki por 72 h.
- **Rastreio:** o `X-Correlation-Id` nasce no gateway, volta na resposta e atravessa HTTP, outbox e atributos SQS ([LLD](../04-lld.md#propagação-do-correlationid)).
- **Serviços AWS:** datasource CloudWatch no Grafana (RDS, SQS, ElastiCache, NLB, S3), com a role do nó.
- **Alertas:** Alertmanager com 9 regras próprias (`Fiapx*`: serviço sem réplica, 5xx, latência, taxa de `FAILED`, capacidade de workers esgotada, circuito do e-mail aberto, erros na notificação, profundidade de fila e DLQ não vazia) e as regras padrão do stack. Os alertas saem por **e-mail** para o endereço `+alertas` do remetente da aplicação, pelo mesmo SMTP, e por um **webhook** obrigatório no deploy; alertas `info` e os auxiliares do stack (`Watchdog`, `InfoInhibitor`) não viram notificação.

Oito dashboards estão descritos no [HLD](../03-hld.md#6-observabilidade), incluindo o *Alertas*, que mostra o que está disparando e se o Alertmanager está recebendo.

## Alternativas consideradas

- **ELK** — rejeitado: mais pesado para o cluster do laboratório; o Loki indexa só rótulos e atende a busca por `correlationId`.
- **Tracing distribuído (OpenTelemetry + Tempo/Jaeger)** — não adotado: com a comunicação assíncrona já correlacionada pelo `correlationId` nos logs, o ganho não compensaria a instrumentação extra no prazo.
- **Plataforma SaaS** — rejeitada: custo e dependência externa.

## Consequências

- **Positivas:** um vídeo pode ser seguido de ponta a ponta numa tela; saúde do cluster, dos serviços e da AWS no mesmo Grafana.
- **Negativas:** o Loki guarda os dados num volume que é destruído junto com o ambiente; consultas ao CloudWatch têm custo por métrica (dashboard com atualização de 1 min); métricas não substituem auditoria persistente; os alertas por e-mail dependem do mesmo SMTP da aplicação — se ele cair, resta o webhook e o dashboard *Alertas*.

## Histórico de revisões

- **Liveness separado de readiness** — os probes usavam o health agregado: uma dependência fora (RDS, Redis) reiniciava pods saudáveis. Liveness passou a refletir só o processo, e readiness as dependências.
- **Alerta com destino real** — o Alertmanager subia sem receiver: os alertas não chegavam a ninguém. O deploy passou a exigir `PROD_ALERTMANAGER_WEBHOOK_URL`.
- **27/09/2026 · logs e rastreio** — buscar um vídeo nos logs só encontrava o ingress e a conclusão no worker. Gateway, API e worker passaram a registrar cada etapa com `correlationId`; o job da outbox restaura o id do evento; Loki e Alloy foram adicionados.
- **27/09/2026 · saúde do ambiente** — `kube-state-metrics` e `node-exporter` estavam desligados, deixando vazios os dashboards de Kubernetes. Foram ligados, junto com métricas do ingress e do KEDA, histograma HTTP e o datasource CloudWatch (leitura confirmada com a role do laboratório).
- **27/09/2026 · alertas chegando a alguém** — o Alertmanager **nunca subia**: a lista de receivers do values substituía a do chart e removia o receiver `null` usado pela rota do `Watchdog`, e a configuração era rejeitada (`undefined receiver "null"`). As regras avaliavam, mas nenhuma notificação saía do cluster. O receiver `null` voltou (também para alertas `info`, como o `CPUThrottlingHigh` esperado do `ffmpeg`, e para o `InfoInhibitor`); o `install.sh` passou a gerar os destinos com `jq` — webhook e e-mail `+alertas`; foram criados 7 alertas da aplicação em `fiapx-alerts.yaml` e o dashboard *Alertas*. Validado ao vivo: um alerta chegou por e-mail e pelo webhook, sem falhas de envio.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
