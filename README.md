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
| Processamento concorrente/enfileirado           |   ✅   | RabbitMQ + `video-worker` escalado por KEDA — sem requisição travada esperando o `ffmpeg` |
| Autenticação usuário/senha                      |   ✅   | `video-api` (JWT) — `/auth/register`, `/auth/login`                                       |
| Listagem de status por usuário                  |   ✅   | `video-api` `/videos` (escopado por JWT, um usuário nunca vê vídeo de outro)               |
| Notificação de erro (e-mail ou similar)         |   ✅   | `notification-worker` — e-mail com fallback para webhook, Circuit Breaker + Bulkhead por canal |
| Armazenamento persistente                       |   ✅   | Postgres (schema próprio por serviço) + MinIO (S3-compatible, vídeos/zips)                 |
| Arquitetura horizontalmente escalável           |   ✅   | HPA (`video-api`) + KEDA (`video-worker`), serviços stateless sem sessão em memória         |
| Testes automatizados e CI/CD                    |   ✅   | JaCoCo ≥90% linha (gate no `mvn verify`) + GitHub Actions (CI) a cada push/PR      |
| Docker / Kubernetes                             |   ✅   | `docker-compose.yml` (dev) + manifests em [`k8s/`](./k8s) (cluster kind validado ao vivo) + overlay `aws` e Terraform (`k8s/terraform/aws/`) para EKS |
| Message broker (RabbitMQ)                       |   ✅   | RabbitMQ com topologia de DLQ própria por fila                                             |
| Postgres + Redis                                |   ✅   | Postgres por serviço; Redis no rate limiting de borda (`video-gateway`)                    |
| Monitoramento (Prometheus/Grafana, ELK, etc.)   |   ✅   | `kube-prometheus-stack` (Prometheus + Grafana + Alertmanager) via Helm, 3 dashboards + alerta de profundidade de fila, métricas de negócio e correlation-id ponta a ponta |

## Arquitetura em uma frase

Upload de vídeo → fila (RabbitMQ) → extração de frames (`ffmpeg`) → zip → notificação em caso de erro. Quatro serviços independentes, sem banco compartilhado entre eles.

