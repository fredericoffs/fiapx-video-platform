# Riscos e resiliência

[← LLD](04-lld.md) · [Arquitetura](README.md) · [ADRs →](adr/README.md)

## 1. Riscos e mitigação

| Situação | Proteção atual | Limite / ação operacional |
|---|---|---|
| Pico sustentado de uploads | Rate limit por cliente na borda (429 explícito), fila e escala até 3 workers | Capacidade finita: acompanhar profundidade e idade da fila e DLQs (dashboards *Autoscaling e borda* e *Serviços AWS*) |
| Queda de worker no meio do processamento | Heartbeat imediato (visibilidade de 120 s), lease no S3, reentrega e ZIP reaproveitado | **Comprovado:** vídeo concluído pelo pod substituto 161 s após o crash |
| Scale-down ou deploy com vídeo em andamento | Desligamento gracioso: o pod termina o vídeo antes de sair (até 17 min) | **Comprovado:** pod em `Terminating` concluiu o vídeo e saiu limpo |
| Fila indisponível | Outbox transacional: o upload é persistido e publicado quando a fila volta | **Comprovado:** uploads ficaram `QUEUED` durante o bloqueio e concluíram depois |
| Falha de SMTP | Retentativas pela reentrega do SQS, isolamento por canal, alerta operacional e DLQ | Corrigir o canal e fazer replay controlado da DLQ; o webhook não entrega ao usuário |
| Transação longa no upload | Upload em três fases, com reserva de limpeza de órfãos | Envio acima de 1 h é rejeitado em vez de gravar vídeo sem arquivo |
| Certificado autoassinado | HTTPS no ingress | O navegador exige aceite explícito; não equivale a certificado público |
| Tag de imagem sobrescrita no ECR | O deploy usa só a tag do SHA do commit; `latest` é informativa | Tags mutáveis de propósito para permitir reexecutar o CD no mesmo commit ([ADR-012](adr/ADR-012-aws-eks.md)) |
| Credenciais compartilhadas | Schemas separados e um Secret Kubernetes por serviço | Evoluir para roles PostgreSQL e identidade AWS de menor privilégio por serviço |
| Expiração da sessão do laboratório | Scripts de renovação e workflows idempotentes | Revalidar credenciais antes de provisionar ou destruir |

## 2. Como a resiliência é verificada

Três scripts versionados exercitam os mecanismos contra o ambiente implantado:

| Script | Quando roda | O que prova |
|---|---|---|
| [`aws-e2e-smoke.sh`](../../scripts/aws-e2e-smoke.sh) | Fim de todo deploy (`cd-aws.yml`) | Upload real, ZIP válido, falha rastreável e **e-mail na caixa de entrada** (IMAP) |
| [`aws-demo-concurrency.sh`](../../scripts/aws-demo-concurrency.sh) | Sob demanda, na demonstração | Ciclo de escala completo: sobe acima do mínimo, 2 vídeos simultâneos, todos concluídos, volta ao mínimo |
| [`aws-resilience-test.sh`](../../scripts/aws-resilience-test.sh) | Workflow manual `resilience-aws.yml` | Pico com 429, crash forçado do worker e SQS bloqueado por policy |

Os scripts falham quando não conseguem **comprovar** o que anunciam: um crash que não interrompeu nada sai como `INCONCLUSIVO`, e um pico sem nenhum 429 reprova o cenário.

## 3. O que os testes revelaram

A primeira execução completa, em 27/09/2026, encontrou três problemas que os testes unitários e de integração não pegavam, porque dependiam do comportamento real do Kubernetes, do SQS e do NLB.

| Problema observado | Causa | Correção |
|---|---|---|
| No scale-down, um vídeo foi refeito do zero (+5 min) | O pod recebia SIGTERM, o consumidor interrompia o `ffmpeg` e o SIGKILL chegava em 30 s | Desligamento gracioso e `terminationGracePeriodSeconds` de 1.020 s ([ADR-010](adr/ADR-010-kubernetes-sem-service-mesh.md)) |
| Crash do worker levou ~18 min para recuperar | O primeiro heartbeat só vinha após 30 s; antes disso valia o padrão da fila (960 s) | Heartbeat imediato ao receber a mensagem ([ADR-003](adr/ADR-003-entrega-e-idempotencia.md)) |
| 25 uploads de um mesmo IP, nenhum 429 | Com `externalTrafficPolicy=Cluster`, o gateway via o IP do nó: todos os clientes dividiam duas cotas | `externalTrafficPolicy=Local` no ingress ([ADR-009](adr/ADR-009-api-gateway.md)) |

![o que os testes revelaram.svg](img/o%20que%20os%20testes%20revelaram.svg)

*"Demo": dois vídeos processados em paralelo, do upload à conclusão. "Crash": do crash forçado do worker até o vídeo concluído pelo pod substituto (antes das correções, ~18 min, aproximados para 1.080 s).*

| Cenário | Antes | Depois |
|---|---|---|
| Demo de concorrência (2 vídeos) | 577 s, um vídeo reprocessado | 279 s, nenhum reprocessado |
| Recuperação após crash do worker | ~18 min | 161 s |
| Pico de 25 uploads de um IP | 25 × 201, 0 × 429 | 20 × 201, 5 × 429 |
| Pod ocupado recebendo SIGTERM | encerrado em 30 s, vídeo perdido na réplica | terminou o vídeo (3 min 29 s) e saiu |

## 4. Operação e evidências

- **Onde olhar:** *Autoscaling e borda* (fila, KEDA, réplicas, 429), *Saúde dos serviços* (reinícios, erros, latência), *Serviços AWS* (idade da mensagem mais antiga, DLQs) e *Logs da aplicação* (seguir um `correlationId`). Ver [observabilidade no HLD](03-hld.md#6-observabilidade).
- **DLQ com mensagens:** o consumidor da DLQ de processamento leva o vídeo a um estado terminal — `COMPLETED` se o ZIP já existir, senão `FAILED` com motivo; o replay para a fila principal é seguro porque o worker reaproveita ZIP existente e a notificação reivindica o envio antes de chamar o canal.
- **Registro:** cada execução gera log (artefato do workflow ou `.log` local). Associe o log ao SHA implantado ao anexá-lo como evidência.

[← LLD](04-lld.md) · [Arquitetura](README.md) · [ADRs →](adr/README.md)
