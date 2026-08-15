# FIAP X — Plataforma de Processamento de Vídeos

Reescrita arquitetural do protótipo original (`projeto-fiapx`), feita para o Hackathon da Fase 5. Documentação de arquitetura (RFC, HLD, LLD, ADRs, artefatos de DDD) vive em [`docs/`](./docs) — este README cobre só o "como rodar".

## Arquitetura em uma frase

Upload de vídeo → fila (RabbitMQ) → extração de frames (`ffmpeg`) → zip → notificação em caso de erro. Três serviços independentes, sem banco compartilhado entre eles.

| Serviço | Responsabilidade | Porta | Banco próprio |
|---|---|---|---|
| `video-api` | Upload, autenticação (JWT), listagem/consulta de status, download | 8081 | Postgres, schema `video_api` |
| `video-worker` | Consome a fila, roda `ffmpeg`, gera o `.zip` — **stateless**, sem acesso a banco (ver [ADR-008](./docs/architecture/hld-lld-adr-rfc.md#adr-008)) | 8082 | nenhum |
| `notification-worker` | Consome eventos de falha, envia e-mail (+ webhook, incremento futuro) | 8083 | Postgres, schema `notification_worker` |

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

**4. Ou suba tudo containerizado** (infra + os 3 serviços, cada um com seu Dockerfile multi-stage):

```bash
docker compose --profile app up --build
```

**5. Confirmar que subiu:**

```bash
curl http://localhost:8081/actuator/health   # video-api
curl http://localhost:8082/actuator/health   # video-worker
curl http://localhost:8083/actuator/health   # notification-worker
```

Cada um deve responder `{"status":"UP"}`.

## Build e testes

Cada serviço é um projeto Maven independente (não é um multi-módulo reactor) — propositalmente, para que qualquer um possa ser extraído para um repositório próprio no futuro sem alterar código.

```bash
cd video-api && ./mvnw -B verify
cd video-worker && ./mvnw -B verify
cd notification-worker && ./mvnw -B verify
```

CI: cada serviço tem seu próprio workflow em `.github/workflows/`, disparado só quando arquivos daquele serviço mudam (`paths:` filter) — simula pipeline independente por microsserviço mesmo dentro do monorepo.

## Concorrência: virtual threads

Os 3 serviços rodam com `spring.threads.virtual.enabled=true` (Java 21, JEP 444) — relevante principalmente no `video-api`, que precisa aceitar muitos uploads concorrentes sem esgotar um pool fixo de threads (RF1/RF2 do enunciado).

## Fluxo de branches

Todo trabalho acontece em `develop`. A `main` fica protegida e só recebe código via Pull Request — nunca commit direto (inclusive de quem administra o repositório).

## Estado atual

**Sprint 0 concluída**: esqueleto dos 3 serviços (Spring Boot 4.1.0, Java 21 — ver [ADR-007](./docs/architecture/hld-lld-adr-rfc.md#adr-007--linguagens-e-versão-de-runtime-dos-serviços)), `docker-compose.yml` local completo, Dockerfiles multi-stage, CI mínimo (`mvn verify` por serviço com path-filter). Ainda sem lógica de negócio — isso é a Sprint 1 (pipeline fim a fim: upload → fila → `ffmpeg` → zip → status).