| Serviço               | Responsabilidade                                                                                                                                                        | Porta | Banco próprio                          |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|----------------------------------------|
| `video-gateway`       | API Gateway (roteamento, CORS, rate limiting de borda) — ver [ADR-009](./docs/architecture/hld-lld-adr-rfc.md#adr-009--api-gateway-spring-cloud-gateway-em-vez-de-kong) | 8080  | nenhum                                 |
| `video-api`           | Upload, autenticação (JWT), listagem/consulta de status, download                                                                                                       | 8081  | Postgres, schema `video_api`           |
| `video-worker`        | Consome a fila, roda `ffmpeg`, gera o `.zip` — **stateless**, sem acesso a banco (ver [ADR-008](./docs/architecture/hld-lld-adr-rfc.md#adr-008))                        | 8082  | nenhum                                 |
| `notification-worker` | Consome eventos de falha, envia e-mail com fallback para webhook — Circuit Breaker + Bulkhead isolados por canal (Resilience4j)                                        | 8083  | Postgres, schema `notification_worker` |

Infra: PostgreSQL, RabbitMQ, Redis, MinIO (S3-compatible), Mailhog (SMTP local). Diagramas completos em [`docs/architecture/hld-lld-adr-rfc.md`](./docs/architecture/hld-lld-adr-rfc.md).

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

**Qualidade e cobertura**: cobertura de teste com piso de 90% (linha, JaCoCo) nos 4 serviços — gate no `mvn verify` (`jacoco:check`), relatório publicado como artefato do CI.

**Análise de segurança e qualidade (sob demanda)**: dois workflows manuais (`workflow_dispatch`, aba Actions), fora da esteira de cada push para não atrasá-la. `Security scan` roda OWASP Dependency-Check nas dependências Maven dos 4 serviços, `npm audit` no `web` e Trivy nas 5 imagens Docker, publicando os relatórios (HTML e SARIF) como artefatos do run. `Qodana` roda a análise estática JetBrains nos projetos JVM e web, com cobertura JaCoCo/lcov anexada.

**Script de criação do banco de dados**: migrations Flyway versionadas por serviço — [`video-api/src/main/resources/db/migration`](./video-api/src/main/resources/db/migration) (`V1__init.sql` a `V4__outbox_correlation_id.sql`: vídeos, outbox, usuários, papel admin, correlation-id) e [`notification-worker/src/main/resources/db/migration`](./notification-worker/src/main/resources/db/migration) (`V1__init.sql`: registro de notificações). Cada serviço migra só o próprio schema; no Kubernetes a migração roda num Job antes do Deployment do `video-api`. Os buckets do MinIO são criados por `minio-init` (Compose) ou pelo Job `minio-init` (`k8s/infra/minio/init-job.yaml`).

**Documentação de arquitetura**: [`docs/architecture/hld-lld-adr-rfc.md`](./docs/architecture/hld-lld-adr-rfc.md) (RFC, HLD, LLD com diagramas ER por schema, 13 ADRs) e os RFCs curtos [`RFC-001`](./docs/architecture/rfc/RFC-001-rabbitmq-vs-kafka.md) (RabbitMQ vs Kafka) e [`RFC-002`](./docs/architecture/rfc/RFC-002-spring-cloud-gateway-vs-kong.md) (Spring Cloud Gateway vs Kong). Índice completo em [`docs/README.md`](./docs/README.md).

**Documentação da API**: cada serviço expõe Swagger UI em `/swagger-ui.html` (OpenAPI 3.1 em `/v3/api-docs`, via [springdoc-openapi](https://springdoc.org/)) — mais relevante no `video-api`, que tem os endpoints de negócio (`/auth/**`, `/videos/**`). Postman collection correspondente versionada em [`docs/postman/fiapx-video-api.postman_collection.json`](./docs/postman/fiapx-video-api.postman_collection.json).

## Concorrência: virtual threads

Habilito `spring.threads.virtual.enabled=true` nos 4 serviços (Java 21, JEP 444) — relevante principalmente no `video-api`, que precisa aceitar muitos uploads concorrentes sem esgotar um pool fixo de threads (RF1/RF2 do enunciado).

## Fluxo de branches

Faço todo trabalho em `develop`. Mantenho a `main` protegida e ela só recebe código via Pull Request — nunca commit direto (inclusive de quem administra o repositório).

## Estado atual

**Sprints 0–7 concluídas** (Spring Boot 4.1.0, Java 21 — ver [ADR-007](./docs/architecture/hld-lld-adr-rfc.md#adr-007--linguagens-e-versão-de-runtime-dos-serviços)): pipeline fim a fim (upload → fila → `ffmpeg` → zip), autenticação JWT, API + Gateway, suíte de testes automatizados com piso de 90% de cobertura, deploy em Kubernetes local (HPA + KEDA validados ao vivo), frontend web completo (React), notificação multicanal resiliente (e-mail + webhook, Circuit Breaker + Bulkhead isolados por canal), e observabilidade completa (métricas de negócio, logging JSON estruturado, correlation-id ponta a ponta, `kube-prometheus-stack`).

Em andamento: deploy real em nuvem via Kubernetes gerenciado (AWS EKS, Learner Lab) — Terraform em `k8s/terraform/aws/`, overlay em `k8s/apps/overlays/aws/` e workflows `terraform-aws.yml` / `cd-aws.yml` / `destroy-aws.yml`, ver a emenda AWS no [ADR-012](./docs/architecture/hld-lld-adr-rfc.md#adr-012--sem-nuvem-pública-como-padrão-de-execução). Validado estaticamente (`terraform validate`, `kubectl kustomize`); ainda não aplicado contra a conta.

## Deploy na AWS (EKS)

Tudo roda pelo GitHub Actions, no Environment `AWS` (secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` do Learner Lab — expiram a cada sessão — e os `PROD_*` da aplicação). Ordem:

1. `Terraform - AWS EKS` (`terraform-aws.yml`, `action: plan` e depois `apply`) — VPC, cluster EKS (`t3.large` ×2, add-on EBS CSI), 5 repositórios ECR. State no bucket S3 `fiapx-terraform-state-<account>`, criado automaticamente por `scripts/aws-tf-init.sh`.
2. `CD - AWS EKS` (`cd-aws.yml`, a cada push em `main` ou manual) — builda as 5 imagens, publica no ECR e roda `scripts/k8s-deploy-aws.sh` (add-ons, infra self-hosted, migração, aplicação). A URL pública (hostname do ELB do `ingress-nginx`) sai no resumo do job.
3. `Destroy AWS` (`destroy-aws.yml`) — ao fim de cada sessão: `scripts/aws-destroy.sh` (limpeza k8s → `terraform destroy` → varredura via `aws` CLI independente do state → `scripts/aws-validate.sh --strict`).

Os mesmos scripts funcionam localmente com `aws`, `terraform`, `kubectl`, `kustomize` e `helm` instalados (`scripts/aws-up.sh`, `scripts/aws-validate.sh`). Para renovar os 3 secrets a cada sessão do lab, copie o bloco de **AWS Details → AWS CLI → Show** e rode:

```bash
pbpaste | ./scripts/aws-sync-gh-secrets.sh --from-stdin --save-profile
```

O script valida as credenciais (`aws sts get-caller-identity`), grava o perfil `default` em `~/.aws/credentials` e atualiza os secrets no Environment `AWS` via `gh`.
