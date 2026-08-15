# FIAP X — Plataforma de Processamento de Vídeos

Reescrita arquitetural do protótipo original (`projeto-fiapx/`), feita para o Hackathon da Fase 5. Documentação completa de arquitetura (RFC, HLD, LLD, ADRs, artefatos de DDD, plano de sprints e checklist de entregáveis) vive no Obsidian, em `FIAP/Fase 5 - LGPD e Gestão e liderança/4. Hackaton/` — este README cobre só o "como rodar".

## Arquitetura em uma frase

Upload de vídeo → fila (RabbitMQ) → extração de frames (`ffmpeg`) → zip → notificação em caso de erro. Três serviços independentes, sem banco compartilhado entre eles.

| Serviço | Responsabilidade | Porta | Banco próprio |
|---|---|---|---|
| `video-api` | Upload, autenticação (JWT), listagem/consulta de status, download | 8081 | Postgres, schema `video_api` |
| `video-worker` | Consome a fila, roda `ffmpeg`, gera o `.zip` — **stateless**, sem acesso a banco (ver ADR-008) | 8082 | nenhum |
| `notification-worker` | Consome eventos de falha, envia e-mail (+ webhook, incremento futuro) | 8083 | Postgres, schema `notification_worker` |

Infra: PostgreSQL, RabbitMQ, Redis, MinIO (S3-compatible), Mailhog (SMTP local). Detalhes e diagramas completos em `Hackaton - Documentação de Arquitetura (HLD, LLD, ADR, RFC)` no Obsidian.

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

Cada serviço é um projeto Maven independente (não é um multi-módulo reactor) — propositalmente, para que qualquer um possa ser extraído para um repositório próprio no futuro sem alterar código (ver `Plano de Implementação - Sprints Detalhados` no Obsidian).

```bash
cd video-api && ./mvnw -B verify
cd video-worker && ./mvnw -B verify
cd notification-worker && ./mvnw -B verify
```

CI: cada serviço tem seu próprio workflow em `.github/workflows/`, disparado só quando arquivos daquele serviço mudam (`paths:` filter) — simula pipeline independente por microsserviço mesmo dentro do monorepo.

## Estado atual

**Sprint 0 concluída**: esqueleto dos 3 serviços (Spring Boot 4.1.0 — ver nota abaixo —, Java 21), `docker-compose.yml` local completo, Dockerfiles multi-stage, CI mínimo (`mvn verify` por serviço com path-filter). Ainda sem lógica de negócio — isso é a Sprint 1 (pipeline fim a fim: upload → fila → `ffmpeg` → zip → status).

Acompanhamento detalhado, sprint a sprint: `Checklist-Geral-Hackathon.md` e `Plano de Implementação - Sprints Detalhados (50 dias).md` no Obsidian.

> [!note] Desvio consciente do ADR-007 original
> O ADR-007 (Obsidian) decidiu "Java 21 + Spring Boot 3". Ao gerar o esqueleto em 2026-08-15, o Spring Initializr recusou Spring Boot 3.x (`compatibility range is >=4.0.0` — a linha 3.x está em manutenção, sem novas features). Optamos por **Spring Boot 4.1.0**, mantendo Java 21 como decidido. A justificativa original do ADR-007 (ecossistema maduro, risco de execução baixo) continua válida — é a mesma stack, só a versão maior mudou. ADR a atualizar no Obsidian antes da Sprint 7 (fechamento de documentação).
