# FIAP X — Plataforma de Processamento de Vídeos

[![CI](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml)
![Arquitetura](https://img.shields.io/badge/arquitetura-hexagonal%20%2F%20ArchUnit-informational)

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![Docker](https://img.shields.io/badge/Docker-multi--stage-2496ED?logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-HPA%20%2B%20KEDA-326CE5?logo=kubernetes&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-EKS%20%2B%20SQS%20%2B%20RDS%20%2B%20S3-FF9900?logo=amazonwebservices&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-banco%20por%20serviço-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-rate%20limiting-DC382D?logo=redis&logoColor=white)
![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker%20%2B%20Bulkhead-blueviolet)

Construí esta arquitetura de processamento de vídeos para o Hackathon da Fase 5. Guardo a documentação de arquitetura (requisitos, RFC, HLD, LLD, riscos, ADRs e artefatos de DDD) em [`docs/`](./docs) — o ponto de partida é o [índice da arquitetura](./docs/architecture/README.md). Este README apresenta o produto e o caminho de execução.

## Requisitos do desafio

| Requisito (enunciado do Hackathon)            | Implementação | Onde                                                                                                                                                                                                                                                                                        |
|-----------------------------------------------|:-------------:|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Processamento concorrente/enfileirado         |   Presente    | Fila (Amazon SQS) + `video-worker` escalado por KEDA — sem requisição travada esperando o `ffmpeg`                                                                                                                                                                                          |
| Autenticação usuário/senha                    |   Presente    | `video-api` (JWT) — `/auth/register`, `/auth/login`                                                                                                                                                                                                                                         |
| Listagem de status por usuário                |   Presente    | `video-api` `/videos` (escopado por JWT, um usuário nunca vê vídeo de outro)                                                                                                                                                                                                                |
| Notificação de erro (e-mail ou similar)       |   Presente    | `notification-worker` — e-mail ao usuário + alerta operacional opcional por webhook; Circuit Breaker e Bulkhead por canal                                                                                                                                                                   |
| Armazenamento persistente                     |   Presente    | RDS PostgreSQL (schema próprio por serviço) + Amazon S3 (object storage) para vídeos/zips                                                                                                                                                                                                   |
| Arquitetura horizontalmente escalável         |   Presente    | HPA (`video-api`) + KEDA (`video-worker`), serviços stateless sem sessão em memória                                                                                                                                                                                                         |
| Testes automatizados e CI/CD                  |   Presente    | JaCoCo ≥90% linha (gate no `mvn verify`) + GitHub Actions (CI) a cada push/PR                                                                                                                                                                                                               |
| Docker / Kubernetes                           |   Presente    | Dockerfile multi-stage por serviço + manifests em [`k8s/`](./k8s) e Terraform (`k8s/terraform/aws/`) para EKS — provisionamento e deploy versionados                                                                                                                                        |
| Message broker                                |   Presente    | Amazon SQS com redrive policy para DLQ nas 3 filas — ver [ADR-002](./docs/architecture/adr/ADR-002-broker.md)                                                                                                                                                                               |
| Postgres + Redis                              |   Presente    | Schemas próprios na mesma instância RDS PostgreSQL; ElastiCache Redis no rate limiting de borda (`video-gateway`) e no limite de tentativas de login (`video-api`)                                                                                                                                                                           |
| Monitoramento (Prometheus/Grafana, ELK, etc.) |   Presente    | Prometheus, Grafana e Alertmanager (`kube-prometheus-stack`), logs no Loki e métricas AWS pelo CloudWatch; 8 dashboards (negócio, saúde, autoscaling, logs, serviços AWS e alertas), 9 alertas próprios entregues por e-mail e webhook, e correlation-id ponta a ponta — ver [observabilidade](./docs/architecture/03-hld.md#6-observabilidade) |

## Arquitetura em uma frase

Upload de vídeo → fila (Amazon SQS) → extração de frames (`ffmpeg`, com timeout) → zip → notificação em caso de erro. Três serviços de negócio, um gateway e um frontend. API e notificação possuem schemas próprios na mesma instância RDS; a credencial de banco ainda é compartilhada.

| Serviço               | Responsabilidade                                                                                                                                            | Porta | Persistência                           |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|----------------------------------------|
| `video-gateway`       | API Gateway (roteamento, CORS, rate limiting de borda) — ver [ADR-009](./docs/architecture/adr/ADR-009-api-gateway.md)                                      | 8080  | nenhum                                 |
| `video-api`           | Upload, autenticação (JWT), listagem/consulta de status, download                                                                                           | 8081  | Postgres, schema `video_api`           |
| `video-worker`        | Consome a fila, roda `ffmpeg`, gera o `.zip` — **stateless**, sem acesso a banco (ver [ADR-008](./docs/architecture/adr/ADR-008-status-por-evento.md))      | 8082  | nenhum                                 |
| `notification-worker` | Consome eventos de falha, envia e-mail ao usuário e alerta operacional opcional — Circuit Breaker + Bulkhead por canal; webhook não encerra retry de e-mail | 8083  | Postgres, schema `notification_worker` |

Serviços gerenciados AWS: RDS PostgreSQL, Amazon SQS, ElastiCache Redis e Amazon S3 — sem banco, broker ou object storage da aplicação hospedados em pods (ver [ADR-014](./docs/architecture/adr/ADR-014-servicos-gerenciados.md)). Diagramas completos no [HLD](./docs/architecture/03-hld.md) e no [LLD](./docs/architecture/04-lld.md).

## Requisitos

- Java 21 (`.sdkmanrc` em cada serviço — `sdk env` se usar [SDKMAN!](https://sdkman.io/)) e Maven (ou o `./mvnw` wrapper de cada serviço), para build
- Node 22, para build/testes do `web`
- `aws` CLI, `terraform`, `kubectl`, `kustomize` e `helm`, para o deploy na AWS (ver [Deploy na AWS](#deploy-na-aws-eks) abaixo)

A stack só roda de fato em AWS EKS — não há caminho de execução local (ver [ADR-012](./docs/architecture/adr/ADR-012-aws-eks.md)).

## Build e testes

Fiz cada serviço como um projeto Maven independente (não um multi-módulo reactor) de propósito, para que qualquer um possa ser extraído para um repositório próprio no futuro sem alterar código.

```bash
(cd video-api && ./mvnw -B verify)
(cd video-worker && ./mvnw -B verify)
(cd notification-worker && ./mvnw -B verify)
(cd video-gateway && ./mvnw -B verify)
```

CI: o workflow `.github/workflows/ci.yml` possui jobs por serviço, selecionados por filtros de caminhos. API e frontend também se validam em conjunto para detectar divergência do contrato OpenAPI.

**Qualidade e cobertura**: cobertura de teste com piso de 90% (linha, JaCoCo) nos 4 serviços — gate no `mvn verify` (`jacoco:check`), com exclusões definidas nos POMs (como as classes de inicialização `*Application`): `ffmpeg`, ZIP e cliente S3 entram na conta. Relatório publicado como artefato do CI. Os testes de integração usam Testcontainers (Postgres) e LocalStack 4.0 (S3 e SQS; a tag `latest` do LocalStack exige licença), então precisam de Docker rodando no runner/máquina que roda `mvn verify`. O CI também tem o job `infra-lint` (shellcheck, actionlint, `terraform validate`, render dos manifests em `k8s/apps/base`) quando `k8s/`, `scripts/` ou os workflows mudam.

**Análise de segurança e qualidade (sob demanda)**: dois workflows manuais (`workflow_dispatch`, aba Actions), fora da esteira de cada push para não atrasá-la. `Security scan` roda OWASP Dependency-Check nas dependências Maven dos 4 serviços, `npm audit` no `web` e Trivy nas 5 imagens Docker, publicando os relatórios (HTML e SARIF) como artefatos do run. `Qodana` roda a análise estática JetBrains nos projetos JVM e web, com cobertura JaCoCo/lcov anexada.

**Script de criação do banco de dados**: migrations Flyway versionadas por serviço — [`video-api/src/main/resources/db/migration`](./video-api/src/main/resources/db/migration) (usuários, vídeos, outbox, revogação, atualização segura do admin e limpeza de storage; consulte todos os arquivos versionados) e [`notification-worker/src/main/resources/db/migration`](./notification-worker/src/main/resources/db/migration) (tentativas de notificação e índice de claim por vídeo/canal). Cada serviço migra só o próprio schema; no Kubernetes a migração roda num Job antes do Deployment do `video-api`, contra o RDS com `sslmode=require`. Buckets S3, filas SQS, RDS e ElastiCache são criados pelo Terraform (`k8s/terraform/aws/`).

**Documentação de arquitetura**: organizada por assunto em [`docs/architecture/`](./docs/architecture/README.md) — [requisitos e evidências](./docs/architecture/01-requisitos.md), [RFC](./docs/architecture/02-rfc.md), [HLD](./docs/architecture/03-hld.md), [LLD](./docs/architecture/04-lld.md)com diagramas ER por schema, [riscos e resiliência](./docs/architecture/05-riscos-e-resiliencia.md) e [14 ADRs](./docs/architecture/adr/README.md), um por arquivo, com histórico de revisões — além do RFC curto [`RFC-002`](./docs/architecture/rfc/RFC-002-spring-cloud-gateway-vs-kong.md) (Spring Cloud Gateway vs Kong). Índice completo em [`docs/README.md`](./docs/README.md).

**Documentação da API**: o `video-api` expõe Swagger UI em `/swagger-ui.html` e contrato em `/v3/api-docs`. O ingresso público não publica essas rotas; use acesso interno ou port-forward para consultá-las. Postman collection correspondente versionada em [`docs/postman/fiapx-video-api.postman_collection.json`](./docs/postman/fiapx-video-api.postman_collection.json).

## Concorrência: virtual threads

Habilito `spring.threads.virtual.enabled=true` nos quatro serviços. Virtual threads reduzem o custo de espera por I/O; a capacidade de extração continua limitada pela CPU e pelo número de workers. Conexões de banco, tamanho dos uploads e cotas também limitam concorrência.

## Fluxo de branches

Faço todo trabalho em `develop`. Mantenho a `main` protegida e ela só recebe código via Pull Request — nunca commit direto (inclusive de quem administra o repositório).

## Estado da entrega

Os componentes e scripts estão implementados. A tabela acima indica presença no repositório, não aprovação automática no ambiente AWS. Para comprovar a entrega, registre as execuções do CI, do E2E após deploy, da demonstração de concorrência e do teste de resiliência com o SHA correspondente.

As execuções de 27/09/2026 — E2E, demonstração de concorrência e testes de resiliência, antes e depois das correções que elas motivaram — estão resumidas em [Requisitos e evidências](./docs/architecture/01-requisitos.md#evidências-de-execução). O link da gravação ainda precisa ser informado; a documentação não presume que uma execução ocorreu apenas porque o script existe.

## Deploy na AWS (EKS)

Tudo roda pelo GitHub Actions, no Environment `AWS` (Settings → Environments → **AWS**). Antes do primeiro deploy, configure:

| Nome no GitHub                                                                        | Tipo                        | Obrigatório? | Para quê                                                                                                                                                                                                                                                                                  |
|---------------------------------------------------------------------------------------|-----------------------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`                     | secret                      | **sim**      | Credenciais do Learner Lab. Expiram a cada sessão; renove com `scripts/aws-sync-gh-secrets.sh` (abaixo).                                                                                                                                                                                  |
| `PROD_ALERTMANAGER_WEBHOOK_URL`                                                       | secret                      | **sim**      | Receptor webhook dos alertas do Alertmanager (ex.: fila de alta profundidade). Sem ele o deploy para em `k8s-deploy-aws.sh`. Com o SMTP configurado, os alertas também chegam por e-mail em `<remetente>+alertas@...`.                                                                                                                      |
| `PROD_SMTP_HOST` + `PROD_SMTP_USER` + `PROD_SMTP_PASSWORD` + `PROD_NOTIFICATION_FROM` | var + secret + secret + var | **sim**      | E-mail das notificações de falha (porta 587, STARTTLS), o único canal que chega ao usuário. Sem SMTP real, o deploy para: `configure SMTP_HOST real (PROD_SMTP_HOST) antes de publicar`. O teste E2E usa as mesmas credenciais pra conferir a chegada por IMAP.                           |
| `PROD_NOTIFICATION_WEBHOOK_URL`                                                       | secret                      | não          | Alerta operacional quando o e-mail falha: avisa a equipe, não o usuário. O payload leva só `videoId` e `errorMessage`, sem o e-mail do usuário (minimização, LGPD); o dono é localizado pelo `videoId` em `/admin/videos`. O Terraform grava em `/fiapx/notification/webhook-url` no SSM. |
| `PROD_DB_PASSWORD`, `PROD_JWT_SECRET`, `PROD_ADMIN_PASSWORD`                          | secret                      | não          | Senha do RDS, segredo dos JWTs e senha do admin semeado (`admin@fiapx.local`). Sem eles o Terraform gera valores aleatórios (`random_password`, estáveis no state).                                                                                                                       |

**Como configurar os receptores.** Use receptores que interpretem os respectivos contratos JSON: Alertmanager e alerta operacional da aplicação têm payloads diferentes. Um coletor HTTP pode servir para inspecionar a demonstração; integração com um canal de mensagens pode exigir um adaptador. Um HTTP 2xx do coletor não comprova que uma pessoa recebeu o alerta. No e-mail com Gmail, use `PROD_SMTP_HOST=smtp.gmail.com`, a própria conta em`PROD_SMTP_USER` e `PROD_NOTIFICATION_FROM`, e em `PROD_SMTP_PASSWORD` uma **App Password** (myaccount.google.com/apppasswords, exige verificação em duas etapas), não a senha normal da conta. Pela linha de comando:

```bash
gh secret set PROD_ALERTMANAGER_WEBHOOK_URL --env AWS --body 'https://webhook.site/<uuid-1>'
gh secret set PROD_NOTIFICATION_WEBHOOK_URL --env AWS --body 'https://webhook.site/<uuid-2>'
gh variable set PROD_SMTP_HOST --env AWS --body 'smtp.gmail.com'
gh variable set PROD_NOTIFICATION_FROM --env AWS --body 'sua-conta@gmail.com'
gh secret set PROD_SMTP_USER --env AWS --body 'sua-conta@gmail.com'
gh secret set PROD_SMTP_PASSWORD --env AWS   # cola a App Password quando pedir
```

`PROD_NOTIFICATION_WEBHOOK_URL` passa pelo Terraform e só chega ao cluster depois de um `apply`. Se você criar esse secret com a infra já no ar, rode o `CD - AWS EKS` sem `provision: skip`.

Os segredos da aplicação ficam no **SSM Parameter Store** (`/fiapx/db/username`, `/fiapx/db/password`, `/fiapx/jwt/secret`, `/fiapx/admin/password`,`/fiapx/notification/webhook-url`), criados pelo Terraform a partir dos `PROD_*` acima ou gerados por ele. O deploy lê os parâmetros por nome e monta um Secret por serviço (`video-api-secrets`, `notification-worker-secrets` — cada um só com as chaves que aquele serviço usa; `video-worker` e`video-gateway` não precisam de nenhum). As credenciais SMTP e o webhook do Alertmanager não passam pelo SSM: o job `deploy` recebe esses valores direto do Environment. Um push em `main` (ou o `CD - AWS EKS` manual) faz tudo sozinho: provisiona o que faltar, builda e faz o deploy. Os workflows:

1. `CD - AWS EKS` (`cd-aws.yml`, a cada push em `main` ou manual) — job `provision`: `scripts/aws-up.sh --apply-if-changed` roda `terraform plan`; com a infra no ar e igual ao código é um no-op de ~2 min, senão aplica o plano (~25 min na primeira vez) — VPC, cluster EKS (`t3.large` ×2, add-on EBS CSI), 5 repositórios ECR, RDS PostgreSQL 17 (`db.t3.micro`), ElastiCache Redis 7.1 (`cache.t3.micro`), 3 filas SQS com DLQ e os parâmetros SSM. Os 2 buckets S3 privados são criados antes do `plan` por `scripts/aws-buckets-init.sh` (a SCP do Learner Lab nega`s3:GetBucketObjectLockConfiguration`, que o provider AWS chama ao ler um `aws_s3_bucket`). State no bucket S3 `fiapx-terraform-state-<account>`, criado automaticamente por `scripts/aws-tf-init.sh`.
2. Ainda no `CD - AWS EKS`, jobs `build-and-push` e `deploy` — builda as 5 imagens, publica no ECR e roda `scripts/k8s-deploy-aws.sh`: descobre RDS, ElastiCache, fila SQS e buckets por nome via `aws` CLI, injeta os hosts no ConfigMap, instala add-ons (ingress-nginx, cert-manager, metrics-server, KEDA, kube-prometheus-stack, Loki e Alloy) e aplica os manifests (`k8s/apps/base`) depois de rodar a migração. Nenhum componente stateful sobe no cluster. A URL pública (hostname do ELB do `ingress-nginx`) sai no resumo do job. Por último, o job roda `scripts/aws-e2e-smoke.sh` pela URL pública (detalhes abaixo); se o fluxo de negócio quebrar, o deploy fica vermelho mesmo com os rollouts prontos. No disparo manual, o input `provision: skip` pula o Terraform e só faz build e deploy. `Terraform - AWS EKS` (`terraform-aws.yml`) continua disponível para rodar `plan`, `apply` ou `destroy` isolados à mão; todos os workflows AWS (inclusive o `Resiliência - AWS EKS`, abaixo) compartilham o grupo de concorrência `fiapx-aws-lifecycle`, então nunca rodam ao mesmo tempo.
3. `Destroy AWS` (`destroy-aws.yml`) — ao fim de cada sessão: `scripts/aws-destroy.sh` (limpeza k8s → `terraform destroy` → varredura via `aws` CLI independente do state, incluindo RDS, ElastiCache, filas `fiapx-*`, buckets `fiapx-videos-*` e parâmetros SSM `/fiapx/*` → `scripts/aws-validate.sh --strict`).

Os mesmos scripts funcionam localmente com `aws`, `terraform`, `kubectl`, `kustomize` e `helm` instalados (`scripts/aws-up.sh`, `scripts/aws-validate.sh`). Para renovar os 3 secrets `AWS_*` a cada sessão do lab, copie o bloco de **AWS Details → AWS CLI → Show** e rode:

```bash
pbpaste | ./scripts/aws-sync-gh-secrets.sh --from-stdin --save-profile
```

O script valida as credenciais (`aws sts get-caller-identity`), grava o perfil `default` em `~/.aws/credentials` e atualiza os secrets no Environment `AWS` via `gh`.

### Teste de ponta a ponta no ambiente implantado

`scripts/aws-e2e-smoke.sh` roda no fim de todo deploy e também pode ser executado localmente, com o `kubectl` apontando pro cluster. Nada é simulado:

1. Cadastra um usuário novo e faz login.
2. Faz upload real de `web/e2e/fixtures/sample.mp4` e espera `COMPLETED` (o `ffmpeg` roda de verdade no `video-worker`).
3. Baixa o zip e confere que ele abre e tem frames `frame_NNNN.png` com assinatura PNG válida.
4. Faz upload de um arquivo com extensão `.mp4` que não é vídeo e espera `FAILED` com `errorMessage`.
5. Confere no log do `notification-worker` que a notificação de falha daquele vídeo saiu pelo canal `EMAIL` (aguarda o e-mail mesmo quando um alerta operacional por webhook ocorre antes).
6. Entra por IMAP na caixa de entrada do destinatário e exige a mensagem com o `videoId`. Isso prova a chegada, não só o envio: a busca é só na INBOX, nunca em Enviados.

O destinatário da notificação é um plus-address de `PROD_NOTIFICATION_FROM` (`conta+e2e-<ts>@gmail.com`), então o e-mail cai na própria caixa da conta remetente, e a mesma App Password do SMTP abre essa caixa por IMAP (`imap.gmail.com`, derivado de `PROD_SMTP_HOST`). Rodando local sem `SMTP_USER`/`SMTP_PASSWORD` no ambiente, o passo 6 é pulado com aviso. Cada execução deixa um usuário `e2e-*` e dois vídeos no banco. Os testes de integração do Maven cobrem os adapters e fluxos por serviço com Testcontainers/LocalStack; `EndToEndVideoProcessingFlowIntegrationTest` encadeia a API com resultado de worker simulado, e o teste de navegador (`web/e2e`) também pode rodar contra o ambiente com `E2E_BASE_URL=https://<host> npm run test:e2e`.

### Teste de resiliência: picos, crash do worker e SQS fora do ar

`scripts/aws-resilience-test.sh` exercita de propósito os mecanismos de confiabilidade contra o ambiente implantado. Roda local (com `kubectl` e `aws`apontando pro ambiente) ou pelo workflow manual `Resiliência - AWS EKS` (`resilience-aws.yml`), que guarda o log como artefato do run. Não roda a cada deploy porque mata pods e altera a fila por alguns minutos. São três cenários:

| Cenário  | O que faz                                                                                                                                                            | Passa quando                                                                                                                                                                                                                                                                                                                                                                 |
|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `burst`  | 25 uploads simultâneos de um mesmo IP; a cota do gateway é 20 `POST /videos` por minuto                                                                              | Cada resposta é `201` (aceito) ou `429` (rejeitado), sem outro código; `totalElements` do usuário é igual ao número de aceitos (nenhum 429 virou vídeo, nenhum 201 sumiu); todos os aceitos terminam, e todo `FAILED` tem `errorMessage`                                                                                                                                     |
| `worker` | Gera um vídeo de 600s, espera **esse** vídeo aparecer como `PROCESSING` na API e mata à força (`--grace-period=0 --force`) os pods do `video-worker` daquele momento | O `delete` funcionou, os pods antigos sumiram e há pelo menos um pod novo pronto; o vídeo termina `COMPLETED` depois da reentrega, concluído por um pod que não estava entre os derrubados (o heartbeat imediato deixa a mensagem com visibilidade de 120s, e o lease de 90s no S3 expira antes). Se o vídeo já estava pronto antes do crash, o resultado sai `INCONCLUSIVO` |
| `sqs`    | Aplica na fila de processamento uma queue policy com `Deny` de `SendMessage`/`ReceiveMessage` pra todos, sem reiniciar nada, e faz 3 uploads                         | Durante a falha os 3 ficam `QUEUED` (persistidos; a outbox do `video-api` loga as tentativas); depois de restaurar a policy original, os 3 terminam `COMPLETED`                                                                                                                                                                                                              |

Um `trap` tenta restaurar a policy original ao encerrar o script; falhas de credenciais ou conexão podem exigir restauração manual. O bloqueio temporário não inclui `SetQueueAttributes`. Um vídeo que esgota as 3 tentativas vai pra DLQ, e o consumer da DLQ resolve o estado terminal: recupera sucesso se houver ZIP pronto ou publica `FAILED` com motivo. Essa é a "falha rastreável", e o cenário `burst` a aceita, desde que tenha `errorMessage`.

### Evidência de processamento simultâneo (KEDA)

Cada réplica do `video-worker` processa um vídeo por vez, e o `ScaledObject` usa `queueLength: 1` (uma mensagem por réplica, contando as que estão em processamento), com mínimo 1 e máximo 3 réplicas. Com o ambiente no ar e o `kubectl` apontando pro cluster:

```bash
./scripts/aws-demo-concurrency.sh --email demo@exemplo.com --password 'senha-da-demo' video1.mp4 video2.mp4
```

O script faz os uploads em paralelo pela URL pública e registra a cada 5s as réplicas do worker e o status de cada vídeo, até todos terminarem e o worker voltar a 1 réplica. No resumo aparecem o pico de réplicas, o pico de vídeos em `PROCESSING` ao mesmo tempo e o tempo até voltar a 1 réplica. A saída fica num `.log`. O script só sai com sucesso se o ciclo inteiro acontecer antes do timeout: o KEDA escalou acima do mínimo, houve 2 vídeos em `PROCESSING` juntos, todos terminaram `COMPLETED` e as réplicas voltaram ao mínimo. Se faltar qualquer um desses, ele diz qual. Use vídeos de 1 min ou mais, porque o segundo vídeo só começa depois que o KEDA lê a fila (a cada 15s) e o pod novo sobe.

Na redução, o pod removido não descarta trabalho: ao receber SIGTERM, para de buscar mensagens e termina o vídeo em andamento antes de sair (`terminationGracePeriodSeconds` de 1.020s, que cobre o timeout de 15 min do `ffmpeg`). Detalhes no [ADR-010](./docs/architecture/adr/ADR-010-kubernetes-sem-service-mesh.md).

### Observabilidade

Com o `kubectl` apontando pro cluster, `kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80` dá acesso ao Grafana (usuário `admin`; senha no Secret `kube-prometheus-stack-grafana`). Os dashboards ficam na tag `fiapx`: métricas de negócio, *Saúde dos serviços*, *Autoscaling e borda*, *Serviços AWS* (CloudWatch), *Logs da aplicação* (Loki) e *Alertas*. Os alertas do Alertmanager chegam por e-mail no endereço `+alertas` do remetente (`PROD_NOTIFICATION_FROM`, pelo mesmo SMTP) e no webhook de `PROD_ALERTMANAGER_WEBHOOK_URL`. Toda resposta da API traz o header `X-Correlation-Id`; buscá-lo em *Logs da aplicação* mostra o caminho do pedido por gateway, API, worker e notificação. Descrição completa na [seção de observabilidade do HLD](./docs/architecture/03-hld.md#6-observabilidade).
