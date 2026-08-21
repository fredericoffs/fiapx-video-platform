# fiapx video platform — web

SPA (React 19 + TypeScript + Vite) que consome exclusivamente o `video-gateway`.

## Setup

```bash
cp .env.example .env   # ajuste VITE_API_BASE_URL se necessário
npm install
npm run dev
```

## Scripts

- `npm run dev` — servidor de desenvolvimento (Vite).
- `npm run build` — typecheck (`tsc -b`) + build de produção.
- `npm run lint` — ESLint (`typescript-eslint` strict, `eslint-plugin-boundaries`).
- `npm run typecheck` — só o typecheck.
- `npm run format` / `format:check` — Prettier.
- `npm run codegen` — regenera `src/shared/api/schema.gen.ts` a partir do OpenAPI real do
  `video-api`. **Precisa do `video-api` de pé em `http://localhost:8081`**:
  ```bash
  cd .. && docker compose --profile app up -d postgres rabbitmq redis minio minio-init video-api
  cd web && npm run codegen
  ```
  O CI falha se o schema commitado divergir do gerado (`git diff --exit-code` depois de rodar
  o codegen contra o serviço real) — sempre rode `npm run codegen` e commite o resultado depois
  de qualquer mudança de contrato no `video-api`.
- `npm test` / `test:watch` — Vitest + Testing Library (componentes/hooks).
- `npm run test:e2e` — Playwright, 1 spec do fluxo feliz completo (registro→login→upload→
  status→download). Sobe o próprio `npm run dev` como servidor; precisa da stack real de pé
  (`docker compose --profile app up`) pra passar de verdade.

## Deploy

`Dockerfile` multi-stage (`node:22-alpine` build → `nginx:alpine` serve, não-root, escuta em
`8080`). `VITE_API_BASE_URL` é _build-time_ (baked no bundle estático) — passe como build arg se
o gateway não estiver em `http://localhost:8080`. Servido pelo serviço `web` no
`docker-compose.yml` (porta `5173`) e pelos manifests em `k8s/apps/base/web/`.

## Estrutura

Organização feature-based (reforçada por `eslint-plugin-boundaries` — sem import cruzado entre
`features/*` a não ser via `shared/`):

```
src/
  app/            # bootstrap: providers, layout raiz, rotas
  features/
    auth/         # login, registro
    upload/       # tela e lógica de upload
    videos/       # listagem de status, download
  shared/
    api/          # cliente gerado (OpenAPI) + wrapper openapi-fetch
    ui/           # componentes shadcn/ui reutilizáveis
    hooks/
    lib/          # session store (Zustand), utils
```
