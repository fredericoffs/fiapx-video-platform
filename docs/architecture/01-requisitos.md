# Requisitos, rastreabilidade e evidências

[← Arquitetura](README.md) · [RFC →](02-rfc.md)

Este documento liga cada requisito do [enunciado](../enunciado.md) à implementação e à forma de comprová-lo. A coluna "Evidência" aponta o que foi efetivamente executado no ambiente AWS, não apenas o que existe no repositório.

## Requisitos funcionais

| ID | Requisito | Implementação | Evidência |
|---|---|---|---|
| RF1 | Processar mais de um vídeo ao mesmo tempo | Cada réplica do `video-worker` processa um vídeo por vez; o KEDA escala de 1 a 3 réplicas com meta de 1 mensagem por réplica ([ADR-010](adr/ADR-010-kubernetes-sem-service-mesh.md)) | Demo de concorrência: 2 vídeos em `PROCESSING` simultâneos, ciclo 1→3→2→1 réplicas ([detalhes](#evidências-de-execução)) |
| RF2 | Não perder requisições em picos | Upload aceito grava vídeo + outbox na mesma transação; SQS com retentativas e DLQ; rate limit explícito na borda ([ADR-003](adr/ADR-003-entrega-e-idempotencia.md)) | Resiliência `burst` e `sqs`: nenhum upload aceito se perdeu, nenhum recusado foi persistido |
| RF3 | Acesso protegido por usuário e senha | BCrypt, JWT com revogação por data e verificação de propriedade na API ([ADR-005](adr/ADR-005-autenticacao.md)) | Testes de integração de autenticação e isolamento entre usuários |
| RF4 | Listagem de status por usuário | Consulta paginada no PostgreSQL, escopada pelo JWT, sem cache ([ADR-013](adr/ADR-013-sem-cqrs.md)) | Testes de autorização; listagem usada por todos os scripts de validação |
| RF5 | Aviso ao usuário em caso de erro | E-mail ao usuário; webhook opcional alerta a operação sem dados pessoais e não encerra as retentativas do e-mail ([ADR-011](adr/ADR-011-notificacao.md)) | E2E a cada deploy: arquivo inválido → `FAILED` → e-mail **encontrado na caixa de entrada** via IMAP |

## Requisitos técnicos

| ID | Requisito | Implementação | Evidência |
|---|---|---|---|
| RT1 | Persistência dos dados | RDS PostgreSQL (schemas por serviço, migrações Flyway) e S3 privado ([ADR-004](adr/ADR-004-armazenamento.md), [ADR-014](adr/ADR-014-servicos-gerenciados.md)) | Upload, download e conferência do ZIP no E2E |
| RT2 | Arquitetura escalável | HPA no `video-api` (CPU 70%) e KEDA no `video-worker` (profundidade da fila) | Réplicas observadas na demo e no dashboard *Autoscaling e borda* |
| RT3 | Projeto versionado no GitHub | [Repositório](https://github.com/fredericoffs/fiapx-video-platform), `main` protegida, trabalho em `develop` | Histórico e pull requests |
| RT4 | Testes que garantam a qualidade | Testes unitários, de integração (Testcontainers, LocalStack) e de arquitetura (ArchUnit); gate JaCoCo ≥ 90% de linhas | Relatórios de cobertura do CI por serviço |
| RT5 | CI/CD | CI por serviço com filtro de caminhos; CD exige o CI aprovado do mesmo SHA e termina com E2E ([ADR-012](adr/ADR-012-aws-eks.md)) | Runs de CI + CD + E2E no GitHub Actions |

## Entregáveis

| Entregável | Onde |
|---|---|
| Documentação da arquitetura | Esta pasta ([índice](README.md)) e artefatos de [DDD](../ddd/context-map.md) |
| Script de criação do banco | Migrações Flyway de [`video-api`](../../video-api/src/main/resources/db/migration/) e [`notification-worker`](../../notification-worker/src/main/resources/db/migration/) |
| Infraestrutura como código | [Terraform](../../k8s/terraform/aws/) e [manifests Kubernetes](../../k8s/apps/base/) |
| Código-fonte | [Repositório](https://github.com/fredericoffs/fiapx-video-platform) |
| Vídeo de até 10 minutos | Link informado no [README principal](../../README.md#estado-da-entrega) |

## Semântica das respostas

Duas respostas HTTP merecem atenção porque definem o contrato de "não perder requisições":

- **`201 Created`** confirma que o vídeo foi **persistido** e que o evento de processamento está na outbox. Não confirma que o processamento terminou; o status evolui de forma assíncrona (`QUEUED` → `PROCESSING` → `COMPLETED`/`FAILED`).
- **`429 Too Many Requests`** é uma **recusa explícita** do rate limit da borda (20 requisições por minuto por IP do cliente, família de rota e método). Um 429 nunca vira vídeo persistido; o cliente sabe que precisa tentar de novo.

O sistema não promete aceitar carga ilimitada: promete que tudo o que foi aceito termina em sucesso ou em falha rastreável.

## Evidências de execução

Execuções no ambiente AWS (Learner Lab) em **27/09/2026**. A primeira rodada revelou três problemas reais; as correções foram implantadas e a segunda rodada confirmou o resultado.

| Verificação | 1ª rodada | 2ª rodada (após correções) |
|---|---|---|
| E2E no deploy (upload, ZIP, falha, e-mail na INBOX) | ✅ | ✅ |
| Demo de concorrência | ✅ ciclo completo, mas 577 s e um vídeo reprocessado do zero | ✅ **279 s**, nenhum vídeo refeito |
| Pod ocupado recebendo SIGTERM (scale-down) | ❌ encerrado em 30 s, vídeo refeito em outro pod | ✅ pod terminou o vídeo em 3 min 29 s e só então saiu |
| Resiliência `burst` (25 uploads de um IP) | ⚠️ 25 aceitos, nenhum 429: rate limit contava por nó | ✅ **20 aceitos + 5 × 429**, só os aceitos persistidos |
| Resiliência `worker` (crash forçado) | ❌ recuperação em ~18 min | ✅ **161 s**, concluído pelo pod substituto |
| Resiliência `sqs` (fila bloqueada por policy) | ✅ | ✅ |

As causas e as correções estão em [Riscos e resiliência](05-riscos-e-resiliencia.md#3-o-que-os-testes-revelaram) e nos históricos do [ADR-003](adr/ADR-003-entrega-e-idempotencia.md), [ADR-009](adr/ADR-009-api-gateway.md) e [ADR-010](adr/ADR-010-kubernetes-sem-service-mesh.md).

### Alertas por e-mail (28/09/2026)

Com o `notification-worker` escalado a 0 réplicas, o alerta `FiapxServiceDown` (critical) percorreu o ciclo completo e chegou por e-mail no endereço `+alertas` e pelo webhook, sem nenhuma falha de envio:

| UTC | Evento |
|---|---|
| 23:20:50 | alerta pendente (0 réplicas prontas detectadas) |
| 23:22:38 | alerta disparando, após os 2 min do `for` |
| 23:23:32 | e-mail e webhook de disparo enviados (e-mail confirmado na caixa de entrada) |
| 23:26:16 | réplica pronta de novo |
| 23:28:32 | e-mail e webhook de resolvido enviados |

No mesmo ambiente, `Watchdog`, `InfoInhibitor` e os alertas `info` estavam ativos e foram para o receiver `null`, sem virar e-mail. A configuração está no [ADR-006](adr/ADR-006-observabilidade.md).

[← Arquitetura](README.md) · [RFC →](02-rfc.md)
