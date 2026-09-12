# FIAP X — Plataforma de Processamento de Vídeos

[![CI](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml)
![Arquitetura](https://img.shields.io/badge/arquitetura-hexagonal%20%2F%20ArchUnit-informational)

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-HPA%20%2B%20KEDA-326CE5?logo=kubernetes&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-broker-FF6600?logo=rabbitmq&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-banco%20por%20serviço-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-rate%20limiting-DC382D?logo=redis&logoColor=white)
![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker%20%2B%20Bulkhead-blueviolet)

Construí esta arquitetura de processamento de vídeos para o Hackathon da Fase 5. Guardo a documentação de arquitetura (RFC, HLD, LLD, ADRs, artefatos de DDD) em [`docs/`](./docs) — este README cobre só o "como rodar".

## Requisitos do desafio

| Requisito (enunciado do Hackathon)             | Status | Onde                                                                                     |
|-------------------------------------------------|:------:|-------------------------------------------------------------------------------------------|
| Processamento concorrente/enfileirado           |   ✅   | Fila (RabbitMQ no local, Amazon SQS na AWS) + `video-worker` escalado por KEDA — sem requisição travada esperando o `ffmpeg` |
| Autenticação usuário/senha                      |   ✅   | `video-api` (JWT) — `/auth/register`, `/auth/login`                                       |
| Listagem de status por usuário                  |   ✅   | `video-api` `/videos` (escopado por JWT, um usuário nunca vê vídeo de outro)               |
| Notificação de erro (e-mail ou similar)         |   ✅   | `notification-worker` — e-mail com fallback para webhook, Circuit Breaker + Bulkhead por canal |
| Armazenamento persistente                       |   ✅   | Postgres (schema próprio por serviço; RDS na AWS) + object storage S3 (MinIO no local, Amazon S3 na AWS) para vídeos/zips |
| Arquitetura horizontalmente escalável           |   ✅   | HPA (`video-api`) + KEDA (`video-worker`), serviços stateless sem sessão em memória         |
| Testes automatizados e CI/CD                    |   ✅   | JaCoCo ≥90% linha (gate no `mvn verify`) + GitHub Actions (CI) a cada push/PR      |
| Docker / Kubernetes                             |   ✅   | `docker-compose.yml` (dev) + manifests em [`k8s/`](./k8s) (cluster kind validado ao vivo) + overlay `aws` e Terraform (`k8s/terraform/aws/`) para EKS |
| Message broker (RabbitMQ)                       |   ✅   | RabbitMQ com DLX/DLQ nas 3 filas (local); no perfil `aws`, Amazon SQS com redrive para DLQ — ver [RFC-003](./docs/architecture/rfc/RFC-003-sqs-vs-rabbitmq-no-perfil-aws.md) |
| Postgres + Redis                                |   ✅   | Postgres por serviço; Redis no rate limiting de borda (`video-gateway`) — RDS e ElastiCache na AWS |
| Monitoramento (Prometheus/Grafana, ELK, etc.)   |   ✅   | `kube-prometheus-stack` (Prometheus + Grafana + Alertmanager) via Helm, 3 dashboards + alerta de profundidade de fila, métricas de negócio e correlation-id ponta a ponta |

## Arquitetura em uma frase

Upload de vídeo → fila (RabbitMQ no local, SQS na AWS) → extração de frames (`ffmpeg`, com timeout) → zip → notificação em caso de erro. Quatro serviços independentes, sem banco compartilhado entre eles.

| Serviço               | Responsabilidade                                                                                                                                                        | Porta | Banco próprio                          |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|----------------------------------------|
| `video-gateway`       | API Gateway (roteamento, CORS, rate limiting de borda) — ver [ADR-009](./docs/architecture/hld-lld-adr-rfc.md#adr-009--api-gateway-spring-cloud-gateway-em-vez-de-kong) | 8080  | nenhum                                 |
| `video-api`           | Upload, autenticação (JWT), listagem/consulta de status, download                                                                                                       | 8081  | Postgres, schema `video_api`           |
| `video-worker`        | Consome a fila, roda `ffmpeg`, gera o `.zip` — **stateless**, sem acesso a banco (ver [ADR-008](./docs/architecture/hld-lld-adr-rfc.md#adr-008))                        | 8082  | nenhum                                 |
| `notification-worker` | Consome eventos de falha, envia e-mail com fallback para webhook — Circuit Breaker + Bulkhead isolados por canal (Resilience4j)                                        | 8083  | Postgres, schema `notification_worker` |

Infra local: PostgreSQL, RabbitMQ, Redis, MinIO (S3-compatible), Mailhog (SMTP local). Na AWS, o perfil Spring `aws` (`SPRING_PROFILES_ACTIVE=aws`) troca por RDS PostgreSQL, Amazon SQS, ElastiCache Redis e Amazon S3 sem mudar domínio nem casos de uso — ver [ADR-014](./docs/architecture/hld-lld-adr-rfc.md#adr-014--serviços-gerenciados-por-perfil-de-execução). Diagramas completos em [`docs/architecture/hld-lld-adr-rfc.md`](./docs/architecture/hld-lld-adr-rfc.md).

## Requisitos locais

- Java 21 (`.sdkmanrc` em cada serviço — `sdk env` se usar [SDKMAN!](https://sdkman.io/))
- Docker + Docker Compose
- Maven (ou use o `./mvnw` wrapper de cada serviço)

## Subindo o ambiente local

**1. Copie o `.env.example` para `.env`** (opcional — os defaults já funcionam para dev local):

```bash
cp .env.example .env
```

**2. Suba só a infraestrutura** (Postgres, RabbitMQ, Redis, MinIO, Mailhog) — útil para rodar os serviços Java direto da IDE:

```bash
docker compose up -d
```

UIs disponíveis: RabbitMQ management em `http://localhost:15672` (guest/guest), MinIO console em `http://localhost:9001` (minioadmin/minioadmin), Mailhog em `http://localhost:8025`.

**3. Rodando um serviço individualmente** (ex.: `video-api`), fora do Docker:

```bash
cd video-api
./mvnw spring-boot:run
```

**4. Ou suba tudo containerizado** (infra + os 4 serviços, cada um com seu Dockerfile multi-stage):

```bash
docker compose --profile app up --build
```

**5. Confirmar que subiu:**

```bash
curl http://localhost:8080/actuator/health   # video-gateway
curl http://localhost:8081/actuator/health   # video-api
curl http://localhost:8082/actuator/health   # video-worker
curl http://localhost:8083/actuator/health   # notification-worker
```

Cada um deve responder `{"status":"UP"}`. A partir da Sprint 2, o fluxo de autenticação/upload pode ser exercitado tanto direto no `video-api` (`:8081`) quanto via `video-gateway` (`:8080`) — ambos devem responder de forma equivalente.

## Build e testes

Fiz cada serviço como um projeto Maven independente (não um multi-módulo reactor) de propósito, para que qualquer um possa ser extraído para um repositório próprio no futuro sem alterar código.

```bash
cd video-api && ./mvnw -B verify
cd video-worker && ./mvnw -B verify
cd notification-worker && ./mvnw -B verify
cd video-gateway && ./mvnw -B verify
```

CI: dei a cada serviço seu próprio workflow em `.github/workflows/`, disparado só quando arquivos daquele serviço mudam (`paths:` filter) — simulo assim um pipeline independente por microsserviço mesmo dentro do monorepo.

**Qualidade e cobertura**: cobertura de teste com piso de 90% (linha, JaCoCo) nos 4 serviços — gate no `mvn verify` (`jacoco:check`), sem exclusões de classes: `ffmpeg`, zip e cliente S3 entram na conta. Relatório publicado como artefato do CI. Os testes de integração usam Testcontainers (Postgres, RabbitMQ) e LocalStack 4.0 (S3 e SQS; a tag `latest` do LocalStack exige licença), então precisam de Docker rodando. O CI também tem o job `infra-lint` (shellcheck, actionlint, `terraform validate`, render dos overlays) quando `k8s/`, `scripts/` ou os workflows mudam.

**Análise de segurança e qualidade (sob demanda)**: dois workflows manuais (`workflow_dispatch`, aba Actions), fora da esteira de cada push para não atrasá-la. `Security scan` roda OWASP Dependency-Check nas dependências Maven dos 4 serviços, `npm audit` no `web` e Trivy nas 5 imagens Docker, publicando os relatórios (HTML e SARIF) como artefatos do run. `Qodana` roda a análise estática JetBrains nos projetos JVM e web, com cobertura JaCoCo/lcov anexada.

**Script de criação do banco de dados**: migrations Flyway versionadas por serviço — [`video-api/src/main/resources/db/migration`](./video-api/src/main/resources/db/migration) (`V1__init.sql` a `V5__outbox_lease.sql`: vídeos, outbox, usuários, papel admin, correlation-id, lease da outbox) e [`notification-worker/src/main/resources/db/migration`](./notification-worker/src/main/resources/db/migration) (`V1__init.sql` e `V2__notification_attempts_unique_sent.sql`: registro de notificações e índice único de envio). Cada serviço migra só o próprio schema; no Kubernetes a migração roda num Job antes do Deployment do `video-api` (na AWS, contra o RDS com `sslmode=require`). Os buckets do MinIO são criados por `minio-init` (Compose) ou pelo Job `minio-init` (`k8s/infra/minio/init-job.yaml`); na AWS, buckets, filas, RDS e ElastiCache são criados pelo Terraform.

**Documentação de arquitetura**: [`docs/architecture/hld-lld-adr-rfc.md`](./docs/architecture/hld-lld-adr-rfc.md) (RFC, HLD, LLD com diagramas ER por schema, 14 ADRs) e os RFCs curtos [`RFC-001`](./docs/architecture/rfc/RFC-001-rabbitmq-vs-kafka.md) (RabbitMQ vs Kafka), [`RFC-002`](./docs/architecture/rfc/RFC-002-spring-cloud-gateway-vs-kong.md) (Spring Cloud Gateway vs Kong) e [`RFC-003`](./docs/architecture/rfc/RFC-003-sqs-vs-rabbitmq-no-perfil-aws.md) (SQS vs RabbitMQ no perfil `aws`). Índice completo em [`docs/README.md`](./docs/README.md).

**Documentação da API**: cada serviço expõe Swagger UI em `/swagger-ui.html` (OpenAPI 3.1 em `/v3/api-docs`, via [springdoc-openapi](https://springdoc.org/)) — mais relevante no `video-api`, que tem os endpoints de negócio (`/auth/**`, `/videos/**`). Postman collection correspondente versionada em [`docs/postman/fiapx-video-api.postman_collection.json`](./docs/postman/fiapx-video-api.postman_collection.json).

## Concorrência: virtual threads

Habilito `spring.threads.virtual.enabled=true` nos 4 serviços (Java 21, JEP 444) — relevante principalmente no `video-api`, que precisa aceitar muitos uploads concorrentes sem esgotar um pool fixo de threads (RF1/RF2 do enunciado).

## Fluxo de branches

Faço todo trabalho em `develop`. Mantenho a `main` protegida e ela só recebe código via Pull Request — nunca commit direto (inclusive de quem administra o repositório).

## Estado atual

**Sprints 0–7 concluídas** (Spring Boot 4.1.0, Java 21 — ver [ADR-007](./docs/architecture/hld-lld-adr-rfc.md#adr-007--linguagens-e-versão-de-runtime-dos-serviços)): pipeline fim a fim (upload → fila → `ffmpeg` → zip), autenticação JWT, API + Gateway, suíte de testes automatizados com piso de 90% de cobertura, deploy em Kubernetes local (HPA + KEDA validados ao vivo), frontend web completo (React), notificação multicanal resiliente (e-mail + webhook, Circuit Breaker + Bulkhead isolados por canal), e observabilidade completa (métricas de negócio, logging JSON estruturado, correlation-id ponta a ponta, `kube-prometheus-stack`).

**Sprint 8** (Kubernetes gerenciado): cluster EKS 1.36 no Learner Lab via Terraform, ECR, ingress-nginx com ELB e add-ons, aplicados e validados ao vivo. **Sprint 9** (correções do núcleo e serviços gerenciados): nome interno do arquivo e timeout do `ffmpeg`, publisher confirms, DLQ de resultados, lease na outbox, `eventId` nos contratos, estado `PROCESSING` real, rotas `/admin` no ingress e rate limit por `X-Forwarded-For`; perfil `aws` com S3, SQS, RDS e ElastiCache (Terraform em `k8s/terraform/aws/`, overlay em `k8s/apps/overlays/aws/`, workflows `terraform-aws.yml` / `cd-aws.yml` / `destroy-aws.yml`) — ver [ADR-012](./docs/architecture/hld-lld-adr-rfc.md#adr-012--sem-nuvem-pública-como-padrão-de-execução) e [ADR-014](./docs/architecture/hld-lld-adr-rfc.md#adr-014--serviços-gerenciados-por-perfil-de-execução). Os gerenciados estão validados por testes (LocalStack) e estaticamente; a aplicação ao vivo com eles é a próxima sessão do lab.

## Deploy na AWS (EKS)

Tudo roda pelo GitHub Actions, no Environment `AWS`. Os únicos secrets obrigatórios no GitHub são `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` do Learner Lab (expiram a cada sessão). Os segredos da aplicação ficam no **SSM Parameter Store** (`/fiapx/db/username`, `/fiapx/db/password`, `/fiapx/jwt/secret`, `/fiapx/notification/webhook-url`), criados pelo Terraform: se `PROD_DB_PASSWORD`, `PROD_JWT_SECRET` ou `PROD_NOTIFICATION_WEBHOOK_URL` existirem no GitHub, o Terraform usa esses valores; se não, gera senha e segredo JWT (`random_password`, estáveis no state) — o webhook só existe se informado. O deploy lê os parâmetros por nome e monta o Secret `fiapx-secrets`. Um push em `main` (ou o `CD - AWS EKS` manual) faz tudo sozinho: provisiona o que faltar, builda e faz o deploy. Os workflows:

1. `CD - AWS EKS` (`cd-aws.yml`, a cada push em `main` ou manual) — job `provision`: `scripts/aws-up.sh --apply-if-changed` roda `terraform plan`; com a infra no ar e igual ao código é um no-op de ~2 min, senão aplica o plano (~25 min na primeira vez) — VPC, cluster EKS (`t3.large` ×2, add-on EBS CSI), 5 repositórios ECR, RDS PostgreSQL 17 (`db.t3.micro`), ElastiCache Redis 7.1 (`cache.t3.micro`), 3 filas SQS com DLQ e os parâmetros SSM. Os 2 buckets S3 privados são criados antes do `plan` por `scripts/aws-buckets-init.sh` (a SCP do Learner Lab nega `s3:GetBucketObjectLockConfiguration`, que o provider AWS chama ao ler um `aws_s3_bucket`). State no bucket S3 `fiapx-terraform-state-<account>`, criado automaticamente por `scripts/aws-tf-init.sh`.
2. Ainda no `CD - AWS EKS`, jobs `build-and-push` e `deploy` — builda as 5 imagens, publica no ECR e roda `scripts/k8s-deploy-aws.sh`: descobre RDS, ElastiCache, fila SQS e buckets por nome via `aws` CLI, injeta os hosts no ConfigMap, instala add-ons (ingress-nginx, metrics-server, KEDA, kube-prometheus-stack), roda a migração e aplica a aplicação com o perfil `aws`. Nenhum Postgres/RabbitMQ/MinIO sobe no cluster e o job de deploy não recebe secret nenhum do GitHub além das credenciais AWS. A URL pública (hostname do ELB do `ingress-nginx`) sai no resumo do job. No disparo manual, o input `provision: skip` pula o Terraform e só faz build e deploy. `Terraform - AWS EKS` (`terraform-aws.yml`) continua disponível para rodar `plan`, `apply` ou `destroy` isolados à mão; os três workflows compartilham o grupo de concorrência `terraform-aws`, então nunca tocam o state ao mesmo tempo.
3. `Destroy AWS` (`destroy-aws.yml`) — ao fim de cada sessão: `scripts/aws-destroy.sh` (limpeza k8s → `terraform destroy` → varredura via `aws` CLI independente do state, incluindo RDS, ElastiCache, filas `fiapx-*`, buckets `fiapx-videos-*` e parâmetros SSM `/fiapx/*` → `scripts/aws-validate.sh --strict`).

Os mesmos scripts funcionam localmente com `aws`, `terraform`, `kubectl`, `kustomize` e `helm` instalados (`scripts/aws-up.sh`, `scripts/aws-validate.sh`). Para renovar os 3 secrets a cada sessão do lab, copie o bloco de **AWS Details → AWS CLI → Show** e rode:

```bash
pbpaste | ./scripts/aws-sync-gh-secrets.sh --from-stdin --save-profile
```

O script valida as credenciais (`aws sts get-caller-identity`), grava o perfil `default` em `~/.aws/credentials` e atualiza os secrets no Environment `AWS` via `gh`.
