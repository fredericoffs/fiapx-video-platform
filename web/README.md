# fiapx video platform — web

[![CI](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml)
[![Qodana](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/qodana.yml/badge.svg?branch=develop)](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/qodana.yml)
![Cobertura](https://img.shields.io/badge/cobertura%20Qodana-82%25-yellowgreen)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Vitest](https://img.shields.io/badge/Vitest-unit%20%2B%20coverage-6E9F18?logo=vitest&logoColor=white)
![Playwright](https://img.shields.io/badge/Playwright-e2e-2EAD33?logo=playwright&logoColor=white)

SPA (React 19 + TypeScript + Vite) que consome exclusivamente o `video-gateway`.

## Setup

```bash
cp .env.example .env   # aponte VITE_API_BASE_URL para um video-gateway acessível (não há mais stack local)
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
  `video-api` (`VITE_API_BASE_URL`, ou `http://localhost:8081` por padrão). Precisa de um
  `video-api` alcançável — rode `cd ../video-api && ./mvnw spring-boot:run` contra a sua própria
  infra, ou aponte para um `video-api` já implantado. Sempre rode `npm run codegen` e commite o
  resultado depois de qualquer mudança de contrato no `video-api`.
- `npm test` / `test:watch` — Vitest + Testing Library (componentes/hooks).
- `npm run test:e2e` — Playwright, 1 spec do fluxo feliz completo (registro→login→upload→
  status→download). Contra o ambiente implantado: `E2E_BASE_URL=https://<host-do-nlb> npm run test:e2e`
  (não sobe servidor local e aceita o certificado autoassinado do ingress; na primeira vez,
  `npx playwright install chromium`). Sem `E2E_BASE_URL`, sobe o próprio `npm run dev` e precisa
  de `VITE_API_BASE_URL` apontando pra um backend real.

## Deploy

`Dockerfile` multi-stage (`node:22-alpine` build → `nginx:alpine` serve, não-root, escuta em
`8080`). `VITE_API_BASE_URL` é _build-time_ (baked no bundle estático) — passe como build arg
com a URL pública do `video-gateway`. Servido pelo Deployment `web` em `k8s/apps/base/web/`.

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
